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
package com.xwiki.projectmanagement.internal.displayer;

import java.io.Reader;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.Parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Helpers for the tests of the work item property displayers.
 *
 * @version $Id$
 */
public final class DisplayerTestUtils
{
    private DisplayerTestUtils()
    {
    }

    /**
     * Make the given parser mock behave like a simplified plain text parser: the whole input becomes a single word
     * inside a paragraph.
     *
     * @param parser the parser mock
     * @throws Exception if the mock setup fails
     */
    public static void mockPlainParser(Parser parser) throws Exception
    {
        when(parser.parse(any(Reader.class))).thenAnswer(invocation -> {
            String text = IOUtils.toString(invocation.<Reader>getArgument(0));
            return new XDOM(List.of(new ParagraphBlock(List.of(new WordBlock(text)))));
        });
    }

    /**
     * @param block the block that should be a word block
     * @return the word of the block
     */
    public static String getWord(Block block)
    {
        return assertInstanceOf(WordBlock.class, block).getWord();
    }

    /**
     * Assert that the given blocks are a single paragraph holding a single word.
     *
     * @param expectedWord the expected word
     * @param blocks the blocks to check
     */
    public static void assertParagraphWord(String expectedWord, List<Block> blocks)
    {
        assertEquals(1, blocks.size());
        Block paragraph = assertInstanceOf(ParagraphBlock.class, blocks.get(0));
        assertEquals(1, paragraph.getChildren().size());
        assertEquals(expectedWord, getWord(paragraph.getChildren().get(0)));
    }

    /**
     * Assert that the given blocks are a single word.
     *
     * @param expectedWord the expected word
     * @param blocks the blocks to check
     */
    public static void assertWord(String expectedWord, List<Block> blocks)
    {
        assertEquals(1, blocks.size());
        assertEquals(expectedWord, getWord(blocks.get(0)));
    }
}
