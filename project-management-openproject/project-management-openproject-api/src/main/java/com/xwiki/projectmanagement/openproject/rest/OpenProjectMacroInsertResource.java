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

package com.xwiki.projectmanagement.openproject.rest;

import javax.ws.rs.Encoded;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.xwiki.projectmanagement.openproject.model.MacroInsertion;

/**
 * Resource for inserting a macro inside the content of a page.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Path("/wikis/{wikiName}/spaces/{spaces: .+}/pages/{pageName}/openproject/macros")
public interface OpenProjectMacroInsertResource
{
    /**
     * Insert a macro in a xwiki page.
     *
     * @param wikiName the wiki where to look for the page.
     * @param spaces the spaces under which the page resides.
     * @param pageName the name of the page.
     * @param macroInsertion the model pointing to the location where the macro should be inserted.
     * @return 200 if the insertion succeeded. 423 if the document is being locked by some other user. 404 if the
     *     document or the location of the insertion could not be found. 400 if the insertion model is missing
     *     information. i.e. macro id.
     */
    @POST
    Response insetIntoPage(
        @PathParam("wikiName") String wikiName,
        @PathParam("spaces") @Encoded String spaces,
        @PathParam("pageName") String pageName,
        MacroInsertion macroInsertion
    );
}
