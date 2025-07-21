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

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiResource;

import com.xpn.xwiki.XWikiException;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.internal.service.CreateConnectionService;

/**
 * REST Resource used for creating OpenProject configuration pages.
 *
 * @version $Id$
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.connection.CreateConnection")
@Path("openproject/connections")
public class CreateConnection extends XWikiResource
{
    @Inject
    private CreateConnectionService createConnectionService;

    /**
     * @param data the {@link com.xwiki.projectmanagement.openproject.config.OpenProjectConnection} that will be
     *     created or updated.
     * @return 200 if the creation/update was successful; 409 if an instance with the same connection name exists; 500
     *     if any error was encountered.
     */
    @POST
    @Path("/create")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response createConnection(Map<String, Object> data)
    {
        String connectionName = (String) data.get("connectionName");
        String serverURL = (String) data.get("serverURL");
        String clientId = (String) data.get("clientId");
        String clientSecret = (String) data.get("clientSecret");
        try {
            createConnectionService.createConnection(connectionName, serverURL, clientId, clientSecret);
        } catch (XWikiException e) {
            return Response.serverError().entity(ExceptionUtils.getStackTrace(e)).build();
        } catch (ProjectManagementException e) {
            return Response.status(Response.Status.CONFLICT).build();
        }

        return Response.ok().build();
    }
}
