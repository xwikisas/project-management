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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.model.jaxb.SearchResults;

/**
 * Resource for retrieving pages containing {@link com.xwiki.projectmanagement.openproject.store.WorkPackageLink} to an
 * Open Project entity.
 *
 * @version $Id$
 * @since 1.1.0-rc-1
 */
@Path("/wikis/{wikiName}/openproject/links")
public interface OpenProjectLinkSearchResource
{
    /**
     * @param wikiName the wiki where to search.
     * @param projectId the OpenProject project id that should match the xwiki pages.
     * @param forInstance if set to true, the endpoint should retrieve the links that have the instance property
     *     matching the OpenProject instance that made the request. Otherwise, the instance property is ignored and all
     *     the xwiki pages matching the project id will be returned.
     * @param number the maximum number of elements that should be returned.
     * @param start the offset of the results.
     * @param orderField the field on which the results should be ordered on.
     * @param order how the results should be ordered. asc or desc.
     * @param withPrettyNames denotes whether the results should contain the pretty names of the documents or not.
     * @return a list of xwiki pages that have a {@link com.xwiki.projectmanagement.openproject.store.WorkPackageLink}
     *     to an OpenProject entity and that the current user has view rights on.
     * @throws XWikiRestException if there was any issue retrieving the xwiki pages.
     */
    @GET
    @Path("/projects/{id}")
    SearchResults getProjects(
        @PathParam("wikiName") String wikiName,
        @PathParam("id") String projectId,
        @QueryParam("forInstance") @DefaultValue("true") Boolean forInstance,
        @QueryParam("number") @DefaultValue("-1") Integer number,
        @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("orderField") @DefaultValue("") String orderField,
        @QueryParam("order") @DefaultValue("asc") String order,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames
    ) throws XWikiRestException;

    /**
     * @param wikiName the wiki where to search.
     * @param workPackageId the OpenProject work package id that should match the xwiki pages.
     * @param forInstance if set to true, the endpoint should retrieve the links that have the instance property
     *     matching the OpenProject instance that made the request. Otherwise, the instance property is ignored and all
     *     the xwiki pages matching the work package id will be returned.
     * @param number the maximum number of elements that should be returned.
     * @param start the offset of the results.
     * @param orderField the field on which the results should be ordered on.
     * @param order how the results should be ordered. asc or desc.
     * @param withPrettyNames denotes whether the results should contain the pretty names of the documents or not.
     * @return a list of xwiki pages that have a {@link com.xwiki.projectmanagement.openproject.store.WorkPackageLink}
     *     to an OpenProject entity and that the current user has view rights on.
     * @throws XWikiRestException if there was any issue retrieving the xwiki pages.
     */
    @GET
    @Path("/workPackages/{id}")
    SearchResults getWorkPackages(
        @PathParam("wikiName") String wikiName,
        @PathParam("id") String workPackageId,
        @QueryParam("forInstance") @DefaultValue("true") Boolean forInstance,
        @QueryParam("number") @DefaultValue("-1") Integer number,
        @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("orderField") @DefaultValue("") String orderField,
        @QueryParam("order") @DefaultValue("asc") String order,
        @QueryParam("prettyNames") @DefaultValue("false") Boolean withPrettyNames
    ) throws XWikiRestException;
}
