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

import javax.naming.OperationNotSupportedException;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.CardLayoutElement;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models the OpenProject macro present in a view page.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class OpenProjectMacroElement extends BaseElement
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectMacroElement.class);

    private static final int LOAD_TIMEOUT = 30;

    /**
     * Collects what the browser knows about the asynchronous rendering of the page: the placeholders still waiting
     * for their content, the asynchronous rendering script, and the requests made to get the content (each answered
     * by 200 with the content, 202 while it is being computed, anything else removing the placeholder).
     */
    private static final String ASYNC_RENDERING_STATE_SCRIPT = String.join("\n",
        "var resources = performance.getEntriesByType('resource');",
        "return JSON.stringify({",
        "  placeholders: Array.from(document.querySelectorAll('.xwiki-async')).map(function(element) {",
        "    return {id: element.dataset.xwikiAsyncId, clientId: element.dataset.xwikiAsyncClientId,",
        "      url: element.dataset.xwikiAsyncUrl, visible: element.offsetParent !== null,",
        "      html: element.outerHTML.substring(0, 300)};",
        "  }),",
        "  asyncScripts: resources.map(function(entry) { return entry.name; })",
        "    .filter(function(name) { return name.indexOf('async.js') >= 0; }),",
        "  asyncRequests: resources.filter(function(entry) { return entry.name.indexOf('/asyncrenderer/') >= 0; })",
        "    .slice(-20).map(function(entry) {",
        "      return {url: entry.name, status: entry.responseStatus, start: Math.round(entry.startTime),",
        "        duration: Math.round(entry.duration)};",
        "    }),",
        "  now: Math.round(performance.now())",
        "}, null, 2);");

    private final WebElement self;

    /**
     * @param element the wrapper of the OpenProject macro.
     */
    public OpenProjectMacroElement(WebElement element)
    {
        self = element;
    }

    /**
     * @return true if the warning message is displayed or not. The warning message is displayed when the user seeing
     *     the macro is not authorized to the OpenProject instance from which the work packages are retrieved.
     */
    public boolean isUserAuthorized()
    {
        try {
            self.findElement(By.className("warningmessage"));
            return false;
        } catch (NoSuchElementException e) {
            return true;
        }
    }

    /**
     * Click the link from the warning message, authorizing the user to the OpenProject instance.
     *
     * @return the view page after it the user was authorized.
     */
    public ViewPageWithOpenProjectMacro authorizeUser()
    {
        self.findElement(By.cssSelector(".warningmessage a")).click();
        ViewPageWithOpenProjectMacro vp = new ViewPageWithOpenProjectMacro();
        vp.waitUntilPageIsReady();
        return vp;
    }

    /**
     * @return the livedata table displayer of the OpenProject macro.
     * @throws OperationNotSupportedException if the id parameter of the OpenProject macro was not provided. The
     *     livedata page object requires an id in order to be retrieved.
     */
    public LiveDataElement getLivedata() throws OperationNotSupportedException
    {
        String id = self.findElement(By.className("liveData")).getAttribute("id");
        if (StringUtils.isEmpty(id)) {
            throw new OperationNotSupportedException(
                "Can't retrieve the livedata element if no id was provided to the macro.");
        }
        return new LiveDataElement(id);
    }

    /**
     * Wait for the work packages to be displayed by the Live Data table. The first requests made to the OpenProject
     * instance can take longer than the default timeout.
     *
     * @return the Live Data table displayer of the OpenProject macro, once it is ready
     * @throws OperationNotSupportedException if the id parameter of the OpenProject macro was not provided
     * @since 1.3.0-rc-2
     */
    public TableLayoutElement waitForTableLayout() throws OperationNotSupportedException
    {
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            // The Live Data is created once the macro, which is rendered asynchronously, has been loaded.
            TableLayoutElement table = getLivedata().getTableLayout();
            table.waitUntilReady();
            return table;
        } catch (TimeoutException e) {
            // The asynchronous rendering of the macro sometimes never finishes on CI, with nothing logged: show what
            // the browser and XWiki are doing at that point.
            String reason = "The OpenProject macro did not display its Live Data in time.";
            LOGGER.error("{} Asynchronous rendering state in the browser:\n{}", reason,
                getDriver().executeScript(ASYNC_RENDERING_STATE_SCRIPT));
            XWikiThreadDump.log(reason);
            throw e;
        } finally {
            getDriver().setTimeout(timeout);
        }
    }

    /**
     * @param column the label of the Live Data column
     * @param row the row index, starting from 1
     * @return {@code true} if the given cell of the Live Data table displays a link
     * @throws OperationNotSupportedException if the id parameter of the OpenProject macro was not provided
     * @since 1.3.1
     */
    public boolean hasLinkInCell(String column, int row) throws OperationNotSupportedException
    {
        return !waitForTableLayout().getCell(column, row).findElements(By.tagName("a")).isEmpty();
    }

    /**
     * @return the livedata cards displayer of the OpenProject macro.
     * @throws OperationNotSupportedException if the id parameter of the OpenProject macro was not provided. The
     *     livedata page object requires an id in order to be retrieved.
     */
    public CardLayoutElement getCards() throws OperationNotSupportedException
    {
        String id = self.getAttribute("id");
        if (StringUtils.isEmpty(id)) {
            throw new OperationNotSupportedException(
                "Can't retrieve the cards element if no id was provided to the macro.");
        }
        return new CardLayoutElement(id);
    }

    /**
     * @return the single displayer of the OpenProject macro.
     */
    public OpenProjectSingleDisplayer getSingleWorkItem()
    {
        return new OpenProjectSingleDisplayer(self);
    }

    /**
     * @return the text displayed by the Live Data pagination, e.g. {@code Entries 1 - 3 out of 3}
     */
    public String getPaginationEntriesText()
    {
        return self.findElement(By.className("pagination-current-entries")).getText();
    }

    /**
     * @return the wrapper of the macro.
     */
    public WebElement getElement()
    {
        return self;
    }
}
