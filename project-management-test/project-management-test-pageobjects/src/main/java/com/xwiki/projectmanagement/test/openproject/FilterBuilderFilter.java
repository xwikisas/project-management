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

public class FilterBuilderFilter extends BaseElement
{
    private final WebElement container;

    public FilterBuilderFilter(WebElement container)
    {
        this.container = container;
    }

    public String getPropertyName()
    {
        return container.findElement(By.className("proj-manag-constraint-title")).getText();
    }

    public FilterBuilderFilter addConstraint()
    {
        container.findElement(By.className("proj-manag-add-constraint")).click();
        return this;
    }

    public WebElement getConstraint(int index)
    {
        return container
            .findElement(By.xpath(String.format("(div[@class='proj-manag-filter-container'])[%d]", index)));
    }

    public FilterBuilderFilter setOperator(int index, String operator)
    {
        WebElement operatorElem = getConstraint(index).findElement(By.className("proj-manag-constraint-operator"));
        Select select = new Select(operatorElem);
        select.selectByVisibleText(operator);
        return this;
    }

    public WebElement getValueElement(int index)
    {
        return getConstraint(index).findElement(By.cssSelector("input.proj-manag-constraint-value"));
    }

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

    public FilterBuilderFilter setValue(int index, String value)
    {
        WebElement valElem = getValueElement(index);
        valElem.sendKeys(value);
        return this;
    }

    public FilterBuilderFilter setDateValue(int index, String value)
    {
        // TODO: Implement once parent is upgraded to 15.10 and use DateRangePicker PO.
        throw new NotImplementedException();
    }

    public List<FilterBuilderConstraint> getConstraints()
    {
        return container
            .findElements(By.className("proj-manag-filter-container"))
            .stream()
            .map(FilterBuilderConstraint::new)
            .collect(Collectors.toList());
    }
}
