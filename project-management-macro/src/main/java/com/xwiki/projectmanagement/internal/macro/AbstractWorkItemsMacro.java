package com.xwiki.projectmanagement.internal.macro;

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

import javax.inject.Inject;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.AbstractMacro;

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
 * @param <T> the macro parameter type.
 * @version $Id$
 * @since 1.2.0-rc-1
 */
public abstract class AbstractWorkItemsMacro<T> extends AbstractMacro<T>
{
    private static final String KEY_CLIENT = "client";

    @Inject
    protected ProjectManagementManager projectManagementManager;

    @Inject
    protected ProjectManagementClientExecutionContext macroContext;

    @Inject
    protected LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @Inject
    protected WorkItemPropertyDisplayerManager displayerManager;

    @Inject
    protected ComponentManager componentManager;

    /**
     * Constructor.
     *
     * @param name the name of the macro.
     * @param description the description of the macro.
     * @param clazz class of the parameters bean of this macro.
     */
    public AbstractWorkItemsMacro(String name, String description, Class<?> clazz)
    {
        super(name, description, clazz);
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

    protected List<LiveDataQuery.Filter> getFilters(String filtersString) throws LiveDataException
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

    protected List<LiveDataQuery.SortEntry> getSortEntries(String sort)
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
