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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;

import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.assertWord;
import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.mockPlainParser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link IdentifierPropertyDisplayer}.
 *
 * @version $Id$
 */
class IdentifierPropertyDisplayerTest
{
    private static final String URL = "https://openproject.example.org/work_packages/42";

    private static final Linkable IDENTIFIER = new Linkable("42", URL);

    private final Parser parser = mock(Parser.class);

    private final IdentifierPropertyDisplayer displayer =
        new IdentifierPropertyDisplayer(new LinkablePropertyDisplayer(this.parser));

    @BeforeEach
    void setUp() throws Exception
    {
        mockPlainParser(this.parser);
    }

    @Test
    void displayTheKeyOfTheWorkItem()
    {
        WorkItem workItem = new WorkItem();
        workItem.setIdentifier(IDENTIFIER);
        workItem.setKey("XWIKI-42");

        // E.g. an OpenProject work package on an instance using project-based semantic identifiers.
        assertLink("XWIKI-42", this.displayer.display(IDENTIFIER, Map.of("workItem", workItem)));
    }

    @Test
    void displayTheIdentifierOfAWorkItemWithoutKey()
    {
        WorkItem workItem = new WorkItem();
        workItem.setIdentifier(IDENTIFIER);

        assertLink("42", this.displayer.display(IDENTIFIER, Map.of("workItem", workItem)));
    }

    @Test
    void displayTheIdentifierWithoutWorkItem()
    {
        assertLink("42", this.displayer.display(IDENTIFIER, Map.of()));
        assertLink("42", this.displayer.display(IDENTIFIER, null));
    }

    @Test
    void displayNothingForAMissingIdentifier()
    {
        WorkItem workItem = new WorkItem();
        workItem.setKey("XWIKI-42");

        assertTrue(this.displayer.display(null, Map.of("workItem", workItem)).isEmpty());
    }

    private static void assertLink(String expectedLabel, List<Block> blocks)
    {
        assertEquals(1, blocks.size());
        LinkBlock link = assertInstanceOf(LinkBlock.class, blocks.get(0));
        assertEquals(new ResourceReference(URL, ResourceType.URL), link.getReference());
        assertWord(expectedLabel, link.getChildren());
    }
}
