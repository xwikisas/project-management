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

import org.xwiki.livedata.macro.LiveDataMacroParameters;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyDisplayHidden;
import org.xwiki.properties.annotation.PropertyDisplayType;
import org.xwiki.properties.annotation.PropertyFeature;
import org.xwiki.properties.annotation.PropertyGroup;
import org.xwiki.properties.annotation.PropertyHidden;
import org.xwiki.properties.annotation.PropertyOrder;
import org.xwiki.stability.Unstable;

import com.xwiki.projectmanagement.ProjectManagementFilter;
import com.xwiki.projectmanagement.ProjectManagementPreset;
import com.xwiki.projectmanagement.ProjectManagementProperties;
import com.xwiki.projectmanagement.ProjectManagementSortEntry;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;

/**
 * @version $Id$
 * @since 1.0
 */
@Unstable
public class ProjectManagementMacroParameters extends LiveDataMacroParameters
    implements ProjectManagementAsyncMacroParams
{
    private String identifier;

    private String url;

    private WorkItemsDisplayer workItemsDisplayer = WorkItemsDisplayer.liveData;

    private String presetId;

    /**
     * Default constructor.
     */
    public ProjectManagementMacroParameters()
    {
        // Set default values.
        setProperties("identifier,type,summary,description,startDate,resolved,assignees");
        setLimit(25);
    }

    /**
     * @return the identifier for a work item (i.e. ISSUE-100 in case of jira) or an url containing either the
     *         identifier for a work item or a filter retrieving a list of work items.
     */
    public String getIdentifier()
    {
        return identifier;
    }

    /**
     * @param identifier see {@link #getIdentifier()}.
     */
    @PropertyFeature("filters")
    public void setIdentifier(String identifier)
    {
        this.identifier = identifier;
    }

    @Override
    public String getProperties()
    {
        return super.getProperties();
    }

    @PropertyDisplayType(ProjectManagementProperties.class)
    @PropertyGroup("display")
    @Override
    public void setProperties(String properties)
    {
        super.setProperties(properties);
    }

    @PropertyDisplayType(ProjectManagementFilter.class)
    @PropertyFeature("filters")
    @Override
    public void setFilters(String filters)
    {
        super.setFilters(filters);
    }

    @PropertyDisplayType(ProjectManagementSortEntry.class)
    @PropertyGroup("display")
    @Override
    public void setSort(String sort)
    {
        super.setSort(sort);
    }

    /**
     * @return the displayer id that determines how the work items should be displayed.
     */
    public WorkItemsDisplayer getWorkItemsDisplayer()
    {
        return workItemsDisplayer;
    }

    /**
     * @param workItemsDisplayer see {@link #getWorkItemsDisplayer()}.
     */
    @PropertyDisplayType(WorkItemsDisplayer.class)
    @PropertyGroup("display")
    @PropertyOrder(40)
    public void setWorkItemsDisplayer(WorkItemsDisplayer workItemsDisplayer)
    {
        this.workItemsDisplayer = workItemsDisplayer;
    }

    @PropertyGroup("display")
    @Override
    public void setLimit(Integer limit)
    {
        super.setLimit(limit);
    }

    @PropertyGroup ("display")
    @Override
    public void setOffset(Long offset)
    {
        super.setOffset(offset);
    }

    // PropertyHidden for parameters that are handled by the application. PropertyAdvanced for parameters that can
    // still be passed to the macro but are hidden in order to make the macro less crowded.

    @PropertyDisplayHidden
    @Override
    public void setPageSizes(String pageSizes)
    {
        super.setPageSizes(pageSizes);
    }

    @PropertyHidden
    @Override
    public Boolean getShowPageSizeDropdown()
    {
        return super.getShowPageSizeDropdown();
    }

    @PropertyHidden
    @Override
    public void setLayouts(String layouts)
    {
        super.setLayouts(layouts);
    }

    @PropertyHidden
    @Override
    public void setSource(String source)
    {
        super.setSource(source);
    }

    @PropertyHidden
    @Override
    public void setSourceParameters(String sourceParameters)
    {
        super.setSourceParameters(sourceParameters);
    }

    @PropertyDisplayHidden
    @Override
    public void setId(String id)
    {
        super.setId(id);
    }

    /**
     * @return the id of the filter preset.
     * @since 1.3.0
     */
    public String getPresetId()
    {
        return presetId;
    }

    /**
     * @param presetId see {@link #getPresetId()}.
     * @since 1.3.0
     */
    @PropertyDisplayType(ProjectManagementPreset.class)
    @PropertyFeature("filters")
    public void setPresetId(String presetId)
    {
        this.presetId = presetId;
    }

    /**
     * We define this method now because, on version 16.0, livedata has the description parameter. We want to hide it.
     * TODO: When parent is greater than 16.0, add the @Override annotation.
     * 
     * @param description remove javadoc when parent is greater than 16.0.
     */
    @PropertyDisplayHidden
    @PropertyDescription("An optional textual description of the Live Data.")
    public void setDescription(String description)
    {
    }
}
