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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The period covered by the OpenProject macros that display the data of the last days: from the start of the day
 * that is the given number of days before today, to the end of today, in the time zone of the given clock.
 *
 * @version $Id$
 * @since 1.4.0
 */
public final class LastDaysPeriod
{
    private final LocalDate firstDay;

    private final LocalDate lastDay;

    private final ZoneId zone;

    /**
     * @param days the number of days before today at which the period starts
     * @param clock the clock giving today and the time zone
     */
    public LastDaysPeriod(int days, Clock clock)
    {
        this.lastDay = LocalDate.now(clock);
        this.firstDay = this.lastDay.minusDays(days);
        this.zone = clock.getZone();
    }

    /**
     * @return the first day of the period
     */
    public LocalDate getFirstDay()
    {
        return this.firstDay;
    }

    /**
     * @return the last day of the period, i.e. today
     */
    public LocalDate getLastDay()
    {
        return this.lastDay;
    }

    /**
     * @return the period as an ISO 8601 interval of date-times with offsets, from the start of the first day to the
     *     end of the last day; each bound has the offset of the time zone at that date-time
     */
    public String toDateTimeInterval()
    {
        return this.firstDay.atStartOfDay(this.zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + '/'
            + this.lastDay.atTime(LocalTime.MAX).atZone(this.zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
