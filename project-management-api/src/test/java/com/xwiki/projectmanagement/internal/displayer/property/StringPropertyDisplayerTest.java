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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;

import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.assertWord;
import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.getWord;
import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.mockPlainParser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StringPropertyDisplayer}.
 *
 * @version $Id$
 */
class StringPropertyDisplayerTest
{
    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private final Parser parser = mock(Parser.class);

    private final StringPropertyDisplayer displayer = new StringPropertyDisplayer(this.parser);

    @Test
    void displayNull()
    {
        assertTrue(this.displayer.display(null, Map.of()).isEmpty());
    }

    @Test
    void displayUnwrapsTheSingleParagraph() throws Exception
    {
        mockPlainParser(this.parser);

        assertWord("text", this.displayer.display("text", Map.of()));
    }

    @Test
    void displayUsesTheStringRepresentationOfTheValue() throws Exception
    {
        mockPlainParser(this.parser);

        assertWord("42", this.displayer.display(42, Map.of()));
    }

    @Test
    void displayKeepsSeveralTopLevelBlocks() throws Exception
    {
        when(this.parser.parse(any(Reader.class))).thenReturn(new XDOM(List.of(
            new ParagraphBlock(List.of(new WordBlock("first"))),
            new ParagraphBlock(List.of(new WordBlock("second"))))));

        List<Block> blocks = this.displayer.display("first\n\nsecond", Map.of());

        assertEquals(2, blocks.size());
        assertEquals("first", getWord(assertInstanceOf(ParagraphBlock.class, blocks.get(0)).getChildren().get(0)));
        assertEquals("second", getWord(assertInstanceOf(ParagraphBlock.class, blocks.get(1)).getChildren().get(0)));
    }

    @Test
    void displayWhenParsingFails() throws Exception
    {
        when(this.parser.parse(any(Reader.class))).thenThrow(new ParseException("boom"));

        assertTrue(this.displayer.display("text", Map.of()).isEmpty());
        assertEquals("Failed to parse the property with value [text] of a work item.", this.logCapture.getMessage(0));
    }
}
