package com.xwiki.projectmanagement.internal.chart;

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

import java.util.List;

/**
 * Represents the data that gets passed to ChartJS for displaying.
 *
 * @version $Id$
 */
public class ChartJSData
{
    private List<String> labels;

    private List<String> xLabels;

    private List<String> yLabels;

    private List<ChartJSDataset> datasets;

    /**
     * @return the labels associated to each dataset.
     */
    public List<String> getLabels()
    {
        return labels;
    }

    /**
     * @param labels see {@link #getLabels()}.
     */
    public void setLabels(List<String> labels)
    {
        this.labels = labels;
    }

    /**
     * @return the xlabels.
     */
    public List<String> getxLabels()
    {
        return xLabels;
    }

    /**
     * @param xLabels see {@link #getxLabels()};
     */
    public void setxLabels(List<String> xLabels)
    {
        this.xLabels = xLabels;
    }

    /**
     * @return the ylabels.
     */
    public List<String> getyLabels()
    {
        return yLabels;
    }

    /**
     * @param yLabels see {@link #getyLabels()}.
     */
    public void setyLabels(List<String> yLabels)
    {
        this.yLabels = yLabels;
    }

    /**
     * @return the datasets.
     */
    public List<ChartJSDataset> getDatasets()
    {
        return datasets;
    }

    /**
     * @param datasets see {@link #getDatasets()}.
     */
    public void setDatasets(List<ChartJSDataset> datasets)
    {
        this.datasets = datasets;
    }
}
