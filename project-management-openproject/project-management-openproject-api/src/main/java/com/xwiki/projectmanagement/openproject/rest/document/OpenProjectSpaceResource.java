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

/**
 * Resource for handling the OpenProject integration spaces.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Path("/wikis/{wikiName}/openproject/spaces")
public interface OpenProjectSpaceResource
{
    /**
     * Creates the OpenProject project template at the given document reference.
     *
     * @param wiki the wiki where the template should be created.
     * @param documentReference the reference identifying where the template should be created.
     * @param withId if set to true, also creates and attaches to the created document unique identifiers. The
     *     returned documents will also contain the attached unique id.
     * @return 200 together with a list of {@link org.xwiki.rest.model.jaxb.Page}, representing the created pages. 401
     *     if the user does not have edit rights on the space. 403 if the page already exists. 406 if the requested wiki
     *     does not have the OpenProject integration UI installed.
     * @throws XWikiRestException if any other problem occurs in the process of space creation.
     */
    @POST
    Response createSpace(
        @PathParam("wikiName") String wiki,
        @QueryParam("docRef") String documentReference,
        @QueryParam("withId") @DefaultValue("false") Boolean withId)
        throws XWikiRestException;
}
