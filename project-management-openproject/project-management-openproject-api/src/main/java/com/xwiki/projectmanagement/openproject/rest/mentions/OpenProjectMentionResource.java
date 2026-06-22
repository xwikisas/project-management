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
package com.xwiki.projectmanagement.openproject.rest.mentions;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.model.jaxb.Objects;
import org.xwiki.rest.model.jaxb.SearchResults;

/**
 * Endpoint for handling xwiki mentions to OpenProject packages.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Path("/openproject")
public interface OpenProjectMentionResource
{
    /**
     * @param workPackageId the OpenProject work package id that should match the xwiki pages.
     * @param instance the id of the OpenProject instance that should be taken into consideration when
     *     returning mentions.
     * @param number the maximum number of elements that should be returned.
     * @param start the offset of the results.
     * @param orderField the field on which the results should be ordered on.
     * @param order how the results should be ordered. asc or desc.
     * @param withPrettyNames denotes whether the results should contain the pretty names of the documents or not.
     * @return a list of xwiki pages that mention the specified work package in their content.
     * @throws XWikiRestException if there was any issue retrieving the xwiki pages.
     */
    @GET
    @Path("/mentions")
    SearchResults getPagesMentioningWorkPackage(
        @QueryParam("workPackage") @DefaultValue("") String workPackageId,
        @QueryParam("instance") @DefaultValue("") String instance,
        @QueryParam("number") @DefaultValue("25") Integer number,
        @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("orderField") @DefaultValue("") String orderField,
        @QueryParam("order") @DefaultValue("asc") String order,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames
    ) throws XWikiRestException;

    /**
     * @param pageId the unique identifier of the page.
     * @param instance the id of the OpenProject instance that should be taken into consideration when
     *     returning mentions.
     * @param number the maximum number of elements that should be returned.
     * @param start the offset of the results.
     * @param withPrettyNames denotes whether the results should contain the pretty names of the documents or not.
     * @return a list of mention objects that the requested page contains.
     * @throws XWikiRestException if there was any issue retrieving the xwiki pages.
     */
    @GET
    @Path("/documents/{id}/mentions")
    Objects getMentionsWithId(
        @PathParam("id") String pageId,
        @QueryParam("instance") @DefaultValue("") String instance,
        @QueryParam("number") @DefaultValue("25") Integer number,
        @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames) throws XWikiRestException;

    /**
     * @param docRef the reference of the page.
     * @param instance the id of the OpenProject instance that should be taken into consideration when
     *     returning mentions.
     * @param number the maximum number of elements that should be returned.
     * @param start the offset of the results.
     * @param withPrettyNames denotes whether the results should contain the pretty names of the documents or not.
     * @return a list of mention objects that the requested page contains.
     * @throws XWikiRestException if there was any issue retrieving the xwiki pages.
     */
    @GET
    @Path("/pages/{docRef}/mentions")
    Objects getMentionsWithRef(@PathParam("docRef") String docRef,
        @QueryParam("instance") @DefaultValue("") String instance,
        @QueryParam("number") @DefaultValue("25") Integer number,
        @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames) throws XWikiRestException;
}
