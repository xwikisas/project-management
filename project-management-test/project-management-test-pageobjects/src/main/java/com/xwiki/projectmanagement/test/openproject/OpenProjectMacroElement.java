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

import javax.naming.OperationNotSupportedException;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.CardLayoutElement;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models the OpenProject macro present in a view page.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class OpenProjectMacroElement extends BaseElement
{
    private static final int LOAD_TIMEOUT = 30;

    private static final By VIEW_MODAL = By.id("openproject-item-view-modal");

    private static final By ERROR_MESSAGE = By.className("xwikirenderingerror");

    private static final By ERROR_DETAILS = By.className("xwikirenderingerrordescription");

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
        } finally {
            getDriver().setTimeout(timeout);
        }
    }

    /**
     * @param column the label of the Live Data column
     * @param row the row index, starting from 1
     * @return {@code true} if the given cell of the Live Data table displays a link
     * @throws OperationNotSupportedException if the id parameter of the OpenProject macro was not provided
     * @since 1.4.0
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
     * Wait for the work packages displayed as a list to be rendered, since the macro is rendered asynchronously.
     *
     * @return the lines of the list, one per work package: its key (or its id prefixed by {@code #}) followed by its
     *     subject
     * @since 1.4.0
     */
    public List<String> getListedWorkPackages()
    {
        waitUntilRendered();
        return getTexts(self.findElements(By.tagName("li")));
    }

    /**
     * Wait for the work packages displayed as an enumeration to be rendered, since the macro is rendered
     * asynchronously.
     *
     * @return the displayed text, i.e. the keys (or the ids prefixed by {@code #}) of the work packages separated by
     *     commas
     * @since 1.4.0
     */
    public String getEnumeratedWorkPackages()
    {
        waitUntilRendered();
        return self.getText();
    }

    /**
     * @return the work packages displayed as links to OpenProject, by their displayed identifier
     * @since 1.4.0
     */
    public List<String> getWorkPackageLinks()
    {
        waitUntilRendered();
        return getTexts(self.findElements(By.tagName("a")));
    }

    /**
     * @return the work packages displayed as closed (struck through) by the list and enumeration displayers, as
     *     displayed
     * @since 1.4.0
     */
    public List<String> getClosedWorkPackages()
    {
        waitUntilRendered();
        return getTexts(self.findElements(By.tagName("del")));
    }

    /**
     * Click the View action of a work package of the Live Data table, and wait for the work package to be displayed
     * in a modal.
     *
     * @param row the row index, starting from 1
     * @return the work package displayed by the modal
     * @throws OperationNotSupportedException if the id parameter of the OpenProject macro was not provided
     * @since 1.4.0
     */
    public OpenProjectSingleDisplayer viewWorkPackage(int row) throws OperationNotSupportedException
    {
        // The OpenProject View action isn't named, so TableLayoutElement#clickAction can't find it.
        waitForTableLayout();
        self.findElements(By.cssSelector(".liveData tbody tr")).get(row - 1).findElement(By.className("action_view"))
            .click();
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            // The modal body is replaced when the modal is displayed, then the work package is rendered
            // asynchronously.
            getDriver().waitUntilElementIsVisible(VIEW_MODAL);
            return new OpenProjectSingleDisplayer(getDriver().findElement(VIEW_MODAL)).waitUntilReady();
        } finally {
            getDriver().setTimeout(timeout);
        }
    }

    /**
     * Close the modal opened by {@link #viewWorkPackage(int)}.
     *
     * @since 1.4.0
     */
    public void closeWorkPackageView()
    {
        getDriver().findElement(VIEW_MODAL).findElement(By.cssSelector(".modal-header .close")).click();
        getDriver().waitUntilElementDisappears(VIEW_MODAL);
    }

    /**
     * Wait for the Live Data of the macro to display its entries in the table layout. Unlike
     * {@link #waitForTableLayout()}, this doesn't need the Live Data to have an id.
     *
     * @return the text of the displayed rows
     * @since 1.4.0
     */
    public List<String> waitForLiveDataRows()
    {
        return waitForLiveDataEntries(By.cssSelector(".liveData .layout-table-root tbody tr"));
    }

    /**
     * Wait for the Live Data of the macro to display its entries in the cards layout.
     *
     * @return the text of the displayed cards
     * @since 1.4.0
     */
    public List<String> waitForLiveDataCards()
    {
        return waitForLiveDataEntries(By.cssSelector(".liveData .layout-cards .card"));
    }

    /**
     * Wait for the macro to display an error, since it can be rendered asynchronously.
     *
     * @return the error message
     * @since 1.4.0
     */
    public String waitForErrorMessage()
    {
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            getDriver().waitUntilElementIsVisible(self, ERROR_MESSAGE);
            return self.findElement(ERROR_MESSAGE).getText();
        } finally {
            getDriver().setTimeout(timeout);
        }
    }

    /**
     * Click on the error message displayed by the macro, which shows or hides the details of the error.
     *
     * @since 1.4.0
     */
    public void toggleErrorDetails()
    {
        boolean displayed = isErrorDetailsDisplayed();
        self.findElement(ERROR_MESSAGE).click();
        getDriver().waitUntilCondition(driver -> isErrorDetailsDisplayed() != displayed);
    }

    /**
     * @return {@code true} if the details of the error displayed by the macro are visible
     * @since 1.4.0
     */
    public boolean isErrorDetailsDisplayed()
    {
        return self.findElement(ERROR_DETAILS).isDisplayed();
    }

    /**
     * @return the details of the error displayed by the macro, once visible
     * @since 1.4.0
     */
    public String getErrorDetails()
    {
        return self.findElement(ERROR_DETAILS).getText();
    }

    private List<String> waitForLiveDataEntries(By entries)
    {
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            // The entries are displayed once the Live Data has been initialized and has fetched them.
            getDriver().waitUntilCondition(driver -> !self.findElements(entries).isEmpty()
                && self.findElements(By.cssSelector(".liveData .layout-loader, .liveData .xwiki-loader")).stream()
                .noneMatch(WebElement::isDisplayed));
            return getTexts(self.findElements(entries));
        } finally {
            getDriver().setTimeout(timeout);
        }
    }

    private void waitUntilRendered()
    {
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            // The macro is rendered asynchronously: the work packages show up once it is, linked to OpenProject.
            getDriver().waitUntilCondition(driver -> !self.findElements(By.tagName("a")).isEmpty());
        } finally {
            getDriver().setTimeout(timeout);
        }
    }

    private static List<String> getTexts(List<WebElement> elements)
    {
        return elements.stream().map(WebElement::getText).collect(Collectors.toList());
    }

    /**
     * @return the wrapper of the macro.
     */
    public WebElement getElement()
    {
        return self;
    }
}
