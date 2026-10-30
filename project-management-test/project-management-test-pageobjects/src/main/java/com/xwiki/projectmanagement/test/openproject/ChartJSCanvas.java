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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models the chart that the Chart Macro renders, as drawn by Chart.js.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public class ChartJSCanvas extends BaseElement
{
    private static final By CANVAS = By.cssSelector(".chart-container canvas.chart");

    private static final int LOAD_TIMEOUT = 30;

    private static final String DATASETS = "datasets";

    private static final String LABEL = "label";

    private final WebElement canvas;

    private final Map<String, Object> chart;

    /**
     * Wait for Chart.js to draw the first chart of the page, since the chart macros can be rendered asynchronously.
     */
    public ChartJSCanvas()
    {
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            getDriver().waitUntilElementIsVisible(CANVAS);
            this.canvas = getDriver().findElement(CANVAS);
            getDriver().waitUntilCondition(driver -> readChart() != null);
            this.chart = readChart();
        } finally {
            getDriver().setTimeout(timeout);
        }
    }

    /**
     * @return the type of the drawn chart, e.g. {@code bar}
     */
    public String getChartType()
    {
        return (String) this.chart.get("type");
    }

    /**
     * @return the labels of the drawn chart, e.g. the values of the property the work packages are grouped on
     * @since 1.4.0
     */
    public List<String> getLabels()
    {
        return ((List<?>) this.chart.get("labels")).stream().map(String::valueOf).collect(Collectors.toList());
    }

    /**
     * @return the labels of the datasets of the drawn chart, as displayed by its legend
     * @since 1.4.0
     */
    public List<String> getDatasetLabels()
    {
        return getDatasets().stream().map(dataset -> String.valueOf(dataset.get(LABEL))).collect(Collectors.toList());
    }

    /**
     * @return the values of the first dataset of the drawn chart, by label, in the order of the labels
     * @since 1.4.0
     */
    public Map<String, Long> getValues()
    {
        List<String> labels = getLabels();
        List<?> data = (List<?>) getDatasets().get(0).get("data");
        Map<String, Long> values = new LinkedHashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            values.put(labels.get(i), ((Number) data.get(i)).longValue());
        }
        return values;
    }

    /**
     * @return the number of datasets that the chart is displaying
     * @since 1.3.0-rc-2
     */
    public int getDatasetCount()
    {
        return getDatasets().size();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getDatasets()
    {
        return (List<Map<String, Object>>) this.chart.get(DATASETS);
    }

    /**
     * @return the type and data of the Chart.js chart drawn on the canvas, or {@code null} if it is not drawn yet
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readChart()
    {
        // Chart.js is loaded as a RequireJS module by the ChartJS macro, which creates the chart once loaded.
        return (Map<String, Object>) getDriver().executeAsyncScript("const [canvas, done] = arguments;"
            + "require(['chart'], Chart => {"
            + "  const chart = Object.values(Chart.instances || {}).find(instance => instance.canvas === canvas);"
            + "  done(chart ? {type: chart.config.type, labels: chart.data.labels || [],"
            + "    datasets: chart.data.datasets.map(dataset => ({label: dataset.label, data: dataset.data}))} : null);"
            + "}, () => done(null));", this.canvas);
    }
}
