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
package com.xwiki.projectmanagement.openproject.apiclient;

import org.xwiki.component.annotation.Role;

import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Open project get items API Client.
 *
 * @version $Id$
 */
@Role
public interface OpenProjectApiClient
{
    /**
     * Retrieves a paginated list of {@link WorkItem} objects from the OpenProject API.
     *
     * @param offset the page number to retrieve (1-based indexing)
     * @param pageSize the number of work items per page
     * @param connectionUrl the base URL of the OpenProject instance to connect to
     * @param token the API access token used for authenticating with the OpenProject instance
     * @param filters the list of filters to apply when retrieving work items
     * @return a {@link PaginatedResult} containing the list of retrieved {@link WorkItem} objects along with pagination
     *     metadata
     */
    PaginatedResult<WorkItem> getWorkItems(int offset, int pageSize, String connectionUrl, String token,
        String filters);
}
