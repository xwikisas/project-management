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
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.xwiki.rest.XWikiRestException;

import com.xwiki.projectmanagement.openproject.model.WorkPackageLink;

/**
 * Resource for creating the link to an Open Project entity.
 *
 * @version $Id$
 * @since 1.1.0-rc-1
 */
@Path("/wikis/{wikiName}/openproject/documents/{id}/links")
public interface OpenProjectLinkObjectsResource
{
    /**
     * Creates the link between an xwiki page and an OpenProject entity. Once created, these links can be accessed
     * through the {@link OpenProjectLinkSearchResource}.
     *
     * @param wikiName the wiki where the xwiki page resides.
     * @param id the unique identifier, created through {@link OpenProjectDocumentResource}, that uniquely identifies an
     *     xwiki page on the instance.
     * @param withInstance If set to true, the request goes through only if it's initiated from one of the
     *     internally configured Open Project instances. Since multiple open project instances can be configured, we
     *     need to make sure that we operate only on the pages linked to the instance that makes the request. If set to
     *     false, the created link will only populate the project and/or work package properties.
     * @param minorRevision if set to true, the new page version will be a minor one.
     * @param link the {@link WorkPackageLink} model that will be used to create a link.
     * @return 201: If the object was created (The Location header will contain the URI associated to the newly created
     *     object). 401: If the user is not authorized.
     * @throws XWikiRestException if any exception was thrown when the object was created.
     */
    @POST
    Response link(
        @PathParam("wikiName") String wikiName,
        @PathParam("id") String id,
        @QueryParam("withInstance") @DefaultValue("true") Boolean withInstance,
        @QueryParam("minorRevision") Boolean minorRevision,
        WorkPackageLink link
    ) throws XWikiRestException;
}
