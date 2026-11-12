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
package com.xwiki.projectmanagement.test.openproject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.edit.test.po.InplaceEditablePage;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import com.fasterxml.jackson.databind.JsonNode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the OpenProject chart macro.
 *
 * @version $Id$
 */
@UITest(
    properties = {
        AbstractOpenProjectIT.PRCHECKER_EXCLUDE_PROPERTY,
        AbstractOpenProjectIT.PLUGINS_PROPERTY
    },
    extraJARs = {
        AbstractOpenProjectIT.JYTHON_JAR,
        AbstractOpenProjectIT.SCHEDULER_API_JAR
    }
)
public class OpenProjectChartMacroIT extends AbstractOpenProjectIT
{
    private static final String NEW = "New";

    private static final String IN_PROGRESS = "In progress";

    private static final String CLOSED = "Closed";

    private static final String PIE = "pie";

    private static final String INPLACE_EDITOR = "InplaceChartEditor";

    private static final String INPLACE_EDITOR_PASSWORD = "pass";

    @Test
    @Order(1)
    void defaultChartsMacroTest(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);

        // Checks the default OP chart macro parameters, status and type bar.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        // The chart is drawn by Chart.js, with one bar per status.
        ChartJSCanvas chart = new ChartJSCanvas();
        assertEquals("bar", chart.getChartType());
        assertThat(chart.getLabels(), hasItems(NEW, IN_PROGRESS, CLOSED));
        // The legend would only name the single series of bars, which has no name.
        assertFalse(chart.isLegendDisplayed());
    }

    @Test
    @Order(2)
    void chartsMacroParameterTest(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);

        // Checks all the chart type parameter values.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        modal.setChartType(PIE);
        // The chart options display translated values.
        assertThat(modal.getChartOptionChoices("period"), containsInAnyOrder("Hourly", "Daily", "Monthly", "Yearly"));
        assertThat(modal.getChartOptionChoices("metric"), containsInAnyOrder("Count", "Cumulative"));
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();
        assertEquals(PIE, new ChartJSCanvas().getChartType());

        for (String[] step : new String[][] { { PIE, "line" }, { "line", "doughnut" }, { "doughnut", "bar" } }) {
            modal = new OpenProjectChartMacroEditModal(setup, testReference);
            modal.clickMore();
            assertEquals(step[0], modal.getChartType());
            modal.setChartType(step[1]);
            modal.clickSubmit();
            modal.getEditPage().clickSaveAndView();
            assertEquals(step[1], new ChartJSCanvas().getChartType());
        }

        // The chart options of an existing call display the translated value, and the call keeps the value.
        setup.createPage(testReference, String.format(
            "{{openprojectchart instance=\"%s\" typeParams=\"{~\"period~\":~\"DAILY~\"}\"/}}", CONNECTION_ID));
        modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.clickMore();
        assertEquals("Daily", modal.getChartOptionSelectedValue("period"));
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();
        setup.gotoPage(testReference, "edit", "editor=wiki");
        String content = new WikiEditPage().getContent();
        assertThat(content, containsString("DAILY"));
        assertThat(content, not(containsString("Daily")));
    }

    @Test
    @Order(3)
    void chartsMacroGroupingPropertyTest(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.deletePage(testReference);

        // Checks that the chart macro groups the results on the given property.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        modal.setChartType(PIE);
        modal.setProperty("priority");
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        ChartJSCanvas chart = new ChartJSCanvas();
        assertEquals(PIE, chart.getChartType());
        // The chart displays the priorities instead of the default status labels.
        List<String> labels = chart.getLabels();
        assertThat(labels, hasItem("Normal"));
        assertThat(labels, not(hasItem(CLOSED)));
        assertThat(labels, not(hasItem(IN_PROGRESS)));
        assertThat(labels, not(hasItem(NEW)));

        // The progress takes few values, so it is proposed: one slice per progress value, sorted by value, the work
        // packages without progress last.
        modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.clickMore();
        modal.setProperty("progress");
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        // Without filters, the OpenProject API only returns the open work packages, while the chart counts them all.
        Map<Integer, Long> progressCounts = new TreeMap<>();
        long withoutProgress = 0;
        for (JsonNode workPackage : this.openProject.getApiResource("/work_packages?pageSize=1000&filters=%5B%5D")
            .path("_embedded").path("elements")) {
            JsonNode progress = workPackage.path("percentageDone");
            if (progress.isNumber()) {
                progressCounts.merge(progress.asInt(), 1L, Long::sum);
            } else {
                withoutProgress++;
            }
        }
        Map<String, Long> expected = new LinkedHashMap<>();
        progressCounts.forEach((progress, count) -> expected.put(String.valueOf(progress), count));
        if (withoutProgress > 0) {
            expected.put("UNSET", withoutProgress);
        }
        chart = new ChartJSCanvas();
        assertEquals(expected, chart.getValues());
        assertEquals(new ArrayList<>(expected.keySet()), chart.getLabels());
    }

    @Test
    @Order(4)
    void chartsMacroFilterTest(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.deletePage(testReference);

        // Checks that the chart macro only displays the work items matching the filter.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        modal.getFilterBuilder().addFilter("status").setSuggestValue(NEW);
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        // A single bar, counting all the new work packages (the demo projects are public, so the anonymous API
        // counts the same work packages as the administrator who views the chart).
        int newCount = this.openProject.getApiResource("/work_packages?pageSize=1&filters=" + URLEncoder.encode(
            "[{\"status\":{\"operator\":\"=\",\"values\":[\"1\"]}}]", StandardCharsets.UTF_8)).get("total").asInt();
        assertEquals(Map.of(NEW, (long) newCount), new ChartJSCanvas().getValues());

        // The chart is drawn again when the page is saved with the in-place editor, which renders the content again
        // without reloading the page. The "Edit" button opens the in-place editor for the users preferring the WYSIWYG
        // editor, who see the public work packages when they haven't connected their OpenProject account.
        setup.createUser(INPLACE_EDITOR, INPLACE_EDITOR_PASSWORD, null, "editor", "Wysiwyg");
        OpenProjectAdminPage.gotoPage().setAccessMode(CONNECTION_ID, "PUBLIC_DATA_AND_USER_ACCOUNTS");
        try {
            setup.loginAndGotoPage(INPLACE_EDITOR, INPLACE_EDITOR_PASSWORD, setup.getURL(testReference));
            new InplaceEditablePage().editInplace().saveAndView();
            assertEquals(Map.of(NEW, (long) newCount), new ChartJSCanvas().getValues());
        } finally {
            switchToSuperAdmin(setup);
            OpenProjectAdminPage.gotoPage().setAccessMode(CONNECTION_ID, "USER_ACCOUNTS");
        }
    }

    @Test
    @Order(5)
    void openClosedMacroTest(TestUtils setup, TestReference testReference)
    {
        // The datasets, the chart type and the grouping property are predefined, only the time range can be configured:
        // the parameters defining them are never displayed and have no effect, even when the macro call sets them.
        setup.createPage(testReference, String.format("{{openprojectopenclosed instance=\"%s\" filters=\"[]\" "
            + "type=\"%s\" property=\"status\"/}}", CONNECTION_ID, PIE));
        setup.gotoPage(testReference);

        // Checks that the chart compares the open and closed work packages.
        ChartJSCanvas chart = new ChartJSCanvas();
        assertEquals("line", chart.getChartType());
        assertThat(chart.getDatasetLabels(), containsInAnyOrder("Open", CLOSED));
        assertTrue(chart.isLegendDisplayed());

        OpenProjectOpenClosedMacroEditModal modal = new OpenProjectOpenClosedMacroEditModal(setup, testReference);
        modal.clickMore();
        assertFalse(modal.hasFilterBuilder());
        assertFalse(modal.isMacroParameterDisplayed("filters"));
        assertFalse(modal.isMacroParameterDisplayed("type"));
        assertFalse(modal.isMacroParameterDisplayed("property"));
        // The chart options can be changed, although the chart type can't be.
        assertThat(modal.getChartOptionNames(), containsInAnyOrder("Date Grouping", "Measure"));
    }
}
