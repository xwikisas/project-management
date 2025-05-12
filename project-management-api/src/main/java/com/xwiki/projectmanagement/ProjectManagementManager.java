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
     * @param client the hint of the client implementation
     * @param workItemId the identifier of the work item that needs retrieval.
     * @return the work item if it was found or null if nothing was found
     * @throws WorkItemException if the work item was not found.
     */
    WorkItem getWorkItem(String client, String workItemId) throws WorkItemException;

    /**
     * Retrieve a list of work items based on a filter.
     *
     * @param client the hint of the client implementation.
     * @param page the number identifying the page that needs retrieval.
     * @param pageSize the maximum number of items the result can have.
     * @param filters a list of filters that the returned items must satisfy.
     * @return a paginated result containing the list of items that satisfy the filters.
     * @throws WorkItemException if there was an exception during the retrieval of the tasks.
     */
    PaginatedResult<WorkItem> getWorkItems(String client, int page, int pageSize, List<LiveDataQuery.Filter> filters)
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
}
