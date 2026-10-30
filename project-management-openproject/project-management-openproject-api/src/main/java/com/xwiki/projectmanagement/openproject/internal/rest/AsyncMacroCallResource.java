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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.job.JobException;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.model.reference.WikiReference;
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
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.internal.context.XWikiContextContextStore;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncRenderer;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Endpoint for generating the async placeholder for an OpenProject macro call.
 *
 * @version $Id$
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.AsyncMacroCallResource")
@Path("/wikis/{wikiName}/openproject/async/displayer/{displayer}/instance/{instance}/workpackage/{identifier}")
public class AsyncMacroCallResource extends XWikiResource
{
    /**
     * The OpenProject work packages are identified by a number.
     */
    private static final Pattern WORK_PACKAGE_ID_PATTERN = Pattern.compile("\\d+");

    private static final String CLIENT_ID = "openproject";

    private static final String LIVE_DATA_SOURCE = "projectmanagement";

    private static final String PROPERTIES = "identifier,type,summary,description,startDate,assignees";

    private static final String ERROR_MESSAGE = "Failed to display the work package.";

    @Inject
    private ComponentManager componentManager;

    @Inject
    private AsyncRendererExecutor asyncRendererExecutor;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * @param wiki the wiki that contains the OpenProject instance.
     * @param displayer the displayer that should be used for the macro call. The value must be one of the
     *     {@link WorkItemsDisplayer}.
     * @param instance the name of the OpenProject connection configuration.
     * @param workPackageId the id of the work package that should be displayed.
     * @return the async placeholder that will be replaced with the rendered macro; 400 if the displayer or the work
     *     package id are not valid; 401 if the user does not have view right on the wiki; 404 if the wiki does not
     *     exist; 500 if the placeholder could not be generated
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN })
    public Response getAsyncCall(
        @PathParam("wikiName") String wiki,
        @PathParam("displayer") String displayer,
        @PathParam("instance") String instance,
        @PathParam("identifier") String workPackageId
    )
    {
        WorkItemsDisplayer workItemsDisplayer = EnumUtils.getEnum(WorkItemsDisplayer.class, displayer);
        if (workItemsDisplayer == null || StringUtils.isEmpty(instance) || workPackageId == null
            || !WORK_PACKAGE_ID_PATTERN.matcher(workPackageId).matches())
        {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (!this.authorizationManager.hasAccess(Right.VIEW, new WikiReference(wiki))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        XWikiContext xcontext = getXWikiContext();
        String currentWiki = xcontext.getWikiId();
        try {
            if (!this.wikiDescriptorManager.exists(wiki)) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            // The OpenProject connection is read from the context wiki, also in the asynchronous rendering job.
            xcontext.setWikiId(wiki);
            return Response.ok(getPlaceholder(workItemsDisplayer, instance, workPackageId)).build();
        } catch (WikiManagerException | JobException | RenderingException | JsonProcessingException
            | ComponentLookupException e)
        {
            return RestErrorResponses.errorResponse(getLogger(), ERROR_MESSAGE, e);
        } finally {
            xcontext.setWikiId(currentWiki);
        }
    }

    private String getPlaceholder(WorkItemsDisplayer displayer, String instance, String workPackageId)
        throws ComponentLookupException, JsonProcessingException, JobException, RenderingException
    {
        AsyncRendererConfiguration configuration = new AsyncRendererConfiguration();
        configuration.setPlaceHolderForced(true);
        // Pass some properties that might be of interest to a potential displayer macro.
        configuration.setContextEntries(
            Set.of(XWikiContextContextStore.PROP_USER, XWikiContextContextStore.PROP_WIKI,
                XWikiContextContextStore.PROP_ACTION, XWikiContextContextStore.PROP_LOCALE));
        ProjectManagementAsyncRenderer asyncRenderer = getAsyncRenderer(displayer, instance, workPackageId);
        AsyncRendererExecutorResponse response = this.asyncRendererExecutor.render(asyncRenderer, configuration);
        AsyncRendererResult result = response.getStatus().getResult();
        if (result != null && !configuration.isPlaceHolderForced()) {
            return result.getResult();
        } else {
            return String.format("<div class=\"xwiki-async\" data-xwiki-async-id=\"%s\" "
                    + "data-xwiki-async-client-id=\"%s\"></div>", response.getJobIdHTTPPath(),
                response.getAsyncClientId());
        }
    }

    private ProjectManagementAsyncRenderer getAsyncRenderer(WorkItemsDisplayer displayer, String instance,
        String workPackageId) throws ComponentLookupException, JsonProcessingException
    {
        ProjectManagementAsyncRenderer asyncRenderer =
            this.componentManager.getInstance(ProjectManagementAsyncRenderer.class);
        Macro<ProjectManagementAsyncMacroParams> displayerMacro =
            this.componentManager.getInstance(Macro.class, displayer.getMacroHint());

        ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();
        parameters.setWorkItemsDisplayer(displayer);
        // The Live Data displayers get the work items from the project management Live Data source, and need
        // properties known to OpenProject, unlike the generic default ones.
        parameters.setSource(LIVE_DATA_SOURCE);
        parameters.setProperties(PROPERTIES);
        // TODO: Would be nicer if we retrieved the translation prefix from the json configuration.
        parameters.setSourceParameters(String.format("instance=%s&client=%s&translationPrefix=%s", encode(instance),
            CLIENT_ID, encode(CLIENT_ID + '.')));
        LiveDataConfiguration liveDataConfiguration = new LiveDataConfiguration();
        liveDataConfiguration.setQuery(new LiveDataQuery());
        liveDataConfiguration.getQuery().setFilters(List.of(new LiveDataQuery.Filter("identifier",
            "=", workPackageId)));
        String filters = this.jsonMapper.writeValueAsString(liveDataConfiguration);
        // The work items displayers read the filters from the parameters, the Live Data one from the content.
        parameters.setFilters(filters);

        MacroTransformationContext context = new MacroTransformationContext();
        asyncRenderer.initialize(displayerMacro, parameters, filters, context, null);
        return asyncRenderer;
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
