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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.internal.macro.DefaultMacroContentParser;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PieChartDisplayer} and the common code of {@link AbstractChartJSDisplayer}. The displayer and
 * the components it uses are the real ones, except for the macro content parser, so that the test checks the wiki
 * syntax actually generated for the chart, parsed by the real XWiki 2.1 parser.
 *
 * @version $Id$
 */
@ComponentTest
@AllComponents(excludes = DefaultMacroContentParser.class)
class PieChartDisplayerTest
{
    private static final String DONE = "Done";

    private static final String NEW = "New";

    @MockComponent
    private MacroContentParser contentParser;

    @Inject
    @Named("pie")
    private ChartTypeDisplayer displayer;

    @Inject
    @Named("xwiki/2.1")
    private Parser parser;

    @Test
    void countsTheWorkItemsOfEachDatasetByValue() throws Exception
    {
        List<PaginatedResult<WorkItem>> datasets = List.of(
            result(workItem(NEW), workItem(DONE), workItem(NEW), workItem(null)),
            result(workItem(DONE)));

        JsonNode data = getChartData(datasets, List.of("Mine"), Map.of());

        // The labels are sorted, the work items without value being counted last.
        assertEquals(List.of(DONE, NEW, "UNSET"), texts(data.path("labels")));
        assertEquals("Mine", data.path("datasets").path(0).path("label").asText());
        assertEquals(List.of("1", "2", "1"), texts(data.path("datasets").path(0).path("data")));
        assertEquals("Unlabeled", data.path("datasets").path(1).path("label").asText());
        assertEquals(List.of("1", "0"), texts(data.path("datasets").path(1).path("data")));
    }

    @Test
    void accumulatesTheCountsWithTheAccumulateMetric() throws Exception
    {
        List<PaginatedResult<WorkItem>> datasets =
            List.of(result(workItem("A"), workItem("B"), workItem("B"), workItem("C")));

        JsonNode data = getChartData(datasets, List.of("Dataset"),
            Map.of(AbstractChartJSDisplayer.PARAM_METRIC, AbstractChartJSDisplayer.PARAM_METRIC_ACCUMULATE));

        assertEquals(List.of("A", "B", "C"), texts(data.path("labels")));
        assertEquals(List.of("1", "3", "4"), texts(data.path("datasets").path(0).path("data")));
    }

    @Test
    void keepsValuesHoldingWikiSyntaxInTheDataOfTheChart() throws Exception
    {
        String status = "Done\"}{{/chartjs}}{{html}}<p>text</p>{{/html}}{{chartjs}}{\"";

        // The values don't end the chartjs macro: the macro holds them in its data.
        JsonNode data = getChartData(List.of(result(workItem(status), workItem(NEW))), List.of("Dataset"), Map.of());

        assertEquals(List.of(status, NEW), texts(data.path("labels")));
        assertEquals(List.of("1", "1"), texts(data.path("datasets").path(0).path("data")));
    }

    @Test
    void executeParsesTheChartMacro() throws Exception
    {
        List<PaginatedResult<WorkItem>> datasets = List.of(result(workItem(NEW)));
        MacroTransformationContext context = new MacroTransformationContext();
        String chartMacro = getDisplayer().getChartMacro(datasets, WorkItem.KEY_STATUS, List.of("Dataset"), Map.of());
        WordBlock chart = new WordBlock("chart");
        when(this.contentParser.parse(chartMacro, context, true, false)).thenReturn(new XDOM(List.of(chart)));

        List<Block> result =
            this.displayer.execute(datasets, WorkItem.KEY_STATUS, List.of("Dataset"), context, Map.of());

        assertEquals(1, result.size());
        assertEquals(GroupBlock.class, result.get(0).getClass());
        assertEquals(List.of(chart), result.get(0).getChildren());
    }

    private JsonNode getChartData(List<PaginatedResult<WorkItem>> datasets, List<String> labels,
        Map<String, String> parameters) throws Exception
    {
        String chartMacro = getDisplayer().getChartMacro(datasets, WorkItem.KEY_STATUS, labels, parameters);
        List<Block> macros = this.parser.parse(new StringReader(chartMacro))
            .getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        assertEquals(1, macros.size());
        MacroBlock macro = (MacroBlock) macros.get(0);
        assertEquals("chartjs", macro.getId());
        assertEquals(Map.of("type", "pie"), macro.getParameters());
        return new ObjectMapper().readTree(macro.getContent());
    }

    private AbstractChartJSDisplayer getDisplayer()
    {
        return (AbstractChartJSDisplayer) this.displayer;
    }

    private static PaginatedResult<WorkItem> result(WorkItem... workItems)
    {
        return new PaginatedResult<>(List.of(workItems), 0, workItems.length, workItems.length);
    }

    private static WorkItem workItem(String status)
    {
        WorkItem workItem = new WorkItem();
        if (status != null) {
            workItem.put(WorkItem.KEY_STATUS, status);
        }
        return workItem;
    }

    private static List<String> texts(JsonNode array)
    {
        List<String> texts = new ArrayList<>();
        array.forEach(node -> texts.add(node.asText()));
        return texts;
    }
}
