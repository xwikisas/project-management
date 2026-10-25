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

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.displayer.property.DatePropertyDisplayer;
import com.xwiki.projectmanagement.internal.displayer.property.LinkablePropertyDisplayer;
import com.xwiki.projectmanagement.internal.displayer.property.ListPropertyDisplayer;
import com.xwiki.projectmanagement.internal.displayer.property.StringPropertyDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;

import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.assertParagraphWord;
import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.assertWord;
import static com.xwiki.projectmanagement.internal.displayer.DisplayerTestUtils.mockPlainParser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultWorkItemPropertyDisplayerManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultWorkItemPropertyDisplayerManagerTest
{
    private static final String URL = "https://openproject.example.org/work_packages/42";

    @InjectMockComponents
    private DefaultWorkItemPropertyDisplayerManager manager;

    @MockComponent
    @Named("plain/1.0")
    private Parser plainParser;

    @MockComponent
    @Named("wiki")
    private ConfigurationSource wikiConfigSource;

    @BeforeComponent
    void beforeComponent()
    {
        when(this.wikiConfigSource.getProperty("dateformat", "dd/MM/yyyy hh:mm:ss")).thenReturn("yyyy-MM-dd");
    }

    @BeforeEach
    void setUp() throws Exception
    {
        mockPlainParser(this.plainParser);
    }

    @Test
    void registeredDisplayers()
    {
        assertInstanceOf(LinkablePropertyDisplayer.class,
            this.manager.getDisplayerForProperty(Linkable.class.getName()));
        for (String property : List.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_SUMMARY, WorkItem.KEY_PROJECT,
            WorkItem.KEY_CREATOR))
        {
            assertSame(this.manager.getDisplayerForProperty(Linkable.class.getName()),
                this.manager.getDisplayerForProperty(property));
        }
        assertInstanceOf(ListPropertyDisplayer.class, this.manager.getDisplayerForProperty("list"));
        assertSame(this.manager.getDisplayerForProperty("list"),
            this.manager.getDisplayerForProperty(WorkItem.KEY_ASSIGNEES));
        assertSame(this.manager.getDisplayerForProperty("list"),
            this.manager.getDisplayerForProperty(WorkItem.KEY_LABELS));
        assertInstanceOf(StringPropertyDisplayer.class, this.manager.getDisplayerForProperty(String.class.getName()));
        assertInstanceOf(DatePropertyDisplayer.class, this.manager.getDisplayerForProperty(Date.class.getName()));
        assertNull(this.manager.getDisplayerForProperty("unknown"));
    }

    @Test
    void displayNull()
    {
        assertTrue(this.manager.displayProperty(WorkItem.KEY_SUMMARY, null, Map.of()).isEmpty());
        assertTrue(this.manager.displayProperty("unknown", null, Map.of()).isEmpty());
    }

    @Test
    void displayWithTheDisplayerOfTheProperty()
    {
        List<Block> blocks =
            this.manager.displayProperty(WorkItem.KEY_LABELS, List.of("bug", "ui"), Map.of());

        assertEquals(2, blocks.size());
        assertWord("bug", assertInstanceOf(GroupBlock.class, blocks.get(0)).getChildren());
        assertWord("ui", assertInstanceOf(GroupBlock.class, blocks.get(1)).getChildren());
    }

    @Test
    void displayWithTheDisplayerOfTheValueType()
    {
        Date date = new GregorianCalendar(2024, Calendar.MARCH, 15).getTime();

        // The date format comes from the wiki configuration.
        assertParagraphWord("2024-03-15", this.manager.displayProperty(WorkItem.KEY_DUE_DATE, date, Map.of()));
        assertParagraphWord("15/03", this.manager.displayProperty(WorkItem.KEY_DUE_DATE, date,
            Map.of("format", "dd/MM")));
        assertWord("text", this.manager.displayProperty(WorkItem.KEY_DESCRIPTION, "text", Map.of()));
    }

    @Test
    void displayLinkableValues()
    {
        // Any map holding the linkable keys is displayed as a link, whatever the displayer of the property.
        for (String property : List.of(WorkItem.KEY_SUMMARY, WorkItem.KEY_ASSIGNEES, "custom")) {
            List<Block> blocks = this.manager.displayProperty(property,
                Map.of(Linkable.KEY_VALUE, "Fix the bug", Linkable.KEY_LOCATION, URL), Map.of());

            LinkBlock link = assertInstanceOf(LinkBlock.class, blocks.get(0));
            assertEquals(URL, link.getReference().getReference());
            assertWord("Fix the bug", link.getChildren());
        }
    }

    @Test
    void displayListOfLinkables()
    {
        List<Block> blocks = this.manager.displayProperty(WorkItem.KEY_ASSIGNEES,
            List.of(new Linkable("John", URL), new Linkable("Jane", null)), Map.of());

        assertEquals(2, blocks.size());
        LinkBlock link =
            assertInstanceOf(LinkBlock.class, assertInstanceOf(GroupBlock.class, blocks.get(0)).getChildren().get(0));
        assertWord("John", link.getChildren());
        assertWord("Jane", assertInstanceOf(GroupBlock.class, blocks.get(1)).getChildren());
    }

    @Test
    void displayFallsBackOnTheStringDisplayer()
    {
        assertWord("75", this.manager.displayProperty(WorkItem.KEY_PROGRESS, 75, Map.of()));
        assertWord("{key=value}", this.manager.displayProperty("custom", Map.of("key", "value"), Map.of()));
    }
}
