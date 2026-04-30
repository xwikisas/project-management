package com.xwiki.projectmanagement.openproject.macro;

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

import org.xwiki.properties.annotation.PropertyDisplayHidden;
import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.properties.annotation.PropertyMandatory;

import com.xwiki.projectmanagement.internal.chart.displayer.BarChartDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementChartMacroParameters;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectFilters;
import com.xwiki.projectmanagement.openproject.OpenProjectInstance;
import com.xwiki.projectmanagement.openproject.OpenProjectProperty;

/**
 * The parameters for the {@link com.xwiki.projectmanagement.openproject.internal.macro.OpenProjectChartMacro}.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
public class OpenProjectChartMacroParameters extends ProjectManagementChartMacroParameters
{
    private String instance;

    /**
     * Default constructor. Sets the default values for the parameters.
     */
    public OpenProjectChartMacroParameters()
    {
        setProperty(WorkItem.KEY_STATUS);
        setType(BarChartDisplayer.TYPE);
        setLimit(100);
    }

    /**
     * @return the OpenProject instance that should be used to retrieve work packages.
     */
    public String getInstance()
    {
        return instance;
    }

    /**
     * @param instance see {@link #getInstance()}.
     */
    @PropertyMandatory
    @PropertyDisplayType(OpenProjectInstance.class)
    public void setInstance(String instance)
    {
        this.instance = instance;
    }

    @PropertyDisplayType(OpenProjectFilters.class)
    @Override
    public void setFilters(String filters)
    {
        super.setFilters(filters);
    }

    @PropertyDisplayType(OpenProjectProperty.class)
    @Override
    public void setProperty(String property)
    {
        super.setProperty(property);
    }

    @PropertyDisplayHidden
    @Override
    public void setClient(String client)
    {
        super.setClient(client);
    }
}
