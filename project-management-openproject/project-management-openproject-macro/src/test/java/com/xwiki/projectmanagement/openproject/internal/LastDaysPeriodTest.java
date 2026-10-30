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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link LastDaysPeriod}.
 *
 * @version $Id$
 */
class LastDaysPeriodTest
{
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static Clock clock(String instant, ZoneId zone)
    {
        return Clock.fixed(Instant.parse(instant), zone);
    }

    @Test
    void goesFromTheStartOfTheFirstDayToTheEndOfToday()
    {
        LastDaysPeriod period = new LastDaysPeriod(10, clock("2026-06-15T10:00:00Z", PARIS));

        assertEquals(LocalDate.of(2026, 6, 5), period.getFirstDay());
        assertEquals(LocalDate.of(2026, 6, 15), period.getLastDay());
        assertEquals("2026-06-05T00:00:00+02:00/2026-06-15T23:59:59.999999999+02:00", period.toDateTimeInterval());
    }

    @Test
    void usesTheDaysOfTheTimeZoneOfTheClock()
    {
        // It's already the next day in Paris.
        LastDaysPeriod period = new LastDaysPeriod(1, clock("2026-06-15T23:30:00Z", PARIS));

        assertEquals("2026-06-15T00:00:00+02:00/2026-06-16T23:59:59.999999999+02:00", period.toDateTimeInterval());
    }

    @Test
    void usesTheOffsetOfEachBoundWhenThePeriodStartsBeforeTheSwitchToSummerTime()
    {
        // Summer time starts on 2026-03-29 in Paris.
        LastDaysPeriod period = new LastDaysPeriod(10, clock("2026-03-30T10:00:00Z", PARIS));

        assertEquals("2026-03-20T00:00:00+01:00/2026-03-30T23:59:59.999999999+02:00", period.toDateTimeInterval());
    }

    @Test
    void usesTheOffsetOfEachBoundWhenThePeriodStartsBeforeTheSwitchToWinterTime()
    {
        // Summer time ends on 2026-10-25 in Paris.
        LastDaysPeriod period = new LastDaysPeriod(10, clock("2026-10-30T10:00:00Z", PARIS));

        assertEquals("2026-10-20T00:00:00+02:00/2026-10-30T23:59:59.999999999+01:00", period.toDateTimeInterval());
    }

    @Test
    void coversOnlyTodayWithoutDays()
    {
        LastDaysPeriod period = new LastDaysPeriod(0, clock("2026-06-15T10:00:00Z", ZoneId.of("UTC")));

        assertEquals(period.getLastDay(), period.getFirstDay());
        assertEquals("2026-06-15T00:00:00Z/2026-06-15T23:59:59.999999999Z", period.toDateTimeInterval());
    }
}
