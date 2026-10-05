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
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.xwiki.test.ui.XWikiWebDriver;

/**
 * Helpers shared by the page objects of a filter preset page, in view and edit mode.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
final class PresetFilterBuilders
{
    private static final By BUILDERS = By.cssSelector(".project-management-filters .proj-manag-constraint-builder");

    private PresetFilterBuilders()
    {
    }

    /**
     * Wait for the filter builders to be initialized by the JavaScript code, which also loads the saved filters.
     *
     * @param driver the web driver
     */
    static void waitUntilReady(XWikiWebDriver driver)
    {
        driver.waitUntilCondition(d -> Boolean.TRUE.equals(driver.executeJavascript(
            "return !!(window.FilterBuilder && window.FilterBuilder.instances.size > 0);")));
    }

    /**
     * @param driver the web driver
     * @return the filter builders of the preset, one per dataset
     */
    static List<FilterBuilderParameter> getFilterBuilders(XWikiWebDriver driver)
    {
        return driver.findElements(BUILDERS).stream().map(FilterBuilderParameter::new)
            .collect(Collectors.toList());
    }

    /**
     * @param driver the web driver
     * @return the number of filter builders of the preset
     */
    static int countFilterBuilders(XWikiWebDriver driver)
    {
        return driver.findElementsWithoutWaiting(BUILDERS).size();
    }
}
