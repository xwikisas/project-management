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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
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

    private static final String PRESET_PARAMETER = "presetId";

    private static final String WIDGET_WRAPPER = "[data-cke-widget-wrapper]";

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
        MacroDialogSelectModal selectModal = openMacroSelectModal();
        // CKEditor opens the macro selector, or directly the edit modal of the macro at the caret position.
        By editModal = By.cssSelector(".macro-editor-modal .macro-name");
        By selectorModal = By.cssSelector(".macro-selector-modal");
        setup.getDriver().waitUntilCondition(driver -> isDisplayed(editModal) || isDisplayed(selectorModal));
        if (!isDisplayed(editModal)) {
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
        selectParameterTab(name);
        WebElement parameterInput = getMacroParameterInput(name);
        parameterInput.clear();
        parameterInput.sendKeys(value);
        return this;
    }

    /**
     * @return the filter parameter which has a model of its own.
     */
    public FilterBuilderParameter getFilterBuilder()
    {
        selectParameterTab("filters");
        return new FilterBuilderParameter();
    }

    private boolean isDisplayed(By locator)
    {
        return getDriver().findElementsWithoutWaiting(locator).stream().anyMatch(WebElement::isDisplayed);
    }

    protected WebElement getMacroParameterInput(String name)
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(By.cssSelector(
            String.format("[class*=-editor-modal] .macro-parameter-field input[name='%s'],select[name='%s']", name,
                name)));
    }

    /**
     * @param name the technical name of the parameter.
     * @return {@code true} if the parameter is displayed in the modal, {@code false} if it is hidden or missing.
     * @since 1.3.0-rc-2
     */
    public boolean isMacroParameterDisplayed(String name)
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector(
            String.format("[class*=-editor-modal] .macro-parameter-field input[name='%s'],select[name='%s']", name,
                name))).stream().anyMatch(WebElement::isDisplayed);
    }

    /**
     * @return {@code true} if the modal displays a filter builder for the filters parameter.
     * @since 1.3.0-rc-2
     */
    public boolean hasFilterBuilder()
    {
        return getDriver()
            .findElementsWithoutWaiting(By.cssSelector(".macro-editor-modal .proj-manag-constraint-builder")).stream()
            .anyMatch(WebElement::isDisplayed);
    }

    /**
     * Display all the parameters: expand every collapsed "More" link of the modal, the one of the parameter list and
     * those of the parameter groups, whose order depends on the XWiki version.
     */
    public void clickMore()
    {
        // An open suggest dropdown (e.g. the one of the project) or a notification can cover the links.
        closeSuggestDropdowns();
        getDriver().waitUntilElementDisappears(By.className("xnotification"));
        By collapsedMore = By.xpath("//*[contains(@class, '-editor-modal')]//li[contains(@class, 'more')]"
            + "[.//*[contains(@class, 'arrow-right')]]");
        // Expanding a link can reveal other collapsed ones, inside the parameters it displays.
        for (int i = 0; i < 10; i++) {
            List<WebElement> links = getDriver().findElementsWithoutWaiting(collapsedMore).stream()
                .filter(WebElement::isDisplayed).collect(Collectors.toList());
            if (links.isEmpty()) {
                break;
            }
            links.get(0).click();
        }
        scrollToFooter();
    }

    /**
     * @param name the technical name of a parameter that is displayed using a selectized input.
     * @return the selectized input of the given parameter name.
     */
    public SuggestInputElement getSuggestInput(String name)
    {
        WebElement input = getMacroParameterInput(name);
        SuggestInputElement suggestInput = new SuggestInputElement(input);
        // The parameters of the modal can be out of view (e.g. above the viewport after scrolling to the footer), and
        // SuggestInputElement#click moves the mouse to its selectize container without scrolling to it.
        getDriver().scrollTo(
            input.findElement(By.xpath("following-sibling::*[contains(@class, 'selectize-control')][1]")));
        return suggestInput;
    }

    /**
     * Select the OpenProject connection that the macro will use.
     *
     * @param connectionId the id of the connection, or an empty string for the default connection
     * @return this object
     */
    public AbstractOpenProjectMacroEditModal selectInstance(String connectionId)
    {
        closeSuggestDropdowns();
        // The instance parameter is in the Connection tab of the macros having parameter groups.
        selectParameterTab("connection");
        WebElement input = getMacroParameterInput(INSTANCE_PARAMETER);
        // It's the last parameter, so it can be out of view.
        getDriver().scrollTo(input.findElement(By.xpath("ancestor::*[contains(@class, 'macro-parameter')][1]")));
        new Select(input).selectByValue(connectionId);
        return this;
    }

    /**
     * @return the label of the selected OpenProject connection, e.g. the default connection
     * @since 1.4.0
     */
    public String getSelectedInstance()
    {
        selectParameterTab("connection");
        return new Select(getMacroParameterInput(INSTANCE_PARAMETER)).getFirstSelectedOption().getText().trim();
    }

    /**
     * Parameters that provide the same feature (e.g. the filters and the filter preset) are displayed as tabs, only
     * one of them being visible at a time. Select the tab of the given parameter, if it has one.
     *
     * @param name the technical name of the parameter.
     * @since 1.3.0-rc-15
     */
    public void selectParameterTab(String name)
    {
        String tabPanelId = "macroParameterTreeNode-" + name;
        getDriver().findElementsWithoutWaiting(By.cssSelector(
            String.format(".macro-editor-modal a[role='tab'][aria-controls='%s']", tabPanelId))).stream()
            .filter(WebElement::isDisplayed).findFirst().ifPresent(tab -> {
                closeSuggestDropdowns();
                tab.click();
                getDriver().waitUntilCondition(
                    driver -> driver.findElement(By.id(tabPanelId)).getAttribute("class").contains("active"));
            });
    }

    /**
     * The first suggest input of the modal (e.g. the project) can be focused when the modal is opened, and its
     * dropdown can then cover the other parameters. Close it by clicking outside it.
     */
    private void closeSuggestDropdowns()
    {
        By openDropdown = By.cssSelector(".selectize-dropdown.active");
        if (getDriver().findElementsWithoutWaiting(openDropdown).stream().anyMatch(WebElement::isDisplayed)) {
            getDriver().findElement(By.cssSelector("[class*=-editor-modal] .modal-header")).click();
            getDriver().waitUntilCondition(driver -> getDriver().findElementsWithoutWaiting(openDropdown).stream()
                .noneMatch(WebElement::isDisplayed));
        }
    }

    /**
     * @return the names of the filter presets that can be selected
     * @since 1.3.0-rc-2
     */
    public List<String> getPresetOptions()
    {
        SuggestInputElement presetSuggest = openPresetSuggestions();
        List<String> options = presetSuggest.getSuggestions().stream()
            .map(suggestion -> suggestion.getLabel().trim()).collect(Collectors.toList());
        presetSuggest.hideSuggestions();
        return options;
    }

    /**
     * Select the filter preset that the macro will use.
     *
     * @param presetName the name of the preset
     * @return this object
     * @since 1.3.0-rc-2
     */
    public AbstractOpenProjectMacroEditModal selectPreset(String presetName)
    {
        // The option labels are prefixed, so they can't be matched exactly.
        openPresetSuggestions().getSuggestions().stream()
            .filter(suggestion -> presetName.equals(suggestion.getLabel().trim())).findFirst()
            .orElseThrow(() -> new NoSuchElementException("No filter preset named " + presetName)).select();
        return this;
    }

    private SuggestInputElement openPresetSuggestions()
    {
        selectParameterTab(PRESET_PARAMETER);
        closeSuggestDropdowns();
        // The preset parameter can be at the bottom of the modal, where its dropdown can't open in view.
        WebElement input = getMacroParameterInput(PRESET_PARAMETER);
        getDriver().scrollTo(input.findElement(By.xpath("ancestor::*[contains(@class, 'macro-parameter')][1]")));
        return new SuggestInputElement(input).click().waitForSuggestions();
    }

    /**
     * @return the name of the selected filter preset, or an empty string if no preset is selected
     * @since 1.3.0-rc-2
     */
    public String getSelectedPreset()
    {
        selectParameterTab(PRESET_PARAMETER);
        return getSuggestInput(PRESET_PARAMETER).getSelectedSuggestions().stream().findFirst()
            .map(suggestion -> suggestion.getLabel().trim()).orElse("");
    }

    /**
     * Set a random value for the {@code id} parameter. The Live Data page object needs the macro to have an id.
     *
     * @return this object.
     */
    public AbstractOpenProjectMacroEditModal setRandomId()
    {
        getDriver().executeJavascript("arguments[0].value = arguments[1];", getMacroParameterInput("id"),
            String.valueOf(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE)));
        return this;
    }

    /**
     * Scroll to the footer of the modal, so that all the parameters are displayed.
     */
    public void scrollToFooter()
    {
        getDriver().scrollTo(getDriver().findElement(By.cssSelector(".macro-editor-modal .modal-footer")));
    }

    @Override
    public void clickSubmit()
    {
        // A notification triggered by a previous save may still be displayed on top of the modal footer.
        getDriver().waitUntilElementDisappears(By.className("xnotification"));
        // Submitting the modal makes CKEditor refresh the edited content (render the macros again), replacing the
        // edited document. The macro widgets are initialized at the end of the refresh, and saving before that would
        // save their output instead of the macro calls, so we wait for the new document and for its widgets to be
        // initialized (their wrapper is created before, and gets the widget id when the widget is initialized).
        CKEditor editor = new CKEditor("content");
        WebElement[] editedBody = new WebElement[1];
        editor.executeOnIframe(() -> editedBody[0] = getDriver().findElement(By.tagName("body")));
        getDriver().findElement(By.cssSelector(".macro-editor-modal .modal-footer .btn-primary")).click();
        editor.executeOnIframe(() -> {
            getDriver().waitUntilCondition(ExpectedConditions.stalenessOf(editedBody[0]));
            getDriver().waitUntilCondition(driver -> {
                List<WebElement> wrappers = getDriver().findElementsWithoutWaiting(By.cssSelector(WIDGET_WRAPPER));
                return !wrappers.isEmpty()
                    && wrappers.stream().allMatch(wrapper -> wrapper.getAttribute("data-cke-widget-id") != null);
            });
        });
    }

    /**
     * Close this modal without applying its changes, then open the edit modal of the same macro again, in the same
     * editor.
     *
     * @return this object, modelling the modal opened again
     * @since 1.4.0
     */
    public AbstractOpenProjectMacroEditModal cancelAndEditAgain()
    {
        closeSuggestDropdowns();
        getDriver().findElement(By.cssSelector(".macro-editor-modal .modal-header .close")).click();
        getDriver().waitUntilElementDisappears(By.cssSelector(".macro-editor-modal"));
        // The edited macro stays selected in the editor, so inserting a macro opens its edit modal.
        openMacroSelectModal();
        waitUntilReady();
        return this;
    }

    private MacroDialogSelectModal openMacroSelectModal()
    {
        getDriver().findElement(By.xpath("//a[contains(@class, 'cke_button') and contains(@title, 'Insert')]"))
            .click();
        getDriver().waitUntilElementIsVisible(By.className("cke_panel_frame"));
        WebElement panelIframe = getDriver().findElement(By.className("cke_panel_frame"));
        getDriver().switchTo().frame(panelIframe);
        getDriver()
            .findElement(By.xpath("//span[@class='cke_menubutton_label' and contains(text(), 'Other Macros')]"))
            .click();
        getDriver().switchTo().defaultContent();
        return new MacroDialogSelectModal();
    }
}
