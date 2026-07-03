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
package com.xwiki.projectmanagement.calendar;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import org.xwiki.component.annotation.Role;
import org.xwiki.fullcalendar.model.CalendarEvent;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.stability.Unstable;

import java.util.List;

/**
 * Provides additional calendar events beyond those obtained from the default work item retrieval. Implementations of
 * this interface can supply extra events that are not represented as standard work items in the project management
 * platform.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Unstable
@Role
public interface CalendarEventProvider
{
    /**
     * Retrieve additional calendar events.
     *
     * @param filters the list of filters to apply when retrieving the events.
     * @return a list of calendar events that supplement the work item events.
     * @throws ProjectManagementException if retrieving the events fails.
     */
    List<CalendarEvent> getMoreEvents(List<LiveDataQuery.Filter> filters) throws ProjectManagementException;
}
