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

import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.bridge.SkinAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.UserAvatar;

/**
 * Resource for the avatar of an Open Project user.
 *
 * @version $Id$
 * @since 1.0-rc-5
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.OpenProjectAvatarResource")
@Path("/wikis/{wikiName}/openproject/instance/{instance}/users/{id}/avatar")
@Singleton
public class OpenProjectAvatarResource extends XWikiResource
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private SkinAccessBridge skinAccessBridge;

    /**
     * @param wiki the wiki where the OPen Project configuration is located
     * @param instance the id that identifies the configured Open Project instance.
     * @param userId the id of the user that is present on the specified instance.
     * @return the stream for the avatar image. 401 if the user is not authenticated to Open Project. 500 if anything
     *     else went wrong.
     */
    @GET
    @Produces("image/jpeg")
    public Response getAvatar(
        @PathParam("wikiName") String wiki,
        @PathParam("instance") String instance,
        @PathParam("id") String userId
    ) throws URISyntaxException
    {
        OpenProjectApiClient openProjectApiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        if (openProjectApiClient == null) {
            // No configuration was found.
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {
            UserAvatar userAvatar = openProjectApiClient.getUserAvatar(userId);
            return Response.ok(userAvatar.getStreamingOutput(), userAvatar.getContentType()).build();
        } catch (WorkItemNotFoundException ignored) {
            // If the user doesn't have an avatar set the API returns 404 when trying to retrieve it.
        } catch (WorkItemRetrievalException e) {
            getLogger().warn("Failed to retrive the avatar for user [{}] with the following open project error: [{}].",
                userId, ExceptionUtils.getRootCauseMessage(e));
        } catch (ProjectManagementException e) {
            getLogger().error("Failed to retrive the avatar for user [{}] due to network or server issues.", userId, e);
        }
        return Response.seeOther(new URI(skinAccessBridge.getSkinFile("icons/xwiki/noavatar.png"))).build();

    }
}
