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
import org.openqa.selenium.support.ui.Select;
import org.xwiki.ckeditor.test.po.MacroDialogEditModal;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Models the edit modal of the Open Project macro.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class OpenProjectMacroEditModal extends MacroDialogEditModal
{
    /**
     * Set the value of a macro parameter.
     *
     * @param name the technical name of the parameter.
     * @param value the value that should be set.
     * @return this object.
     */
    public MacroDialogEditModal setMacroParameter(String name, CharSequence... value)
    {
        WebElement parameterInput = getMacroParameterInput(name);
        parameterInput.clear();
        parameterInput.sendKeys(value);
        return this;
    }

    /**
     * @param name the technical name of the parameter.
     * @return the input value of the parameter.
     */
    public String getMacroParameter(String name)
    {
        return getMacroParameterInput(name).getDomProperty("value");
    }

    /**
     * @param name the technical name of the parameter.
     * @return the input element of the parameter.
     */
    public WebElement getMacroParameterInput(String name)
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(
            // We match *-editor-modal so the page object can be used both in Dashboard and CKEditor tests.
            By.cssSelector(
                String.format("[class*=-editor-modal] .macro-parameter-field input[name='%s'],select[name='%s']",
                    name, name)));
    }

    /**
     * Click the "More" section of the modal that reveals all the other parameters.
     */
    public void clickMore()
    {
        getDriver().findElement(By.cssSelector("li.more")).click();
        getDriver().scrollTo(getDriver().findElement(By.cssSelector(".macro-editor-modal .modal-footer")));
    }

    /**
     * @param name the technical name of a parameter that is displayed using a selectized input.
     * @return the selectized input of the given parameter name.
     */
    public SuggestInputElement getSuggestInput(String name)
    {
        return new SuggestInputElement(getMacroParameterInput(name));
    }

    /**
     * Select a displayer from the list of available project management displayers.
     *
     * @param displayerName the pretty name of the displayer.
     * @return this object.
     */
    public OpenProjectMacroEditModal selectDisplayer(String displayerName)
    {
        WebElement input = getDriver().findElement(By.xpath("//select[@name='workItemsDisplayer']"));
        Select select = new Select(input);
        input.click();
        select.selectByVisibleText(displayerName);
        return this;
    }

    @Override
    public void clickSubmit()
    {
        this.getDriver().findElement(By.cssSelector(".macro-editor-modal .modal-footer .btn-primary")).click();
    }

    /**
     * @return the filter parameter which has a model of its own.
     */
    public FilterBuilderParameter getFilterBuilder()
    {
        return new FilterBuilderParameter();
    }
}
