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

import org.apache.commons.lang3.NotImplementedException;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.BaseElement;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Models a filter inside the filter parameter of the open project macro.
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
     * @return the property on which this filter filters on.
     */
    public String getPropertyName()
    {
        return container.findElement(By.className("proj-manag-constraint-title")).getText();
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
     * @param index the index of the constraint that needs to be retrieved.
     * @return a model of the constraint identified by the index.
     */
    public WebElement getConstraint(int index)
    {
        return container
            .findElement(By.xpath(String.format("(div[@class='proj-manag-filter-container'])[%d]", index)));
    }

    /**
     * @param index the index of the constraint that should have its operator updated.
     * @param operator the pretty name of the operator that should be set to the constraint identified by the
     *     index.
     * @return this object.
     */
    public FilterBuilderFilter setOperator(int index, String operator)
    {
        WebElement operatorElem = getConstraint(index).findElement(By.className("proj-manag-constraint-operator"));
        Select select = new Select(operatorElem);
        select.selectByVisibleText(operator);
        return this;
    }

    /**
     * @param index the index of the constraint element present in this filter.
     * @return the value element of the constraint identified by the index.
     */
    public WebElement getValueElement(int index)
    {
        return getConstraint(index).findElement(By.cssSelector("input.proj-manag-constraint-value"));
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
     * Sets the value to a date constraint value.
     *
     * @param index the index of the constraint element present in this filter.
     * @param value the value that should be set to the date input element of the constraint.
     * @return this object.
     */
    public FilterBuilderFilter setDateValue(int index, String value)
    {
        // TODO: Implement once parent is upgraded to 15.10 and use DateRangePicker PO.
        throw new NotImplementedException();
    }

    /**
     * @return the list of constraints set in this filter.
     */
    public List<FilterBuilderConstraint> getConstraints()
    {
        return container
            .findElements(By.className("proj-manag-filter-container"))
            .stream()
            .map(FilterBuilderConstraint::new)
            .collect(Collectors.toList());
    }
}
