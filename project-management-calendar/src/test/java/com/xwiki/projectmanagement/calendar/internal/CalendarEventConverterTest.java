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
package com.xwiki.projectmanagement.calendar.internal;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.fullcalendar.model.CalendarEvent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CalendarEventConverter}.
 *
 * @version $Id$
 */
@ComponentTest
class CalendarEventConverterTest
{
    private static final Date START = Date.from(Instant.parse("2026-03-10T09:00:00Z"));

    private static final Date DUE = Date.from(Instant.parse("2026-03-12T17:30:00Z"));

    @InjectMockComponents
    private CalendarEventConverter converter;

    @Test
    void convert()
    {
        WorkItem workItem = createWorkItem("42", START);
        workItem.setDueDate(DUE);
        workItem.setDescription("<p>Fix the <b>login</b> page</p>");
        workItem.setStatus("In progress");
        workItem.setType("Bug");
        workItem.setPriority("High");
        Linkable creator = new Linkable("John", "/users/john");
        workItem.setCreator(creator);
        List<Linkable> assignees = List.of(new Linkable("Jane", "/users/jane"));
        workItem.setAssignees(assignees);

        CalendarEvent event = this.converter.convert(workItem);

        // The id and the title are the values of the linkable properties, not their locations.
        assertEquals("42", event.getId());
        assertEquals("Summary 42", event.getTitle());
        assertEquals(START, event.getStart());
        assertEquals(DUE, event.getEnd());
        assertEquals("Fix the login page", event.getDescription());
        assertEquals("In progress", event.getStatus());
        assertFalse(event.isAllDay());
        Map<String, Object> expectedMeta = new HashMap<>();
        expectedMeta.put("type", "Bug");
        expectedMeta.put("assignees", assignees);
        expectedMeta.put("creator", creator);
        expectedMeta.put("priority", "High");
        expectedMeta.put("entity-type", "workItem");
        assertEquals(expectedMeta, event.getMeta());
    }

    @Test
    void convertWithoutDueDateLastsOneHour()
    {
        CalendarEvent event = this.converter.convert(createWorkItem("1", START));

        assertEquals(START, event.getStart());
        assertEquals(Date.from(Instant.parse("2026-03-10T10:00:00Z")), event.getEnd());
    }

    @Test
    void convertWithoutStartDate()
    {
        WorkItem workItem = createWorkItem("1", null);
        workItem.setDueDate(DUE);

        CalendarEvent event = this.converter.convert(workItem);

        // The work item is displayed as an all-day event on its due date.
        assertEquals(DUE, event.getStart());
        assertEquals(Date.from(Instant.parse("2026-03-13T17:30:00Z")), event.getEnd());
        assertTrue(event.isAllDay());
        assertEquals("1", event.getId());
        assertEquals("workItem", event.getMeta().get("entity-type"));
    }

    @Test
    void convertWithoutStartDateAndDueDate()
    {
        // A work item without any date cannot be placed in the calendar.
        assertNull(this.converter.convert(createWorkItem("1", null)));
    }

    @Test
    void convertWithoutLinkableProperties()
    {
        WorkItem workItem = new WorkItem();
        workItem.setStartDate(START);

        CalendarEvent event = this.converter.convert(workItem);

        assertNull(event.getId());
        assertNull(event.getTitle());
        assertNull(event.getDescription());
        assertEquals("workItem", event.getMeta().get("entity-type"));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "'', ''",
        "Plain text, Plain text",
        "'  <div>\n<p>First</p>\n<p>Second</p>\n</div>  ', 'First\nSecond'",
        "<p>Tom &amp; Jerry &lt;3 &quot;cheese&quot;</p>, Tom & Jerry <3 \"cheese\"",
        "<a href=\"https://example.org\" title=\"x\">link</a> &nbsp;, 'link  '",
        "<img src=\"x.png\"/>, ''"
    })
    void convertDescription(String description, String expectedDescription)
    {
        WorkItem workItem = createWorkItem("1", START);
        workItem.setDescription(description);

        assertEquals(expectedDescription, this.converter.convert(workItem).getDescription());
    }

    @Test
    void convertAllSkipsWorkItemsWithoutDates()
    {
        WorkItem dueDateOnly = createWorkItem("4", null);
        dueDateOnly.setDueDate(DUE);

        List<CalendarEvent> events = this.converter.convertAll(
            List.of(createWorkItem("1", START), createWorkItem("2", null), createWorkItem("3", DUE), dueDateOnly));

        assertEquals(3, events.size());
        assertEquals("1", events.get(0).getId());
        assertEquals("3", events.get(1).getId());
        assertEquals("4", events.get(2).getId());
    }

    @Test
    void convertAllWithNoWorkItems()
    {
        assertTrue(this.converter.convertAll(Collections.emptyList()).isEmpty());
    }

    private static WorkItem createWorkItem(String id, Date startDate)
    {
        WorkItem workItem = new WorkItem();
        workItem.setIdentifier(new Linkable(id, "/work_packages/" + id));
        workItem.setSummary(new Linkable("Summary " + id, "/work_packages/" + id));
        workItem.setStartDate(startDate);
        return workItem;
    }
}
