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

import com.xwiki.projectmanagement.ProjectManagementFilters;
import com.xwiki.projectmanagement.calendar.CalendarViews;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.stability.Unstable;

import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Parameters for the project management calendar macro. Extends {@link ProjectManagementMacroParameters} with
 * calendar-specific view settings.
 *
 * @version $Id$
 * @since 1.2.0-rc-7
 */
@Unstable
public class CalendarMacroParameters
{
    private CalendarViews defaultView = CalendarViews.month;

    private int firstDay = 1;

    private String minTime;

    private String maxTime;

    private String timeFormat;

    private String client;

    private String filters;

    private int limit = 50;

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
    @PropertyDescription("The project management client implementation hint.")
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
    @PropertyDescription("The initial view of the calendar (month, agendaWeek, agendaDay).")
    public void setDefaultView(CalendarViews defaultView)
    {
        this.defaultView = defaultView;
    }

    /**
     * @return the first day of the week (0 = Sunday, 1 = Monday, etc.).
     */
    public int getFirstDay()
    {
        return this.firstDay;
    }

    /**
     * @param firstDay see {@link #getFirstDay()}.
     */
    @PropertyDescription("The first day of the week (0 = Sunday, 1 = Monday, etc.).")
    public void setFirstDay(int firstDay)
    {
        this.firstDay = firstDay;
    }

    /**
     * @return the minimum time displayed in the agenda views (e.g., {@code 06:00}).
     */
    public String getMinTime()
    {
        return this.minTime;
    }

    /**
     * @param minTime see {@link #getMinTime()}.
     */
    @PropertyDescription("The minimum time displayed in the agenda views (e.g., 06:00).")
    public void setMinTime(String minTime)
    {
        this.minTime = minTime;
    }

    /**
     * @return the maximum time displayed in the agenda views (e.g., {@code 20:00}).
     */
    public String getMaxTime()
    {
        return this.maxTime;
    }

    /**
     * @param maxTime see {@link #getMaxTime()}.
     */
    @PropertyDescription("The maximum time displayed in the agenda views (e.g., 20:00).")
    public void setMaxTime(String maxTime)
    {
        this.maxTime = maxTime;
    }

    /**
     * @return the time format string (e.g., {@code H:mm}).
     */
    public String getTimeFormat()
    {
        return this.timeFormat;
    }

    /**
     * @param timeFormat see {@link #getTimeFormat()}.
     */
    @PropertyDescription("The time format string (e.g., H:mm).")
    public void setTimeFormat(String timeFormat)
    {
        this.timeFormat = timeFormat;
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
    @PropertyDisplayType(ProjectManagementFilters.class)
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
