package com.xwiki.projectmanagement.macro;

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

import org.xwiki.component.manager.ComponentManager;
import org.xwiki.properties.annotation.PropertyDisplayType;

import com.xwiki.projectmanagement.JSONArray;
import com.xwiki.projectmanagement.ProjectManagementChartType;
import com.xwiki.projectmanagement.ProjectManagementChartTypeParams;
import com.xwiki.projectmanagement.ProjectManagementFilters;
import com.xwiki.projectmanagement.ProjectManagementProperty;
import com.xwiki.projectmanagement.internal.chart.ChartTypeDisplayer;
import com.xwiki.projectmanagement.internal.chart.PieChartDisplayer;

/**
 * Class.
 *
 * @version $Id$
 * @since 1.1.0
 */
public class ProjectManagementChartMacroParameters
{
    private String client;

    private String type = PieChartDisplayer.TYPE;

    private String property;

    private String filters;

    private String datasetsLabels;

    private Integer limit = 10;

    private Long offset = 0L;

    private String typeParams;

    /**
     * @return the client id. The client being the project management implementor. i.e. openproject.
     */
    public String getClient()
    {
        return client;
    }

    /**
     * @param client see {@link #getClient()}.
     */
    public void setClient(String client)
    {
        this.client = client;
    }

    /**
     * @return the type of the chart. This value is associated to a chart type implementation that handles the
     *     displaying for the given filter.
     */
    public String getType()
    {
        return type;
    }

    /**
     * @param type see {@link #getType()}.
     */
    @PropertyDisplayType(ProjectManagementChartType.class)
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * @return the work item property that will be used in displaying the chart.
     */
    public String getProperty()
    {
        return property;
    }

    /**
     * @param property see {@link #getProperty()}.
     */
    @PropertyDisplayType(ProjectManagementProperty.class)
    public void setProperty(String property)
    {
        this.property = property;
    }

    /**
     * @return a JSON representing the filters applied on the work items dataset. The JSON is the serialized version of
     *     the {@link org.xwiki.livedata.LiveDataConfiguration}.
     */
    public String getFilters()
    {
        return filters;
    }

    /**
     * @param filters see {@link #getFilters()}.
     */
    @PropertyDisplayType(ProjectManagementFilters.class)
    public void setFilters(String filters)
    {
        this.filters = filters;
    }

    /**
     * @return the limit applied of the work items resulting list.
     */
    public Integer getLimit()
    {
        return limit;
    }

    /**
     * @param limit see {@link #getLimit()}.
     */
    public void setLimit(Integer limit)
    {
        this.limit = limit;
    }

    /**
     * @return the offset of the work items resulting list.
     */
    public Long getOffset()
    {
        return offset;
    }

    /**
     * @param offset see {@link #getOffset()}.
     */
    public void setOffset(Long offset)
    {
        this.offset = offset;
    }


    /**
     * @return a JSON representation of the chart type implementor configuration object. A
     *     {@link com.xwiki.projectmanagement.internal.chart.ChartTypeDisplayer} will return the configuration type by
     *     calling {@link ChartTypeDisplayer#getParameterType()}.
     */
    public String getTypeParams()
    {
        return typeParams;
    }

    /**
     * @param typeParams see {@link #getTypeParams()}.
     */
    @PropertyDisplayType(ProjectManagementChartTypeParams.class)
    public void setTypeParams(String typeParams)
    {
        this.typeParams = typeParams;
    }

    /**
     * @return a JSON array of labels used to describe each dataset displayed.
     */
    public String getDatasetsLabels()
    {
        return datasetsLabels;
    }

    /**
     * @param datasetsLabels see {@link #getDatasetsLabels()}.
     */
    @PropertyDisplayType(JSONArray.class)
    public void setDatasetsLabels(String datasetsLabels)
    {
        this.datasetsLabels = datasetsLabels;
    }
}
