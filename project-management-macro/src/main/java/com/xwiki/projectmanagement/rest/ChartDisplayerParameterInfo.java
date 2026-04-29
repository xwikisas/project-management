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
package com.xwiki.projectmanagement.rest;

import java.util.List;

/**
 * Model representing the metadata associated with a
 * {@link com.xwiki.projectmanagement.internal.chart.ChartTypeDisplayer}.
 */
public class ChartDisplayerParameterInfo
{
    private String id;

    private String label;

    private String description;

    private List<String> values;

    private boolean multiple;

    /**
     * @return the id of the parameter.
     */
    public String getId()
    {
        return id;
    }

    /**
     * @param id see {@link #getId()}.
     */
    public void setId(String id)
    {
        this.id = id;
    }

    /**
     * @return the label of the parameter. It represents the pretty name of the parameter.
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
     * @return the description of the parameter. It describes what the parameter does and how it affects the
     *     {@link com.xwiki.projectmanagement.internal.chart.ChartTypeDisplayer}.
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @param description see {@link #getDescription()}.
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * @return a list of values that can be assigned to the attribute.
     */
    public List<String> getValues()
    {
        return values;
    }

    /**
     * @param values see {@link #getValues()}.
     */
    public void setValues(List<String> values)
    {
        this.values = values;
    }

    /**
     * @return whether the parameter supports multiple values from the {@link #getValues()} list or not.
     */
    public boolean isMultiple()
    {
        return multiple;
    }

    /**
     * @param multiple see {@link #isMultiple()}.
     */
    public void setMultiple(boolean multiple)
    {
        this.multiple = multiple;
    }
}
