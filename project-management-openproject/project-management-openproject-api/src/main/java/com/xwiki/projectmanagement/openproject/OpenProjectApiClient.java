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

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Sprint;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.UserAvatar;
import com.xwiki.projectmanagement.openproject.model.Version;
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
     * @param offset the offset index from which to start retrieving work packages.
     * @param pageSize the maximum number of work packages to return.
     * @param filters optional filters to apply (e.g. query parameters encoded as a string).
     * @param sortBy optional sorting criteria to apply (e.g. field name and sort direction).
     * @return a {@link PaginatedResult} containing the list of {@link WorkPackage} and pagination metadata.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the work packages.
     */
    PaginatedResult<WorkPackage> getWorkPackages(Integer offset, Integer pageSize, String filters, String sortBy)
        throws ProjectManagementException;

    /**
     * Retrieves a list of available work packages from the current OpenProject configuration and specified project.
     *
     * @param project the project (project name or id) from which we want to retrieve the work packages
     * @param offset the offset index from which to start retrieving work packages.
     * @param pageSize the maximum number of work packages to return.
     * @param filters optional filters to apply (e.g. query parameters encoded as a string).
     * @param sortBy optional sorting criteria to apply (e.g. field name and sort direction).
     * @return a {@link PaginatedResult} containing the list of {@link WorkPackage} and pagination metadata.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the work packages.
     */
    PaginatedResult<WorkPackage> getProjectWorkPackages(String project, Integer offset, Integer pageSize,
        String filters, String sortBy)
        throws ProjectManagementException;

    /**
     * Retrieves a list of available users based on the specified page size and filter criteria  from the current
     * OpenProject configuration.
     *
     * @param offset the offset from which to start retrieving users.
     * @param pageSize the number of users to retrieve per page.
     * @param filters a JSON-formatted string representing filter criteria to apply to the request
     * @return a list of {@link User}
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the users.
     */
    PaginatedResult<User> getUsers(Integer offset, Integer pageSize, String filters) throws ProjectManagementException;

    /**
     * Retrieves a list of available users for creating a work package based on the specified page size and filter
     * criteria from the current OpenProject configuration.
     *
     * @param url the URL of the work package for which we want to retrieve the available users.
     * @param offset the offset from which to start retrieving users.
     * @param pageSize the number of users to retrieve per page.
     * @param filters a JSON-formatted string representing filter criteria to apply to the request
     * @return a list of {@link User}
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the users.
     * @since 1.1
     */
    default PaginatedResult<User> getAvailableUsers(String url, Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        throw new UnsupportedOperationException(
            "Retrieving available users is not supported by this client implementation.");
    }

    /**
     * Retrieves a paginated list of available projects based on the specified page size and filter criteria from the
     * current OpenProject configuration.
     *
     * @param offset the offset from which to start retrieving projects.
     * @param pageSize the number of projects to retrieve per page.
     * @param filters a JSON-formatted string representing filter criteria to apply to the request.
     * @return a list of {@link Project}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the projects.
     */
    PaginatedResult<Project> getProjects(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException;

    /**
     * Retrieves a paginated list of available projects for creating work packages based on the specified page size and
     * filter criteria from the current OpenProject configuration.
     *
     * @param url the URL of the work package for which we want to retrieve the available projects.
     * @param offset the offset from which to start retrieving projects.
     * @param pageSize the number of projects to retrieve per page.
     * @param filters a JSON-formatted string representing filter criteria to apply to the request.
     * @return a list of {@link Project}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the projects.
     * @since 1.1
     */
    default PaginatedResult<Project> getAvailableProjects(String url, Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        throw new UnsupportedOperationException(
            "Retrieving available projects is not supported by this client implementation.");
    }

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

    /**
     * Retrieves all available versions from the current OpenProject configuration.
     *
     * @return a {@link PaginatedResult} containing the list of {@link Version}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the versions.
     * @since 1.2
     */
    default PaginatedResult<Version> getVersions() throws ProjectManagementException
    {
        throw new UnsupportedOperationException(
            "Retrieving versions is not supported by this client implementation.");
    }

    /**
     * Retrieves a paginated list of available sprints from the current OpenProject configuration.
     *
     * @param offset the offset from which to start retrieving sprints.
     * @param pageSize the number of sprints to retrieve per page.
     * @param filters a JSON-formatted string representing filter criteria to apply to the request.
     * @return a {@link PaginatedResult} containing the list of {@link Sprint}.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the sprints.
     * @since 1.2
     */
    default PaginatedResult<Sprint> getSprints(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        throw new UnsupportedOperationException(
            "Retrieving sprints is not supported by this client implementation.");
    }

    /**
     * Retrieve the user avatar.
     *
     * @param userId the id of the user for which we want to retrieve the avatar.
     * @return the model containing the image stream and its content type.
     * @since 1.0-rc-5
     */
    UserAvatar getUserAvatar(String userId) throws ProjectManagementException;

    /**
     * Retrieves work packages form response.
     *
     * @param jsonBody the json body.
     * @return the json node.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the work packages
     *     form
     * @since 1.1
     */
    default JsonNode getWorkPackagesFormResponse(String jsonBody) throws ProjectManagementException
    {
        throw new UnsupportedOperationException(
            "Retrieving work packages form response is not supported by this client implementation.");
    }

    /**
     * Creates a work package in OpenProject.
     *
     * @param url the URL to create the work package.
     * @param jsonBody the JSON body representing the work package to be created.
     * @return the created work package.
     * @throws ProjectManagementException if there was an issue during the creation process.
     * @since 1.1
     */
    default JsonNode createWorkPackage(String url, String jsonBody) throws ProjectManagementException
    {
        throw new UnsupportedOperationException(
            "Creating work packages is not supported by this client implementation.");
    }

    /**
     * Retrieves the identifier of the OpenProject instance. This is the {@code installation_uuid} exposed by the
     * instance through its public {@code /.well-known/openproject-metadata} endpoint, so it can be retrieved with a
     * client that uses no authentication.
     *
     * @return the identifier of the OpenProject instance.
     * @throws ProjectManagementException if some error was encountered while trying to retrieve the instance id.
     * @since 1.2
     */
    default String getInstanceId() throws ProjectManagementException
    {
        throw new UnsupportedOperationException(
            "Retrieving the instance id is not supported by this client implementation.");
    }
}
