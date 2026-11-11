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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Models the edit modal of the OpenProject - Chart macro.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public class OpenProjectChartMacroEditModal extends AbstractOpenProjectMacroEditModal
{
    private static final String MACRO_NAME = "OpenProject - Chart";

    private static final int EXPECTED_MACRO_COUNT = 1;

    private static final String TYPE_PARAMETER = "type";

    private static final String PROPERTY_PARAMETER = "property";

    public OpenProjectChartMacroEditModal()
    {
    }

    /**
     * Insert an OpenProject - Chart macro in the given page and wait for its edit modal to be displayed.
     *
     * @param setup the test setup.
     * @param docRef the page in which the macro will be inserted.
     */
    public OpenProjectChartMacroEditModal(TestUtils setup, DocumentReference docRef)
    {
        super(setup, docRef, MACRO_NAME, EXPECTED_MACRO_COUNT);
    }

    public String getChartType()
    {
        return getTypeSelect().getFirstSelectedOption().getDomAttribute("value");
    }

    public OpenProjectChartMacroEditModal setChartType(String type)
    {
        getTypeSelect().selectByValue(type);
        return this;
    }

    /**
     * Select the property whose values will be grouped by the chart.
     *
     * @param property the technical name of the property.
     * @return this object.
     */
    public OpenProjectChartMacroEditModal setProperty(String property)
    {
        getSuggestInput(PROPERTY_PARAMETER).click().waitForSuggestions().selectByValue(property);
        return this;
    }

    /**
     * @param option the technical name of an option of the Chart Options field, e.g. {@code period}
     * @return the choices that the option proposes, as displayed
     * @since 1.4.0
     */
    public List<String> getChartOptionChoices(String option)
    {
        WebElement input = getChartOptionInput(option);
        SuggestInputElement suggestInput = new SuggestInputElement(input);
        getDriver().scrollTo(getSelectizeControl(input));
        List<String> choices = suggestInput.click().waitForSuggestions().getSuggestions().stream()
            .map(SuggestInputElement.SuggestionElement::getLabel).collect(Collectors.toList());
        suggestInput.hideSuggestions();
        return choices;
    }

    /**
     * @param option the technical name of an option of the Chart Options field, e.g. {@code period}
     * @return the selected value of the option, as displayed, or an empty string if none is selected
     * @since 1.4.0
     */
    public String getChartOptionSelectedValue(String option)
    {
        WebElement input = getChartOptionInput(option);
        // Wait for the selection to be initialized.
        new SuggestInputElement(input);
        return getSelectizeControl(input).findElements(By.cssSelector(".selectize-input > .item")).stream()
            .map(item -> item.getAttribute("textContent").trim()).findFirst().orElse("");
    }

    private WebElement getChartOptionInput(String option)
    {
        selectParameterTab("display");
        // The chart options are loaded once the modal is displayed, and again when the chart type changes.
        By optionInput = By.cssSelector(
            String.format(".macro-editor-modal .proj-manag-chart-type-param input[name='%s'].selectized", option));
        getDriver().waitUntilCondition(driver -> !getDriver().findElementsWithoutWaiting(optionInput).isEmpty());
        return getDriver().findElementWithoutWaiting(optionInput);
    }

    private WebElement getSelectizeControl(WebElement input)
    {
        return input.findElement(By.xpath("following-sibling::*[contains(@class, 'selectize-control')][1]"));
    }

    private Select getTypeSelect()
    {
        // We don't use org.xwiki.test.ui.po.Select since it presses Escape after selecting an option, which closes
        // the macro edit modal.
        return new Select(getMacroParameterInput(TYPE_PARAMETER));
    }
}
