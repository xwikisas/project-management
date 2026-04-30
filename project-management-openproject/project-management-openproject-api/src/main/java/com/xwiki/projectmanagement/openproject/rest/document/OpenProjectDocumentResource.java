package com.xwiki.projectmanagement.openproject.rest.document;

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

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.model.jaxb.Page;

/**
 * Resource for creating and retrieving document based on unique identifiers.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Path("/wikis/{wikiName}/openproject/documents")
public interface OpenProjectDocumentResource
{
    /**
     * Retrieve the reference of a document given its unique identifier.
     *
     * @param wiki the wiki where the document resides.
     * @param id the unique identifier associated to a xwiki document.
     * @param withPrettyNames whether the properties that support pretty names should be displayed as such. i.e.
     *     users.
     * @param withObjects whether the objects attached to the page should also be returned.
     * @param withClass not sure, actually.
     * @param withAttachments whether the attachments of the page should also be listed.
     * @return 200 and the reference of the xwiki document that can be used for the rest api. 404 if the id was not
     *     found.
     * @throws XWikiRestException if something went wrong during the retrieval of the page.
     */
    @GET
    @Path("/{id}")
    Response getDocument(
        @PathParam("wikiName") String wiki,
        @PathParam("id") String id,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames,
        @QueryParam("objects") @DefaultValue("false") Boolean withObjects,
        @QueryParam("class") @DefaultValue("false") Boolean withClass,
        @QueryParam("attachments") @DefaultValue("false") Boolean withAttachments) throws XWikiRestException;

    /**
     * Updates or creates a xwiki document based on an unique identifier. Can also be used to attach an unique
     * identifier to existing pages.
     *
     * @param wiki the wiki where the document resides or will be created.
     * @param documentReference the reference of the document that will be updated/created.
     * @param minorRevision whether the update will create a minor version or a major version.
     * @param page the model of the page that will be used to update/create the document.
     * @return 201: If the page was created. 202: If the page was updated. 304: If the page was not modified. 401: If
     *     the user is not authorized.
     * @throws XWikiRestException if something went wrong during the update/creation of the page.
     */
    @PUT
    Response updateDocument(
        @PathParam("wikiName") String wiki,
        @QueryParam("docRef") String documentReference,
        @QueryParam("minorRevision") Boolean minorRevision,
        Page page)
        throws XWikiRestException;
}
