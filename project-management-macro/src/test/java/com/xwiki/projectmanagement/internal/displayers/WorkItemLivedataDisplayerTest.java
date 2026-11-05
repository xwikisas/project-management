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
package com.xwiki.projectmanagement.internal.displayers;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkItemLivedataDisplayer}.
 *
 * @version $Id$
 */
@ComponentTest
class WorkItemLivedataDisplayerTest
{
    private static final String FILTERS = "{\"query\":{\"filters\":[]}}";

    @InjectMockComponents
    private WorkItemLivedataDisplayer displayer;

    @MockComponent
    private ComponentManager componentManager;

    @Mock
    private Macro<ProjectManagementMacroParameters> liveDataMacro;

    private final MacroTransformationContext context = new MacroTransformationContext();

    private final ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();

    private final List<Block> liveData = List.of(new WordBlock("liveData"));

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.componentManager.getInstance(Macro.class, "liveData")).thenReturn(this.liveDataMacro);
    }

    @Test
    void displaysTwentyFiveWorkItemsPerPageByDefault() throws Exception
    {
        when(this.liveDataMacro.execute(this.parameters, "content", this.context)).thenReturn(this.liveData);

        assertEquals(this.liveData, this.displayer.execute(this.parameters, "content", this.context));
        // The limit is the size of the pages of the table.
        assertEquals(25, this.parameters.getLimit());
        assertNull(this.parameters.getLayouts());
    }

    @Test
    void displaysTheCardsWithTheGivenNumberOfWorkItemsPerPage() throws Exception
    {
        this.parameters.setLimit(10);
        this.parameters.setWorkItemsDisplayer(WorkItemsDisplayer.liveDataCards);
        this.parameters.setFilters(FILTERS);
        // The filters are passed to Live Data as its content.
        when(this.liveDataMacro.execute(this.parameters, FILTERS, this.context)).thenReturn(this.liveData);

        assertEquals(this.liveData, this.displayer.execute(this.parameters, "", this.context));
        assertEquals(10, this.parameters.getLimit());
        assertEquals("cards,table", this.parameters.getLayouts());
        assertEquals("", this.parameters.getFilters());
    }
}
