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
 * Days of the week mapped to their numeric value (0 for Sunday, 1 for Monday, etc.). Used to configure the first day of
 * the week in the calendar view.
 *
 * @version $Id$
 * @since 1.2.0-rc-9
 */
public enum WeekDays
{
    /**
     * Sunday, has value 0.
     */
    SUNDAY(0),

    /**
     * Monday, has value 1.
     */
    MONDAY(1),

    /**
     * Tuesday, has value 2.
     */
    TUESDAY(2),

    /**
     * Wednesday, has value 3.
     */
    WEDNESDAY(3),

    /**
     * Thursday, has value 4.
     */
    THURSDAY(4),

    /**
     * Friday, has value 5.
     */
    FRIDAY(5),

    /**
     * Saturday, has value 6.
     */
    SATURDAY(6);

    private final int dayValue;

    WeekDays(int dayValue)
    {
        this.dayValue = dayValue;
    }

    /**
     * @return the numeric representation of the day (0-6, where 0 is Sunday).
     */
    public int getDayValue()
    {
        return this.dayValue;
    }
}
