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
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Models a filter inside the filter parameter of the OpenProject macro.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class FilterBuilderFilter extends BaseElement
{
    private final WebElement container;

    /**
     * @param container the filter wrapper that contains all the data.
     */
    public FilterBuilderFilter(WebElement container)
    {
        this.container = container;
    }

    /**
     * Adds a constraint to this filter.
     *
     * @return this object.
     */
    public FilterBuilderFilter addConstraint()
    {
        container.findElement(By.className("proj-manag-add-constraint")).click();
        return this;
    }

    /**
     * @param index the index of the constraint element present in this filter, starting at 1
     * @return the value element of the constraint identified by the index
     */
    private WebElement getValueElement(int index)
    {
        return container
            .findElement(By.xpath(String.format("(div[@class='proj-manag-filter-container'])[%d]", index)))
            .findElement(By.cssSelector("input.proj-manag-constraint-value"));
    }

    /**
     * Sets the value to a selectized constraint value.
     *
     * @param index the index of the constraint element present in this filter.
     * @param value the label of the value that should be selected from the selectized list.
     * @return this object.
     */
    public FilterBuilderFilter setSuggestValue(int index, String value)
    {
        WebElement valElem = getValueElement(index);
        SuggestInputElement suggest = new SuggestInputElement(valElem);
        // Clicking from the suggest variable throws as exception.
        getDriver().findElementWithoutWaiting(valElem,
            By.xpath("following-sibling::*[contains(@class, 'selectize-control')][1]")).click();
        suggest.waitForSuggestions().selectByVisibleText(value);
        return this;
    }

    /**
     * Sets the value to a constraint value.
     *
     * @param index the index of the constraint element present in this filter.
     * @param value the value that should be set to the input element of the constraint.
     * @return this object.
     */
    public FilterBuilderFilter setValue(int index, String value)
    {
        WebElement valElem = getValueElement(index);
        valElem.sendKeys(value);
        return this;
    }

    /**
     * Replace the value of a text constraint and move the focus away from it, so that the change is taken into account.
     *
     * @param index the index of the constraint, starting at 1
     * @param value the new value
     * @return this object
     * @since 1.3.0-rc-2
     */
    public FilterBuilderFilter typeValue(int index, String value)
    {
        WebElement valElem = getValueElement(index);
        valElem.clear();
        valElem.sendKeys(value, Keys.TAB);
        return this;
    }
}
