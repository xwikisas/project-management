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

import com.xwiki.projectmanagement.calendar.macro.CalendarMacroParameters;
import com.xwiki.projectmanagement.openproject.OpenProjectEventType;
import com.xwiki.projectmanagement.openproject.OpenProjectFilter;
import com.xwiki.projectmanagement.openproject.OpenProjectInstance;
import org.xwiki.properties.annotation.PropertyDisplayHidden;
import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.properties.annotation.PropertyMandatory;

import java.awt.Color;
import java.util.List;

/**
 * The parameters for the {@link com.xwiki.projectmanagement.openproject.internal.macro.OpenProjectCalendarMacro}.
 *
 * @version $Id$
 * @since 1.2.0
 */
public class OpenProjectCalendarMacroParameters extends CalendarMacroParameters
{
    private String instance;

    private List<OpenProjectEventType> types = List.of(OpenProjectEventType.WORK_PACKAGE);

    private String sprintColor;

    private String versionColor;

    /**
     * Default constructor. Sets the default values for the parameters.
     */
    public OpenProjectCalendarMacroParameters()
    {
        setLimit(25);
    }

    /**
     * @return the OpenProject instance that should be used to retrieve work packages.
     */
    public String getInstance()
    {
        return this.instance;
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

    /**
     * @return the types of work packages to include in the calendar, as a comma-separated list of enum names.
     */
    public List<OpenProjectEventType> getTypes()
    {
        return this.types;
    }

    /**
     * @param types see {@link #getTypes()}.
     */
    @PropertyDisplayType(OpenProjectEventType.class)
    public void setTypes(List<OpenProjectEventType> types)
    {
        this.types = types;
    }

    /**
     * Overrides the filters from the parent class to provide a more specific description and display type for
     * OpenProject filters.
     *
     * @param filters the filters to be applied to the work packages retrieved from the configured OpenProject instance
     */
    @PropertyDisplayType(OpenProjectFilter.class)
    @Override
    public void setFilters(String filters)
    {
        super.setFilters(filters);
    }

    @PropertyDisplayHidden
    @Override
    public void setClient(String client)
    {
        super.setClient(client);
    }

    /**
     * Get the color scheme that should be used when rendering OpenProject versions.
     *
     * @return the color to be used by the OpenProject versions
     */
    public String getVersionColor()
    {
        return this.versionColor;
    }

    /**
     * @param versionColor see {@link #getVersionColor()}.
     */
    @PropertyDisplayType(Color.class)
    public void setVersionColor(String versionColor)
    {
        this.versionColor = versionColor;
    }

    /**
     * Get the color scheme that should be used when rendering OpenProject sprints.
     *
     * @return the color to be used by the OpenProject sprints
     */
    public String getSprintColor()
    {
        return this.sprintColor;
    }

    /**
     * @param sprintColor see {@link #getSprintColor()}.
     */
    @PropertyDisplayType(Color.class)
    public void setSprintColor(String sprintColor)
    {
        this.sprintColor = sprintColor;
    }
}
