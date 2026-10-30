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
package com.xwiki.projectmanagement.openproject.internal;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.xwiki.fullcalendar.model.CalendarEvent;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.Sprint;
import com.xwiki.projectmanagement.openproject.model.Version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectCalendarEventProvider}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectCalendarEventProviderTest
{
    private static final LiveDataQuery.Filter PROJECT_FILTER = new LiveDataQuery.Filter("project", "=", "12");

    private static final LiveDataQuery.Filter OTHER_FILTER = new LiveDataQuery.Filter("startDate", "before", "x");

    @InjectMockComponents
    private OpenProjectCalendarEventProvider provider;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private ProjectManagementClientExecutionContext executionContext;

    @Mock
    private OpenProjectApiClient apiClient;

    @BeforeEach
    void setUp()
    {
        when(this.executionContext.get("instance")).thenReturn("main");
        when(this.executionContext.get("limit")).thenReturn("10");
        when(this.executionContext.get("sprintColor")).thenReturn("#ff0000");
        when(this.executionContext.get("versionColor")).thenReturn("#00ff00");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
    }

    private void requestTypes(boolean sprints, boolean versions)
    {
        when(this.executionContext.get("sprint")).thenReturn(String.valueOf(sprints));
        when(this.executionContext.get("version")).thenReturn(String.valueOf(versions));
    }

    private static Sprint sprint(int id, String name, String start, String finish)
    {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setName(name);
        sprint.setStartDate(start);
        sprint.setFinishDate(finish);
        return sprint;
    }

    private static Version version(int id, String name, String start, String end)
    {
        Version version = new Version();
        version.setId(id);
        version.setName(name);
        version.setStartDate(start);
        version.setEndDate(end);
        version.setDescription("Description of " + name);
        version.setStatus("open");
        return version;
    }

    private static Date date(String isoDate)
    {
        return java.sql.Date.valueOf(LocalDate.parse(isoDate));
    }

    @Test
    void failsWithoutConfiguredInstance()
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.notConfigured());
        requestTypes(true, true);

        WorkItemRetrievalException exception =
            assertThrows(WorkItemRetrievalException.class, () -> this.provider.getMoreEvents(List.of()));

        assertEquals("No configuration for instance [main] was found.", exception.getMessage());
    }

    @Test
    void failsWhenTheProjectIsNotAnId()
    {
        requestTypes(true, true);
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("project", "=", "xwiki-platform"));

        WorkItemRetrievalException exception =
            assertThrows(WorkItemRetrievalException.class, () -> this.provider.getMoreEvents(filters));

        assertEquals("The project [xwiki-platform] is not a valid OpenProject project id.", exception.getMessage());
        assertEquals(400, exception.getStatusCode());
        verifyNoInteractions(this.apiClient);
    }

    @Test
    void getSprintsWithTheDefaultLimit() throws Exception
    {
        requestTypes(true, false);
        when(this.executionContext.get("limit")).thenReturn(null);
        when(this.apiClient.getSprints(1, 25, "")).thenReturn(
            new PaginatedResult<>(List.of(sprint(3, "Sprint 3", "2026-03-02", "2026-03-13")), 1, 25, 1));

        assertEquals(List.of("sprint-3"), this.provider.getMoreEvents(List.of()).stream().map(CalendarEvent::getId)
            .collect(Collectors.toList()));
    }

    @Test
    void failsWhenTheUserIsNotConnected()
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.notConnected());
        requestTypes(true, true);

        WorkItemRetrievalException exception =
            assertThrows(WorkItemRetrievalException.class, () -> this.provider.getMoreEvents(List.of()));

        assertEquals("The current user isn't connected to the OpenProject instance [main].", exception.getMessage());
        assertEquals(401, exception.getStatusCode());
    }

    @Test
    void returnsNothingWhenNeitherSprintsNorVersionsAreRequested() throws Exception
    {
        requestTypes(false, false);

        assertTrue(this.provider.getMoreEvents(List.of(PROJECT_FILTER)).isEmpty());
        verifyNoInteractions(this.apiClient);
    }

    @Test
    void getSprintsOfTheFilteredProject() throws Exception
    {
        requestTypes(true, false);
        when(this.apiClient.getProjectSprints(1, 10, "", 12)).thenReturn(new PaginatedResult<>(
            List.of(sprint(3, "Sprint 3", "2026-03-02", "2026-03-13"), sprint(4, "No start", null, "2026-03-20"),
                sprint(5, "Bad start", "not-a-date", null)), 1, 10, 3));

        List<CalendarEvent> events = this.provider.getMoreEvents(List.of(OTHER_FILTER, PROJECT_FILTER));

        // Sprints without a valid start date are skipped.
        assertEquals(1, events.size());
        CalendarEvent event = events.get(0);
        assertEquals("sprint-3", event.getId());
        assertEquals("Sprint 3", event.getTitle());
        assertEquals(date("2026-03-02"), event.getStart());
        assertEquals(date("2026-03-13"), event.getEnd());
        assertTrue(event.isAllDay());
        assertEquals("#ff0000", event.getColor());
        assertEquals(Map.of("op-id", 3, "entity-type", "sprint"), event.getMeta());
    }

    @Test
    void sprintWithoutFinishDateLastsOneDay() throws Exception
    {
        requestTypes(true, false);
        when(this.apiClient.getSprints(1, 10, "")).thenReturn(
            new PaginatedResult<>(List.of(sprint(3, "Sprint 3", "2026-03-02", "")), 1, 10, 1));

        List<CalendarEvent> events = this.provider.getMoreEvents(List.of(OTHER_FILTER));

        assertEquals(1, events.size());
        assertEquals(date("2026-03-02").getTime() + 24 * 60 * 60 * 1000, events.get(0).getEnd().getTime());
    }

    @Test
    void ignoresSprintsOfProjectsWithoutBacklogs() throws Exception
    {
        requestTypes(true, false);
        when(this.apiClient.getProjectSprints(1, 10, "", 12))
            .thenThrow(new WorkItemRetrievalException("Forbidden", 403));

        assertTrue(this.provider.getMoreEvents(List.of(PROJECT_FILTER)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, -2 })
    void propagatesOtherSprintRetrievalErrors(int statusCode) throws Exception
    {
        requestTypes(true, false);
        // A negative value stands for an error without status code.
        WorkItemRetrievalException error =
            new WorkItemRetrievalException("Bad request", statusCode < 0 ? null : statusCode);
        when(this.apiClient.getProjectSprints(anyInt(), anyInt(), anyString(),
            anyInt())).thenThrow(error);

        List<LiveDataQuery.Filter> filters = List.of(PROJECT_FILTER);
        assertSame(error, assertThrows(WorkItemRetrievalException.class, () -> this.provider.getMoreEvents(filters)));
    }

    @Test
    void getVersionsOfTheFilteredProject() throws Exception
    {
        requestTypes(false, true);
        when(this.apiClient.getProjectVersions(12)).thenReturn(new PaginatedResult<>(
            List.of(version(7, "1.0", "2026-01-05", null), version(8, "No start", null, "2026-02-01")), 1, 10, 2));

        List<CalendarEvent> events = this.provider.getMoreEvents(List.of(PROJECT_FILTER));

        assertEquals(1, events.size());
        CalendarEvent event = events.get(0);
        assertEquals("version-7", event.getId());
        assertEquals("1.0", event.getTitle());
        assertEquals("Description of 1.0", event.getDescription());
        assertEquals("open", event.getStatus());
        assertEquals("#00ff00", event.getColor());
        assertEquals(date("2026-01-05"), event.getStart());
        // Without end date, the version lasts one day.
        assertEquals(date("2026-01-05").getTime() + 24 * 60 * 60 * 1000, event.getEnd().getTime());
        assertTrue(event.isAllDay());
        assertEquals(Map.of("op-id", 7, "entity-type", "version"), event.getMeta());
    }

    @Test
    void getAllVersionsAndSprintsWithoutProjectFilter() throws Exception
    {
        requestTypes(true, true);
        when(this.apiClient.getSprints(1, 10, "")).thenReturn(
            new PaginatedResult<>(List.of(sprint(3, "Sprint 3", "2026-03-02", "2026-03-13")), 1, 10, 1));
        when(this.apiClient.getVersions()).thenReturn(
            new PaginatedResult<>(List.of(version(7, "1.0", "2026-01-05", "2026-01-30")), 1, 10, 1));

        List<CalendarEvent> events = this.provider.getMoreEvents(List.of());

        // Sprints come first, then the versions.
        assertEquals(List.of("sprint-3", "version-7"), events.stream().map(CalendarEvent::getId)
            .collect(Collectors.toList()));
        assertEquals(date("2026-01-30"), events.get(1).getEnd());
    }
}
