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

import org.junit.jupiter.api.Test;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the OpenProject chart macro.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
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
    @Test
    void defaultChartsMacroTest(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);

        // Checks the default OP chart macro parameters, status and type bar.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        new ViewPageWithOpenProjectMacro().waitUntilPageIsReady();

        ChartJSCanvas chart = new ChartJSCanvas();
        assertEquals("bar", chart.getChartType());
        String dataSource = chart.getDataSource();
        assertFalse(dataSource == null || dataSource.isEmpty());
        assertTrue(chart.hasLabel("New"));
        assertTrue(chart.hasLabel("In progress"));
        assertTrue(chart.hasLabel("Closed"));
    }

    @Test
    void chartsMacroParameterTest(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);

        // Checks all the chart type parameter values.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        modal.setChartType("pie");
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();
        assertEquals("pie", new ChartJSCanvas().getChartType());

        for (String[] step : new String[][] { { "pie", "line" }, { "line", "doughnut" }, { "doughnut", "bar" } }) {
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
    void chartsMacroGroupingPropertyTest(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);

        // Checks that the chart macro groups the results on the given property.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        modal.setChartType("pie");
        modal.setProperty("priority");
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        new ViewPageWithOpenProjectMacro().waitUntilPageIsReady();

        ChartJSCanvas chart = new ChartJSCanvas();
        assertEquals("pie", chart.getChartType());
        String dataSource = chart.getDataSource();
        assertFalse(dataSource == null || dataSource.isEmpty());

        // Checks that the default status labels are not displayed.
        assertFalse(chart.hasLabel("Closed"));
        assertFalse(chart.hasLabel("In progress"));
        assertFalse(chart.hasLabel("New"));
        assertTrue(chart.hasLabel("Normal"));
    }

    @Test
    void chartsMacroFilterTest(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);

        // Checks that the chart macro only displays the work items matching the filter.
        OpenProjectChartMacroEditModal modal = new OpenProjectChartMacroEditModal(setup, testReference);
        modal.selectInstance(CONNECTION_ID);
        modal.clickMore();
        new FilterBuilderParameter().addFilter("status").setSuggestValue(1, "New");
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        new ViewPageWithOpenProjectMacro().waitUntilPageIsReady();

        ChartJSCanvas chart = new ChartJSCanvas();
        assertTrue(chart.hasLabel("New"));
        assertFalse(chart.hasLabel("In progress"));
        assertFalse(chart.hasLabel("Closed"));
    }

    @Test
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

        new ViewPageWithOpenProjectMacro().waitUntilPageIsReady();

        // Checks that the chart compares the open and closed work packages.
        ChartJSCanvas chart = new ChartJSCanvas();
        assertEquals("line", chart.getChartType());
        assertEquals(2, chart.getDatasetCount());
        assertTrue(chart.hasLabel("Open"));
        assertTrue(chart.hasLabel("Closed"));
    }
}
