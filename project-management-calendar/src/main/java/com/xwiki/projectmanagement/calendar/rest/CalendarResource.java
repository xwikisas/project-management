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

package com.xwiki.projectmanagement.calendar.rest;

import org.xwiki.rest.XWikiRestComponent;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST resource that returns project management work items as FullCalendar-compatible JSON events.
 *
 * @version $Id$
 */
@Path("/wikis/{wikiName}/projectmanagement/{hint}/calendar")
public interface CalendarResource extends XWikiRestComponent
{
    /**
     * Retrieve work items from a project management implementation and return them as FullCalendar-compatible JSON
     * events.
     *
     * @param wiki the wiki from where the project management implementation can retrieve data.
     * @param projectManagementHint the hint of the project management implementation (e.g., {@code openproject}).
     * @param filters a JSON string representing the filters to apply on the work items. The JSON is the serialized
     *     version of the {@link org.xwiki.livedata.LiveDataQuery.Filter} list.
     * @param start the start date of the range to filter work items (ISO format), as sent by FullCalendar.
     * @param end the end date of the range to filter work items (ISO format), as sent by FullCalendar.
     * @param limit the maximum number of work items to return.
     * @param offset the offset for pagination.
     * @return a JSON array of calendar events. HTTP 200: successful retrieval. HTTP 401: the user is not logged in.
     *     HTTP 403: the user does not have the rights to retrieve the work items.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    Response getCalendarEvents(
        @PathParam("wikiName") String wiki,
        @PathParam("hint") String projectManagementHint,
        @QueryParam("filters") String filters,
        @QueryParam("start") String start,
        @QueryParam("end") String end,
        @QueryParam("limit") int limit,
        @QueryParam("offset") Long offset
    );
}
