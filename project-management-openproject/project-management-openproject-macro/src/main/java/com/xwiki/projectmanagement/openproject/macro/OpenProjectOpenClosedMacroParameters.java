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
package com.xwiki.projectmanagement.openproject.macro;

import org.xwiki.properties.annotation.PropertyDisplayHidden;
import org.xwiki.properties.annotation.PropertyDisplayType;

import com.xwiki.projectmanagement.internal.chart.displayer.LineChartDisplayer;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * The parameters for the Open v Closed macro.
 *
 * @version $Id$
 * @since 1.3.0
 */
public class OpenProjectOpenClosedMacroParameters extends OpenProjectChartMacroParameters
{
    private int days;

    /**
     * Default constructor filling in the parameters.
     */
    public OpenProjectOpenClosedMacroParameters()
    {
        setType(LineChartDisplayer.TYPE);
        // TODO: Replace with preset ID.
        setFilters(
            "[{\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":[{\"operator\":\"o\",\"value\":\"\""
                + "}]}]}},{\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":[{\"operator\":\"c\",\"valu"
                + "e\":\"\"}]}]}}]");
        setTypeParams("{\"period\":\"DAILY\",\"metric\":\"accumulate\"}");
        setProperty(WorkItem.KEY_CREATION_DATE);
    }

    @PropertyDisplayHidden
    @Override
    public void setFilters(String filters)
    {
        super.setFilters(filters);
    }

    @PropertyDisplayHidden
    @Override
    public void setProperty(String property)
    {
        super.setProperty(property);
    }

    @PropertyDisplayHidden
    @Override
    public void setOffset(Long offset)
    {
        super.setOffset(offset);
    }

    /**
     * @return the number of days that should encompass the chart, starting from today. The chart should display
     *     workpackages from {today - n} to {today}, where {n} is this parameter.
     */
    public int getDays()
    {
        return days;
    }

    /**
     * @param days see {@link #getDays()}.
     */
    @PropertyDisplayType(Integer.class)
    public void setDays(int days)
    {
        this.days = days;
    }

    @Override
    @PropertyDisplayHidden
    public void setDatasetsLabels(String datasetsLabels)
    {
        super.setDatasetsLabels(datasetsLabels);
    }
}
