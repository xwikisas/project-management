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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ListItemBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.macro.Macro;

import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Helpers shared by the OpenProject macro tests.
 *
 * @version $Id$
 */
public final class OpenProjectMacroTestUtils
{
    private OpenProjectMacroTestUtils()
    {
    }

    /**
     * @param year the year
     * @param month the month, from 1 (January) to 12 (December)
     * @param day the day of the month
     * @return the start of the given day, in the default time zone
     */
    public static Date date(int year, int month, int day)
    {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Make the given (mocked) asynchronous executor run the macro it receives synchronously, in the calling thread.
     *
     * @param asyncExecutor the mocked asynchronous executor
     * @throws Exception in case of error
     */
    @SuppressWarnings("unchecked")
    public static void executeSynchronously(ProjectManagementAsyncExecutor asyncExecutor) throws Exception
    {
        when(asyncExecutor.execute(any(), any(), any(), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            Macro<ProjectManagementAsyncMacroParams> macro = invocation.getArgument(0);
            return macro.execute(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
        });
    }

    /**
     * Serialize the text of the given blocks: words, spaces and special symbols are kept, paragraphs, headings and
     * list items are terminated by a new line, table cells by a {@code |} and table rows by a new line.
     *
     * @param blocks the blocks to serialize
     * @return the text of the blocks
     */
    public static String text(List<? extends Block> blocks)
    {
        StringBuilder builder = new StringBuilder();
        blocks.forEach(block -> appendText(block, builder));
        return builder.toString();
    }

    /**
     * @param blocks the blocks to look into
     * @return the links found in the given blocks, as {@code label -> reference} strings
     */
    public static List<String> links(List<? extends Block> blocks)
    {
        return blocks.stream()
            .flatMap(block -> block.<LinkBlock>getBlocks(new ClassBlockMatcher(LinkBlock.class),
                Block.Axes.DESCENDANT_OR_SELF).stream())
            .map(link -> text(link.getChildren()).trim() + " -> " + link.getReference().getReference())
            .collect(Collectors.toList());
    }

    private static void appendText(Block block, StringBuilder builder)
    {
        if (block instanceof WordBlock) {
            builder.append(((WordBlock) block).getWord());
        } else if (block instanceof SpaceBlock) {
            builder.append(' ');
        } else if (block instanceof SpecialSymbolBlock) {
            builder.append(((SpecialSymbolBlock) block).getSymbol());
        } else {
            block.getChildren().forEach(child -> appendText(child, builder));
            if (block instanceof ParagraphBlock || block instanceof HeaderBlock || block instanceof ListItemBlock
                || block instanceof TableRowBlock)
            {
                builder.append('\n');
            } else if (block instanceof TableCellBlock) {
                builder.append('|');
            }
        }
    }
}
