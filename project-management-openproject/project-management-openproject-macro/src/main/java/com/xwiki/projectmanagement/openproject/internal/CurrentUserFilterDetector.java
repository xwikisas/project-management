/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.projectmanagement.openproject.internal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.internal.macro.PresetFiltersCombiner;

/**
 * Tells whether the work packages a macro displays are filtered on the current user ({@code me}), e.g. on the work
 * packages assigned to the current user. OpenProject can only resolve this filter for an OpenProject account: it
 * rejects it for anonymous requests.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component(roles = CurrentUserFilterDetector.class)
@Singleton
public class CurrentUserFilterDetector
{
    private static final String CURRENT_USER = "me";

    /**
     * The operators matching a text: {@code me} is then a text, not the current user. They are the operators of the
     * Live Data filters and of the {@code query_props} parameter of the OpenProject URLs.
     */
    private static final Set<String> TEXT_OPERATORS = Set.of("contains", "startsWith", "endsWith", "~", "!~", "**");

    private static final Pattern QUERY_PROPS_PATTERN = Pattern.compile("[?&]query_props=([^&#]+)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private PresetFiltersCombiner presetFiltersCombiner;

    @Inject
    private Logger logger;

    /**
     * @param presetId the id of the preset of the macro, if any
     * @param filters the filters of the macro, as a serialized Live Data configuration (or a list of configurations,
     *     one per chart dataset), if any
     * @return {@code true} if the filters of the macro, combined with the filters of its preset, filter on the
     *     current user
     */
    public boolean hasCurrentUserFilter(String presetId, String filters)
    {
        String combinedFilters = filters;
        try {
            combinedFilters = this.presetFiltersCombiner.combine(presetId, filters);
        } catch (MacroExecutionException e) {
            // The macro reports the invalid preset when it is executed.
            this.logger.debug("Failed to combine the filters of the preset [{}]: [{}].", presetId, e.getMessage());
        }
        return hasCurrentUserFilter(combinedFilters);
    }

    /**
     * @param identifier the identifier of an OpenProject macro, possibly the URL of a work package list of OpenProject,
     *     holding its filters in its {@code query_props} parameter
     * @return {@code true} if the identifier filters on the current user
     */
    public boolean hasCurrentUserFilterInIdentifier(String identifier)
    {
        Matcher matcher = QUERY_PROPS_PATTERN.matcher(StringUtils.defaultString(identifier));
        if (!matcher.find()) {
            return false;
        }
        try {
            JsonNode properties =
                this.objectMapper.readTree(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8));
            for (JsonNode filter : properties.path("f")) {
                if (isCurrentUser(filter.path("o"), filter.path("v"))) {
                    return true;
                }
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            this.logger.debug("Failed to read the filters of the identifier [{}]: [{}].", identifier, e.getMessage());
        }
        return false;
    }

    private boolean hasCurrentUserFilter(String filters)
    {
        if (StringUtils.isBlank(filters)) {
            return false;
        }
        try {
            JsonNode root = this.objectMapper.readTree(filters);
            // A chart holds one configuration per dataset.
            for (JsonNode configuration : root.isArray() ? root : List.of(root)) {
                for (JsonNode filter : configuration.path("query").path("filters")) {
                    for (JsonNode constraint : filter.path("constraints")) {
                        if (isCurrentUser(constraint.path("operator"), constraint.path("value"))) {
                            return true;
                        }
                    }
                }
            }
        } catch (JsonProcessingException e) {
            this.logger.debug("Failed to read the filters [{}]: [{}].", filters, e.getMessage());
        }
        return false;
    }

    private static boolean isCurrentUser(JsonNode operator, JsonNode value)
    {
        if (TEXT_OPERATORS.contains(operator.asText())) {
            return false;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (CURRENT_USER.equals(item.asText())) {
                    return true;
                }
            }
            return false;
        }
        return CURRENT_USER.equals(value.asText());
    }
}
