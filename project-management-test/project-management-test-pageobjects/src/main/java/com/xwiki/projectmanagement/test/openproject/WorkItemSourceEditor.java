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

import java.util.ArrayList;
import java.util.Arrays;
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
 * Models the field of the macro editor choosing how a work item macro selects its work items ("Select the work
 * packages by"): the matching filters, specific work packages, an OpenProject link, or the advanced way of the macros
 * using another source.
 *
 * @version $Id$
 * @since 1.5.0
 */
public class WorkItemSourceEditor extends BaseElement
{
    /**
     * The ways to select the work items.
     */
    public enum Way
    {
        /**
         * The work items matching filters, written as a query.
         */
        MATCHING_FILTERS("query"),

        /**
         * Specific work items, picked one by one.
         */
        SPECIFIC("list"),

        /**
         * The work items of a link to the tool, e.g. an OpenProject work package list or saved query.
         */
        LINK("url"),

        /**
         * Another source, only offered when the macro uses it.
         */
        ADVANCED("advanced");

        private final String value;

        Way(String value)
        {
            this.value = value;
        }

        private static Way fromValue(String value)
        {
            return Arrays.stream(values()).filter(way -> way.value.equals(value)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown way: " + value));
        }
    }

    private static final String WIDGET = ".macro-editor-modal .pm-work-item-source";

    private static final By WAY_SELECT = By.className("pm-source-way-select");

    // The suggestions of the picker, displayed in the page body: Selectize (XWiki 15.10) or Tom Select (XWiki 18.x).
    private static final By SUGGESTIONS = By.cssSelector(".selectize-dropdown, .ts-dropdown");

    private static final String PICKER = "pm-workitem-picker";

    private static final String PICKER_ITEM = PICKER + "-item";

    private static final String HIDDEN = "pm-hidden";

    private static final String CLASS = "class";

    private static final String VALUE = "value";

    private static final String DATA_VALUE = "data-value";

    private static final By QUERY_LOADING = By.cssSelector(".macro-editor-modal .proj-manag-query-loading");

    private static final By TEXT_TOGGLE = By.className("pm-source-toggle-text");

    private static final By QUERY_TEXT = By.className("pm-source-query-text");

    private static final By URL_INPUT = By.className("pm-source-url-input");

    private static final By IDENTIFIER_NOTICE = By.className("pm-source-identifier");

    private static final By LOCKED_NOTICE = By.className("pm-source-locked");

    /**
     * Wait for the field to be ready: the work package picker displayed, which the widget mounts while it selects
     * the way of the edited macro.
     */
    public WorkItemSourceEditor()
    {
        getDriver().waitUntilCondition(driver -> {
            List<WebElement> widgets = getDriver().findElementsWithoutWaiting(By.cssSelector(WIDGET));
            return !widgets.isEmpty()
                && !getDriver().findElementsWithoutWaiting(widgets.get(0), By.className(PICKER)).isEmpty();
        });
    }

    private WebElement getWidget()
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(By.cssSelector(WIDGET));
    }

    private WebElement getEditor(Way way)
    {
        return getWidget().findElement(By.cssSelector(String.format(".pm-source-editor[data-way='%s']", way.value)));
    }

    private static boolean isShown(WebElement element)
    {
        return !element.getAttribute(CLASS).contains(HIDDEN);
    }

    /**
     * Select how the macro selects its work items, and wait for the editor of this way to be displayed.
     *
     * @param way the way to select
     * @return this object
     */
    public WorkItemSourceEditor selectWay(Way way)
    {
        WebElement select = getWidget().findElement(WAY_SELECT);
        getDriver().scrollTo(select);
        new Select(select).selectByValue(way.value);
        getDriver().waitUntilCondition(driver -> isShown(getEditor(way)));
        return this;
    }

    /**
     * @return the selected way
     */
    public Way getWay()
    {
        return Way.fromValue(new Select(getWidget().findElement(WAY_SELECT)).getFirstSelectedOption()
            .getAttribute(VALUE));
    }

    /**
     * @return the ways that can be selected, in the order they are displayed
     */
    public List<Way> getWays()
    {
        return new Select(getWidget().findElement(WAY_SELECT)).getOptions().stream()
            .map(option -> Way.fromValue(option.getAttribute(VALUE))).collect(Collectors.toList());
    }

    /**
     * Pick specific work packages: search each given text (a subject, an id or a key) and pick the first work
     * package found, or the typed id or key itself.
     *
     * @param searches the texts to search, one per work package
     * @return this object
     */
    public WorkItemSourceEditor pickWorkPackages(String... searches)
    {
        for (String search : searches) {
            int count = getPickedCount();
            WebElement input = getEditor(Way.SPECIFIC).findElement(By.className(PICKER + "-input"));
            getDriver().scrollTo(getEditor(Way.SPECIFIC));
            SuggestInputElement picker = new SuggestInputElement(input);
            picker.sendKeys(search).waitForSuggestions();
            getDriver().waitUntilCondition(driver -> !picker.getSuggestions().isEmpty());
            picker.getSuggestions().get(0).select();
            getDriver().waitUntilCondition(driver -> getPickedCount() == count + 1);
        }
        return this;
    }

    /**
     * Type the id or key of a work package in the picker and press Enter, which picks the typed id or key rather than
     * the first work package found by the search.
     *
     * @param id the id or key to type
     * @return this object
     */
    public WorkItemSourceEditor typeAndEnterWorkPackage(String id)
    {
        int count = getPickedCount();
        WebElement input = getEditor(Way.SPECIFIC).findElement(By.className(PICKER + "-input"));
        getDriver().scrollTo(getEditor(Way.SPECIFIC));
        SuggestInputElement picker = new SuggestInputElement(input);
        picker.sendKeys(id).waitForSuggestions();
        getDriver().waitUntilCondition(driver -> !picker.getSuggestions().isEmpty());
        picker.sendKeys(Keys.ENTER);
        getDriver().waitUntilCondition(driver -> getPickedCount() == count + 1);
        // The suggestions are closed once the work package is picked.
        getDriver().waitUntilCondition(driver -> getDriver().findElementsWithoutWaiting(SUGGESTIONS).stream()
            .noneMatch(WebElement::isDisplayed));
        return this;
    }

    private int getPickedCount()
    {
        return getEditor(Way.SPECIFIC).findElements(By.className(PICKER_ITEM)).size();
    }

    /**
     * @return the labels of the picked work packages, e.g. {@code #12 Set date and location of conference}, once they
     *     are loaded; the work packages that can't be found are labelled by their id or key
     */
    public List<String> getPickedWorkPackages()
    {
        By items = By.className(PICKER_ITEM);
        By label = By.className(PICKER + "-label");
        List<List<String>> labels = new ArrayList<>(List.of(List.of()));
        // A work package is labelled by its id until it's found, and its chip is then replaced.
        getDriver().waitUntilCondition(driver -> {
            try {
                List<String> current = new ArrayList<>();
                for (WebElement item : getEditor(Way.SPECIFIC).findElements(items)) {
                    String text = item.findElement(label).getText().trim();
                    if (!item.getAttribute(CLASS).contains(PICKER + "-missing")
                        && item.getAttribute(DATA_VALUE).equals(text)) {
                        return false;
                    }
                    current.add(text);
                }
                labels.set(0, current);
                return true;
            } catch (StaleElementReferenceException e) {
                return false;
            }
        });
        return labels.get(0);
    }

    /**
     * @return the ids or keys of the picked work packages that can't be found
     */
    public List<String> getMissingWorkPackages()
    {
        getPickedWorkPackages();
        return getEditor(Way.SPECIFIC).findElements(By.className(PICKER + "-missing")).stream()
            .map(item -> item.getAttribute(DATA_VALUE)).collect(Collectors.toList());
    }

    /**
     * Replace the picked work packages by the given list of ids or keys, typed (or pasted) in the text area of "Edit
     * as text", and go back to the picker.
     *
     * @param ids the ids or keys, separated by commas, spaces or new lines
     * @return this object
     */
    public WorkItemSourceEditor pasteIds(String ids)
    {
        WebElement textArea = editAsText();
        textArea.clear();
        textArea.sendKeys(ids);
        editWithPicker();
        return this;
    }

    /**
     * @return the ids or keys of the picked work packages, as shown by "Edit as text" (the picker being displayed
     *     again afterwards)
     */
    public String getIdsAsText()
    {
        String text = editAsText().getAttribute(VALUE);
        editWithPicker();
        return text;
    }

    private WebElement getPasteToggle()
    {
        return getEditor(Way.SPECIFIC).findElement(By.className(PICKER + "-pasteToggle"));
    }

    private WebElement getIdsTextArea()
    {
        return getEditor(Way.SPECIFIC).findElement(By.cssSelector("." + PICKER + "-paste textarea"));
    }

    private WebElement editAsText()
    {
        WebElement textArea = getIdsTextArea();
        if (!textArea.isDisplayed()) {
            WebElement toggle = getPasteToggle();
            getDriver().scrollTo(toggle);
            toggle.click();
            getDriver().waitUntilCondition(driver -> textArea.isDisplayed());
        }
        return textArea;
    }

    private void editWithPicker()
    {
        WebElement textArea = getIdsTextArea();
        if (textArea.isDisplayed()) {
            WebElement toggle = getPasteToggle();
            getDriver().scrollTo(toggle);
            toggle.click();
            getDriver().waitUntilCondition(driver -> !textArea.isDisplayed());
        }
    }

    /**
     * Type the link whose work packages the macro displays, replacing the previous one.
     *
     * @param url the link, e.g. of an OpenProject work package list
     * @return this object
     */
    public WorkItemSourceEditor setLink(String url)
    {
        WebElement input = getEditor(Way.LINK).findElement(URL_INPUT);
        getDriver().scrollTo(input);
        // Deleting the link clears its description.
        getDriver().executeJavascript("arguments[0].value = '';"
            + " arguments[0].dispatchEvent(new Event('input', {bubbles: true}));", input);
        getDriver().waitUntilCondition(driver -> getLinkDescriptionContainer().getText().trim().isEmpty());
        input.sendKeys(url);
        return this;
    }

    /**
     * @return the link whose work packages the macro displays
     */
    public String getLink()
    {
        return getEditor(Way.LINK).findElement(URL_INPUT).getAttribute(VALUE);
    }

    private WebElement getLinkDescriptionContainer()
    {
        return getEditor(Way.LINK).findElement(By.className("pm-source-url-description"));
    }

    /**
     * Wait for the description of the link, e.g. the name of its saved query, or a warning when the link is of
     * another OpenProject server.
     *
     * @return the description of the link
     */
    public String getLinkDescription()
    {
        WebElement container = getLinkDescriptionContainer();
        getDriver().waitUntilCondition(driver -> !container.getText().trim().isEmpty());
        return container.getText().trim();
    }

    /**
     * @return {@code true} if the description of the link is a warning, e.g. for the link of another server
     */
    public boolean isLinkDescriptionWarning()
    {
        getLinkDescription();
        return !getDriver().findElementsWithoutWaiting(getLinkDescriptionContainer(),
            By.className("warningmessage")).isEmpty();
    }

    /**
     * Edit the query of the matching filters as text, replacing it.
     *
     * @param query the query, e.g. {@code status = 1 AND type = 2}
     * @return this object
     */
    public WorkItemSourceEditor editQueryAsText(String query)
    {
        WebElement textArea = showQueryText();
        textArea.clear();
        textArea.sendKeys(query);
        return this;
    }

    /**
     * Go back to the filters from the query edited as text, and wait for the filters to show the query.
     *
     * @return this object
     */
    public WorkItemSourceEditor editQueryWithFilters()
    {
        WebElement editor = getEditor(Way.MATCHING_FILTERS);
        WebElement text = editor.findElement(QUERY_TEXT);
        if (isShown(text)) {
            WebElement toggle = editor.findElement(TEXT_TOGGLE);
            getDriver().scrollTo(toggle);
            toggle.click();
            getDriver().waitUntilCondition(driver -> !isShown(text));
        }
        getDriver().waitUntilElementDisappears(QUERY_LOADING);
        return this;
    }

    /**
     * @return the query of the matching filters, as text
     */
    public String getQueryText()
    {
        return showQueryText().getAttribute(VALUE);
    }

    private WebElement showQueryText()
    {
        WebElement editor = getEditor(Way.MATCHING_FILTERS);
        WebElement text = editor.findElement(QUERY_TEXT);
        if (!isShown(text)) {
            WebElement toggle = editor.findElement(TEXT_TOGGLE);
            getDriver().scrollTo(toggle);
            toggle.click();
            getDriver().waitUntilCondition(driver -> isShown(text));
        }
        WebElement textArea = text.findElement(By.tagName("textarea"));
        getDriver().scrollTo(textArea);
        return textArea;
    }

    /**
     * @return the filters of the matching filters way, once they show the query of the macro
     */
    public FilterBuilderParameter getFilters()
    {
        getDriver().waitUntilElementDisappears(QUERY_LOADING);
        return new FilterBuilderParameter(getEditor(Way.MATCHING_FILTERS)
            .findElement(By.cssSelector("[data-builder-role='query'] .proj-manag-constraint-builder")));
    }

    /**
     * @return what the matching filters select when there is no filter, e.g. all the work packages the user can see
     */
    public String getNoFilterHint()
    {
        return getFilters().getSummary();
    }

    /**
     * @return the notice telling that the macro uses the deprecated parameter listing its work packages, or an empty
     *     string if it doesn't
     */
    public String getDeprecationNotice()
    {
        WebElement notice = getWidget().findElement(IDENTIFIER_NOTICE);
        return isShown(notice) ? notice.findElement(By.className("pm-source-identifier-message")).getText().trim()
            : "";
    }

    /**
     * Convert the deprecated parameter listing the work packages of the macro (work packages or a link) to the way
     * replacing it, which gets selected.
     *
     * @return this object
     */
    public WorkItemSourceEditor convertIdentifier()
    {
        WebElement notice = getWidget().findElement(IDENTIFIER_NOTICE);
        WebElement button = notice.findElement(By.className("pm-source-convert"));
        getDriver().scrollTo(button);
        button.click();
        getDriver().waitUntilCondition(driver -> !isShown(notice));
        return this;
    }

    /**
     * @return {@code true} if the macro content is an advanced Live Data configuration, kept as it is: the ways can't
     *     be changed
     */
    public boolean isLocked()
    {
        return isShown(getWidget().findElement(LOCKED_NOTICE));
    }

    /**
     * @return the notice displayed when the macro content is an advanced Live Data configuration, or an empty string
     */
    public String getAdvancedNotice()
    {
        WebElement notice = getWidget().findElement(LOCKED_NOTICE);
        return isShown(notice) ? notice.getText().trim() : "";
    }

    /**
     * @return {@code true} if the content of the advanced way can be changed
     */
    public boolean isAdvancedContentEditable()
    {
        return getEditor(Way.ADVANCED).findElement(By.tagName("textarea")).getAttribute("readonly") == null;
    }

    /**
     * @return the warning displayed in the footer of the macro editor, listing what submitting the macro drops (e.g.
     *     the filters, when specific work packages are selected), or an empty string if nothing is dropped
     */
    public String getFooterWarning()
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector(".macro-editor-modal .modal-footer "
            + ".pm-dropped-values")).stream().map(element -> element.getText().trim()).collect(Collectors.joining());
    }
}
