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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models a filter builder, such as the filter parameter of the OpenProject macro or a dataset of a filter preset.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class FilterBuilderParameter extends BaseElement
{
    private static final String ADD_FILTER_CLASS = "proj-manag-add-filter";

    private final WebElement container;

    /**
     * Model the first filter builder of the page.
     */
    public FilterBuilderParameter()
    {
        this(null);
    }

    /**
     * Model the given filter builder, when there are several builders on the page.
     *
     * @param container the element with the {@code proj-manag-constraint-builder} class
     * @since 1.3.0-rc-2
     */
    public FilterBuilderParameter(WebElement container)
    {
        this.container = container;
    }

    /**
     * @return the container of the parameter value.
     */
    private WebElement getContainer()
    {
        if (this.container != null) {
            return this.container;
        }
        return getDriver().findElement(By.className("proj-manag-constraint-builder"));
    }

    /**
     * @return the title of the builder, e.g. {@code Series 1}, or an empty string if it has no title
     * @since 1.3.0-rc-2
     */
    public String getTitle()
    {
        // The builders without a title are expected, so don't wait for one.
        return getDriver().findElementsWithoutWaiting(getContainer(), By.className("proj-manag-header-title"))
            .stream().findFirst().map(WebElement::getText).orElse("");
    }

    /**
     * @return {@code true} if the builder, e.g. a series of a chart, can be removed
     * @since 1.4.0
     */
    public boolean isRemovable()
    {
        // A builder that can't be removed has no (or a hidden) remove icon, so don't wait for one.
        return getDriver().findElementsWithoutWaiting(getContainer(), By.className("proj-manag-remove-filter"))
            .stream().anyMatch(WebElement::isDisplayed);
    }

    /**
     * @return {@code true} if the filters can't be changed: no filter can be added and the values are disabled
     * @since 1.3.0-rc-2
     */
    public boolean isReadOnly()
    {
        // The builder is displayed with its filters, so don't wait for elements that a read-only builder doesn't have.
        WebElement builder = getContainer();
        return getDriver().findElementsWithoutWaiting(builder, By.className(ADD_FILTER_CLASS)).isEmpty()
            && getDriver().findElementsWithoutWaiting(builder,
                By.cssSelector(".proj-manag-constraints .proj-manag-constraint-value")).stream()
            .noneMatch(WebElement::isEnabled);
    }

    /**
     * Select from the "+ Add filter" list a work package property that should be filtered.
     *
     * @param filterProperty the technical work package property name that should be filtered.
     * @return the model of a filter.
     */
    public FilterBuilderFilter addFilter(String filterProperty)
    {
        WebElement builder = getContainer();
        Select select = new Select(builder.findElement(By.className(ADD_FILTER_CLASS)));

        try {
            select.selectByValue(filterProperty);
        } catch (NoSuchElementException ignored) {
            // If no such element, we let the code execute, maybe it finds an already added filter and returns it.
        }

        // The last row of the property, a property being able to have several rows.
        WebElement createdFilter = null;
        for (WebElement row : builder.findElements(By.cssSelector(".proj-manag-constraints .proj-manag-constraint"))) {
            if (filterProperty.equals(
                row.findElement(By.className("proj-manag-constraint-name")).getAttribute("value")))
            {
                createdFilter = row;
            }
        }
        if (createdFilter == null) {
            throw new NoSuchElementException("Could not find the added filter.");
        }

        return new FilterBuilderFilter(createdFilter);
    }

    /**
     * @return what the filters select, in plain words
     * @since 1.4.0
     */
    public String getSummary()
    {
        return getContainer().findElement(By.className("proj-manag-filter-summary")).getText();
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
        // The macro editor focuses its first parameter, whose suggestions may be displayed on top of the filters.
        getDriver().executeJavascript("if (document.activeElement) { document.activeElement.blur(); }");
        getDriver().waitUntilCondition(driver -> driver.findElements(By.className("selectize-dropdown")).stream()
            .noneMatch(WebElement::isDisplayed));
        // Only the filters, not the hidden template of a filter. Each removal changes the filters, so they're looked up
        // again after each one.
        By deleteFilter = By.cssSelector(".proj-manag-constraints .proj-manag-delete-filter");
        int count = getContainer().findElements(deleteFilter).size();
        for (int i = 0; i < count; i++) {
            getContainer().findElement(deleteFilter).click();
            int remaining = count - i - 1;
            getDriver().waitUntilCondition(driver -> getContainer().findElements(deleteFilter).size() == remaining);
        }
    }
}
