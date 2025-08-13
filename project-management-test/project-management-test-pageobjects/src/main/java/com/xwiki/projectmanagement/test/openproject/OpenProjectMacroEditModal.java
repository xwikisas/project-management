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
import org.xwiki.ckeditor.test.po.MacroDialogEditModal;
import org.xwiki.test.ui.po.SuggestInputElement;

public class OpenProjectMacroEditModal extends MacroDialogEditModal
{
    public MacroDialogEditModal setMacroParameter(String name, CharSequence... value)
    {
        WebElement parameterInput = getMacroParameterInput(name);
        parameterInput.clear();
        parameterInput.sendKeys(value);
        return this;
    }

    public String getMacroParameter(String name)
    {
        return getMacroParameterInput(name).getDomProperty("value");
    }

    public WebElement getMacroParameterInput(String name)
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(
            // We match *-editor-modal so the page object can be used both in Dashboard and CKEditor tests.
            By.cssSelector(
                String.format("[class*=-editor-modal] .macro-parameter-field input[name='%s'],input[name='%s']",
                    name, name)));
    }

    public void clickMore()
    {
        getDriver().findElement(By.cssSelector("li.more")).click();
    }

    public SuggestInputElement getSuggestInput(String name)
    {
        return new SuggestInputElement(getMacroParameterInput(name));
    }

    public FilterBuilderParameter getFilterBuilder() {
        return new FilterBuilderParameter();
    }

}
