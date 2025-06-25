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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.openproject.apiclient.internal.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;

/**
 * Rest endpoint that suggests priorities of projects from current OpenProject instance.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.suggest.PrioritiesSuggest")
@Singleton
@Path("/wikis/{wikiName}/openproject/instance/{instance}/priorities")
public class PrioritiesSuggest extends XWikiResource
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    /**
     * @param wiki the wiki that contains the configured client.
     * @param instance the open project client where to search for work item suggestions.
     * @return a list of objects with the following properties: value, label, icon, url, hint.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getPriorities(
        @PathParam("wikiName") String wiki,
        @PathParam("instance") String instance
    )
    {
        OpenProjectApiClient openProjectApiClient;
        try {
            openProjectApiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
        List<Map<String, String>> response = openProjectApiClient.getPriorities();
        return Response.ok(response).build();
    }
}
