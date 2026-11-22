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
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.BaseElement;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Models a filter (a row: a property, an operator and its values) inside the filter parameter of the OpenProject
 * macro.
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
     * @return the value element of this filter
     */
    /**
     * @return the field holding the value of the filter
     */
    WebElement getValueElement()
    {
        return this.container.findElement(By.cssSelector("input.proj-manag-constraint-value"));
    }

    /**
     * Adds a value to a filter whose values are picked from a list of suggestions, e.g. a list property, which can
     * hold several values.
     *
     * @param value the label of the value that should be selected from the suggestions
     * @return this object
     * @since 1.4.0
     */
    public FilterBuilderFilter setSuggestValue(String value)
    {
        WebElement valElem = getValueElement();
        SuggestInputElement suggest = new SuggestInputElement(valElem);
        // Clicking from the suggest variable throws as exception.
        getDriver().findElementWithoutWaiting(valElem,
            By.xpath("following-sibling::*[contains(@class, 'selectize-control')][1]")).click();
        suggest.waitForSuggestions().selectByVisibleText(value);
        return this;
    }

    /**
     * @return the labels of the values picked from the suggestions, in their order
     * @since 1.4.0
     */
    public List<String> getSuggestValues()
    {
        return new SuggestInputElement(getValueElement()).getSelectedSuggestions().stream()
            .map(SuggestInputElement.SuggestionElement::getLabel).collect(Collectors.toList());
    }

    /**
     * @return the text alternative of the operator of the filter, read by assistive technologies, or an empty string
     *     if it has none
     * @since 1.5.0
     */
    public String getOperatorAccessibleName()
    {
        return getTextAlternative(this.container.findElement(By.className("proj-manag-constraint-operator")));
    }

    /**
     * @return the text alternative of the field in which the user enters the value of the filter (e.g. the text field,
     *     the field in which the values to pick from the suggestions are typed, or the field of the date picker), read
     *     by assistive technologies, or an empty string if it has none
     * @since 1.5.0
     */
    public String getValueAccessibleName()
    {
        // The value can be entered in another field than its input, e.g. a field suggesting values (the input then
        // having the selectized class before XWiki 16, tomselected after) or a date picker (the input being hidden).
        // The field suggesting values isn't checked for visibility: Selectize moves it out of sight once a single value
        // is selected.
        return getTextAlternative(this.container.findElement(By.cssSelector(
            ".proj-manag-constraint-value-container input:not(.selectized, .tomselected, .hidden)")));
    }

    /**
     * The text alternative given by the attributes of a field (its {@code aria-label}, else its title), which is how
     * the filter builder names its fields. It isn't computed by the browser (WebDriver's "Get Computed Label"), which
     * isn't supported by all the drivers that the tests can use.
     */
    private String getTextAlternative(WebElement field)
    {
        String label = field.getAttribute("aria-label");
        if (label == null || label.isEmpty()) {
            label = field.getAttribute("title");
        }
        return label == null ? "" : label;
    }

    /**
     * @param operator the label of the operator to select, e.g. "is not one of"
     * @return this object
     * @since 1.4.0
     */
    public FilterBuilderFilter selectOperator(String operator)
    {
        new Select(this.container.findElement(By.className("proj-manag-constraint-operator")))
            .selectByVisibleText(operator);
        return this;
    }

    /**
     * Remove the filter, with its delete icon, and wait for it to be removed.
     *
     * @since 1.5.0
     */
    public void remove()
    {
        this.container.findElement(By.className("proj-manag-delete-filter")).click();
        getDriver().waitUntilCondition(driver -> {
            try {
                return !this.container.isDisplayed();
            } catch (StaleElementReferenceException e) {
                return true;
            }
        });
    }

    /**
     * Press Enter on the operator of the filter, as a keyboard user choosing an operator.
     *
     * @return this filter
     * @since 1.5.0
     */
    public FilterBuilderFilter pressEnterOnOperator()
    {
        this.container.findElement(By.className("proj-manag-constraint-operator")).sendKeys(Keys.ENTER);
        return this;
    }

    /**
     * Selects an operator the way a script or some assistive technologies do: the browser doesn't report the change
     * as a user action.
     *
     * @param operator the label of the operator to select, e.g. "is not one of"
     * @return this object
     * @since 1.5.0
     */
    public FilterBuilderFilter selectOperatorWithoutUserAction(String operator)
    {
        WebElement select = this.container.findElement(By.className("proj-manag-constraint-operator"));
        getDriver().executeScript("const select = arguments[0];"
            + "select.value = Array.from(select.options).find(option => option.text === arguments[1]).value;"
            + "select.dispatchEvent(new Event('change', {bubbles: true}));", select, operator);
        return this;
    }

    /**
     * Sets the value of a filter typed in a text field.
     *
     * @param value the value that should be set to the input element of the filter
     * @return this object
     * @since 1.4.0
     */
    public FilterBuilderFilter setValue(String value)
    {
        getValueElement().sendKeys(value);
        return this;
    }

    /**
     * Picks the value of a date filter with its date picker: opens it, clicks the given days of the first month it
     * displays (the first and the last day of a range of dates), and applies them.
     *
     * @param days the days of the month to click, e.g. {@code 10, 12} for a range of dates
     * @return this object
     * @since 1.5.0
     */
    public FilterBuilderFilter pickDays(int... days)
    {
        this.container.findElement(By.className("proj-manag-date-val")).click();
        By pickerLocator = By.cssSelector(".daterangepicker");
        getDriver().waitUntilCondition(driver -> getDisplayedDatePicker(pickerLocator) != null);
        WebElement picker = getDisplayedDatePicker(pickerLocator);
        for (int day : days) {
            // The days of the previous and next months, displayed to fill the weeks, have the "off" class.
            picker.findElements(By.cssSelector(".drp-calendar.left td.available:not(.off)")).stream()
                .filter(cell -> cell.getText().equals(String.valueOf(day))).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No day " + day + " in the date picker.")).click();
        }
        picker.findElement(By.className("applyBtn")).click();
        getDriver().waitUntilCondition(driver -> getDisplayedDatePicker(pickerLocator) == null);
        return this;
    }

    /**
     * Press Enter on the date of a date filter, as a keyboard user closing its date picker.
     *
     * @return this object
     * @since 1.5.0
     */
    public FilterBuilderFilter pressEnterOnDate()
    {
        this.container.findElement(By.className("proj-manag-date-val")).sendKeys(Keys.ENTER);
        By pickerLocator = By.cssSelector(".daterangepicker");
        getDriver().waitUntilCondition(driver -> getDisplayedDatePicker(pickerLocator) == null);
        return this;
    }

    private WebElement getDisplayedDatePicker(By pickerLocator)
    {
        // A date picker is created for each date filter, only the open one being displayed.
        return getDriver().findElementsWithoutWaiting(pickerLocator).stream().filter(WebElement::isDisplayed)
            .findFirst().orElse(null);
    }

    /**
     * Replace the value of a text filter and move the focus away from it, so that the change is taken into account.
     *
     * @param value the new value
     * @return this object
     * @since 1.4.0
     */
    public FilterBuilderFilter typeValue(String value)
    {
        WebElement valElem = getValueElement();
        valElem.clear();
        valElem.sendKeys(value, Keys.TAB);
        return this;
    }
}
