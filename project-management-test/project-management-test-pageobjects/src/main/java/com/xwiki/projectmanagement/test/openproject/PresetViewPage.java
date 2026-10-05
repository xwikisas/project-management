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

import java.util.List;

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Models the view mode of a filter preset page.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public class PresetViewPage extends ViewPage
{
    /**
     * Wait for the filter builders to be ready.
     */
    public PresetViewPage()
    {
        waitUntilPageIsReady();
        PresetFilterBuilders.waitUntilReady(getDriver());
    }

    /**
     * @return {@code true} if the preset is meant for chart macros, as displayed by the "For Charts?" field
     */
    public boolean isChartPreset()
    {
        String value = getDriver().findElement(
            By.xpath("//div[@id='xwikicontent']//dt[normalize-space() = 'For Charts?']/following-sibling::dd[1]"))
            .getText().trim();
        return "Yes".equals(value);
    }

    /**
     * @return the filter builders of the preset, one per dataset
     */
    public List<FilterBuilderParameter> getFilterBuilders()
    {
        return PresetFilterBuilders.getFilterBuilders(getDriver());
    }

    /**
     * Wait for the saved filters to be displayed in the filter builders.
     *
     * @param count the expected number of filters, across all the datasets
     */
    public void waitUntilFiltersAreDisplayed(int count)
    {
        getDriver().waitUntilCondition(driver -> driver.findElements(
            By.cssSelector(".project-management-filters .proj-manag-constraints .proj-manag-constraint")).size()
            >= count);
    }

    /**
     * @return the serialized reference of the preset page, e.g. {@code xwiki:ProjectManagement.Code.Presets.Preset1}
     */
    public String getPresetReference()
    {
        return getMetaDataValue("reference");
    }
}
