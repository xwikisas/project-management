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
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.fullcalendar.model.CalendarEvent;
import org.xwiki.livedata.LiveDataQuery;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides calendar events from OpenProject by fetching sprints and versions and converting them into
 * {@link CalendarEvent} instances.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Singleton
@Component
@Named("openproject")
public class OpenProjectCalendarEventProvider implements CalendarEventProvider
{
    private static final String OP_ID = "op-id";

    private static final String ENTITY_TYPE = "entity-type";

    private static final String SPRINT = "sprint";

    private static final String VERSION = "version";

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    @Inject
    private Logger logger;

    @Override
    public List<CalendarEvent> getMoreEvents(List<LiveDataQuery.Filter> filters) throws ProjectManagementException
    {
        boolean getSprints = Boolean.parseBoolean((String) this.executionContext.get(SPRINT));
        boolean getVersion = Boolean.parseBoolean((String) this.executionContext.get(VERSION));
        OpenProjectApiClient apiClient = getOpenProjectApiClient();
        List<CalendarEvent> calendarEvents = new ArrayList<>();
        int projectId = getProjectVersion(filters);
        if (getSprints) {
            calendarEvents.addAll(getSprintsCalendarEvents(projectId, apiClient));
        }
        if (getVersion) {
            calendarEvents.addAll(getVersionsCalendarEvents(projectId, apiClient));
        }

        return calendarEvents;
    }

    private List<CalendarEvent> getVersionsCalendarEvents(int projectId, OpenProjectApiClient apiClient)
        throws ProjectManagementException
    {
        PaginatedResult<Version> versions;
        if (projectId != -1) {
            versions = apiClient.getProjectVersions(projectId);
        } else {
            versions = apiClient.getVersions();
        }
        return convertVersionsToCalendarEvents(versions);
    }

    private List<CalendarEvent> getSprintsCalendarEvents(int projectId, OpenProjectApiClient apiClient)
        throws ProjectManagementException
    {
        PaginatedResult<Sprint> sprints = new PaginatedResult<>();
        int limit = Integer.parseInt((String) this.executionContext.get("limit"));
        if (projectId != -1) {
            try {
                sprints = apiClient.getProjectSprints(1, limit, "", projectId);
            } catch (WorkItemRetrievalException e) {
                // If the project doesn't have backlogs enabled, it will return a 403 error message.
                if (e.getStatusCode().equals(403)) {
                    this.logger.debug("Sprints are not available for the project with ID {}.", projectId);
                } else {
                    throw e;
                }
            }
        } else {
            sprints = apiClient.getSprints(1, limit, "");
        }
        return convertSprintsToCalendarEvents(sprints);
    }

    private int getProjectVersion(List<LiveDataQuery.Filter> filters)
    {
        for (LiveDataQuery.Filter filter : filters) {
            if (filter.getProperty().equals("project")) {
                LiveDataQuery.Constraint constraint = filter.getConstraints().get(0);
                return  Integer.parseInt((String) constraint.getValue());
            }
        }
        return -1;
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
                event.setMeta(Map.of(OP_ID, sprint.getId(), ENTITY_TYPE, SPRINT));
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
                event.setMeta(Map.of(OP_ID, version.getId(), ENTITY_TYPE, VERSION));
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
