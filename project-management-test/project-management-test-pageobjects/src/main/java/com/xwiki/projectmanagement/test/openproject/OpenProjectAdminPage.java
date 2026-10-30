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
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.xwiki.administration.test.po.AdministrationSectionPage;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Models the administration page of the OpenProject integration.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class OpenProjectAdminPage extends AdministrationSectionPage
{
    @FindBy(id = "create-connection-button")
    private WebElement addNewConnectionBtn;

    private static final String SECTION_ID = "OpenProject";

    private static final String CONNECTION_NAME_COLUMN = "Connection name";

    private static final String CONFIGURATION_CLASS = "OpenProject.Code.OpenProjectConfigurationClass";

    private static final String EDIT_CONNECTION_ACTION = "edit-connection";

    private static final By CONNECTION_MODAL = By.id("handleConnectionModal");

    public OpenProjectAdminPage()
    {
        super(SECTION_ID);
    }

    /**
     * Go to the Administration page to the OpenProject section.
     *
     * @return the view model of the admin page.
     */
    public static OpenProjectAdminPage gotoPage()
    {
        AdministrationSectionPage.gotoPage(SECTION_ID);
        return new OpenProjectAdminPage();
    }

    /**
     * Add a new OAuth connection, and wait for it to be saved and listed.
     *
     * @param name the name of the connection - needs to be unique among the other connections.
     * @param clientUrl the url where the OAuth provider is located.
     * @param clientID the client id provided by the OAuth provider.
     * @param clientSecret the client secret provided by tge OAuth provider.
     */
    public void addNewConnection(String name, String clientUrl, String clientID, String clientSecret)
    {
        submitNewConnection(name, clientUrl, clientID, clientSecret);
        waitForNotificationSuccessMessage(String.format("Connection %s has been saved!", name));
        waitUntilConnectionIsListed(name, true);
    }

    /**
     * Fill and submit the form adding a new OAuth connection, without waiting for the outcome. Use it for the
     * connections that are expected to be rejected, then wait for the error notification.
     *
     * @param name the name of the connection
     * @param clientUrl the url where the OAuth provider is located
     * @param clientID the client id provided by the OAuth provider
     * @param clientSecret the client secret provided by tge OAuth provider
     * @since 1.4.0
     */
    public void submitNewConnection(String name, String clientUrl, String clientID, String clientSecret)
    {
        addNewConnectionBtn.click();
        fillAndSendConnectionModal(name, clientUrl, clientID, clientSecret);
    }

    /**
     * Update an OAuth connection, and wait for the connection to be saved and listed under its new name.
     *
     * @param currentName the current name of the connection
     * @param name the new name for the selected connection.
     * @param clientUrl the new client url for the selected connection.
     * @param clientID the new client id for the selected connection.
     * @param clientSecret the new client secret for the selected connection.
     */
    public void updateConnection(String currentName, String name, String clientUrl, String clientID,
        String clientSecret)
    {
        clickConnectionAction(currentName, EDIT_CONNECTION_ACTION);
        fillAndSendConnectionModal(name, clientUrl, clientID, clientSecret);
        waitForNotificationSuccessMessage(String.format("Connection %s has been saved!", name));
        waitUntilConnectionIsListed(name, true);
        if (!name.equals(currentName)) {
            waitUntilConnectionIsListed(currentName, false);
        }
    }

    /**
     * Enable or disable showing the public data of a connection to the users without an OpenProject account, and wait
     * for the connection to be saved.
     *
     * @param name the name of the connection
     * @param showPublicData {@code true} to show the public data of the connection to these users
     * @since 1.4.0
     */
    public void setShowPublicData(String name, boolean showPublicData)
    {
        clickConnectionAction(name, EDIT_CONNECTION_ACTION);
        getDriver().waitUntilElementIsVisible(CONNECTION_MODAL);
        WebElement modal = getDriver().findElement(CONNECTION_MODAL);
        WebElement checkbox = modal.findElement(By.id("show-public-data"));
        if (checkbox.isSelected() != showPublicData) {
            checkbox.click();
        }
        modal.findElement(By.className("btn-primary")).click();
        // The modal is closed once the connection is saved.
        getDriver().waitUntilElementDisappears(CONNECTION_MODAL);
    }

    /**
     * Open the form editing a connection, read the hint displayed under its client secret field, and close the form
     * without saving.
     *
     * @param name the name of the connection
     * @return the hint displayed under the client secret field, or an empty string when there is none
     * @since 1.4.0
     */
    public String getEditConnectionClientSecretHint(String name)
    {
        clickConnectionAction(name, EDIT_CONNECTION_ACTION);
        return getClientSecretHintAndCloseModal();
    }

    /**
     * Open the form creating a connection, read the hint displayed under its client secret field, and close the form
     * without saving.
     *
     * @return the hint displayed under the client secret field, or an empty string when there is none
     * @since 1.4.0
     */
    public String getNewConnectionClientSecretHint()
    {
        addNewConnectionBtn.click();
        return getClientSecretHintAndCloseModal();
    }

    /**
     * Start the authorization of a connection by the current user, and wait for the browser to leave the
     * administration: it is redirected to the OAuth provider, then back to the administration.
     *
     * @param name the name of the connection
     * @since 1.4.0
     */
    public void authorizeConnection(String name)
    {
        WebElement page = getDriver().findElement(By.tagName("html"));
        clickConnectionAction(name, "authorize-connection");
        getDriver().waitUntilCondition(ExpectedConditions.stalenessOf(page));
    }

    /**
     * Trigger the color sync job by pressing the "Sync" button.
     *
     * @return the view page of the Job Scheduler.
     */
    public ViewPage triggerColorSyncJob()
    {
        getDriver().findElement(By.id("open-project-sync-colors")).click();
        ViewPage schedulerPage = new ViewPage();
        schedulerPage.waitUntilPageIsReady();
        return schedulerPage;
    }

    /**
     * @return {@code true} if the Job Scheduler page displays the message confirming that the color sync job was
     *     triggered
     */
    public boolean isColorSyncJobTriggered()
    {
        return getDriver().hasElementWithoutWaiting(By.xpath(
            "//div[contains(@class, 'infomessage')]/p[text() = 'Job OpenProject Styling Updater triggered']"));
    }

    /**
     * @param name the name of a connection
     * @return {@code true} if the connection is listed in the connections Live Data
     * @since 1.4.0
     */
    public boolean isConnectionListed(String name)
    {
        return getConnectionRow(name) > 0;
    }

    /**
     * @param name the name of a listed connection
     * @return the authorization state of the connection for the current user, as displayed, e.g. {@code Authorized}
     * @since 1.4.0
     */
    public String getConnectionAuthorization(String name)
    {
        return getConnectionsTable().getCell("Authorized?", getListedConnectionRow(name)).getText();
    }

    /**
     * @param name the name of a listed connection
     * @return the number of actions that can be performed on the connection
     * @since 1.4.0
     */
    public int getConnectionActionCount(String name)
    {
        return getConnectionsTable().getCell("Actions", getListedConnectionRow(name))
            .findElements(By.className("action")).size();
    }

    /**
     * Delete a connection, and wait for it to be removed from the connections Live Data.
     *
     * @param name the name of the connection
     */
    public void deleteConnection(String name)
    {
        clickConnectionAction(name, "delete-connection");
        By modalSelector = By.id("deleteConnectionModal");
        getDriver().waitUntilElementIsVisible(modalSelector);
        getDriver().findElement(modalSelector).findElement(By.className("btn-danger")).click();
        waitForNotificationSuccessMessage(String.format("Connection %s has been deleted!", name));
        waitUntilConnectionIsListed(name, false);
    }

    /**
     * Set and save the characters that trigger the work package search in the WYSIWYG editor.
     *
     * @param marker the new work package search trigger
     */
    public void setWorkPackageSearchMarker(String marker)
    {
        setWorkPackageSearchMarker(marker, true);
    }

    /**
     * Set the characters that trigger the work package search in the WYSIWYG editor and click the save button.
     *
     * @param marker the new work package search trigger
     * @param wait whether to wait for the configuration to be saved; pass {@code false} when the value is expected to
     *     be rejected by the form validation
     */
    public void setWorkPackageSearchMarker(String marker, boolean wait)
    {
        // ConfigurableClass generates one form per configuration page, named after the lower cased section.
        getFormContainerElement(SECTION_ID.toLowerCase() + "_OpenProject.Code.OpenProjectConfiguration")
            .setFieldValue(getWorkPackageSearchMarkerInput(), marker);
        clickSave(wait);
    }

    /**
     * @return the validation message displayed for the work package search trigger, or an empty string if the value
     *     is valid
     */
    public String getWorkPackageSearchMarkerValidationMessage()
    {
        return (String) getDriver().executeJavascript("return arguments[0].validationMessage;",
            getWorkPackageSearchMarkerInput());
    }

    private WebElement getWorkPackageSearchMarkerInput()
    {
        return getDriver().findElement(By.name(CONFIGURATION_CLASS + "_0_workPackageSearchMarker"));
    }

    /**
     * Open the modal used to create a new filter preset.
     *
     * @return the modal
     * @since 1.3.0-rc-2
     */
    public PresetCreateModal clickCreatePreset()
    {
        getDriver().findElement(By.id("proj-manag-create-preset")).click();
        return new PresetCreateModal();
    }

    /**
     * @param name the name of a filter preset
     * @return whether the presets Live Data lists the preset as meant for the charts, as displayed ({@code Yes} or
     *     {@code No}), or {@code null} if the preset is not listed
     * @since 1.4.0
     */
    public String getListedPresetChartFlag(String name)
    {
        TableLayoutElement ld = new LiveDataElement("openproject-preset-filters").getTableLayout();
        ld.waitUntilReady();
        for (int i = 1; i <= ld.countRows(); i++) {
            if (name.equals(ld.getCell("Preset Name", i).getText())) {
                return ld.getCell("Chart preset?", i).getText();
            }
        }
        return null;
    }

    private TableLayoutElement getConnectionsTable()
    {
        return new LiveDataElement("openproject_connections").getTableLayout();
    }

    /**
     * @return the index of the connection in the Live Data, starting at 1, or {@code -1} if there is no connection
     *     with the given name
     */
    private int getConnectionRow(String name)
    {
        TableLayoutElement ld = getConnectionsTable();
        ld.waitUntilReady();
        return findConnectionRow(ld, name);
    }

    private int getListedConnectionRow(String name)
    {
        int row = getConnectionRow(name);
        if (row < 0) {
            throw new IllegalStateException(String.format("The connection [%s] is not listed.", name));
        }
        return row;
    }

    private static int findConnectionRow(TableLayoutElement ld, String name)
    {
        for (int i = 1; i <= ld.countRows(); i++) {
            if (name.equals(ld.getCell(CONNECTION_NAME_COLUMN, i).getText())) {
                return i;
            }
        }
        return -1;
    }

    private void clickConnectionAction(String name, String action)
    {
        getConnectionsTable().clickAction(getListedConnectionRow(name), action);
    }

    /**
     * The connections Live Data is refreshed asynchronously after a connection is saved or deleted, while the
     * notification is displayed.
     */
    private void waitUntilConnectionIsListed(String name, boolean listed)
    {
        LiveDataElement liveData = new LiveDataElement("openproject_connections");
        getDriver().waitUntilCondition(driver -> {
            try {
                return liveData.isReady() && (findConnectionRow(liveData.getTableLayout(), name) > 0) == listed;
            } catch (StaleElementReferenceException e) {
                // The rows are being refreshed.
                return false;
            }
        });
    }

    private String getClientSecretHintAndCloseModal()
    {
        getDriver().waitUntilElementIsVisible(CONNECTION_MODAL);
        WebElement modal = getDriver().findElement(CONNECTION_MODAL);
        WebElement hint = modal.findElement(By.id("client-secret-keep-hint"));
        String hintText = hint.isDisplayed() ? hint.getText() : "";
        modal.findElement(By.cssSelector(".modal-footer [data-dismiss='modal']")).click();
        getDriver().waitUntilElementDisappears(CONNECTION_MODAL);
        return hintText;
    }

    private void fillAndSendConnectionModal(String name, String clientUrl, String clientID, String clientSecret)
    {
        getDriver().waitUntilElementIsVisible(CONNECTION_MODAL);
        WebElement modal = getDriver().findElement(CONNECTION_MODAL);
        WebElement elem = modal.findElement(By.id("connection-name"));
        elem.clear();
        elem.sendKeys(name);
        elem = modal.findElement(By.id("server-url"));
        elem.clear();
        elem.sendKeys(clientUrl);
        elem = modal.findElement(By.id("client-id"));
        elem.clear();
        elem.sendKeys(clientID);
        elem = modal.findElement(By.id("client-secret"));
        elem.clear();
        elem.sendKeys(clientSecret);

        modal.findElement(By.className("btn-primary")).click();
    }
}
