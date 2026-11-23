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

/**
 * The reasons why the events of a calendar can't be loaded, told apart by the status of the response of the calendar
 * REST resource (see {@link com.xwiki.projectmanagement.calendar.internal.rest.DefaultCalendarResource}).
 *
 * @version $Id$
 * @since 1.5.0
 */
public enum CalendarLoadError
{
    /**
     * The request was rejected, e.g. because of an invalid filter.
     */
    INVALID_REQUEST("400", "invalidRequest", false),

    /**
     * The current user isn't authenticated to the project management tool, or their authentication is no longer valid.
     */
    NOT_AUTHENTICATED("401", "notAuthenticated", true),

    /**
     * The current user isn't allowed to see the events.
     */
    FORBIDDEN("403", "forbidden", true),

    /**
     * Any other failure, e.g. the project management tool is unreachable or answered with an error.
     */
    OTHER("other", "failure", false);

    private static final String TRANSLATION_PREFIX = "calendar.loadError.";

    private final String status;

    private final String translationKey;

    private final boolean warning;

    CalendarLoadError(String status, String translationSuffix, boolean warning)
    {
        this.status = status;
        this.translationKey = TRANSLATION_PREFIX + translationSuffix;
        this.warning = warning;
    }

    /**
     * @return the status of the response of the calendar REST resource reporting this error, or {@code other} for the
     *     statuses that have no specific message; the script of the calendar shows the message having this status
     */
    public String getStatus()
    {
        return this.status;
    }

    /**
     * @return the translation key of the default message explaining the error
     */
    public String getTranslationKey()
    {
        return this.translationKey;
    }

    /**
     * @return {@code true} if the error is displayed as a warning, since the user may be able to fix it (e.g. by
     *     authenticating), {@code false} if it is displayed as an error
     */
    public boolean isWarning()
    {
        return this.warning;
    }
}
