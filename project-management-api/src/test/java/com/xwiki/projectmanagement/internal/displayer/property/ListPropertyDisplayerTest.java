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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.WordBlock;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.model.Linkable;

import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.assertWord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListPropertyDisplayer}.
 *
 * @version $Id$
 */
class ListPropertyDisplayerTest
{
    private final WorkItemPropertyDisplayerManager displayerManager = mock(WorkItemPropertyDisplayerManager.class);

    private final ListPropertyDisplayer displayer = new ListPropertyDisplayer(this.displayerManager);

    @Test
    void displayNullOrEmptyList()
    {
        assertTrue(this.displayer.display(null, Map.of()).isEmpty());
        assertTrue(this.displayer.display(List.of(), Map.of()).isEmpty());
        verifyNoInteractions(this.displayerManager);
    }

    @Test
    void displayEachEntryWithTheDisplayerOfItsType()
    {
        Map<String, Object> params = Map.of("format", "dd/MM");
        Linkable linkable = new Linkable("John", "https://example.org/users/1");
        when(this.displayerManager.displayProperty(String.class.getName(), "bug", params))
            .thenReturn(List.of(new WordBlock("bug")));
        when(this.displayerManager.displayProperty(Linkable.class.getName(), linkable, params))
            .thenReturn(List.of(new WordBlock("John")));

        List<Block> blocks = this.displayer.display(List.of("bug", linkable), params);

        assertEquals(2, blocks.size());
        assertWord("bug", assertInstanceOf(GroupBlock.class, blocks.get(0)).getChildren());
        assertWord("John", assertInstanceOf(GroupBlock.class, blocks.get(1)).getChildren());
    }

    @Test
    void displaySkipsTheNullEntries()
    {
        when(this.displayerManager.displayProperty(String.class.getName(), "bug", Map.of()))
            .thenReturn(List.of(new WordBlock("bug")));

        List<Block> blocks = this.displayer.display(Arrays.asList(null, "bug", null), Map.of());

        assertEquals(1, blocks.size());
        assertWord("bug", assertInstanceOf(GroupBlock.class, blocks.get(0)).getChildren());
    }
}
