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
 * Hello.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Path("/wikis/{wikiName}/spaces/{spaces: .+}/pages/{pageName}/openproject/macros")
public interface OpenProjectMacroInsertResource
{
    /**
     * @param wikiName wiki.
     * @param spaces space.
     * @param pageName page.
     * @param macroInsertion selection.
     * @return smth.
     */
    @POST
    Response insetIntoPage(
        @PathParam("wikiName") String wikiName,
        @PathParam("spaces") @Encoded String spaces,
        @PathParam("pageName") String pageName,
        MacroInsertion macroInsertion
    );
}
