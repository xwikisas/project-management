package com.xwiki.projectmanagement.internal;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Default implementation of the {@link ProjectManagementManager}. It searches the component manager for the client
 * implementation matching the hint and passes the parameters further.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultProjectManagementManager implements ProjectManagementManager
{
    /**
     * The number of work items retrieved by each request when retrieving all of them. Retrieving a work item with all
     * its properties can be slow (e.g. OpenProject takes about 2 seconds to return 100 work packages with long
     * descriptions), so the pages are small enough for each request to be answered well before its timeout.
     */
    private static final int PAGE_SIZE = 100;

    @Inject
    private ComponentManager componentManager;

    @Override
    public WorkItem getWorkItem(String client, String workItemId) throws WorkItemException
    {
        return getClient(client).getWorkItem(workItemId);
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(String client, int offset, int pageSize,
        List<LiveDataQuery.Filter> filters, List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemException
    {
        return getClient(client).getWorkItems(offset, pageSize, filters, sortEntries);
    }

    @Override
    public WorkItem createWorkItem(String client, WorkItem workItem) throws WorkItemException
    {
        return getClient(client).createWorkItem(workItem);
    }

    @Override
    public WorkItem updateWorkItem(String client, WorkItem workItem) throws WorkItemException
    {
        return getClient(client).updateWorkItem(workItem);
    }

    @Override
    public boolean deleteWorkItem(String client, String workItemId) throws WorkItemException
    {
        return getClient(client).deleteWorkItem(workItemId);
    }

    @Override
    public PaginatedResult<WorkItem> getAllWorkItems(String client, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries, int max) throws WorkItemException
    {
        ProjectManagementClient projectManagementClient = getClient(client);
        List<WorkItem> workItems = new ArrayList<>();
        int total = 0;
        while (workItems.size() < max) {
            // The pages have a fixed size so that each one starts at a multiple of the page size, as the clients that
            // paginate by page number (e.g. OpenProject) expect.
            PaginatedResult<WorkItem> page = projectManagementClient.getWorkItems(workItems.size(),
                PAGE_SIZE, filters, sortEntries);
            List<WorkItem> pageItems = page.getItems() == null ? List.of() : page.getItems();
            int pageTotal = page.getTotalItems() == null ? 0 : page.getTotalItems();
            total = Math.max(pageTotal, workItems.size() + pageItems.size());
            workItems.addAll(pageItems.subList(0, Math.min(pageItems.size(), max - workItems.size())));
            if (pageItems.size() < PAGE_SIZE || workItems.size() >= total) {
                break;
            }
        }
        return new PaginatedResult<>(workItems, 0, max, total);
    }

    @Override
    public Optional<Map<String, Long>> countWorkItems(String client, List<LiveDataQuery.Filter> filters,
        String property) throws WorkItemException
    {
        return getClient(client).countWorkItems(filters, property);
    }

    @Override
    public Optional<String> getWorkItemsLocation(String client, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemException
    {
        return getClient(client).getWorkItemsLocation(filters, sortEntries);
    }

    private ProjectManagementClient getClient(String hint) throws WorkItemException
    {
        try {
            return componentManager.getInstance(ProjectManagementClient.class, hint);
        } catch (ComponentLookupException e) {
            throw new WorkItemException(String.format("Failed to retrieve the client with hint [%s].", hint), e);
        }
    }
}
