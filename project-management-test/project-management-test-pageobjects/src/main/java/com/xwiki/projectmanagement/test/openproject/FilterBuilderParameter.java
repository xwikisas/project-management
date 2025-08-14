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

public class FilterBuilderParameter extends BaseElement
{
    public WebElement getContainer()
    {
        return getDriver().findElement(By.className("proj-manag-constraint-builder"));
    }

    public FilterBuilderFilter addFilter(String filterProperty)
    {
        Select select = new Select(getDriver().findElement(By.id("proj-manag-add-constraint")));

        try {
            select.selectByValue(filterProperty);
        } catch (NoSuchElementException ignored) {
            // If no such element, we let the code execute, maybe it finds an already added filter and returns it.
        }

        WebElement createdFilter = getDriver().findElement(By.xpath(String.format(
            "//*[@class='proj-manag-constraint-title' and contains(text(), '%s')]"
                + "/ancestor::div[@class='proj-manag-constraint']",
            filterProperty)));

        return new FilterBuilderFilter(createdFilter);
    }

    public List<FilterBuilderFilter> getFilters()
    {
        return getContainer()
            .findElements(By.cssSelector(".proj-manag-constraints .proj-manag-constraint"))
            .stream()
            .map(FilterBuilderFilter::new)
            .collect(Collectors.toList());
    }

    public void clearFilters()
    {
        try {
            getDriver().findElements(By.className("proj-manag-delete-filter")).forEach(WebElement::click);
        } catch (StaleElementReferenceException ignored) {
            // No problem if the elements were deleted faster than necessary. But it shouldn't happen.
        }
    }
}
