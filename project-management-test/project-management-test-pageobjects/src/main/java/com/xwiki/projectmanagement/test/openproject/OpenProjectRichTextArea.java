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

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.xwiki.ckeditor.test.po.AutocompleteDropdown;
import org.xwiki.ckeditor.test.po.RichTextAreaElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The OpenProject features of the CKEditor rich text area.
 *
 * @version $Id$
 * @since 1.4.0
 */
public class OpenProjectRichTextArea extends BaseElement
{
    private final RichTextAreaElement textArea;

    /**
     * @param textArea the CKEditor rich text area
     */
    public OpenProjectRichTextArea(RichTextAreaElement textArea)
    {
        this.textArea = textArea;
    }

    /**
     * Insert a work package through the work package search: type the query, select the work package in the
     * autocomplete dropdown and wait for the OpenProject macro to be inserted.
     *
     * @param query the text to type, starting with the work package search marker
     * @param workPackage the subject of the work package to select in the autocomplete dropdown
     */
    public void insertWorkPackage(String query, String workPackage)
    {
        searchWorkPackage(query, query, workPackage);
        insertSelectedWorkPackage();
    }

    /**
     * Type some text in the work package search and wait for the given work package to be selected in the
     * autocomplete dropdown.
     *
     * @param text the text to type, starting with the work package search marker when the search isn't started yet
     * @param query the resulting query of the work package search, starting with the marker
     * @param workPackage the subject of the work package expected to be selected in the autocomplete dropdown
     * @since 1.4.1
     */
    public void searchWorkPackage(String text, String query, String workPackage)
    {
        this.textArea.sendKeys(text);
        new AutocompleteDropdown().waitForItemSelected(query, workPackage);
    }

    /**
     * Insert the work package selected in the autocomplete dropdown of the work package search and wait for the
     * OpenProject macro to be inserted.
     *
     * @since 1.4.1
     */
    public void insertSelectedWorkPackage()
    {
        AutocompleteDropdown dropdown = new AutocompleteDropdown();
        this.textArea.sendKeys(Keys.ENTER);
        dropdown.waitForItemSubmitted();
        // The macro is inserted asynchronously, after the autocomplete dropdown is closed.
        this.textArea.waitUntilContentContains("startmacro:openproject");
    }

