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
package com.xwiki.projectmanagement.openproject.internal.rest.suggest;

import java.util.List;
import java.util.Map;

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

import com.xwiki.projectmanagement.openproject.apiclient.internal.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;

/**
 * Rest endpoint that suggests projects based on some query string.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.suggest.ProjectsSuggest")
@Singleton
@Path("/wikis/{wikiName}/openproject/instance/{instance}/projects")
public class ProjectsSuggest extends XWikiResource
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    /**
     * @param wiki the wiki that contains the configured client.
     * @param instance the open project client where to search for work item suggestions.
     * @param search the string that should match the work item summary (or maybe other props as well).
     * @param pageSize the number of elements that should be returned.
     * @return a list of objects with the following properties: value, label, icon, url, hint.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getProjects(
        @PathParam("wikiName") String wiki,
        @PathParam("instance") String instance,
        @QueryParam("search") @DefaultValue("") String search,
        @QueryParam("pageSize") @DefaultValue("10") int pageSize)
    {
        String lowerSearch = search.toLowerCase();

        OpenProjectApiClient openProjectApiClient;
        try {
            openProjectApiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
        String filter = lowerSearch.isEmpty() ? "[]" : String.format("[{\"name\":{\"operator\":\"~\","
                + "\"values\":[\"%s\"]}}]",
            lowerSearch);
        List<Map<String, String>> response = openProjectApiClient.getProjects(pageSize, filter);
        return Response.ok(response).build();
    }
}
