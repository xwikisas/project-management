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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.fullcalendar.model.CalendarEvent;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.calendar.CalendarEventProvider;
import com.xwiki.projectmanagement.calendar.internal.CalendarEventConverter;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultCalendarResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultCalendarResourceTest
{
    private static final String WIKI = "xwiki";

    private static final String HINT = "test";

    private static final String FILTERS = "{\"query\":{\"filters\":[]}}";

    private static final String START = "2026-03-01T00:00:00Z";

    private static final String END = "2026-04-12T00:00:00Z";

    @InjectMockComponents
    private DefaultCalendarResource resource;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @MockComponent
    private CalendarEventConverter calendarEventConverter;

    @Mock
    private ProjectManagementClient client;

    @Mock
    private CalendarEventProvider calendarEventProvider;

    @Captor
    private ArgumentCaptor<List<LiveDataQuery.Filter>> filtersCaptor;

    private final LiveDataQuery.Filter statusFilter = new LiveDataQuery.Filter("status", "equals", "open");

    private final List<WorkItem> workItems = List.of(new WorkItem());

    private final CalendarEvent workItemEvent = new CalendarEvent();

    private final CalendarEvent providerEvent = new CalendarEvent();

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.componentManager.getInstance(ProjectManagementClient.class, HINT)).thenReturn(this.client);
        when(this.componentManager.getInstance(CalendarEventProvider.class, HINT))
            .thenReturn(this.calendarEventProvider);

        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setQuery(new LiveDataQuery());
        configuration.getQuery().setFilters(List.of(this.statusFilter));
        when(this.stringLiveDataConfigResolver.resolve(FILTERS)).thenReturn(configuration);

        when(this.client.getWorkItems(anyInt(), anyInt(), anyList(), anyList()))
            .thenReturn(new PaginatedResult<>(this.workItems, 1, 25, 1));
        when(this.calendarEventConverter.convertAll(this.workItems)).thenReturn(List.of(this.workItemEvent));
        when(this.calendarEventProvider.getMoreEvents(anyList())).thenReturn(List.of(this.providerEvent));
    }

    @Test
    void getCalendarEvents() throws Exception
    {
        Response response = this.resource.getCalendarEvents(WIKI, HINT, FILTERS, START, END, 0, false);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(List.of(this.workItemEvent, this.providerEvent), response.getEntity());

        // The work items are filtered by the requested filters and by the calendar window: they must start before its
        // end and end after its start. A missing limit defaults to 25 work items.
        verify(this.client).getWorkItems(eq(1), eq(25), this.filtersCaptor.capture(), eq(Collections.emptyList()));
        List<LiveDataQuery.Filter> filters = this.filtersCaptor.getValue();
        assertEquals(List.of("status equals open", "startDate before " + END, "dueDate after " + START),
            serialize(filters));
        // The provider of the additional events receives the same filters.
        verify(this.calendarEventProvider).getMoreEvents(filters);
    }

    @Test
    void getCalendarEventsWithLimitAndNoFilters() throws Exception
    {
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        when(this.stringLiveDataConfigResolver.resolve("")).thenReturn(configuration);

        Response response = this.resource.getCalendarEvents(WIKI, HINT, null, START, END, 10, false);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(this.client).getWorkItems(eq(1), eq(10), this.filtersCaptor.capture(), any());
        assertEquals(List.of("startDate before " + END, "dueDate after " + START),
            serialize(this.filtersCaptor.getValue()));
    }

    @Test
    void getCalendarEventsExcludingWorkItems() throws Exception
    {
        Response response = this.resource.getCalendarEvents(WIKI, HINT, FILTERS, START, END, 0, true);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(List.of(this.providerEvent), response.getEntity());
        verify(this.client, never()).getWorkItems(anyInt(), anyInt(), anyList(), anyList());
        verifyNoInteractions(this.calendarEventConverter);
        verify(this.calendarEventProvider).getMoreEvents(this.filtersCaptor.capture());
        assertEquals(List.of("status equals open", "startDate before " + END, "dueDate after " + START),
            serialize(this.filtersCaptor.getValue()));
    }

    @Test
    void getCalendarEventsWithUnknownClient() throws Exception
    {
        when(this.componentManager.getInstance(ProjectManagementClient.class, "unknown"))
            .thenThrow(new ComponentLookupException("No client"));

        Response response = this.resource.getCalendarEvents(WIKI, "unknown", FILTERS, START, END, 0, false);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.client, this.calendarEventProvider);
    }

    @Test
    void getCalendarEventsWithoutCalendarEventProvider() throws Exception
    {
        when(this.componentManager.getInstance(CalendarEventProvider.class, HINT))
            .thenThrow(new ComponentLookupException("No provider"));

        Response response = this.resource.getCalendarEvents(WIKI, HINT, FILTERS, START, END, 0, false);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.client);
    }

    @Test
    void getCalendarEventsWithInvalidFilters() throws Exception
    {
        when(this.stringLiveDataConfigResolver.resolve("{invalid")).thenThrow(new LiveDataException("Invalid JSON"));

        Response response = this.resource.getCalendarEvents(WIKI, HINT, "{invalid", START, END, 0, false);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("The passed filter is invalid.", response.getEntity());
        verifyNoInteractions(this.client, this.calendarEventProvider);
    }

    @Test
    void getCalendarEventsWhenWorkItemRetrievalFails() throws Exception
    {
        when(this.client.getWorkItems(anyInt(), anyInt(), anyList(), anyList()))
            .thenThrow(new WorkItemRetrievalException("Failed to retrieve the work items."));

        Response response = this.resource.getCalendarEvents(WIKI, HINT, FILTERS, START, END, 0, false);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.calendarEventProvider);
    }

    @Test
    void getCalendarEventsWhenEventProviderFails() throws Exception
    {
        when(this.calendarEventProvider.getMoreEvents(anyList()))
            .thenThrow(new ProjectManagementException("Failed to retrieve the versions."));

        Response response = this.resource.getCalendarEvents(WIKI, HINT, FILTERS, START, END, 0, true);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    private static List<String> serialize(List<LiveDataQuery.Filter> filters)
    {
        List<String> result = new ArrayList<>();
        for (LiveDataQuery.Filter filter : filters) {
            result.add(filter.getProperty() + ' ' + filter.getConstraints().stream()
                .map(constraint -> constraint.getOperator() + ' ' + constraint.getValue())
                .collect(Collectors.joining(", ")));
        }
        return result;
    }
}
