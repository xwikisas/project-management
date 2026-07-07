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

package com.xwiki.projectmanagement.calendar.macro;

import com.xwiki.projectmanagement.ProjectManagementFilter;
import com.xwiki.projectmanagement.calendar.CalendarViews;
import com.xwiki.projectmanagement.calendar.TimeInterval;
import com.xwiki.projectmanagement.calendar.WeekDays;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.stability.Unstable;

/**
 * Parameters for the project management calendar macro. Extends {@link ProjectManagementMacroParameters} with
 * calendar-specific view settings.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Unstable
public class CalendarMacroParameters
{
    private CalendarViews defaultView = CalendarViews.month;

    private WeekDays firstDay = WeekDays.SUNDAY;

    private String timeInterval;

    private String client;

    private String filters;

    private int limit = 25;

    /**
     * @return the client id (e.g., "openproject").
     */
    public String getClient()
    {
        return this.client;
    }

    /**
     * @param client see {@link #getClient()}.
     */
    public void setClient(String client)
    {
        this.client = client;
    }

    /**
     * @return the initial view of the calendar (e.g., {@code month}, {@code agendaWeek}, {@code agendaDay}).
     */
    public CalendarViews getDefaultView()
    {
        return this.defaultView;
    }

    /**
     * @param defaultView see {@link #getDefaultView()}.
     */
    public void setDefaultView(CalendarViews defaultView)
    {
        this.defaultView = defaultView;
    }

    /**
     * @return the first day of the week (0 = Sunday, 1 = Monday, etc.).
     */
    public WeekDays getFirstDay()
    {
        return this.firstDay;
    }

    /**
     * @param firstDay see {@link #getFirstDay()}.
     */
    public void setFirstDay(WeekDays firstDay)
    {
        this.firstDay = firstDay;
    }

    /**
     * @return the time interval displayed in the agenda views, formatted as {@code min-max} (e.g.,
     *     {@code 06:00-20:00}).
     */
    public String getTimeInterval()
    {
        return this.timeInterval;
    }

    /**
     * @param timeInterval see {@link #getTimeInterval()}.
     */
    @PropertyDisplayType(TimeInterval.class)
    public void setTimeInterval(String timeInterval)
    {
        this.timeInterval = timeInterval;
    }

    /**
     * @return a JSON representing the filters applied on the work items dataset. The JSON is the serialized version of
     *     the {@link org.xwiki.livedata.LiveDataConfiguration}.
     */
    public String getFilters()
    {
        return this.filters;
    }

    /**
     * @param filters see {@link #getFilters()}.
     */
    @PropertyDisplayType(ProjectManagementFilter.class)
    public void setFilters(String filters)
    {
        this.filters = filters;
    }

    /**
     * @return the limit applied of the work items resulting list.
     */
    public Integer getLimit()
    {
        return this.limit;
    }

    /**
     * @param limit see {@link #getLimit()}.
     */
    public void setLimit(Integer limit)
    {
        this.limit = limit;
    }
}
