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
package com.xwiki.projectmanagement.openproject.internal;

import java.net.HttpURLConnection;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.Project;

/**
 * Resolves the project parameter of the OpenProject macros, which holds the id or the identifier of a project.
 *
 * @version $Id$
 * @since 1.4.0
 */
final class OpenProjectProjectResolver
{
    private static final String ASCII_DIGITS = "0123456789";

    private OpenProjectProjectResolver()
    {
    }

    /**
     * @param project the id or the identifier of an OpenProject project
     * @param apiClient the client used to resolve the identifier of the project
     * @return the id of the project; the project is returned as is, without requesting OpenProject, when it is
     *     already an id
     * @throws MacroExecutionException if the project couldn't be retrieved
     */
    static String getProjectId(String project, OpenProjectApiClient apiClient) throws MacroExecutionException
    {
        // OpenProject accepts only ASCII digits in the project filters (e.g. not the Arabic-Indic ones).
        if (StringUtils.isNotEmpty(project) && StringUtils.containsOnly(project, ASCII_DIGITS)) {
            return project;
        }
        return String.valueOf(getProject(project, apiClient).getId());
    }

    /**
     * @param project the id or the identifier of an OpenProject project
     * @param apiClient the client used to retrieve the project
     * @return the project
     * @throws MacroExecutionException if the project couldn't be retrieved, e.g. when it doesn't exist
     */
    static Project getProject(String project, OpenProjectApiClient apiClient) throws MacroExecutionException
    {
        try {
            return apiClient.getProject(project);
        } catch (ProjectManagementException e) {
            // OpenProject answers 404 both for the projects that don't exist and for those the user can't see.
            if (e instanceof WorkItemRetrievalException && Integer.valueOf(HttpURLConnection.HTTP_NOT_FOUND)
                .equals(((WorkItemRetrievalException) e).getStatusCode()))
            {
                throw new MacroExecutionException(String.format(
                    "The OpenProject project [%s] doesn't exist or you are not allowed to view it.", project), e);
            }
            throw new MacroExecutionException(
                String.format("Failed to retrieve the project [%s] from OpenProject.", project), e);
        }
    }
}
