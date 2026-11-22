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

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.descriptor.ComponentDescriptor;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * Reads the content of a work item macro with the source chosen by its {@code source} parameter, and expresses the
 * selected work items with the existing macro parameters (see {@link WorkItemSourceResult}). Reading the content has
 * no side effect, so a macro can read it before its execution, e.g. to know whether the work items are filtered on the
 * current user.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = WorkItemSourceReader.class)
@Singleton
public class WorkItemSourceReader
{
    /**
     * The source used when the {@code source} parameter isn't set.
     */
    public static final String DEFAULT_SOURCE = "query";

    /**
     * The key of the identifier of the work items in the execution context of the client.
     */
    public static final String IDENTIFIER = "identifier";

    // The property of the Live Data configuration holding its query (the same word as the default source).
    private static final String QUERY = DEFAULT_SOURCE;

    private static final String FILTERS = "filters";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    /**
     * @param request the macro reading its content
     * @return the work items selected by the macro: when the content is empty, its filters and identifier, unchanged;
     *     otherwise, its filters combined with the filters read from the content, and the identifier read from the
     *     content
     * @throws MacroExecutionException if the source doesn't exist, if the content isn't valid for the source, or if
     *     the content is used with a parameter it replaces
     */
    public WorkItemSourceResult read(WorkItemSourceRequest request) throws MacroExecutionException
    {
        if (StringUtils.isBlank(request.getContent())) {
            return new WorkItemSourceResult(request.getFilters(), request.getIdentifier(), Collections.emptyMap());
        }
        checkReplacedParameters(request);
        String source = StringUtils.defaultIfBlank(request.getSource(), DEFAULT_SOURCE).trim();
        WorkItemSelection selection;
        try {
            selection = getSource(request.getClient(), source)
                .read(request.getContent(), new WorkItemSourceContext(request.getClient()));
        } catch (WorkItemSourceException e) {
            throw new MacroExecutionException(e.getMessage(), e);
        }
        Map<String, String> clientScope = new LinkedHashMap<>(selection.getClientScope());
        String identifier = clientScope.remove(IDENTIFIER);
        // The identifier holds either the listed work items or a link: the listed work items win.
        if (!selection.getIds().isEmpty()) {
            identifier = String.join(",", selection.getIds());
        }
        String filters = request.isFiltersReplaced() ? null : request.getFilters();
        return new WorkItemSourceResult(mergeFilters(filters, selection.getFilters()), identifier, clientScope);
    }

    private static void checkReplacedParameters(WorkItemSourceRequest request) throws MacroExecutionException
    {
        if (StringUtils.isNotBlank(request.getIdentifier())) {
            throw new MacroExecutionException("The [identifier] parameter can't be used together with the macro "
                + "content: list the work items, or put their link, in the content instead.");
        }
        if (request.isFiltersReplaced() && StringUtils.isNotBlank(request.getFilters())) {
            throw new MacroExecutionException("The [filters] parameter can't be used together with the macro "
                + "content: write the filters in the content instead.");
        }
    }

    private WorkItemSource getSource(String client, String source) throws MacroExecutionException
    {
        ComponentManager componentManager = this.componentManagerProvider.get();
        List<String> hints = StringUtils.isEmpty(client) ? List.of(source) : List.of(client + '/' + source, source);
        try {
            for (String hint : hints) {
                String roleHint = findRoleHint(componentManager, hint);
                if (roleHint != null) {
                    return componentManager.getInstance(WorkItemSource.class, roleHint);
                }
            }
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException(String.format("Failed to load the source [%s].", source), e);
        }
        throw new MacroExecutionException(String.format("Unknown source [%s].", source));
    }

    /**
     * @return the hint of the source component matching the given hint, ignoring the case (e.g. {@code LIST} for the
     *     {@code list} source), or {@code null} if there is none
     */
    private static String findRoleHint(ComponentManager componentManager, String hint)
    {
        if (componentManager.hasComponent(WorkItemSource.class, hint)) {
            return hint;
        }
        List<ComponentDescriptor<WorkItemSource>> descriptors =
            componentManager.getComponentDescriptorList((Type) WorkItemSource.class);
        return descriptors.stream().map(ComponentDescriptor::getRoleHint).filter(hint::equalsIgnoreCase).findFirst()
            .orElse(null);
    }

    /**
     * @return the filters with the given filters added to their configuration, or to the configuration of each chart
     *     dataset
     */
    private String mergeFilters(String filters, List<LiveDataQuery.Filter> addedFilters)
        throws MacroExecutionException
    {
        if (addedFilters.isEmpty()) {
            return filters;
        }
        ArrayNode added = this.objectMapper.valueToTree(addedFilters);
        try {
            JsonNode root = StringUtils.isBlank(filters) ? null : this.objectMapper.readTree(filters);
            if (root == null || !root.isContainerNode() || root.isEmpty()) {
                // No filter: a single configuration (a single chart dataset).
                root = this.objectMapper.createObjectNode();
            }
            for (JsonNode configuration : root.isArray() ? root : List.of(root)) {
                if (configuration instanceof ObjectNode) {
                    addFilters((ObjectNode) configuration, added);
                }
            }
            return this.objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new MacroExecutionException("Failed to parse the filters.", e);
        }
    }

    private static void addFilters(ObjectNode configuration, ArrayNode added)
    {
        JsonNode query = configuration.get(QUERY);
        ObjectNode queryNode = query instanceof ObjectNode ? (ObjectNode) query : configuration.putObject(QUERY);
        JsonNode filters = queryNode.get(FILTERS);
        ArrayNode filtersNode = filters instanceof ArrayNode ? (ArrayNode) filters : queryNode.putArray(FILTERS);
        filtersNode.addAll(added.deepCopy());
    }
}
