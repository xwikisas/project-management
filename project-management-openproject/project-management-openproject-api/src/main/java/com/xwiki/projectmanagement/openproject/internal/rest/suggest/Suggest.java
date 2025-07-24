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

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

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

    private static final String VALUE = "value";

    private static final String LABEL = "label";

    private static final String URL = "url";

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
        OpenProjectApiClient openProjectApiClient;

        String lowerSearch = search.toLowerCase();

        openProjectApiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        if (openProjectApiClient == null) {
            // No configuration was found.
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<Map<String, String>> response;

        try {
            switch (suggest) {
                case ID:
                    response = getIdentifiersSuggestions(openProjectApiClient, lowerSearch, pageSize);
                    break;
                case PRIORITIES:
                    response = getPrioritiesSuggestions(openProjectApiClient);
                    break;
                case STATUSES:
                    response = getStatusesSuggestions(openProjectApiClient);
                    break;
                case PROJECTS:
                    response = getProjectsSuggestions(openProjectApiClient, lowerSearch, pageSize);
                    break;
                case TYPE:
                    response = getTypesSuggestions(openProjectApiClient);
                    break;
                case USERS:
                    response = getUsersSuggestions(openProjectApiClient, lowerSearch, pageSize);
                    break;
                default:
                    response = Collections.emptyList();
                    break;
            }
        } catch (ProjectManagementException e) {
            return Response.serverError().entity(e).build();
        }
        return Response.ok(response).build();
    }

    private List<Map<String, String>> getIdentifiersSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString,
        int pageSize) throws ProjectManagementException
    {
        String filter = buildFilter("subject", searchString);
        return getSuggestions(openProjectApiClient.getWorkPackages(0, pageSize, filter, "").getItems());
    }

    private List<Map<String, String>> getPrioritiesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        return getSuggestions(openProjectApiClient.getPriorities().getItems());
    }

    private List<Map<String, String>> getStatusesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        return getSuggestions(openProjectApiClient.getStatuses().getItems());
    }

    private List<Map<String, String>> getProjectsSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize) throws ProjectManagementException
    {
        String filter = buildFilter(NAME, searchString);
        return getSuggestions(openProjectApiClient.getProjects(pageSize, filter).getItems());
    }

    private List<Map<String, String>> getTypesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        return getSuggestions(openProjectApiClient.getTypes().getItems());
    }

    private List<Map<String, String>> getUsersSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize) throws ProjectManagementException
    {
        String filter = buildFilter(NAME, searchString);
        return getSuggestions(openProjectApiClient.getUsers(pageSize, filter).getItems());
    }

    private List<Map<String, String>> getSuggestions(
        List<? extends BaseOpenProjectObject> openProjectObjects)
    {
        return openProjectObjects
            .stream()
            .map(
                obj -> createSuggestion(
                    String.valueOf(obj.getId()),
                    obj.getName(),
                    obj.getSelf().getLocation()
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
        return Map.of(
            VALUE, value,
            LABEL, label,
            URL, url
        );
    }
}
