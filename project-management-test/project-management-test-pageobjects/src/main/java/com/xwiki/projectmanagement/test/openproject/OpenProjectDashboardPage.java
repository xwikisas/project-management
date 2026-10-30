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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * The OpenProject dashboard ({@code OpenProject.WebHome}).
 *
 * @version $Id$
 * @since 1.4.0
 */
public class OpenProjectDashboardPage extends ViewPage
{
    private static final By CONNECTION_SELECT = By.id("openproject-dashboard-connection-select");

    private static final By PROJECT_SELECT = By.id("openproject-dashboard-project-select");

    private static final String CONNECTION_NAME_PARAMETER = "connectionName=";

    /**
     * Go to the dashboard.
     *
     * @return the dashboard
     */
    public static OpenProjectDashboardPage gotoPage()
    {
        getUtil().gotoPage("OpenProject", "WebHome");
        OpenProjectDashboardPage dashboard = new OpenProjectDashboardPage();
        dashboard.waitUntilPageIsReady();
        return dashboard;
    }

    /**
     * @return the name of the connection selected in the connection picker
     */
    public String getSelectedConnection()
    {
        return new Select(getDriver().findElement(CONNECTION_SELECT)).getFirstSelectedOption().getAttribute("value");
    }

    /**
     * Select a connection in the connection picker, and wait for the dashboard to be reloaded with it.
     *
     * @param connection the name of the connection to select
     * @return the reloaded dashboard
     */
    public OpenProjectDashboardPage selectConnection(String connection)
    {
        WebElement select = getDriver().findElement(CONNECTION_SELECT);
        new Select(select).selectByValue(connection);
        // Selecting a connection saves it and reloads the dashboard.
        getDriver().waitUntilCondition(ExpectedConditions.stalenessOf(select));
        OpenProjectDashboardPage dashboard = new OpenProjectDashboardPage();
        dashboard.waitUntilPageIsReady();
        return dashboard;
    }

    /**
     * @return {@code true} if the project picker is displayed, which happens when the current user is authorized
     *     for the selected connection
     */
    public boolean hasProjectPicker()
    {
        return getDriver().hasElementWithoutWaiting(PROJECT_SELECT);
    }

    /**
     * @return the name of the project selected in the project picker, or {@code null} if no project is selected
     */
    public String getSelectedProject()
    {
        List<SuggestInputElement.SuggestionElement> selected = getProjectPicker().getSelectedSuggestions();
        return selected.isEmpty() ? null : selected.get(0).getLabel();
    }

    /**
     * Select a project in the project picker, and wait for the dashboard to be reloaded with it.
     *
     * @param project the name of the project to select, which must not be the selected one
     * @return the reloaded dashboard
     */
    public OpenProjectDashboardPage selectProject(String project)
    {
        WebElement select = getDriver().findElement(PROJECT_SELECT);
        new SuggestInputElement(select).click().sendKeys(project).waitForSuggestions().selectByVisibleText(project);
        // Selecting a project saves it and reloads the dashboard.
        getDriver().waitUntilCondition(ExpectedConditions.stalenessOf(select));
        OpenProjectDashboardPage dashboard = new OpenProjectDashboardPage();
        dashboard.waitUntilPageIsReady();
        return dashboard;
    }

    /**
     * Wait for the dashboard gadgets to be displayed, which happens once the dashboard has checked that the selected
     * connection works and has projects.
     *
     * @return this dashboard
     */
    public OpenProjectDashboardPage waitUntilGadgetsAreDisplayed()
    {
        getDriver().waitUntilElementIsVisible(By.id("openproject-dashboard-gadgets"));
        return this;
    }

    /**
     * @return the titles of the gadgets displayed on the dashboard
     */
    public List<String> getGadgetTitles()
    {
        return getDriver()
            .findElementsWithoutWaiting(By.cssSelector("#openproject-dashboard-gadgets .gadget-title"))
            .stream()
            .map(WebElement::getText)
            .collect(Collectors.toList());
    }

    /**
     * @return the texts of the warnings displayed on the dashboard
     */
    public List<String> getWarnings()
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector("#xwikicontent .box.warningmessage")).stream()
            .filter(WebElement::isDisplayed)
            .map(WebElement::getText)
            .collect(Collectors.toList());
    }

    /**
     * @return the names of the connections that the displayed warnings ask the current user to authorize
     */
    public List<String> getConnectionsToAuthorize()
    {
        return getDriver()
            .findElementsWithoutWaiting(
                By.cssSelector("#xwikicontent .box.warningmessage a[href*='RenewOAuthConnection']"))
            .stream()
            .filter(WebElement::isDisplayed)
            .map(link -> getConnectionName(link.getAttribute("href")))
            .collect(Collectors.toList());
    }

    private SuggestInputElement getProjectPicker()
    {
        return new SuggestInputElement(getDriver().findElement(PROJECT_SELECT));
    }

    private static String getConnectionName(String url)
    {
        String parameters = url.substring(url.indexOf(CONNECTION_NAME_PARAMETER) + CONNECTION_NAME_PARAMETER.length());
        int end = parameters.indexOf('&');
        return URLDecoder.decode(end < 0 ? parameters : parameters.substring(0, end), StandardCharsets.UTF_8);
    }
}
