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

import java.util.Date;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the date-times returned by the OpenProject API.
 *
 * @version $Id$
 * @since 1.4.0
 */
public final class OpenProjectDateTimes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectDateTimes.class);

    private OpenProjectDateTimes()
    {
    }

    /**
     * Parses an ISO 8601 date-time, such as the creation and update dates of OpenProject (e.g.
     * {@code 2025-08-13T22:30:15.123Z}), into the instant it denotes. The parsing is lenient: an offset without colon
     * ({@code +0200}) is accepted, and a value without offset or without time is taken in the time zone of the XWiki
     * server (at midnight for a date without time).
     *
     * @param text the date-time to parse
     * @return the parsed date, or {@code null} if the value is empty or isn't a valid ISO 8601 date-time
     */
    public static Date parse(String text)
    {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return DateTime.parse(text.trim()).toDate();
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Ignoring the invalid OpenProject date-time [{}].", text);
            return null;
        }
    }
}
