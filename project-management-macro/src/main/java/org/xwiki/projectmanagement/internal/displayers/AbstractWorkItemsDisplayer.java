package org.xwiki.projectmanagement.internal.displayers;

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

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Abstract macro that processes the parameters before sending them to the project management client, retrieving the
 * work items.
 *
 * @param <T> the type of the macro parameters.
 * @version $Id$
 */
public abstract class AbstractWorkItemsDisplayer<T extends ProjectManagementMacroParameters> extends AbstractMacro<T>
{
    @Inject
    protected ProjectManagementManager projectManagementManager;

    @Inject
    protected ProjectManagementClientExecutionContext macroContext;

    /**
     * @param name the name of the work item.
     */
    public AbstractWorkItemsDisplayer(String name)
    {
        super(name);
    }

    @Override
    public List<Block> execute(T parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        String clientId = (String) macroContext.get("client");

        if (clientId == null || clientId.isEmpty()) {
            throw new MacroExecutionException("Failed to retrieve the client id from the source params.");
        }

        List<LiveDataQuery.Filter> filters = null;
        try {
            filters = getFilters(parameters.getFilters());
        } catch (Exception e) {
            throw new MacroExecutionException("Failed to parse the filters.");
        }

        PaginatedResult<WorkItem> workItemList = null;
        try {
            // TODO: Maybe separate this logic in a separate method and allow the implementations to override it?
            workItemList = projectManagementManager.getWorkItems(clientId,
                Math.toIntExact(parameters.getOffset()), parameters.getLimit(), filters);
        } catch (WorkItemException e) {
            throw new MacroExecutionException(
                String.format("Failed to retrieve the work items from the client [%s].", clientId), e);
        }

        return internalExecute(workItemList, parameters, context);
    }

    /**
     * Execute the macro being provided the work items that need displaying.
     *
     * @param workItemList a paginated result of work items matching the filter from the parameters.
     * @param parameters the parameters received in order to render the work items.
     * @param context the macro transformation context that can be used, among other things, to determine if the
     *     call of the macro is inline or not.
     * @return a list of blocks that will be rendered.
     */
    protected abstract List<Block> internalExecute(PaginatedResult<WorkItem> workItemList, T parameters,
        MacroTransformationContext context);

    @Override
    protected void setDefaultCategories(Set<String> defaultCategories)
    {
        super.setDefaultCategories(Collections.singleton("Internal"));
    }

    private Map<String, Object> getSourceParameters(String sourceParametersString) throws Exception
    {
        if (StringUtils.isEmpty(sourceParametersString)) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> urlParams = getURLParameters('?' + sourceParametersString);
        Map<String, Object> sourceParams = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : urlParams.entrySet()) {
            if (entry.getValue().size() > 1) {
                sourceParams.put(entry.getKey(), entry.getValue());
            } else {
                sourceParams.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return sourceParams;
    }

    private List<LiveDataQuery.Filter> getFilters(String filtersString) throws Exception
    {
        List<LiveDataQuery.Filter> filters =
            getURLParameters('?' + StringUtils.defaultString(filtersString)).entrySet().stream()
                .map(this::getFilter).collect(Collectors.toList());
        return filters.isEmpty() ? null : filters;
    }

    private LiveDataQuery.Filter getFilter(Map.Entry<String, List<String>> entry)
    {
        LiveDataQuery.Filter filter = new LiveDataQuery.Filter();
        filter.setProperty(entry.getKey());
        filter.getConstraints()
            .addAll(entry.getValue().stream().map(LiveDataQuery.Constraint::new).collect(Collectors.toList()));
        return filter;
    }

    private Map<String, List<String>> getURLParameters(String url) throws Exception
    {
        URL baseURL = new URL("http://www.xwiki.org");
        String queryString = new URL(baseURL, url).getQuery();
        Map<String, List<String>> parameters = new HashMap<>();
        for (String entry : queryString.split("&")) {
            String[] parts = entry.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (key.isEmpty()) {
                continue;
            }
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            List<String> values = parameters.computeIfAbsent(key, k -> new ArrayList<>());
            values.add(value);
        }
        return parameters;
    }
}
