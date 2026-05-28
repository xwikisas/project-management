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
package com.xwiki.projectmanagement.api;

import java.util.List;

import org.xwiki.component.annotation.Role;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.Project;

/**
 * The blueprint for a project management projects api. It allows performing different actions on the projects of the
 * implementer.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Role
public interface ProjectsApi
{
    /**
     * @param id the unique identifier for the project within the project management platform.
     * @return the retrieved project that matches the id.
     */
    Project get(String id) throws WorkItemNotFoundException, WorkItemRetrievalException;

    /**
     * Retrieve a list of projects based on a list of filters.
     *
     * @param page the number identifying the page that needs to be retrieved.
     * @param pageSize the maximum number of items that can be present in the returned result.
     * @param filters a list of filters that the returned items need to match.
     * @return a paginated result containing the items matching the list of filters.
     */
    PaginatedResult<Project> get(int page, int pageSize, List<LiveDataQuery.Filter> filters)
        throws WorkItemRetrievalException;
}
