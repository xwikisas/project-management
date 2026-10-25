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
package com.xwiki.projectmanagement.internal.displayer.property;

import java.io.Reader;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;

import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.assertParagraphWord;
import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.mockPlainParser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatePropertyDisplayer}.
 *
 * @version $Id$
 */
class DatePropertyDisplayerTest
{
    private static final String FORMAT = "format";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private final Parser parser = mock(Parser.class);

    // An afternoon time, to check that the hours are formatted on 24 hours by default.
    private final Date date = new GregorianCalendar(2024, Calendar.MARCH, 15, 15, 5, 7).getTime();

    @BeforeEach
    void setUp() throws Exception
    {
        mockPlainParser(this.parser);
    }

    @Test
    void displayNull()
    {
        assertTrue(new DatePropertyDisplayer(this.parser).display(null, Map.of()).isEmpty());
    }

    @Test
    void displayWithTheDefaultFormat()
    {
        assertParagraphWord("15/03/2024 15:05:07", new DatePropertyDisplayer(this.parser).display(this.date, Map.of()));
    }

    @Test
    void displayWithTheConfiguredFormat()
    {
        DatePropertyDisplayer displayer = new DatePropertyDisplayer(this.parser, "yyyy-MM-dd");

        assertParagraphWord("2024-03-15", displayer.display(this.date, Map.of()));
        // An empty format parameter is ignored.
        assertParagraphWord("2024-03-15", displayer.display(this.date, Map.of(FORMAT, "")));
    }

    @Test
    void displayWithAnInvalidConfiguredFormat()
    {
        DatePropertyDisplayer displayer = new DatePropertyDisplayer(this.parser, "invalid");

        assertParagraphWord("15/03/2024 15:05:07", displayer.display(this.date, Map.of()));
    }

    @Test
    void displayWithTheFormatParameter()
    {
        DatePropertyDisplayer displayer = new DatePropertyDisplayer(this.parser, "yyyy-MM-dd");

        assertParagraphWord("15.03.2024", displayer.display(this.date, Map.of(FORMAT, "dd.MM.yyyy")));
    }

    @Test
    void displayWithAnInvalidFormatParameter()
    {
        DatePropertyDisplayer displayer = new DatePropertyDisplayer(this.parser, "yyyy-MM-dd");

        assertParagraphWord("2024-03-15", displayer.display(this.date, Map.of(FORMAT, "invalid")));
    }

    @Test
    void displayWhenParsingFails() throws Exception
    {
        reset(this.parser);
        when(this.parser.parse(any(Reader.class))).thenThrow(new ParseException("boom"));
        DatePropertyDisplayer displayer = new DatePropertyDisplayer(this.parser, "yyyy-MM-dd");

        assertTrue(displayer.display(this.date, Map.of()).isEmpty());
        assertEquals("Failed to parse the formatted date [2024-03-15]. Cause: [ParseException: boom].",
            this.logCapture.getMessage(0));
    }

    @Test
    void displayWithANullFormatParameter()
    {
        DatePropertyDisplayer displayer = new DatePropertyDisplayer(this.parser, "yyyy-MM-dd");
        Map<String, Object> params = new HashMap<>();
        params.put(FORMAT, null);

        assertParagraphWord("2024-03-15", displayer.display(this.date, params));
    }
}
