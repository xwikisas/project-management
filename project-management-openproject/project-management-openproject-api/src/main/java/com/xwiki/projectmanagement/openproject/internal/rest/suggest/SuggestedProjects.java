package com.xwiki.projectmanagement.openproject.internal.rest.suggest;

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

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Version;

import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE;

/**
 * Retrieves the projects designated by the values given to the {@link Suggest} endpoint (the selected project, the
 * projects whose versions are suggested), which are their ids or their identifiers.
 *
 * @version $Id$
 * @since 1.5.0
 */
final class SuggestedProjects
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestedProjects.class);

    /**
     * The identifiers of the OpenProject projects start with a lower case letter, followed by lower case letters,
     * digits, dashes or underscores (for example {@code demo-project}).
     */
    private static final Pattern PROJECT_IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");

    private SuggestedProjects()
    {
    }

    /**
     * @param project the id (for example {@code 3}) or the identifier (for example {@code demo-project}) of a
     *     project, or any other value (for example the value of the "use the dashboard-selected project" option),
     *     which is ignored
     * @return the project, or {@code null} when the value doesn't designate a project, or when the project doesn't
     *     exist or isn't visible to the current user
     * @throws ProjectManagementException if OpenProject couldn't be reached or failed to answer
     */
    static Project getProject(OpenProjectApiClient openProjectApiClient, String project)
        throws ProjectManagementException
    {
        if (!isProject(project)) {
            return null;
        }
        try {
            return openProjectApiClient.getProject(project);
        } catch (WorkItemRetrievalException e) {
            // OpenProject answers 404 both for the projects that don't exist and for those the user can't see: the
            // client keeps showing the value it has.
            if (isNotFound(e)) {
                LOGGER.debug("Failed to retrieve the project [{}]. Root cause: [{}]", project,
                    ExceptionUtils.getRootCauseMessage(e));
                return null;
            }
            throw e;
        }
    }

    /**
     * @param projects the ids or the identifiers of projects; the other values are ignored, as well as the projects
     *     that don't exist or that the current user can't see
     * @param versions the versions to return when none of the given values designates a project, e.g. all the versions
     * @return the versions available in the given projects (their own versions, and the versions that other projects
     *     share with them)
     * @throws ProjectManagementException if OpenProject couldn't be reached or failed to answer
     */
    static List<Version> getAvailableVersions(OpenProjectApiClient openProjectApiClient, List<String> projects,
        List<Version> versions) throws ProjectManagementException
    {
        List<String> projectValues = projects == null ? List.of()
            : projects.stream().filter(SuggestedProjects::isProject).distinct().collect(Collectors.toList());
        if (projectValues.isEmpty()) {
            return versions;
        }
        List<Version> availableVersions = new ArrayList<>();
        for (String projectValue : projectValues) {
            Integer projectId = Suggest.getSelectedId(projectValue);
            if (projectId == null) {
                Project project = getProject(openProjectApiClient, projectValue);
                projectId = project == null ? null : project.getId();
            }
            if (projectId != null) {
                availableVersions.addAll(getProjectVersions(openProjectApiClient, projectId));
            }
        }
        return availableVersions;
    }

    private static List<Version> getProjectVersions(OpenProjectApiClient openProjectApiClient, int projectId)
        throws ProjectManagementException
    {
        try {
            return openProjectApiClient.getProjectVersions(projectId).getItems();
        } catch (WorkItemRetrievalException e) {
            if (isNotFound(e)) {
                LOGGER.debug("Failed to retrieve the versions of the project [{}]. Root cause: [{}]", projectId,
                    ExceptionUtils.getRootCauseMessage(e));
                return List.of();
            }
            throw e;
        }
    }

    private static boolean isProject(String value)
    {
        return Suggest.getSelectedId(value) != null || (value != null
            && PROJECT_IDENTIFIER_PATTERN.matcher(value).matches()
            && !USE_SELECTED_DASHBOARD_PROJECT_VALUE.equals(value));
    }

    private static boolean isNotFound(WorkItemRetrievalException e)
    {
        return Integer.valueOf(HttpURLConnection.HTTP_NOT_FOUND).equals(e.getStatusCode());
    }
}
