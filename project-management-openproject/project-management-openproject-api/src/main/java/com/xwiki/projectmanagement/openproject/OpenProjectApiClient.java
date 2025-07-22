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

package com.xwiki.projectmanagement.openproject;

import org.xwiki.component.annotation.Role;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

/**
 * Defines the methods that a OpenProject api client should implement.
 *
 * @version $Id$
 */
@Role
public interface OpenProjectApiClient
{
    /**
     * Retrieves a list of available work packages from the current OpenProject configuration.
     *
     * @param offset the offset index from which to start retrieving work items.
     * @param pageSize the maximum number of work items to return.
     * @param filters optional filters to apply (e.g. query parameters encoded as a string).
     * @return a {@link PaginatedResult} containing the list of {@link WorkPackage} and pagination metadata.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the work packages.
     */
    PaginatedResult<WorkPackage> getWorkPackages(int offset, int pageSize, String filters)
        throws ProjectManagementException;

    /**
     * Retrieves a list of available users based on the specified page size and filter criteria  from the current
     * OpenProject configuration.
     *
     * @param pageSize the number of users to retrieve per page.
     * @param filters a JSON-formatted string representing filter criteria to apply to the request
     * @return a list of {@link User}
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the users.
     */
    PaginatedResult<User> getUsers(int pageSize, String filters) throws ProjectManagementException;

    /**
     * Retrieves a paginated list of available projects based on the specified page size and filter criteria from the
     * current OpenProject configuration.
     *
     * @param pageSize the number of users to retrieve per page.
     * @param filters a JSON-formatted string representing filter criteria to apply to the request.
     * @return a list of {@link Project}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the projects.
     */
    PaginatedResult<Project> getProjects(int pageSize, String filters) throws ProjectManagementException;

    /**
     * Retrieves all available types from the current OpenProject configuration.
     *
     * @return a List of {@link Type}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the types.
     */
    PaginatedResult<Type> getTypes() throws ProjectManagementException;

    /**
     * Retrieves all available statuses from the current OpenProject configuration.
     *
     * @return a List of {@link Status}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the statuses.
     */
    PaginatedResult<Status> getStatuses() throws ProjectManagementException;

    /**
     * Retrieves all available priorities from the current OpenProject configuration.
     *
     * @return a List of {@link Priority}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the priorities.
     */
    PaginatedResult<Priority> getPriorities() throws ProjectManagementException;
}
