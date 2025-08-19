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
import org.openqa.selenium.support.FindBy;
import org.xwiki.administration.test.po.AdministrationSectionPage;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Models the administration page of the Open Project integration.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class OpenProjectAdminPage extends AdministrationSectionPage
{
    @FindBy(id = "create-connection-button")
    private WebElement addNewConnectionBtn;

    @FindBy(id = "open-project-sync-colors")
    private WebElement syncColorsBtn;

    private static final String SECTION_ID = "OpenProject";

    public OpenProjectAdminPage()
    {
        super(SECTION_ID);
    }

    /**
     * Go to the Administration page to the Open Project section.
     *
     * @return the view model of the admin page.
     */
    public static OpenProjectAdminPage gotoPage()
    {
        AdministrationSectionPage.gotoPage(SECTION_ID);
        return new OpenProjectAdminPage();
    }

    /**
     * Add a new OAuth connection.
     *
     * @param name the name of the connection - needs to be unique among the other connections.
     * @param clientUrl the url where the OAuth provider is located.
     * @param clientID the client id provided by the OAuth provider.
     * @param clientSecret the client secret provided by tge OAuth provider.
     */
    public void addNewConnection(String name, String clientUrl, String clientID, String clientSecret)
    {
        addNewConnectionBtn.click();
        fillAndSendConnectionModal(name, clientUrl, clientID, clientSecret);
    }

    /**
     * Update an OAuth connection displayed in the liveData present in the Admin section.
     *
     * @param index the index of the connection from the livedata. It starts at 1.
     * @param name the new name for the selected connection.
     * @param clientUrl the new client url for the selected connection.
     * @param clientID the new client id for the selected connection.
     * @param clientSecret the new client secret for the selected connection.
     */
    public void updateConnection(int index, String name, String clientUrl, String clientID, String clientSecret)
    {
        TableLayoutElement ld = getConnectionsLivedata().getTableLayout();
        ld.clickAction(index, "edit-connection");

        fillAndSendConnectionModal(name, clientUrl, clientID, clientSecret);
    }

    /**
     * Trigger the color sync job by pressing the "Sync" button.
     *
     * @return the view page of the Job Scheduler.
     */
    public ViewPage triggerColorSyncJob()
    {
        getDriver().findElement(By.id("open-project-sync-colors")).click();
        return new ViewPage();
    }

    /**
     * Delete a connection identified by its index in the live data.
     *
     * @param index the index of the connection inside the livedata. The indexing starts at 1.
     */
    public void deleteConnection(int index)
    {
        TableLayoutElement ld = getConnectionsLivedata().getTableLayout();
        ld.clickAction(index, "delete-connection");
        getDriver().waitUntilElementIsVisible(By.id("deleteConnectionModal"));
        WebElement modal = getDriver().findElement(By.id("deleteConnectionModal"));
        modal.findElement(By.className("btn-danger")).click();
    }

    /**
     * @return the livedata inside the Admin page.
     */
    public LiveDataElement getConnectionsLivedata()
    {
        return new LiveDataElement("openproject_connections");
    }

    /**
     * Trigger the Open Project style sync.
     *
     * @return the view page of the Job Scheduler.
     */
    public ViewPage triggerStyleSync()
    {
        syncColorsBtn.click();
        return new ViewPage();
    }

    private void fillAndSendConnectionModal(String name, String clientUrl, String clientID, String clientSecret)
    {
        getDriver().waitUntilElementIsVisible(By.id("handleConnectionModal"));
        WebElement modal = getDriver().findElement(By.id("handleConnectionModal"));
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

    private void sleep(long l)
    {
        try {
            Thread.sleep(l);
        } catch (Exception ignoredException) {

        }
    }
}
