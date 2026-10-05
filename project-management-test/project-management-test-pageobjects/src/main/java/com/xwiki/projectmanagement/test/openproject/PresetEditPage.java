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
import org.xwiki.test.ui.po.InlinePage;

/**
 * Models the edit mode of a filter preset page.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public class PresetEditPage extends InlinePage
{
    private static final String FIELD_PREFIX = "ProjectManagement.Code.Presets.PresetClass_0_";

    /**
     * Wait for the preset form and its filter builders to be ready.
     */
    public PresetEditPage()
    {
        getDriver().waitUntilElementIsVisible(By.id(FIELD_PREFIX + "name"));
        PresetFilterBuilders.waitUntilReady(getDriver());
    }

    /**
     * @return the name of the preset
     */
    public String getName()
    {
        return getDriver().findElement(By.id(FIELD_PREFIX + "name")).getDomProperty("value");
    }

    /**
     * @return the id of the preset, which is used by the macros to reference it
     */
    public String getPresetId()
    {
        return getDriver().findElement(By.id(FIELD_PREFIX + "id")).getDomProperty("value");
    }

    /**
     * @return the filter builders of the preset, one per dataset
     */
    public List<FilterBuilderParameter> getFilterBuilders()
    {
        return PresetFilterBuilders.getFilterBuilders(getDriver());
    }

    /**
     * Add a new dataset to a chart preset and wait for its filter builder.
     *
     * @return the filter builder of the new dataset
     */
    public FilterBuilderParameter addDataset()
    {
        int count = PresetFilterBuilders.countFilterBuilders(getDriver());
        getDriver().findElement(By.className("project-management-new-dataset")).click();
        getDriver().waitUntilCondition(driver -> PresetFilterBuilders.countFilterBuilders(getDriver()) == count + 1);
        return getFilterBuilders().get(count);
    }

    /**
     * Wait for the filter value that is going to be saved to contain the given text.
     *
     * @param text the expected text, e.g. a filter value
     */
    public void waitUntilFilterValueContains(String text)
    {
        getDriver().waitUntilCondition(driver -> {
            String value = getDriver().findElement(By.id(FIELD_PREFIX + "filter")).getDomProperty("value");
            return value != null && value.contains(text);
        });
    }

    /**
     * Save the preset and wait for its view page.
     *
     * @return the view page of the preset
     */
    public PresetViewPage clickSaveAndViewPreset()
    {
        clickSaveAndView(true);
        return new PresetViewPage();
    }
}
