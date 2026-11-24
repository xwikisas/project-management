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
package com.xwiki.projectmanagement.calendar.internal.macro;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.macro.MacroExecutionException;

/**
 * The hours displayed by the week and day views of a calendar, written in the {@code timeInterval} parameter of the
 * calendar macros as a start and an end separated by a dash, e.g. {@code 06:00-20:00}. Both are optional.
 *
 * @version $Id$
 * @since 1.5.0
 */
final class VisibleHours
{
    /**
     * A time of the day, in hours and minutes, with optional seconds.
     */
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

    private final String start;

    private final String end;

    private VisibleHours(String start, String end)
    {
        this.start = start;
        this.end = end;
    }

    /**
     * The calendar displays no time slot when its visible hours don't end after they start: this is reported as an
     * invalid parameter, rather than displaying an empty week or day without explanation. The times that can't be
     * read are passed to the calendar as they are.
     *
     * @param timeInterval the value of the {@code timeInterval} parameter, or {@code null} when it isn't set
     * @return the visible hours
     * @throws MacroExecutionException if the end of the visible hours doesn't come after their start
     */
    static VisibleHours parse(String timeInterval) throws MacroExecutionException
    {
        String start = null;
        String end = null;
        if (timeInterval != null) {
            String[] parts = timeInterval.split("-", 2);
            if (parts.length == 2) {
                start = StringUtils.trimToNull(parts[0]);
                end = StringUtils.trimToNull(parts[1]);
            }
        }
        Duration startTime = parseTime(start);
        Duration endTime = parseTime(end);
        if (startTime != null && endTime != null && endTime.compareTo(startTime) <= 0) {
            throw new MacroExecutionException(String.format("The [timeInterval] parameter must end after it starts "
                + "(e.g. 06:00-20:00), but is [%s].", timeInterval));
        }
        return new VisibleHours(start, end);
    }

    /**
     * @return the first visible hour, e.g. {@code 06:00}, or {@code null} for the default one
     */
    String getStart()
    {
        return this.start;
    }

    /**
     * @return the end of the last visible hour, e.g. {@code 20:00}, or {@code null} for the default one
     */
    String getEnd()
    {
        return this.end;
    }

    /**
     * @param time a time of the day, e.g. {@code 06:00}, or {@code null}
     * @return the duration since the start of the day, or {@code null} if there's no time or it can't be read
     */
    private static Duration parseTime(String time)
    {
        Matcher matcher = time != null ? TIME.matcher(time) : null;
        if (matcher == null || !matcher.matches()) {
            return null;
        }
        Duration duration =
            Duration.ofHours(Integer.parseInt(matcher.group(1))).plusMinutes(Integer.parseInt(matcher.group(2)));
        return matcher.group(3) != null ? duration.plusSeconds(Integer.parseInt(matcher.group(3))) : duration;
    }
}
