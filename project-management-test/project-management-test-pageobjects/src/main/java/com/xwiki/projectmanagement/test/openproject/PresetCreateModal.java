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

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models the modal used to create a filter preset, opened from the OpenProject administration section.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public class PresetCreateModal extends BaseElement
{
    private static final By POPUP = By.id("proj-manag-preset-popup");

    /**
     * Wait for the modal to be displayed.
     */
    public PresetCreateModal()
    {
        getDriver().waitUntilElementIsVisible(POPUP);
    }

    /**
     * @param name the name of the preset
     * @return this object
     */
    public PresetCreateModal setName(String name)
    {
        WebElement input = getPopup().findElement(By.name("title"));
        input.clear();
        input.sendKeys(name);
        return this;
    }

    /**
     * @param chartPreset whether the preset is meant for chart macros, i.e. it can have multiple datasets
     * @return this object
     */
    public PresetCreateModal setChartPreset(boolean chartPreset)
    {
        WebElement checkbox = getPopup().findElement(By.name("isMultiple"));
        if (checkbox.isSelected() != chartPreset) {
            checkbox.click();
        }
        return this;
    }

    /**
     * Click the create button. Nothing happens if the name is empty.
     */
    public void clickCreate()
    {
        getPopup().findElement(By.className("btn-primary")).click();
    }

    /**
     * Click the create button and wait for the edit page of the new preset.
     *
     * @return the edit page of the new preset
     */
    public PresetEditPage create()
    {
        clickCreate();
        return new PresetEditPage();
    }

    /**
     * @return {@code true} if the modal is displayed
     */
    public boolean isDisplayed()
    {
        return getDriver().findElementsWithoutWaiting(POPUP).stream().anyMatch(WebElement::isDisplayed);
    }

    private WebElement getPopup()
    {
        return getDriver().findElement(POPUP);
    }
}
