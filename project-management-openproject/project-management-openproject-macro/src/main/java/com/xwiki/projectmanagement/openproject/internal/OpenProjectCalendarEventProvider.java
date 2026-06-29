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

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.calendar.CalendarEventProvider;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.Sprint;
import com.xwiki.projectmanagement.openproject.model.Version;
import org.xwiki.component.annotation.Component;
import org.xwiki.fullcalendar.model.CalendarEvent;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Singleton
@Component
@Named("openproject")
public class OpenProjectCalendarEventProvider implements CalendarEventProvider
{

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    @Override
    public List<CalendarEvent> getMoreEvents() throws ProjectManagementException
    {
        boolean getSprints = Boolean.parseBoolean((String) this.executionContext.get("sprint"));
        boolean getVersion = Boolean.parseBoolean((String) this.executionContext.get("version"));
        OpenProjectApiClient apiClient = getOpenProjectApiClient();
        List<CalendarEvent> calendarEvents = new ArrayList<>();
        if (getSprints) {
            PaginatedResult<Sprint> sprints =
                apiClient.getSprints(1, Integer.parseInt((String) this.executionContext.get("limit")), "");
            calendarEvents.addAll(convertSprintsToCalendarEvents(sprints));
        }
        if (getVersion) {
            PaginatedResult<Version> versions = apiClient.getVersions();
            calendarEvents.addAll(convertVersionsToCalendarEvents(versions));
        }

        return calendarEvents;
    }

    private OpenProjectApiClient getOpenProjectApiClient() throws WorkItemRetrievalException
    {
        String connectionName = (String) this.executionContext.get("instance");
        OpenProjectApiClient openProjectApiClient =
            this.openProjectConfiguration.getOpenProjectApiClient(connectionName);

        if (openProjectApiClient == null) {
            throw new WorkItemRetrievalException(
                String.format("No configuration for instance [%s] was found.", connectionName));
        }
        return openProjectApiClient;
    }

    private List<CalendarEvent> convertSprintsToCalendarEvents(PaginatedResult<Sprint> sprints)
    {
        List<CalendarEvent> events = new ArrayList<>();
        for (Sprint sprint : sprints.getItems()) {
            Date startDate = parseDate(sprint.getStartDate());
            Date endDate = parseDate(sprint.getFinishDate());
            if (startDate != null) {
                CalendarEvent event = new CalendarEvent();
                event.setStart(startDate);
                // Default to one day after start date.
                event.setEnd(
                    Objects.requireNonNullElseGet(endDate, () -> new Date(startDate.getTime() + 24 * 60 * 60 * 1000)));
                event.setId("sprint-" + sprint.getId());
                event.setTitle(sprint.getName());
                event.setAllDay(true);
                event.setColor((String) this.executionContext.get("sprintColor"));
                event.setMeta(Map.of("op-id", sprint.getId(), "entity-type", "sprint"));
                events.add(event);
            }
        }
        return events;
    }

    private List<CalendarEvent> convertVersionsToCalendarEvents(PaginatedResult<Version> versions)
    {
        List<CalendarEvent> events = new ArrayList<>();
        for (Version version : versions.getItems()) {
            Date startDate = parseDate(version.getStartDate());
            Date endDate = parseDate(version.getEndDate());
            if (startDate != null) {
                CalendarEvent event = new CalendarEvent();
                event.setStart(startDate);
                // Default to one day after start date.
                event.setEnd(
                    Objects.requireNonNullElseGet(endDate, () -> new Date(startDate.getTime() + 24 * 60 * 60 * 1000)));
                event.setId("version-" + version.getId());
                event.setTitle(version.getName());
                event.setColor((String) this.executionContext.get("versionColor"));
                event.setDescription(version.getDescription());
                event.setStatus(version.getStatus());
                event.setAllDay(true);
                event.setMeta(Map.of("op-id", version.getId(),"entity-type", "version"));
                events.add(event);
            }
        }
        return events;
    }

    private Date parseDate(String dateStr)
    {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(dateStr));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
