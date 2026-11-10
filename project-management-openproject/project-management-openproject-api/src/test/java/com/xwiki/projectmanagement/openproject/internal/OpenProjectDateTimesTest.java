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

import java.time.Instant;
import java.util.Date;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link OpenProjectDateTimes}.
 *
 * @version $Id$
 */
class OpenProjectDateTimesTest
{
    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @ParameterizedTest
    @ValueSource(strings = { "2025-08-13T22:30:15.123Z", "2025-08-14T00:30:15.123+02:00",
        "2025-08-14T00:30:15.123+0200", " 2025-08-13T22:30:15.123Z " })
    void parseDateTimeWithOffset(String text)
    {
        assertEquals(Date.from(Instant.parse("2025-08-13T22:30:15.123Z")), OpenProjectDateTimes.parse(text));
    }

    @Test
    void parseDateTimeWithoutOffset()
    {
        // Taken in the time zone of the server.
        assertEquals(new GregorianCalendar(2025, GregorianCalendar.AUGUST, 13, 22, 30, 15).getTime(),
            OpenProjectDateTimes.parse("2025-08-13T22:30:15"));
    }

    @Test
    void parseDateOnly()
    {
        // Midnight in the time zone of the server.
        assertEquals(new GregorianCalendar(2025, GregorianCalendar.AUGUST, 13).getTime(),
            OpenProjectDateTimes.parse("2025-08-13"));
    }

    @Test
    void parseInvalidValue()
    {
        assertNull(OpenProjectDateTimes.parse("yesterday"));
        assertEquals("Ignoring the invalid OpenProject date-time [yesterday].", this.logCapture.getMessage(0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    void parseEmptyValue(String text)
    {
        assertNull(OpenProjectDateTimes.parse(text));
    }
}
