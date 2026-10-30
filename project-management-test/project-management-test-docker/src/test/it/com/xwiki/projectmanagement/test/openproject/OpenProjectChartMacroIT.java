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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    }

    @Test
    @Order(3)
    void chartsMacroGroupingPropertyTest(TestUtils setup, TestReference testReference)
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
        modal.getFilterBuilder().addFilter("status").setSuggestValue(1, NEW);
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        // A single bar, counting all the new work packages (the demo projects are public, so the anonymous API
        // counts the same work packages as the administrator who views the chart).
        int newCount = this.openProject.getApiResource("/work_packages?pageSize=1&filters=" + URLEncoder.encode(
            "[{\"status\":{\"operator\":\"=\",\"values\":[\"1\"]}}]", StandardCharsets.UTF_8)).get("total").asInt();
        assertEquals(Map.of(NEW, (long) newCount), new ChartJSCanvas().getValues());
    }

    @Test
    @Order(5)
    void openClosedMacroTest(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);

        // The datasets and the grouping property are predefined, only the time range can be configured.
        OpenProjectOpenClosedMacroEditModal modal = new OpenProjectOpenClosedMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        assertFalse(modal.hasFilterBuilder());
        assertFalse(modal.isMacroParameterDisplayed("property"));
        modal.setMacroParameter("days", "30");
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        // Checks that the chart compares the open and closed work packages.
        ChartJSCanvas chart = new ChartJSCanvas();
        assertEquals("line", chart.getChartType());
        assertThat(chart.getDatasetLabels(), containsInAnyOrder("Open", CLOSED));
    }
}