    /**
     * Delete the last characters of the work package search query, so that it gets shorter than the minimum length
     * of the search, and wait for the autocomplete dropdown to be closed.
     *
     * @param count the number of characters to delete
     * @return {@code true} if the autocomplete dropdown is closed, {@code false} if it is still opened after the wait
     *     timeout
     * @since 1.4.1
     */
    public boolean deleteWorkPackageSearchCharacters(int count)
    {
        for (int i = 0; i < count; i++) {
            this.textArea.sendKeys(Keys.BACK_SPACE);
        }
        try {
            getDriver().waitUntilElementDisappears(By.className("cke_autocomplete_opened"));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Type some text in the rich text area, without waiting for anything.
     *
     * @param text the text to type
     * @since 1.4.1
     */
    public void type(String text)
    {
        this.textArea.sendKeys(text);
    }

    /**
     * Start a new paragraph, by pressing Enter.
     *
     * @since 1.4.1
     */
    public void newParagraph()
    {
        this.textArea.sendKeys(Keys.ENTER);
    }

    /**
     * Delete the last typed characters, without waiting for anything.
     *
     * @param count the number of characters to delete
     * @since 1.4.1
     */
    public void deleteCharacters(int count)
    {
        for (int i = 0; i < count; i++) {
            this.textArea.sendKeys(Keys.BACK_SPACE);
        }
    }

    /**
     * Close the autocomplete dropdown of the work package search by pressing Escape, and wait for it to be closed.
     *
     * @since 1.4.1
     */
    public void closeWorkPackageSearch()
    {
        this.textArea.sendKeys(Keys.ESCAPE);
        getDriver().waitUntilElementDisappears(By.className("cke_autocomplete_opened"));
    }

    /**
     * Start recording the requests that the work package search sends to the server, until the page is reloaded.
     *
     * @since 1.4.1
     * @see #waitForWorkPackageSearch(String)
     */
    public void recordWorkPackageSearches()
    {
        getDriver().executeScript(String.join("\n",
            "if (!window.recordedWorkPackageSearches) {",
            "  window.recordedWorkPackageSearches = [];",
            "  const open = XMLHttpRequest.prototype.open;",
            "  XMLHttpRequest.prototype.open = function (method, url) {",
            "    const parsedURL = new URL(url, window.location.href);",
            "    if (parsedURL.pathname.endsWith('/projectmanagement/openproject/workitems')) {",
            "      const filter = JSON.parse(parsedURL.searchParams.get('filter'));",
            "      const search = {query: filter.query.filters[0].constraints[0].value, done: false};",
            "      window.recordedWorkPackageSearches.push(search);",
            // The search is done once the editor handled its response, in callbacks that jQuery and the promises
            // call asynchronously, after the response is loaded.
            "      this.addEventListener('loadend', () => setTimeout(() => setTimeout(() => search.done = true)));",
            "    }",
            "    return open.apply(this, arguments);",
            "  };",
            "}"));
    }

    /**
     * Wait for the last recorded work package search to be the given one, and to be done.
     *
     * @param query the query of the search to wait for, i.e. the text typed after the work package search marker
     * @return the queries of the searches sent since {@link #recordWorkPackageSearches()} was called, in the order
     *     they were sent
     * @since 1.4.1
     */
    @SuppressWarnings("unchecked")
    public List<String> waitForWorkPackageSearch(String query)
    {
        getDriver().waitUntilCondition(driver -> Boolean.TRUE.equals(getDriver().executeScript(
            "const searches = window.recordedWorkPackageSearches;"
                + " const last = searches[searches.length - 1];"
                + " return last !== undefined && last.query === arguments[0] && last.done;", query)));
        return (List<String>) getDriver().executeScript(
            "return window.recordedWorkPackageSearches.map(search => search.query);");
    }

    /**
     * Select an entry of the work package search: type the query, select the entry in the autocomplete dropdown and
     * wait for the dropdown to be closed.
     *
     * @param query the text to type, starting with the work package search marker
     * @param label the label of the entry to select in the autocomplete dropdown
     */
    public void selectWorkPackageSearchEntry(String query, String label)
    {
        this.textArea.sendKeys(query);
        AutocompleteDropdown dropdown = new AutocompleteDropdown();
        dropdown.waitForItemSelected(query, label);
        this.textArea.sendKeys(Keys.ENTER);
        dropdown.waitForItemSubmitted();
    }

    /**
     * Type the query of the work package search and wait for the given message to be displayed instead of the work
     * packages.
     *
     * @param query the text to type, starting with the work package search marker
     * @param message the text of the message expected in the autocomplete dropdown
     * @return the URL of the link of the message (e.g. to connect an OpenProject account), or {@code null} if it has
     *     none
     * @since 1.4.1
     */
    public String searchWorkPackageMessage(String query, String message)
    {
        this.textArea.sendKeys(query);
        new AutocompleteDropdown().waitForItemSelected(query, message);
        List<WebElement> links = getDriver().findElementsWithoutWaiting(
            By.cssSelector(".cke_autocomplete_opened .cke_autocomplete_selected a[href]"));
        return links.isEmpty() ? null : links.get(0).getAttribute("href");
    }

    /**
     * Insert a macro through the quick actions: type a slash followed by the query, select the macro in the
     * autocomplete dropdown and wait for its edit modal to be displayed.
     *
     * @param query the text typed after the slash
     * @param macroName the name of the macro to select in the autocomplete dropdown
     * @return the edit modal of the macro
     */
    public OpenProjectDirectMacroEditModal insertMacroWithQuickAction(String query, String macroName)
    {
        String text = "/" + query;
        this.textArea.sendKeys(text);
        AutocompleteDropdown dropdown = new AutocompleteDropdown();
        dropdown.waitForItemSelected(text, macroName);
        this.textArea.sendKeys(Keys.ENTER);
        dropdown.waitForItemSubmitted();
        return new OpenProjectDirectMacroEditModal();
    }
}
