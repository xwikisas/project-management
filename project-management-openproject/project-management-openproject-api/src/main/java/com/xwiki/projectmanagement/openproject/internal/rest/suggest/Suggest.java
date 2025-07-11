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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.apiclient.internal.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

/**
 * Rest endpoint that suggests ids based on some query string.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.suggest.Suggest")
@Singleton
@Path("/wikis/{wikiName}/openproject/instance/{instance}/suggest/{suggest}")
public class Suggest extends XWikiResource
{
    private static final String ID = "id";

    private static final String STATUSES = "statuses";

    private static final String PROJECTS = "projects";

    private static final String PRIORITIES = "priorities";

    private static final String NAME = "name";

    private static final String TYPE = "type";

    private static final String USERS = "users";

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    /**
     * @param wiki the wiki that contains the configured client.
     * @param instance the open project client where to search for work item suggestions.
     * @param suggest the type of suggestions to retrieve
     * @param search the string that should match the work item summary (or maybe other props as well).
     * @param pageSize the number of elements that should be returned.
     * @return a list of objects with the following properties: value, label, icon, url, hint.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getSuggestions(
        @PathParam("wikiName") String wiki,
        @PathParam("instance") String instance,
        @PathParam("suggest") @DefaultValue("") String suggest,
        @QueryParam("search") @DefaultValue("") String search,
        @QueryParam("pageSize") @DefaultValue("10") int pageSize)
    {
        String lowerSearch = search.toLowerCase();
        String connectionUrl;
        OpenProjectApiClient openProjectApiClient;
        try {
            openProjectApiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
            connectionUrl = openProjectConfiguration.getConnectionUrl(instance);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
        List<Map<String, String>> response;

        switch (suggest) {
            case ID:
                response = getIdentifiersSuggestions(openProjectApiClient, lowerSearch, pageSize);
                break;
            case PRIORITIES:
                response = getPrioritiesSuggestions(openProjectApiClient, connectionUrl);
                break;
            case STATUSES:
                response = getStatusesSuggestions(openProjectApiClient, connectionUrl);
                break;
            case PROJECTS:
                response = getProjectsSuggestions(openProjectApiClient, connectionUrl, lowerSearch, pageSize);
                break;
            case TYPE:
                response = getTypesSuggestions(openProjectApiClient, connectionUrl);
                break;
            case USERS:
                response = getUsersSuggestions(openProjectApiClient, connectionUrl, lowerSearch, pageSize);
                break;
            default:
                response = Collections.emptyList();
                break;
        }

        return Response.ok(response).build();
    }

    private List<Map<String, String>> getIdentifiersSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString,
        int pageSize)
    {
        String filter = buildFilter("subject", searchString);
        PaginatedResult<WorkPackage> workPackages = openProjectApiClient.getWorkPackages(0, pageSize, filter);

        return workPackages
            .getItems()
            .stream()
            .map(
                wp -> createSuggestion(
                    String.valueOf(wp.getId()),
                    wp.getSubject(),
                    wp.getSelf().getLocation()
                )
            )
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getPrioritiesSuggestions(OpenProjectApiClient openProjectApiClient,
        String connectionUrl)
    {
        return openProjectApiClient
            .getPriorities()
            .stream()
            .map(
                priority -> createSuggestion(
                    priority.getId().toString(),
                    priority.getName(),
                    buildEditUrl(connectionUrl, PRIORITIES, priority.getId())
                )
            )
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getStatusesSuggestions(OpenProjectApiClient openProjectApiClient,
        String connectionUrl)
    {
        return openProjectApiClient
            .getStatuses()
            .stream()
            .map(
                status -> createSuggestion(
                    status.getId().toString(),
                    status.getName(),
                    buildEditUrl(connectionUrl, STATUSES, status.getId())
                )
            )
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getProjectsSuggestions(OpenProjectApiClient openProjectApiClient,
        String connectionUrl,
        String searchString, int pageSize)
    {
        String filter = buildFilter(NAME, searchString);
        return openProjectApiClient
            .getProjects(pageSize, filter)
            .stream()
            .map(
                project -> createSuggestion(
                    project.getId().toString(),
                    project.getName(),
                    String.format("%s/projects/%s", connectionUrl, project.getId())
                )
            )
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getTypesSuggestions(OpenProjectApiClient openProjectApiClient,
        String connectionUrl)
    {
        return openProjectApiClient
            .getTypes()
            .stream()
            .map(
                type -> createSuggestion(
                    type.getId().toString(),
                    type.getName(),
                    String.format("%s/types/%s/edit/settings", connectionUrl, type.getId())
                )
            )
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getUsersSuggestions(OpenProjectApiClient openProjectApiClient,
        String connectionUrl,
        String searchString, int pageSize)
    {
        String filter = buildFilter(NAME, searchString);
        return openProjectApiClient
            .getUsers(pageSize, filter)
            .stream()
            .map(
                user -> createSuggestion(user.getId().toString(), user.getName(),
                    String.format("%s/users/%s", connectionUrl,
                        user.getId())
                )
            )
            .collect(Collectors.toList());
    }

    private String buildFilter(String fieldName, String searchValue)
    {
        if (searchValue.isEmpty()) {
            return "[]";
        }
        return String.format("[{\"%s\":{\"operator\":\"~\",\"values\":[\"%s\"]}}]", fieldName, searchValue);
    }

    private Map<String, String> createSuggestion(String value, String label, String url)
    {
        Map<String, String> suggestion = new HashMap<>(3);
        suggestion.put("value", value);
        suggestion.put("label", label);
        suggestion.put("url", url);
        return suggestion;
    }

    private String buildEditUrl(String connectionUrl, String entity, Object id)
    {
        return String.format("%s/%s/%s/edit", connectionUrl, entity, id);
    }
}
