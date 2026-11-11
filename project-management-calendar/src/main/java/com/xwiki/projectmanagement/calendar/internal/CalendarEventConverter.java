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

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import org.apache.commons.text.StringEscapeUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.fullcalendar.model.CalendarEvent;

import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Converts a list of {@link WorkItem} instances to {@link CalendarEvent} instances that can be consumed by the
 * FullCalendar macro.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component(roles = CalendarEventConverter.class)
@Singleton
public class CalendarEventConverter
{
    private static final long MILLIS_PER_HOUR = 3600000L;

    /**
     * Convert a single {@link WorkItem} to a {@link CalendarEvent}.
     *
     * @param workItem the work item to convert.
     * @return the calendar event representation of the work item, or {@code null} if the work item has neither a
     *     start date nor a due date
     */
    public CalendarEvent convert(WorkItem workItem)
    {
        CalendarEvent event = new CalendarEvent();
        event.setId(workItem.getLinkableValue(WorkItem.KEY_IDENTIFIER));
        event.setTitle(workItem.getLinkableValue(WorkItem.KEY_SUMMARY));
        Date startDate = workItem.getStartDate();
        Date dueDate = workItem.getDueDate();
        if (startDate != null && isDateOnly(startDate) && (dueDate == null || isDateOnly(dueDate))) {
            // Dates without time (e.g. the dates of the OpenProject work packages) are displayed as an all-day event
            // covering the due date too (the end of an all-day event is exclusive).
            event.setStart(startDate);
            event.setEnd(getNextDay(Objects.requireNonNullElse(dueDate, startDate)));
            event.setAllDay(true);
        } else if (startDate != null) {
            event.setStart(startDate);
            event.setEnd(
                Objects.requireNonNullElseGet(dueDate, () -> new Date(startDate.getTime() + MILLIS_PER_HOUR)));
            event.setAllDay(false);
        } else if (dueDate != null) {
            // Without a start date, the work item is displayed as an all-day event on its due date (the end of an
            // all-day event is exclusive).
            event.setStart(dueDate);
            event.setEnd(getNextDay(dueDate));
            event.setAllDay(true);
        } else {
            // A work item without any date cannot be placed in the calendar.
            return null;
        }

        event.setDescription(stripHtml(workItem.getDescription()));
        event.setStatus(workItem.getStatus());
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", workItem.getType());
        meta.put("assignees", workItem.getAssignees());
        meta.put("creator", workItem.getCreator());
        meta.put("priority", workItem.getPriority());
        meta.put("entity-type", "workItem");
        event.setMeta(meta);
        return event;
    }

    /**
     * Convert a list of {@link WorkItem} instances to a list of {@link CalendarEvent} instances.
     *
     * @param workItems the work items to convert.
     * @return the calendar event representations.
     */
    public List<CalendarEvent> convertAll(List<WorkItem> workItems)
    {
        List<CalendarEvent> events = new ArrayList<>();
        for (WorkItem workItem : workItems) {
            CalendarEvent event = convert(workItem);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * @param date a date
     * @return {@code true} if the date has no time, i.e. if it is at midnight in the server time zone (the dates
     *     without time are parsed as such)
     */
    private static boolean isDateOnly(Date date)
    {
        return LocalTime.MIDNIGHT.equals(date.toInstant().atZone(ZoneId.systemDefault()).toLocalTime());
    }

    private static Date getNextDay(Date date)
    {
        ZoneId zone = ZoneId.systemDefault();
        return Date.from(date.toInstant().atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Strip HTML tags and unescape HTML entities from a string. This prevents raw HTML markup (e.g., from OpenProject
     * descriptions) from appearing in the calendar event's tooltip.
     *
     * @param html the input string that may contain HTML markup.
     * @return the cleaned plain text, or {@code null} if the input was {@code null}.
     */
    private String stripHtml(String html)
    {
        if (html == null || html.isEmpty()) {
            return html;
        }
        String text = html.replaceAll("<[^>]*>", "");
        return StringEscapeUtils.unescapeHtml4(text.trim());
    }
}
