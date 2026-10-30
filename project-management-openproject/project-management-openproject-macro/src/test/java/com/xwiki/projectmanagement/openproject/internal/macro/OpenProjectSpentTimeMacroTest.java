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
package com.xwiki.projectmanagement.openproject.internal.macro;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSpentTimeMacroParameters;
import com.xwiki.projectmanagement.openproject.model.TimeEntry;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.date;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectSpentTimeMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectSpentTimeMacroTest extends AbstractDirectMacroTest
{
    private static final Linkable WORK_PACKAGE = new Linkable("#1 Fix", SERVER_URL + "/work_packages/1");

    private static final Linkable PROJECT = new Linkable("Alpha", SERVER_URL + "/projects/alpha");

    @InjectMockComponents
    private OpenProjectSpentTimeMacro macro;

    private final OpenProjectSpentTimeMacroParameters parameters = new OpenProjectSpentTimeMacroParameters();

    @BeforeEach
    void setUp() throws Exception
    {
        this.parameters.setInstance("main");
        this.parameters.setCount(10);
        when(this.apiClient.getTimeEntries(any(), any(), anyString()))
            .thenReturn(new PaginatedResult<>(List.of(), 1, 10, 0));
    }

    private static TimeEntry entry(Date spentOn, String hours, String comment, Linkable activity)
    {
        TimeEntry entry = new TimeEntry();
        entry.setSpentOn(spentOn);
        entry.setHours(hours);
        entry.setComment(comment);
        entry.setActivity(activity);
        entry.setWorkPackage(WORK_PACKAGE);
        entry.setProjectLink(PROJECT);
        return entry;
    }

    private static String hours(double hours)
    {
        // The hours are formatted with the default locale.
        return String.format("%.2f", hours);
    }

    private String executeAndGetFilters() throws Exception
    {
        this.macro.execute(this.parameters, "", this.context);
        ArgumentCaptor<String> filtersCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.apiClient).getTimeEntries(isNull(), eq(10), filtersCaptor.capture());
        return filtersCaptor.getValue();
    }

    private static String dateRange(LocalDate today, int days)
    {
        return String.format("{\"created_at\":{\"operator\":\"<>d\",\"values\":[\"%s\",\"%s\"]}}",
            today.minusDays(days), today.plusDays(1));
    }

    @Test
    void displaysTheTimeEntriesGroupedByDay() throws Exception
    {
        this.parameters.setProject("12");
        Date firstDay = date(2026, 3, 2);
        Date secondDay = date(2026, 3, 1);
        when(this.apiClient.getTimeEntries(any(), any(), anyString())).thenReturn(new PaginatedResult<>(List.of(
            entry(firstDay, "PT1H30M", "Review", new Linkable("Development", SERVER_URL + "/activities/1")),
            entry(secondDay, "invalid", null, new Linkable(" ", "")),
            entry(firstDay, "PT2H", null, null)), 1, 10, 3));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        // Invalid durations count as 0 hours, the entries of the same day are grouped in the order of the first
        // entry of each day.
        assertEquals("Total: " + hours(3.5) + " h\n"
            + "ACTIVITY|LOGGED FOR|COMMENT|HOURS|\n"
            + "2026-03-02|||" + hours(3.5) + "|\n"
            + "Development|Alpha / #1 Fix|Review|" + hours(1.5) + "|\n"
            + "-|Alpha / #1 Fix||" + hours(2) + "|\n"
            + "2026-03-01|||" + hours(0) + "|\n"
            + "-|Alpha / #1 Fix||" + hours(0) + "|\n"
            + "View project dashboard\n", text(result));
        List<String> links = links(result);
        assertEquals("Alpha -> " + SERVER_URL + "/projects/alpha", links.get(0));
        assertEquals("#1 Fix -> " + SERVER_URL + "/work_packages/1", links.get(1));
        assertEquals("View project dashboard -> " + SERVER_URL + "/projects/12", links.get(links.size() - 1));
    }

    @Test
    void linksToAProjectWithReservedURLCharacters() throws Exception
    {
        this.parameters.setProject("a/b c?d#e\"");

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals(List.of("View project dashboard -> " + SERVER_URL + "/projects/a%2Fb%20c%3Fd%23e%22"),
            links(result));
    }

    @Test
    void displaysNoTimeEntry() throws Exception
    {
        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("No time entries found.\nView time entries\n", text(result));
        assertEquals(List.of("View time entries -> " + SERVER_URL + "/time_entries"), links(result));
    }

    @Test
    void filtersByProjectAndDays() throws Exception
    {
        this.parameters.setProject("12");
        this.parameters.setDays(7);
        LocalDate before = LocalDate.now();

        String filters = executeAndGetFilters();

        // Accept both days in case the test runs around midnight.
        String expectedBefore =
            "[{\"project\":{\"operator\":\"=\",\"values\":[\"12\"]}}," + dateRange(before, 7) + "]";
        String expectedAfter =
            "[{\"project\":{\"operator\":\"=\",\"values\":[\"12\"]}}," + dateRange(LocalDate.now(), 7) + "]";
        assertTrue(filters.equals(expectedBefore) || filters.equals(expectedAfter), filters);
    }

    @Test
    void filtersByDaysOnly() throws Exception
    {
        this.parameters.setDays(3);
        LocalDate before = LocalDate.now();

        String filters = executeAndGetFilters();

        assertTrue(filters.equals("[" + dateRange(before, 3) + "]")
            || filters.equals("[" + dateRange(LocalDate.now(), 3) + "]"), filters);
    }

    @Test
    void filtersByProjectOnly() throws Exception
    {
        this.parameters.setProject("12");
        this.parameters.setDays(0);

        assertEquals("[{\"project\":{\"operator\":\"=\",\"values\":[\"12\"]}}]", executeAndGetFilters());
    }

    @Test
    void filtersByProjectWithSpecialCharacters() throws Exception
    {
        this.parameters.setProject("a\"]}},{\"x\":{\"operator\":\"!\",\"values\":[\"\\");
        this.parameters.setDays(0);

        assertEquals("[{\"project\":{\"operator\":\"=\",\"values\":"
            + "[\"a\\\"]}},{\\\"x\\\":{\\\"operator\\\":\\\"!\\\",\\\"values\\\":[\\\"\\\\\"]}}]",
            executeAndGetFilters());
    }

    @Test
    void doesNotFilterWithoutProjectNorDays() throws Exception
    {
        this.parameters.setProject(" ");
        this.parameters.setDays(0);

        assertEquals("", executeAndGetFilters());
    }

    @Test
    void failsWhenTheTimeEntriesCannotBeRetrieved() throws Exception
    {
        ProjectManagementException cause = new ProjectManagementException("unreachable");
        when(this.apiClient.getTimeEntries(any(), any(), anyString())).thenThrow(cause);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        assertEquals("Failed to retrieve time entries from OpenProject.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
