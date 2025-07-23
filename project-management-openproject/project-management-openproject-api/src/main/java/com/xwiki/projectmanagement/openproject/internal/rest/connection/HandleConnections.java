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
package com.xwiki.projectmanagement.openproject.internal.rest.connection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.service.HandleConnectionsService;
import com.xwiki.projectmanagement.exception.ProjectManagementException;

/**
 * REST Resource used for creating OpenProject configuration pages.
 *
 * @version $Id$
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.connection.HandleConnections")
@Path("wikis/{wikiName}/spaces/{spaceName: .+}/pages/{pageName}/openproject/configurations")
public class HandleConnections extends XWikiResource
{
    @Inject
    private HandleConnectionsService handleConnectionsService;

    @Inject
    private AuthorizationManager authorizationManager;

    /**
     * Creates a new OpenProject connection configuration document in XWiki using the provided path parameters and
     * configuration data.
     *
     * @param wikiName the name of the wiki where the connection page will be created
     * @param spaceName the full space path where the page resides
     * @param pageName the name of the page where the connection configuration will be stored
     * @param openProjectConnection the {@link OpenProjectConnection} to store
     * @return 201 if the creation was successful; 409 if an instance with the same connection name exists; 500 if any
     *     error was encountered.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createConnection(
        @PathParam("wikiName") String wikiName,
        @PathParam("spaceName") String spaceName,
        @PathParam("pageName") String pageName,
        OpenProjectConnection openProjectConnection)
    {
        return processConnection(wikiName, spaceName, pageName, openProjectConnection, true);
    }

    /**
     * Updates an existing OpenProject connection configuration document in XWiki using the provided path parameters and
     * configuration data.
     *
     * @param wikiName the name of the wiki where the connection page exists
     * @param spaceName the full space path where the page resides
     * @param pageName the name of the page where the connection configuration is stored
     * @param openProjectConnection the {@link OpenProjectConnection} containing the updated data
     * @return 200 if the update was successful; 409 if an instance with the same connection name exists; 500 if any
     *     error was encountered.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response updateConnection(
        @PathParam("wikiName") String wikiName,
        @PathParam("spaceName") String spaceName,
        @PathParam("pageName") String pageName,
        OpenProjectConnection openProjectConnection
    )
    {
        return processConnection(wikiName, spaceName, pageName, openProjectConnection, false);
    }

    private void checkAccessRights() throws XWikiException
    {
        DocumentReference currentUserRef = getXWikiContext().getUserReference();
        if (!authorizationManager.hasAccess(Right.ADMIN, currentUserRef, getXWikiContext().getWikiReference())) {
            throw new XWikiException(
                XWikiException.MODULE_XWIKI_ACCESS,
                XWikiException.ERROR_XWIKI_ACCESS_DENIED,
                "You need Admin rights to perform this operation."
            );
        }
    }

    private Response processConnection(
        String wikiName,
        String spaceName,
        String pageName,
        OpenProjectConnection openProjectConnection,
        boolean isNewConnection
    )
    {
        try {
            checkAccessRights();
            DocumentReference documentReference =
                new DocumentReference(pageName, getSpaceReference(spaceName, wikiName));
            handleConnectionsService.handleConnection(openProjectConnection, documentReference);

            if (isNewConnection) {
                return Response.status(Response.Status.CREATED).build();
            } else {
                return Response.status(Response.Status.OK).build();
            }
        } catch (XWikiException e) {
            return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
        } catch (ProjectManagementException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        } catch (XWikiRestException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
}
