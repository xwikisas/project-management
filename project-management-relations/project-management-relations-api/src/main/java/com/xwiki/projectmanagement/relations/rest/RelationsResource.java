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
package com.xwiki.projectmanagement.relations.rest;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.xwiki.rest.XWikiRestException;

import com.xwiki.projectmanagement.relations.model.ProjectManagementRelation;

/**
 * Endpoint for managing relations.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Path("/wikis/{wikiName}/spaces/{spaceName: .+}/pages/{pageName}/projectmanagement/relations")
public interface RelationsResource
{
    /**
     * Retrieve the relation for a given page.
     *
     * @param wikiName the name of the wiki in which the page resides
     * @param spaces the spaces of the page
     * @param pageName the name of the page
     * @param clientId the value that should match the retrieved relation class instance.
     * @param ancestors whether the lookup should be done in the ancestors of the document or not.
     * @return the relation object attached to the document OR, if ancestorLookup is set to true, returns  relation
     *     object thats belongs to the closest ancestor. 404 if no object was found.
     * @throws XWikiRestException when failing in retrieving the document.
     */
    @GET
    @Path("/client/{id}")
    ProjectManagementRelation getClientRelation(
        @PathParam("wikiName") String wikiName,
        @PathParam("spaceName") @Encoded String spaces,
        @PathParam("pageName") String pageName,
        @PathParam("id") String clientId,
        @QueryParam("ancestors") @DefaultValue("false") Boolean ancestors
    ) throws XWikiRestException;
}
