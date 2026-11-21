package com.xwiki.projectmanagement.internal.source;

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

import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * The {@code filters} source: the content holds the filters in the Live Data JSON format of the {@code filters}
 * parameter, e.g. {@code {"query":{"filters":[{"property":"status","constraints":[{"value":"1"}]}]}}}, without the
 * escaping that a parameter value requires.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named("filters")
@Singleton
public class FiltersWorkItemSource implements WorkItemSource
{
    private static final String INVALID_CONTENT = "The macro content isn't valid Live Data JSON";

    @Inject
    private LiveDataConfigurationResolver<String> configurationResolver;

    @Override
    public WorkItemSelection read(String content, WorkItemSourceContext context) throws WorkItemSourceException
    {
        LiveDataConfiguration configuration;
        try {
            configuration = this.configurationResolver.resolve(content);
        } catch (LiveDataException | RuntimeException e) {
            throw getReadException(e);
        }
        if (configuration == null || configuration.getQuery() == null
            || configuration.getQuery().getFilters() == null)
        {
            return WorkItemSelection.ofFilters(Collections.emptyList());
        }
        return WorkItemSelection.ofFilters(configuration.getQuery().getFilters());
    }

    /**
     * The messages of the JSON parser describe the parser too (e.g. how it displays the source): only the reason of
     * a syntax error and its position are kept, and the parser exception isn't the cause, since the macro error
     * displays the message of the root cause.
     */
    private static WorkItemSourceException getReadException(Exception e)
    {
        JsonProcessingException jsonError = ExceptionUtils.throwableOfType(e, JsonProcessingException.class);
        if (jsonError == null) {
            return new WorkItemSourceException(
                String.format("%s: %s", INVALID_CONTENT, ExceptionUtils.getRootCauseMessage(e)), e);
        }
        boolean syntaxError = jsonError instanceof JsonParseException;
        StringBuilder message = new StringBuilder(syntaxError ? "The macro content isn't valid JSON" : INVALID_CONTENT);
        JsonLocation location = jsonError.getLocation();
        if (location != null && location.getLineNr() > 0) {
            message.append(String.format(" (line %d, column %d)", location.getLineNr(), location.getColumnNr()));
        }
        String reason = StringUtils.substringBefore(jsonError.getOriginalMessage(), ":").trim();
        if (syntaxError && !reason.isEmpty() && !reason.contains("[Source")) {
            message.append(": ").append(StringUtils.uncapitalize(reason));
        }
        return new WorkItemSourceException(message.append('.').toString());
    }
}
