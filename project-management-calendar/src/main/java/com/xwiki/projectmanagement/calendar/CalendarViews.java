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

/**
 * Supported view modes for the FullCalendar component. Each constant corresponds to a FullCalendar view option.
 *
 * @version $Id$
 * @since 1.2.0-rc-9
 */
public enum CalendarViews
{
    /**
     * Display a full month grid.
     */
    month,

    /**
     * Display a week view with agenda (time slots).
     */
    agendaWeek,

    /**
     * Display a single day with agenda (time slots).
     */
    agendaDay,

    /**
     * Display a week view.
     */
    basicWeek,

    /**
     * Display a single day.
     */
    basicDay;
}
