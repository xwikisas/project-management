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

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models the canvas element that the Chart Macro renders.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public class ChartJSCanvas extends BaseElement
{
    private static final By CANVAS = By.cssSelector(".chart-container canvas.chart");

    private final WebElement canvas;

    public ChartJSCanvas()
    {
        getDriver().waitUntilElementIsVisible(CANVAS);
        this.canvas = getDriver().findElement(CANVAS);
    }

    public String getChartType()
    {
        return this.canvas.getDomAttribute("data-type");
    }

    /**
     * @return the serialized ChartJS data that the chart is displaying.
     */
    public String getDataSource()
    {
        return this.canvas.getDomAttribute("data-source");
    }

    /**
     * @param label the label to look for.
     * @return {@code true} if the data of the chart contains the given label; {@code false} otherwise.
     */
    public boolean hasLabel(String label)
    {
        String dataSource = getDataSource();
        return dataSource != null && dataSource.contains(String.format("\"%s\"", label));
    }
}
