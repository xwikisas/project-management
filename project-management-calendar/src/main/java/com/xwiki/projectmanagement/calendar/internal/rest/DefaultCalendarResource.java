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

package com.xwiki.projectmanagement.calendar.internal.rest;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.calendar.CalendarEventProvider;
import com.xwiki.projectmanagement.calendar.internal.CalendarEventConverter;
import com.xwiki.projectmanagement.calendar.rest.CalendarResource;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.rest.AbstractProjectManagementResource;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.fullcalendar.model.CalendarEvent;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of {@link CalendarResource}.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Singleton
@Named("com.xwiki.projectmanagement.calendar.internal.rest.DefaultCalendarResource")
public class DefaultCalendarResource extends AbstractProjectManagementResource implements CalendarResource
{
    /**
     * The maximum number of work items displayed in a calendar window when the calendar isn't given a limit. They are
     * retrieved with all their properties (e.g. the description shown when hovering an event), which can take about 2
     * seconds per 100 OpenProject work packages, so a few hundreds is already slow.
     */
    private static final int MAX_WORK_ITEMS = 1000;

    @Inject
    private CalendarEventConverter calendarEventConverter;

    @Inject
    private ProjectManagementManager projectManagementManager;

    @Override
    public Response getCalendarEvents(String wiki, String projectManagementHint, String filters, String start,
        String end, int limit, boolean excludeWorkItems)
    {
        return handleRequest(wiki, () -> {
            try {
                ProjectManagementClient client =
                    this.componentManager.getInstance(ProjectManagementClient.class, projectManagementHint);
                CalendarEventProvider calendarEventProvider =
                    this.componentManager.getInstance(CalendarEventProvider.class, projectManagementHint);
                List<LiveDataQuery.Filter> queryFilters = new ArrayList<>(getFilters(filters));
                // Filter work items whose date range overlaps with the requested calendar window, including the ones
                // having only a start date or only a due date.
                queryFilters.add(new LiveDataQuery.Filter(WorkItem.KEY_DATE_RANGE, "between", start + '/' + end));
                List<CalendarEvent> calendarEvents = new ArrayList<>();
                // Fetch standard work item events unless the caller explicitly requests only provider events.
                if (!excludeWorkItems) {
                    // Without a limit, all the work items of the calendar window are displayed, up to a maximum.
                    PaginatedResult<WorkItem> workItems = limit > 0
                        ? client.getWorkItems(0, limit, queryFilters, Collections.emptyList())
                        : this.projectManagementManager.getAllWorkItems(projectManagementHint, queryFilters,
                            Collections.emptyList(), MAX_WORK_ITEMS);
                    calendarEvents.addAll(this.calendarEventConverter.convertAll(workItems.getItems()));
                }
                // Add additional events from the calendar event provider.
                calendarEvents.addAll(calendarEventProvider.getMoreEvents(queryFilters));
                return Response.ok(calendarEvents).build();
            } catch (ComponentLookupException e) {
                return errorResponse(Response.Status.BAD_REQUEST,
                    String.format("No project management client or calendar event provider with id [%s].",
                        projectManagementHint), e);
            } catch (LiveDataException e) {
                return Response.status(Response.Status.BAD_REQUEST).entity("The passed filter is invalid.").build();
            } catch (ProjectManagementException e) {
                return errorResponse(getErrorStatus(e), "Failed to retrieve the calendar events.", e);
            }
        });
    }

    /**
     * Answer with the invalid request, authentication or rights failure reported by the project management client, so
     * that the caller can tell the user what to do, and with an internal server error for any other failure.
     */
    private static Response.Status getErrorStatus(ProjectManagementException e)
    {
        if (e instanceof WorkItemRetrievalException) {
            Integer statusCode = ((WorkItemRetrievalException) e).getStatusCode();
            if (statusCode != null && (statusCode == Response.Status.BAD_REQUEST.getStatusCode()
                || statusCode == Response.Status.UNAUTHORIZED.getStatusCode()
                || statusCode == Response.Status.FORBIDDEN.getStatusCode()))
            {
                return Response.Status.fromStatusCode(statusCode);
            }
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }
}
