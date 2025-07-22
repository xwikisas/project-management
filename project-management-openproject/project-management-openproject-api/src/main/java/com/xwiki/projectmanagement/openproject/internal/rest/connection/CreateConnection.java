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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.rest.XWikiResource;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.service.HandleConnectionsService;

/**
 * REST Resource used for creating OpenProject configuration pages.
 *
 * @version $Id$
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.connection.CreateConnection")
@Path("wikis/{wikiName}/spaces/{spaceName:.+}/pages/{pageName}/openproject/configurations")
public class CreateConnection extends XWikiResource
{
    @Inject
    private HandleConnectionsService handleConnectionsService;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

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
     * @param oldDocumentReference the old document reference used when updating the connection
     * @return a response indicating the result of the operation
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response handleConnection(
        @PathParam("wikiName") String wikiName,
        @PathParam("spaceName") String spaceName,
        @PathParam("pageName") String pageName,
        @QueryParam("oldDocumentReference") String oldDocumentReference,
        OpenProjectConnection openProjectConnection)
    {
        DocumentReference currentUserRef = getXWikiContext().getUserReference();
        if (!authorizationManager.hasAccess(Right.ADMIN, currentUserRef, getXWikiContext().getWikiReference())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity("You need Admin rights to perform this operation.")
                .build();
        }
        try {
            DocumentReference oldDocumentRef = null;
            if (oldDocumentReference != null && !oldDocumentReference.trim().isEmpty()) {
                oldDocumentRef = documentReferenceResolver.resolve(oldDocumentReference);
            }

            DocumentReference documentReference =
                new DocumentReference(pageName, getSpaceReference(spaceName, wikiName));
            handleConnectionsService.createOrUpdateConnection(openProjectConnection, oldDocumentRef, documentReference);
        } catch (Exception e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }

        return Response.ok().build();
    }
}
