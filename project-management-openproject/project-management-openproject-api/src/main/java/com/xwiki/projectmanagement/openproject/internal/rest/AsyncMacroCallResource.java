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

package com.xwiki.projectmanagement.openproject.internal.rest;

import java.util.Arrays;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.job.JobException;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.async.internal.AsyncRendererConfiguration;
import org.xwiki.rendering.async.internal.AsyncRendererExecutor;
import org.xwiki.rendering.async.internal.AsyncRendererExecutorResponse;
import org.xwiki.rendering.async.internal.AsyncRendererResult;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rest.XWikiResource;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.internal.context.XWikiContextContextStore;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncRenderer;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Endpoint for generating the async placeholder for an open project macro call.
 *
 * @version $Id$
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.AsyncMacroCallResource")
@Path("/wikis/{wikiName}/openproject/async/displayer/{displayer}/instance/{instance}/workpackage/{identifier}")
public class AsyncMacroCallResource extends XWikiResource
{
    @Inject
    private ComponentManager componentManager;

    @Inject
    private AsyncRendererExecutor asyncRendererExecutor;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * @param wiki the wiki that contains the Open Project instance.
     * @param displayer the displayer that should be used for the macro call. The value must be one of the
     *     {@link com.xwiki.projectmanagement.internal.WorkItemsDisplayer}.
     * @param instance the name of the Open Project connection configuration.
     * @param workPackageId the id of the work package that should be displayed.
     * @return the async placeholder that will be replaced with the rendered macro; 401 if the user does not have the
     *     rights to view the response;
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN })
    public Response getAsyncCall(
        @PathParam("wikiName") String wiki,
        @PathParam("displayer") @DefaultValue(".") String displayer,
        @PathParam("instance") String instance,
        @PathParam("identifier") String workPackageId
    )
    {
        if (!authorizationManager.hasAccess(Right.VIEW)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            AsyncRendererConfiguration configuration = new AsyncRendererConfiguration();
            configuration.setPlaceHolderForced(true);
            ProjectManagementAsyncRenderer asyncRenderer =
                getAsyncRenderer(displayer, instance, workPackageId, configuration);
            AsyncRendererExecutorResponse response = asyncRendererExecutor.render(asyncRenderer, configuration);
            AsyncRendererResult result = response.getStatus().getResult();
            if (result != null && !configuration.isPlaceHolderForced()) {
                return Response.ok(result.getResult()).build();
            } else {
                String placeholder = String.format("<div class=\"xwiki-async\" data-xwiki-async-id=\"%s\" "
                        + "data-xwiki-async-client-id=\"%s\"></div>", response.getJobIdHTTPPath(),
                    response.getAsyncClientId());
                return Response.ok(placeholder).build();
            }
        } catch (JobException | RenderingException | JsonProcessingException e) {
            return Response.serverError().build();
        } catch (ComponentLookupException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    private ProjectManagementAsyncRenderer getAsyncRenderer(String displayer, String instance,
        String workPackageId, AsyncRendererConfiguration configuration)
        throws ComponentLookupException, JsonProcessingException
    {
        ProjectManagementAsyncRenderer asyncRenderer =
            componentManager.getInstance(ProjectManagementAsyncRenderer.class);
        // Pass some properties that might be of interest to a potential displayer macro.
        configuration.setContextEntries(
            Set.of(XWikiContextContextStore.PROP_USER, XWikiContextContextStore.PROP_WIKI,
                XWikiContextContextStore.PROP_ACTION, XWikiContextContextStore.PROP_LOCALE));

        Macro<ProjectManagementMacroParameters> displayerMacro = componentManager.getInstance(Macro.class, displayer);
        ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();
        // TODO: Would be nicer if we retrieved the translation prefix from the json configuration.
        parameters.setSourceParameters(String.format("instance=%s&client=%s&translationPrefix=%s", instance,
            "openproject", "openproject."));
        LiveDataConfiguration liveDataConfiguration = new LiveDataConfiguration();
        liveDataConfiguration.setQuery(new LiveDataQuery());
        liveDataConfiguration.getQuery().setFilters(Arrays.asList(new LiveDataQuery.Filter("identifier.value",
            "=", workPackageId)));
        String content = jsonMapper.writeValueAsString(liveDataConfiguration);

        MacroTransformationContext context = new MacroTransformationContext();
        asyncRenderer.initialize(displayerMacro, parameters, content, context);
        return asyncRenderer;
    }
}
