package com.xwiki.projectmanagement.internal.displayers;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Abstract macro that processes the parameters before sending them to the project management client, retrieving the
 * work items.
 *
 * @version $Id$
 */
public abstract class AbstractWorkItemsDisplayer extends AbstractMacro<ProjectManagementMacroParameters>
{
    private static final String KEY_CLIENT = "client";

    @Inject
    protected ProjectManagementManager projectManagementManager;

    @Inject
    protected ProjectManagementClientExecutionContext macroContext;

    @Inject
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @Inject
    private WorkItemPropertyDisplayerManager displayerManager;

    @Inject
    private ComponentManager componentManager;

    /**
     * @param name the name of the work item.
     */
    public AbstractWorkItemsDisplayer(String name, String description)
    {
        super(name, description, ProjectManagementMacroParameters.class);
    }

    @Override
    public List<Block> execute(ProjectManagementMacroParameters parameters, String content,
        MacroTransformationContext context)
        throws MacroExecutionException
    {
        String clientId = (String) macroContext.get(KEY_CLIENT);

        if (clientId == null || clientId.isEmpty()) {
            throw new MacroExecutionException("Failed to retrieve the client id from the source params.");
        }

        List<LiveDataQuery.Filter> filters;
        try {
            filters = getFilters(content);
        } catch (LiveDataException e) {
            throw new MacroExecutionException("Failed to parse the filters.");
        }
        List<LiveDataQuery.SortEntry> sortEntries = getSortEntries(parameters.getSort());

        PaginatedResult<WorkItem> workItemList = null;
        try {
            // TODO: Maybe separate this logic in a separate method and allow the implementations to override it?
            workItemList = getWorkItems(clientId, parameters, filters, sortEntries);
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
    protected abstract List<Block> internalExecute(PaginatedResult<WorkItem> workItemList,
        ProjectManagementMacroParameters parameters,
        MacroTransformationContext context);

    @Override
    protected void setDefaultCategories(Set<String> defaultCategories)
    {
        super.setDefaultCategories(Collections.singleton("Internal"));
    }

    protected PaginatedResult<WorkItem> getWorkItems(String clientId, ProjectManagementMacroParameters parameters,
        List<LiveDataQuery.Filter> filters, List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemException
    {
        long offset = parameters.getOffset() == null ? 0 : parameters.getOffset();
        return projectManagementManager.getWorkItems(clientId, Math.toIntExact(offset),
            parameters.getLimit(), filters, sortEntries);
    }

    protected WorkItemPropertyDisplayerManager getPropertyDisplayerManager()
    {
        String clientId = (String) macroContext.get(KEY_CLIENT);
        if (clientId == null || clientId.isEmpty()) {
            return displayerManager;
        }
        try {
            return componentManager.getInstance(WorkItemPropertyDisplayerManager.class, clientId);
        } catch (ComponentLookupException e) {
            return displayerManager;
        }
    }

    private List<LiveDataQuery.Filter> getFilters(String filtersString) throws LiveDataException
    {
        String serializedCfg = filtersString == null ? "" : filtersString;
        LiveDataConfiguration configuration = this.stringLiveDataConfigResolver.resolve(serializedCfg);
        if (configuration == null) {
            return Collections.emptyList();
        }

        if (configuration.getQuery() == null || configuration.getQuery().getFilters() == null) {
            return Collections.emptyList();
        }
        return configuration.getQuery().getFilters();
    }

    private List<LiveDataQuery.SortEntry> getSortEntries(String sort)
    {
        if (sort == null || sort.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LiveDataQuery.SortEntry> sortEntries = new ArrayList<>();
        for (String strSortEntry : sort.split("\\s*,\\s*")) {
            String[] components = strSortEntry.split(":");
            String property = components[0];
            LiveDataQuery.SortEntry sortEntry = new LiveDataQuery.SortEntry(property);
            if (components.length >= 2) {
                sortEntry.setDescending("desc".equals(components[1]));
            }
            sortEntries.add(sortEntry);
        }
        return sortEntries;
    }
}
