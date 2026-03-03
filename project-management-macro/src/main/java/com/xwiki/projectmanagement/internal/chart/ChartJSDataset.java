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
 * Represents the ChartJS dataset.
 *
 * @version $Id$
 */
public class ChartJSDataset
{
    private String label;

    private List<Long> data;

    /**
     * @return the label of the dataset.
     */
    public String getLabel()
    {
        return label;
    }

    /**
     * @param label see {@link #getLabel()}.
     */
    public void setLabel(String label)
    {
        this.label = label;
    }

    /**
     * @return the data of the dataset.
     */
    public List<Long> getData()
    {
        return data;
    }

    /**
     * @param data see {@link #getData()}.
     */
    public void setData(List<Long> data)
    {
        this.data = data;
    }
}
