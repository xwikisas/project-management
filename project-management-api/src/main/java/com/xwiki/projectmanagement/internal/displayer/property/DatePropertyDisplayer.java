package com.xwiki.projectmanagement.internal.displayer.property;

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

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;

/**
 * Displays a date property taking in consideration some configured display format.
 *
 * @version $Id$
 */
public class DatePropertyDisplayer implements WorkItemPropertyDisplayer
{
    /**
     * The format used when none is configured.
     */
    public static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

    /**
     * The format of a day, used when no day format can be derived from the configured date format.
     */
    public static final String DEFAULT_DAY_FORMAT = "dd/MM/yyyy";

    /**
     * The pattern letters of the time of the day and of the time zone.
     */
    private static final String TIME_LETTERS = "aHkKhmsSzZX";

    private static final char QUOTE = '\'';

    private final Logger logger = LoggerFactory.getLogger(DatePropertyDisplayer.class);

    private final Parser parser;

    /**
     * Provides the format of the dates, called at each display so that a change of the configured format is taken
     * into account.
     */
    private final Supplier<String> dateFormat;

    private boolean dayOnly;

    /**
     * @param parser used to parse the serialized date value as a list of blocks.
     */
    public DatePropertyDisplayer(Parser parser)
    {
        this(parser, DEFAULT_DATE_FORMAT);
    }

    /**
     * @param parser used to parse the serialized date value as a list of blocks.
     * @param dateFormat the format that should be used to serialize the date.
     */
    public DatePropertyDisplayer(Parser parser, String dateFormat)
    {
        this(parser, () -> dateFormat);
    }

    /**
     * @param parser used to parse the serialized date value as a list of blocks
     * @param dateFormat provides the format that should be used to serialize the date, called at each display, e.g.
     *     to use the current date format preference of the current wiki
     */
    public DatePropertyDisplayer(Parser parser, Supplier<String> dateFormat)
    {
        this.parser = parser;
        this.dateFormat = dateFormat;
    }

    /**
     * Creates a displayer of the dates that have no meaningful time, e.g. the due date of a work item, which would
     * otherwise be displayed at midnight: it displays them without the time fields of the given format, nor of the
     * {@code format} parameter.
     *
     * @param parser used to parse the serialized date value as a list of blocks
     * @param dateFormat the format of the dates, from which the time fields are removed
     * @return the displayer
     * @see #toDayFormat(String)
     */
    public static DatePropertyDisplayer forDays(Parser parser, String dateFormat)
    {
        return forDays(parser, () -> dateFormat);
    }

    /**
     * Same as {@link #forDays(Parser, String)}, with a format read at each display.
     *
     * @param parser used to parse the serialized date value as a list of blocks
     * @param dateFormat provides the format of the dates, from which the time fields are removed, called at each
     *     display
     * @return the displayer
     */
    public static DatePropertyDisplayer forDays(Parser parser, Supplier<String> dateFormat)
    {
        DatePropertyDisplayer displayer = new DatePropertyDisplayer(parser, dateFormat);
        displayer.dayOnly = true;
        return displayer;
    }

    /**
     * Derives the format of a day from a date format, by removing its time and time zone fields, with the separators
     * preceding them, e.g. {@code dd/MM/yyyy} from {@code dd/MM/yyyy HH:mm:ss}, or {@code EEE, d MMM yyyy} from
     * {@code EEE, d MMM yyyy HH:mm z}. It's used to display the dates that have no meaningful time, e.g. the due date
     * of a work item, which would otherwise be displayed at midnight.
     *
     * @param dateFormat a {@link SimpleDateFormat} pattern
     * @return the pattern without the time fields, or {@link #DEFAULT_DAY_FORMAT} when the given pattern is invalid
     *     or has no day field
     */
    public static String toDayFormat(String dateFormat)
    {
        if (dateFormat == null) {
            return DEFAULT_DAY_FORMAT;
        }
        try {
            new SimpleDateFormat(dateFormat);
        } catch (IllegalArgumentException e) {
            return DEFAULT_DAY_FORMAT;
        }
        StringBuilder dayFormat = new StringBuilder();
        // The separators (literals) met since the last field, kept only between two day fields. A time field drops
        // the ones preceding it, so that e.g. the " 'at' " of "yyyy-MM-dd 'at' HH:mm, EEEE" is dropped but not the
        // following ", ".
        StringBuilder separators = new StringBuilder();
        int start = 0;
        while (start < dateFormat.length()) {
            char first = dateFormat.charAt(start);
            int end = getTokenEnd(dateFormat, start);
            String token = dateFormat.substring(start, end);
            if (!isPatternLetter(first)) {
                separators.append(token);
            } else if (TIME_LETTERS.indexOf(first) >= 0) {
                separators.setLength(0);
            } else {
                if (dayFormat.length() > 0) {
                    dayFormat.append(separators);
                }
                dayFormat.append(token);
                separators.setLength(0);
            }
            start = end;
        }
        return dayFormat.length() > 0 ? dayFormat.toString() : DEFAULT_DAY_FORMAT;
    }

    /**
     * @return the end of the token starting at the given index: a quoted literal, a field (a run of the same pattern
     *     letter) or a single unquoted literal character
     */
    private static int getTokenEnd(String format, int start)
    {
        char first = format.charAt(start);
        int end = start + 1;
        if (first == QUOTE) {
            while (end < format.length()) {
                if (format.charAt(end) != QUOTE) {
                    end++;
                } else if (end + 1 < format.length() && format.charAt(end + 1) == QUOTE) {
                    // An escaped quote inside a quoted literal.
                    end += 2;
                } else {
                    return end + 1;
                }
            }
        } else if (isPatternLetter(first)) {
            while (end < format.length() && format.charAt(end) == first) {
                end++;
            }
        }
        return end;
    }

    /**
     * @return whether the given character is reserved as a pattern letter, the other letters (e.g. accented ones)
     *     being literals
     */
    private static boolean isPatternLetter(char character)
    {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    @Override
    public List<Block> display(Object property, Map<String, Object> params)
    {
        if (property == null) {
            return Collections.emptyList();
        }
        if (!(property instanceof Date)) {
            return new StringPropertyDisplayer(parser).display(property, params);
        }
        String formattedDate = format((Date) property, params);
        try {
            return parser.parse(new StringReader(formattedDate)).getChildren();
        } catch (ParseException e) {
            logger.warn("Failed to parse the formatted date [{}]. Cause: [{}].", formattedDate,
                ExceptionUtils.getRootCauseMessage(e));
            return Collections.emptyList();
        }
    }

    private String format(Date date, Map<String, Object> params)
    {
        // SimpleDateFormat isn't thread-safe, so a new one is created for each display.
        SimpleDateFormat formatter = null;
        Object paramsFormat = params.get("format");
        if (paramsFormat != null && !paramsFormat.toString().isEmpty()) {
            formatter = createFormatter(paramsFormat.toString());
        }
        if (formatter == null) {
            formatter = createFormatter(this.dateFormat.get());
        }
        if (formatter == null) {
            formatter = new SimpleDateFormat(this.dayOnly ? DEFAULT_DAY_FORMAT : DEFAULT_DATE_FORMAT);
        }
        return formatter.format(date);
    }

    /**
     * @return a formatter for the given pattern, without its time fields when displaying days, or {@code null} when
     *     the pattern is missing or invalid
     */
    private SimpleDateFormat createFormatter(String pattern)
    {
        if (pattern == null) {
            return null;
        }
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern);
            return this.dayOnly ? new SimpleDateFormat(toDayFormat(pattern)) : formatter;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
