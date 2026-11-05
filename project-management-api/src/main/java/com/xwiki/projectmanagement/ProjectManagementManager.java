package com.xwiki.projectmanagement;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.xwiki.component.annotation.Role;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Offers access to the CRUD operations of different project management clients.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface ProjectManagementManager
{
    /**
     * Retrieve a work item from a specific project management client.
     *
     * @param client the hint of the client implementation.
     * @param workItemId the identifier of the work item that needs retrieval.
     * @return the work item matching the id.
     * @throws WorkItemException if the work item was not found.
     */
    WorkItem getWorkItem(String client, String workItemId) throws WorkItemException;

    /**
     * Retrieve a list of work items based on a filter.
     *
     * @param client the hint of the client implementation.
     * @param offset the index, starting at 0, of the first work item to retrieve among all the work items matching
     *     the filters.
     * @param pageSize the maximum number of items the result can have.
     * @param filters a list of filters that the returned items must satisfy.
     * @param sortEntries a list of sort entries that denote how the results should be arranged.
     * @return a paginated result containing the list of items that satisfy the filters.
     * @throws WorkItemException if there was an exception during the retrieval of the tasks.
     */
    PaginatedResult<WorkItem> getWorkItems(String client, int offset, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries)
        throws WorkItemException;

    /**
     * Create a work item.
     *
     * @param client the hint of the client implementation.
     * @param workItem the work item that will be created.
     * @return the work item that was created.
     * @throws WorkItemCreationException if there was an exception during the creation of the work item.
     */
    WorkItem createWorkItem(String client, WorkItem workItem) throws WorkItemException;

    /**
     * Update a work item.
     *
     * @param client the hint of the client implementation.
     * @param workItem the work item that will be updated.
     * @return the work item that was updated.
     * @throws WorkItemException if there was an exception during the updating of the work item.
     */
    WorkItem updateWorkItem(String client, WorkItem workItem) throws WorkItemException;

    /**
     * The work item that will be deleted.
     *
     * @param client the hint of the client implementation.
     * @param workItemId the id of the work item that needs to be deleted.
     * @return true if the work item was deleted; false otherwise.
     * @throws WorkItemException if there was any exception during the deletion of the work item.
     */
    boolean deleteWorkItem(String client, String workItemId) throws WorkItemException;

    /**
     * Retrieves all the work items matching the filters, page by page, up to a maximum number of work items, e.g. to
     * aggregate them in a chart.
     *
     * @param client the hint of the client implementation
     * @param filters the filters that the returned work items must match
     * @param sortEntries how the work items are sorted, which decides the ones left out when more than the maximum
     *     number of work items match the filters
     * @param max the maximum number of work items to retrieve
     * @return the retrieved work items, at most {@code max}, with the total number of work items matching the filters
     * @throws WorkItemException if the work items can't be retrieved
     * @since 1.4.0
     */
    PaginatedResult<WorkItem> getAllWorkItems(String client, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries, int max) throws WorkItemException;

    /**
     * Counts the work items matching the filters by value of a property, without retrieving them.
     *
     * @param client the hint of the client implementation
     * @param filters the filters that the counted work items must match
     * @param property the id of the property whose values group the work items
     * @return the number of work items by value of the property, the {@code null} key counting the work items
     *     without a value; empty when the client can't count the work items by this property
     * @throws WorkItemException if the work items can't be counted
     * @see ProjectManagementClient#countWorkItems(List, String)
     * @since 1.4.0
     */
    Optional<Map<String, Long>> countWorkItems(String client, List<LiveDataQuery.Filter> filters, String property)
        throws WorkItemException;

    /**
     * @param client the hint of the client implementation
     * @param filters the filters that the work items must match
     * @param sortEntries how the work items are sorted
     * @return the URL of the page of the project management platform listing the work items matching the filters;
     *     empty when the client can't tell
     * @throws WorkItemException if the client can't be found
     * @see ProjectManagementClient#getWorkItemsLocation(List, List)
     * @since 1.4.0
     */
    Optional<String> getWorkItemsLocation(String client, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemException;
}
