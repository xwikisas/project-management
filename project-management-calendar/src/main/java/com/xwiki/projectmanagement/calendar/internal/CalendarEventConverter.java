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

import java.util.*;

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
 * @since 1.2.0-rc-7
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
     * @return the calendar event representation of the work item.
     */
    public CalendarEvent convert(WorkItem workItem)
    {
        CalendarEvent event = new CalendarEvent();
        event.setId(workItem.getLinkableValue(WorkItem.KEY_IDENTIFIER));
        event.setTitle(workItem.getLinkableValue(WorkItem.KEY_SUMMARY));
        Date startDate = workItem.getStartDate();
        if (startDate == null) {
            return null;
        }
        event.setStart(startDate);
        Date dueDate = workItem.getDueDate();
        event.setEnd(Objects.requireNonNullElseGet(dueDate, () -> new Date(startDate.getTime() + MILLIS_PER_HOUR)));

        event.setDescription(stripHtml(workItem.getDescription()));
        event.setStatus(workItem.getStatus());
        event.setAllDay(false);
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", workItem.getType());
        meta.put("asignees", workItem.getAssignees());
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
