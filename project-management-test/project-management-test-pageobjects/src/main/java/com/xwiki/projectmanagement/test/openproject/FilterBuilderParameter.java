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
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models the filter parameter of the Open Project macro.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class FilterBuilderParameter extends BaseElement
{
    /**
     * @return the container of the parameter value.
     */
    public WebElement getContainer()
    {
        return getDriver().findElement(By.className("proj-manag-constraint-builder"));
    }

    /**
     * Select from the "Add Filter" list a work package property that should be filtered.
     *
     * @param filterProperty the technical work package property name that should be filtered.
     * @return the model of a filter.
     */
    public FilterBuilderFilter addFilter(String filterProperty)
    {
        Select select = new Select(getDriver().findElement(By.id("proj-manag-add-constraint")));

        try {
            select.selectByValue(filterProperty);
        } catch (NoSuchElementException ignored) {
            // If no such element, we let the code execute, maybe it finds an already added filter and returns it.
        }

        List<WebElement> presentFilters =
            getDriver().findElements(By.xpath("//*[contains(@class, 'proj-manag-constraint-name')]"));
        WebElement createdFilter = null;
        for (WebElement presentFilter : presentFilters) {
            if (presentFilter.getAttribute("value").equals(filterProperty)) {
                createdFilter = presentFilter.findElement(By.xpath("ancestor::div[@class='proj-manag-constraint']"));
                break;
            }
        }
        if (createdFilter == null) {
            throw new NoSuchElementException("Could not find the added filter.");
        }

        return new FilterBuilderFilter(createdFilter);
    }

    /**
     * @return retrieve all the created filters of the filter parameter.
     */
    public List<FilterBuilderFilter> getFilters()
    {
        return getContainer()
            .findElements(By.cssSelector(".proj-manag-constraints .proj-manag-constraint"))
            .stream()
            .map(FilterBuilderFilter::new)
            .collect(Collectors.toList());
    }

    /**
     * Clear all the filters inside the filter parameter.
     */
    public void clearFilters()
    {
        try {
            getDriver().findElements(By.className("proj-manag-delete-filter")).forEach(WebElement::click);
        } catch (StaleElementReferenceException ignored) {
            // No problem if the elements were deleted faster than necessary. But it shouldn't happen.
        }
    }
}
