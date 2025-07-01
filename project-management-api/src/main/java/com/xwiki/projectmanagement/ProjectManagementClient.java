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

import org.xwiki.component.annotation.Role;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * The blueprint for a project management client. A client is responsible with providing the CRUD operations over the
 * work items of a specific project management platform.
 *
 * @version $Id$
 * @since 1.0
 */
@Role
public interface ProjectManagementClient
{
    /**
     * Retrieve a work item.
     *
     * @param workItemId the id of the work item that needs retrieval.
     * @return the work item that matches the passed id.
     * @throws WorkItemNotFoundException if the work item has not been found.
     */
    WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException;

    /**
     * Retrieve a list of work items based on a list of filters.
     *
     * @param page the number identifying the page that needs to be retrieved.
     * @param pageSize the maximum number of items that can be present in the returned result.
     * @param filters a list of filters that the returned items need to match.
     * @param sortEntries a list of {@link org.xwiki.livedata.LiveDataQuery.SortEntry} objects that denote how the
     *     results should be sorted.
     * @return a paginated result containing the items matching the list of filters.
     * @throws WorkItemRetrievalException if there was any exception during the retrieval of the work item.
     */
    PaginatedResult<WorkItem> getWorkItems(int page, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries) throws
        WorkItemRetrievalException;

    /**
     * Creates a work item.
     *
     * @param workItem the work item that needs to be created.
     * @return the created work item.
     * @throws WorkItemCreationException if there was an exception while creating the work item.
     */
    WorkItem createWorkItem(WorkItem workItem) throws WorkItemCreationException;

    /**
     * Updates an existing work item.
     *
     * @param workItem the new state of the work item that will be updated.
     * @return the updated work item.
     * @throws WorkItemUpdatingException if there was an exception while updating the work item, such as the work
     *     item not existing.
     */
    WorkItem updateWorkItem(WorkItem workItem) throws WorkItemUpdatingException;

    /**
     * Deletes a work item.
     *
     * @param workItemId the id of the work item that needs deletion.
     * @return true if the work item was deleted, false otherwise.
     * @throws WorkItemDeletionException if there was exception while deleting the work item, such as the item not
     *     existing.
     */
    boolean deleteWorkItem(String workItemId) throws WorkItemDeletionException;
}
