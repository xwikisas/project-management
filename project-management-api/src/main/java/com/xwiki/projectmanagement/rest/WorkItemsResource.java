package com.xwiki.projectmanagement.rest;

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

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.xwiki.projectmanagement.model.WorkItem;

/**
 * The resource that exposes CRUD operations over the work items of the different project management implementations.
 *
 * @version $Id$
 * @since 1.0
 */
@Path("/wikis/{wikiName}/projectmanagement/{hint}/workitems")
public interface WorkItemsResource
{
    /**
     * Retrieve a work item from a specific project management implementation.
     *
     * @param wiki the wiki from where the project management implementation can retrieve. Depending on the
     *     implementation, different wikis can have different configurations or the wiki might be irrelevant to the
     *     query.
     * @param projectManagementHint the hint of the project management implementation.
     * @param workItemId the id of the work item.
     * @return a work item model. HTTP 200: successful retrieval. HTTP 401: the user is not logged in. HTTP 403: the
     *     user does not have the rights to retrieve the work items.
     */
    @GET
    @Path("/{workItemId}")
    @Produces({ MediaType.APPLICATION_JSON })
    Response getWorkItem(
        @PathParam("wikiName") String wiki,
        @PathParam("hint") String projectManagementHint,
        @PathParam("workItemId") String workItemId
    );

    /**
     * Retrieve a list of work items.
     *
     * @param wiki the wiki from where the project management implementation can retrieve. Depending on the
     *     implementation, different wikis can have different configurations or the wiki might be irrelevant to the
     *     query.
     * @param projectManagementHint the hint of the project management implementation.
     * @param page the page number that is being requested.
     * @param pageSize the maximum number of elements that can be returned by this query.
     * @return a paginated result of work items specific to the page number. HTTP 200: successful retrieval. HTTP 401:
     *     the user is not logged in. HTTP 403: the user does not have the rights to retrieve the work items.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    Response getWorkItems(
        @PathParam("wikiName") String wiki,
        @PathParam("hint") String projectManagementHint,
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("pageSize") @DefaultValue("10") int pageSize
    );

    /**
     * Create a work item based the project management implementation.
     *
     * @param wiki the wiki from where the project management implementation can retrieve. Depending on the
     *     implementation, different wikis can have different configurations or the wiki might be irrelevant to the
     *     query.
     * @param projectManagementHint the hint of the project management implementation.
     * @param workItem the work item that needs to be created.
     * @return the created work item. HTTP 200: successful retrieval. HTTP 400: invalid payload. HTTP 401: the user is
     *     not logged in. HTTP 403: the user does not have the rights to retrieve the work items. HTTP 409: work item
     *     could not be created due to a similar resource already existing.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    Response createWorkItem(
        @PathParam("wikiName") String wiki,
        @PathParam("hint") String projectManagementHint,
        WorkItem workItem
    );

    /**
     * Update a work item.
     *
     * @param wiki the wiki from where the project management implementation can retrieve. Depending on the
     *     implementation, different wikis can have different configurations or the wiki might be irrelevant to the
     *     query.
     * @param projectManagementHint the hint of the project management implementation.
     * @param workItem the work item that needs to be updated.
     * @return the updated work item. HTTP 200: successful retrieval. HTTP 400: invalid payload. HTTP 401: the user is
     *     not logged in. HTTP 403: the user does not have the rights to retrieve the work items. HTTP 404: the work
     *     item that needs updating could not be found.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    Response updateWorkItem(
        @PathParam("wikiName") String wiki,
        @PathParam("hint") String projectManagementHint,
        WorkItem workItem
    );

    /**
     * Removes a work item identified by an id.
     *
     * @param wiki the wiki from where the project management implementation can retrieve. Depending on the
     *     implementation, different wikis can have different configurations or the wiki might be irrelevant to the
     *     query.
     * @param projectManagementHint the hint of the project management implementation.
     * @param workItemId the id of the work item that needs to be removed.
     * @return the deleted item. HTTP 200: successful retrieval. HTTP 401: the user is not logged in. HTTP 403: the user
     *     does not have the rights to retrieve the work items. HTTP 404: the work item does not exist.
     */
    @DELETE
    @Path("/{workItemId}")
    @Produces({ MediaType.APPLICATION_JSON })
    Response deleteWorkItem(
        @PathParam("wikiName") String wiki,
        @PathParam("hint") String projectManagementHint,
        @PathParam("workItemId") String workItemId
    );
}
