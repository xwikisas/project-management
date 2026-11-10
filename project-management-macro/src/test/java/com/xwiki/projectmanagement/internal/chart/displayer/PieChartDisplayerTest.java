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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import com.xwiki.projectmanagement.chart.ChartPeriod;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
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
    void countsTheWorkItemsOnceForEachOfTheirValues() throws Exception
    {
        String alice = "Alice";
        String bob = "Bob";
        List<PaginatedResult<WorkItem>> datasets = List.of(result(
            assignedTo(new Linkable(alice, "https://op/users/1")),
            assignedTo(new Linkable(alice, "https://op/users/1"), new Linkable(bob, "https://op/users/2")),
            assignedTo(),
            new WorkItem()));

        JsonNode data = getChartData(datasets, WorkItem.KEY_ASSIGNEES, List.of("Dataset"), Map.of());

        // The labels are the names of the assignees, not the raw values of the assignee lists.
        assertEquals(List.of(alice, bob, "UNSET"), texts(data.path("labels")));
        assertEquals(List.of("2", "1", "2"), texts(data.path("datasets").path(0).path("data")));
    }

    @Test
    void sortsTheNumericValuesByValue() throws Exception
    {
        List<PaginatedResult<WorkItem>> datasets = List.of(result(withProgress(100), withProgress(20),
            withProgress(100), withProgress(0), withProgress(null)));

        JsonNode data = getChartData(datasets, WorkItem.KEY_PROGRESS, List.of("Dataset"), Map.of());

        // Sorted as text, "100" would come before "20".
        assertEquals(List.of("0", "20", "100", "UNSET"), texts(data.path("labels")));
        assertEquals(List.of("1", "1", "2", "1"), texts(data.path("datasets").path(0).path("data")));
    }

    @Test
    void sortsTheNumericValuesBeforeTheOtherValues() throws Exception
    {
        List<PaginatedResult<WorkItem>> datasets =
            List.of(result(workItem("B"), workItem("10"), workItem("A"), workItem("9")));

        JsonNode data = getChartData(datasets, List.of("Dataset"), Map.of());

        assertEquals(List.of("9", "10", "A", "B"), texts(data.path("labels")));
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
    void countsTheWorkItemsByHourOfTheDay() throws Exception
    {
        List<PaginatedResult<WorkItem>> datasets =
            List.of(result(createdAt(2), createdAt(14), createdAt(14)));

        JsonNode data = getChartData(datasets, WorkItem.KEY_CREATION_DATE, List.of("Dataset"),
            Map.of(AbstractChartJSDisplayer.PARAM_PERIOD, ChartPeriod.HOURLY.name()));

        // 02:00 and 14:00 are different hours.
        assertEquals(List.of("2026-03-05 02", "2026-03-05 14"), texts(data.path("labels")));
        assertEquals(List.of("1", "2"), texts(data.path("datasets").path(0).path("data")));
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

    @Test
    void displaysTheCountsGivenByTheClientLikeTheWorkItemsTheyCount() throws Exception
    {
        // The client counted 2 new and 1 done work items in the first dataset, plus 1 without status, and 1 done work
        // item in the second dataset: the chart is the one of these work items.
        Map<String, Long> firstCounts = new HashMap<>();
        firstCounts.put(NEW, 2L);
        firstCounts.put(DONE, 1L);
        firstCounts.put(null, 1L);
        List<Map<String, Long>> counts = List.of(firstCounts, Map.of(DONE, 1L));
        List<PaginatedResult<WorkItem>> datasets = List.of(
            result(workItem(NEW), workItem(DONE), workItem(NEW), workItem(null)),
            result(workItem(DONE)));
        List<String> labels = List.of("Mine");
        Map<String, String> parameters =
            Map.of(AbstractChartJSDisplayer.PARAM_METRIC, AbstractChartJSDisplayer.PARAM_METRIC_ACCUMULATE);

        assertEquals(getDisplayer().getChartMacro(datasets, WorkItem.KEY_STATUS, labels, parameters),
            getDisplayer().getChartMacroFromCounts(counts, labels, parameters));
        JsonNode data = getChartData(getDisplayer().getChartMacroFromCounts(counts, labels, Map.of()));
        assertEquals(List.of(DONE, NEW, "UNSET"), texts(data.path("labels")));
        assertEquals(List.of("1", "2", "1"), texts(data.path("datasets").path(0).path("data")));
        assertEquals(List.of("1", "0"), texts(data.path("datasets").path(1).path("data")));
    }

    @Test
    void executeCountsParsesTheChartMacro() throws Exception
    {
        List<Map<String, Long>> counts = List.of(Map.of(NEW, 3L));
        MacroTransformationContext context = new MacroTransformationContext();
        String chartMacro = getDisplayer().getChartMacroFromCounts(counts, List.of("Dataset"), Map.of());
        WordBlock chart = new WordBlock("chart");
        when(this.contentParser.parse(chartMacro, context, true, false)).thenReturn(new XDOM(List.of(chart)));

        List<Block> result =
            this.displayer.executeCounts(counts, WorkItem.KEY_STATUS, List.of("Dataset"), context, Map.of());

        assertEquals(1, result.size());
        assertEquals(List.of(chart), result.get(0).getChildren());
    }

    private JsonNode getChartData(List<PaginatedResult<WorkItem>> datasets, List<String> labels,
        Map<String, String> parameters) throws Exception
    {
        return getChartData(datasets, WorkItem.KEY_STATUS, labels, parameters);
    }

    private JsonNode getChartData(List<PaginatedResult<WorkItem>> datasets, String property, List<String> labels,
        Map<String, String> parameters) throws Exception
    {
        return getChartData(getDisplayer().getChartMacro(datasets, property, labels, parameters));
    }

    private JsonNode getChartData(String chartMacro) throws Exception
    {
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

    private static WorkItem createdAt(int hour)
    {
        // The work items are grouped by period in the time zone of the server.
        WorkItem workItem = new WorkItem();
        workItem.setCreationDate(
            Date.from(LocalDateTime.of(2026, 3, 5, hour, 30).atZone(ZoneId.systemDefault()).toInstant()));
        return workItem;
    }

    private static WorkItem withProgress(Integer progress)
    {
        WorkItem workItem = new WorkItem();
        workItem.setProgress(progress);
        return workItem;
    }

    private static WorkItem assignedTo(Linkable... assignees)
    {
        WorkItem workItem = new WorkItem();
        workItem.setAssignees(List.of(assignees));
        return workItem;
    }

    private static List<String> texts(JsonNode array)
    {
        List<String> texts = new ArrayList<>();
        array.forEach(node -> texts.add(node.asText()));
        return texts;
    }
}
