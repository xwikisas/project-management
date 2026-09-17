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
import org.xwiki.ckeditor.test.po.CKEditor;
import org.xwiki.ckeditor.test.po.MacroDialogEditModal;
import org.xwiki.ckeditor.test.po.MacroDialogSelectModal;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

/**
 * Common behavior of the edit modals of the Project Management macros.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public abstract class AbstractOpenProjectMacroEditModal extends MacroDialogEditModal
{
    private static final String INSTANCE_PARAMETER = "instance";

    private WYSIWYGEditPage editPage;

    protected AbstractOpenProjectMacroEditModal()
    {
    }

    /**
     * Edit the given page with the WYSIWYG editor, insert the given macro and wait for its edit modal to be displayed.
     *
     * @param setup the test setup.
     * @param docRef the page in which the macro will be inserted.
     * @param macroName the pretty name of the macro, as it is displayed in the macro selector.
     * @param expectedMacroCount the number of macros that are expected to be displayed after filtering the macro
     *     selector by {@code macroName}.
     */
    protected AbstractOpenProjectMacroEditModal(TestUtils setup, DocumentReference docRef, String macroName,
        int expectedMacroCount)
    {
        ViewPage page = setup.gotoPage(docRef);
        this.editPage = page.editWYSIWYG();
        new CKEditor("content").waitToLoad();
        MacroDialogSelectModal selectModal = openMacroSelectModal(setup);
        if (!setup.getDriver().hasElement(By.cssSelector(".macro-editor-modal .macro-name"))) {
            selectModal.waitUntilReady();
            selectModal.filterByText(macroName, expectedMacroCount);
            setup.getDriver().findElement(By.cssSelector(".macro-selector-modal .modal-footer .btn-primary")).click();
        }
        waitUntilReady();
    }

    /**
     * @return the WYSIWYG edit page that contains this modal, or {@code null} if the modal was not opened by this page
     *     object.
     */
    public WYSIWYGEditPage getEditPage()
    {
        return this.editPage;
    }

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
        return getDriver().findElementWithoutWaitingWithoutScrolling(By.cssSelector(
            String.format("[class*=-editor-modal] .macro-parameter-field input[name='%s'],select[name='%s']", name,
                name)));
    }

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
     * Select the OpenProject connection that the macro will use.
     *
     * @param connectionId the id of the connection.
     * @return the suggest input of the instance parameter.
     */
    public SuggestInputElement selectInstance(String connectionId)
    {
        SuggestInputElement instanceSuggest = getSuggestInput(INSTANCE_PARAMETER).click().waitForSuggestions();
        instanceSuggest.selectByValue(connectionId);
        return instanceSuggest;
    }

    @Override
    public void clickSubmit()
    {
        // A notification triggered by a previous save may still be displayed on top of the modal footer.
        getDriver().waitUntilElementDisappears(By.className("xnotification"));
        getDriver().findElement(By.cssSelector(".macro-editor-modal .modal-footer .btn-primary")).click();
    }

    private MacroDialogSelectModal openMacroSelectModal(TestUtils setup)
    {
        setup.getDriver().findElement(By.xpath("//a[contains(@class, 'cke_button') and contains(@title, 'Insert')]"))
            .click();
        setup.getDriver().waitUntilElementIsVisible(By.className("cke_panel_frame"));
        WebElement panelIframe = setup.getDriver().findElement(By.className("cke_panel_frame"));
        setup.getDriver().switchTo().frame(panelIframe);
        setup.getDriver()
            .findElement(By.xpath("//span[@class='cke_menubutton_label' and contains(text(), 'Other Macros')]"))
            .click();
        setup.getDriver().switchTo().defaultContent();
        return new MacroDialogSelectModal();
    }
}
