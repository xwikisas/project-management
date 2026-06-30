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
import com.xwiki.projectmanagement.calendar.CalendarEventProvider;
import com.xwiki.projectmanagement.calendar.internal.CalendarEventConverter;
import com.xwiki.projectmanagement.calendar.rest.CalendarResource;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.internal.rest.AbstractProjectManagementResource;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
 * @since 1.2.0-rc-9
 */
@Component
@Singleton
@Named("com.xwiki.projectmanagement.calendar.internal.rest.DefaultCalendarResource")
public class DefaultCalendarResource extends AbstractProjectManagementResource implements CalendarResource
{
    @Inject
    private CalendarEventConverter calendarEventConverter;

    @Override
    public Response getCalendarEvents(String wiki, String projectManagementHint, String filters, String start,
        String end, int limit, boolean excludeWorkItems)
    {
        prepareClientContext();
        try {
            ProjectManagementClient client =
                this.componentManager.getInstance(ProjectManagementClient.class, projectManagementHint);
            CalendarEventProvider calendarEventProvider =
                this.componentManager.getInstance(CalendarEventProvider.class, projectManagementHint);
            int pageSize = limit != 0 ? limit : 25;
            List<LiveDataQuery.Filter> queryFilters = getFilters(filters);
            // Filter work items whose date range overlaps with the requested calendar window.
            queryFilters.add(new LiveDataQuery.Filter(WorkItem.KEY_START_DATE, "before", end));
            queryFilters.add(new LiveDataQuery.Filter(WorkItem.KEY_DUE_DATE, "after", start));
            List<CalendarEvent> calendarEvents = new ArrayList<>();
            // Fetch standard work item events unless the caller explicitly requests only provider events.
            if (!excludeWorkItems) {
                PaginatedResult<WorkItem> workItems =
                    client.getWorkItems(1, pageSize, queryFilters, Collections.emptyList());
                calendarEvents.addAll(this.calendarEventConverter.convertAll(workItems.getItems()));
            }
            // Add additional events from the calendar event provider.
            calendarEvents.addAll(calendarEventProvider.getMoreEvents());
            return Response.ok(calendarEvents).build();
        } catch (ComponentLookupException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ExceptionUtils.getStackTrace(e)).build();
        } catch (LiveDataException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("The passed filter is invalid.").build();
        } catch (ProjectManagementException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ExceptionUtils.getStackTrace(e))
                .build();
        }
    }
}
