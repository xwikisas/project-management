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

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.xwiki.livedata.macro.LiveDataMacroParameters;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyDisplayHidden;
import org.xwiki.properties.annotation.PropertyDisplayType;
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

    private WorkItemsDisplayer displayer;

    private WorkItemsDisplayer legacyDisplayer;

    private String presetId;

    /**
     * Default constructor.
     */
    public ProjectManagementMacroParameters()
    {
        // Set default values.
        setProperties("identifier,type,summary,description,startDate,resolved,assignees");
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
    @PropertyGroup("filters")
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
    @PropertyGroup("filters")
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
     * @return how the work items are displayed: the {@code displayer} parameter if set, otherwise the deprecated
     *     {@code workItemsDisplayer} parameter if set, otherwise a Live Data table
     * @since 1.4.0
     */
    public WorkItemsDisplayer getDisplayer()
    {
        if (this.displayer != null) {
            return this.displayer;
        }
        return this.legacyDisplayer != null ? this.legacyDisplayer : WorkItemsDisplayer.TABLE;
    }

    /**
     * @param displayer see {@link #getDisplayer()}
     * @since 1.4.0
     */
    @PropertyDisplayType(WorkItemsDisplayer.class)
    @PropertyGroup("display")
    @PropertyOrder(40)
    public void setDisplayer(WorkItemsDisplayer displayer)
    {
        this.displayer = displayer;
    }

    /**
     * @return the value of the deprecated {@code workItemsDisplayer} parameter, i.e. the
     *     {@link WorkItemsDisplayer#getLegacyName() legacy name} of a displayer, or {@code null} if it isn't set
     * @deprecated since 1.4.0, use {@link #getDisplayer()} instead
     */
    @Deprecated(since = "1.4.0")
    public String getWorkItemsDisplayer()
    {
        return this.legacyDisplayer != null ? this.legacyDisplayer.getLegacyName() : null;
    }

    /**
     * @param workItemsDisplayer the legacy name of a displayer (e.g. {@code workItemsSingle}); the {@code displayer}
     *     parameter takes precedence over it when both are set
     * @deprecated since 1.4.0, use {@link #setDisplayer(WorkItemsDisplayer)} instead
     */
    @Deprecated(since = "1.4.0")
    @PropertyDisplayHidden
    public void setWorkItemsDisplayer(String workItemsDisplayer)
    {
        WorkItemsDisplayer value = WorkItemsDisplayer.fromString(workItemsDisplayer);
        if (value == null) {
            throw new IllegalArgumentException(String.format(
                "Unknown displayer [%s]. Allowed values are %s. Use the displayer parameter (%s) instead.",
                workItemsDisplayer, joinDisplayers(WorkItemsDisplayer::getLegacyName),
                joinDisplayers(WorkItemsDisplayer::toString)));
        }
        this.legacyDisplayer = value;
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
    @PropertyGroup("filters")
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
    @PropertyHidden
    @PropertyDescription("An optional textual description of the Live Data.")
    public void setDescription(String description)
    {
    }

    private static String joinDisplayers(Function<WorkItemsDisplayer, String> name)
    {
        return Arrays.stream(WorkItemsDisplayer.values()).map(name).collect(Collectors.joining(", "));
    }
}
