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
package com.xwiki.projectmanagement.internal.chart.displayer;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PieChartDisplayer}.
 *
 * @version $Id$
 */
@ComponentTest
@AllComponents
class PieChartDisplayerTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @Test
    void executeWithValuesHoldingWikiSyntax() throws Exception
    {
        MacroContentParser contentParser = this.componentManager.registerMockComponent(MacroContentParser.class);
        when(contentParser.parse(anyString(), any(MacroTransformationContext.class), anyBoolean(),
            anyBoolean())).thenReturn(new XDOM(Collections.emptyList()));
        String status = "Done\"}{{/chartjs}}{{html}}<p>text</p>{{/html}}{{chartjs}}{\"";
        WorkItem workItem = new WorkItem();
        workItem.put(WorkItem.KEY_STATUS, status);
        WorkItem otherWorkItem = new WorkItem();
        otherWorkItem.put(WorkItem.KEY_STATUS, "New");

        ChartTypeDisplayer displayer = this.componentManager.getInstance(ChartTypeDisplayer.class, "pie");
        displayer.execute(List.of(new PaginatedResult<>(List.of(workItem, otherWorkItem), 0, 2, 2)),
            WorkItem.KEY_STATUS, List.of("Dataset"), new MacroTransformationContext(), Map.of());

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(contentParser).parse(content.capture(), any(MacroTransformationContext.class), eq(true),
            eq(false));
        // The values are kept in the data of the chart and don't end the chartjs macro.
        Parser parser = this.componentManager.getInstance(Parser.class, "xwiki/2.1");
        List<Block> macros = parser.parse(new StringReader(content.getValue()))
            .getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        assertEquals(1, macros.size());
        MacroBlock chartMacro = (MacroBlock) macros.get(0);
        assertEquals("chartjs", chartMacro.getId());
        JsonNode data = new ObjectMapper().readTree(chartMacro.getContent());
        assertEquals(List.of(status, "New"),
            List.of(data.path("labels").path(0).asText(), data.path("labels").path(1).asText()));
    }
}
