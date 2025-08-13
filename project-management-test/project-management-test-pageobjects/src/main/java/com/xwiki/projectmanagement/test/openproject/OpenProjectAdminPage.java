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

import javax.swing.text.View;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.xwiki.administration.test.po.AdministrationSectionPage;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.test.ui.po.ViewPage;

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

    public static OpenProjectAdminPage gotoPage()
    {
        AdministrationSectionPage.gotoPage(SECTION_ID);
        return new OpenProjectAdminPage();
    }

    public void addNewConnection(String name, String clientUrl, String clientID, String clientSecret)
    {
        addNewConnectionBtn.click();
        getDriver().waitUntilElementIsVisible(By.id("handleConnectionModal"));
        WebElement modal = getDriver().findElement(By.id("handleConnectionModal"));
        modal.findElement(By.id("connection-name")).sendKeys(name);
        modal.findElement(By.id("server-url")).sendKeys(clientUrl);
        modal.findElement(By.id("client-id")).sendKeys(clientID);
        modal.findElement(By.id("client-secret")).sendKeys(clientSecret);

        modal.findElement(By.className("btn-primary")).click();
//        waitForNotificationSuccessMessage(String.format("Connection %s has been saved!", name));
    }

    public LiveDataElement getConnectionsLivedata()
    {
        return new LiveDataElement("openproject_connections");
    }

    public ViewPage triggerStyleSync()
    {
        syncColorsBtn.click();
        return new ViewPage();
    }
}
