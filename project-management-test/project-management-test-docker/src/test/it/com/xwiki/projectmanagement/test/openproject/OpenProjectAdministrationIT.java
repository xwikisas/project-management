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

import java.util.Collections;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the administration section of the OpenProject integration.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
@UITest(
    properties = {
        AbstractOpenProjectIT.PRCHECKER_EXCLUDE_PROPERTY,
        AbstractOpenProjectIT.PLUGINS_PROPERTY
    },
    extraJARs = {
        AbstractOpenProjectIT.JYTHON_JAR,
        AbstractOpenProjectIT.SCHEDULER_API_JAR
    }
)
public class OpenProjectAdministrationIT extends AbstractOpenProjectIT
{
    private static final String AUTHORIZED_COLUMN = "Authorized?";

    private static final String ACTIONS_COLUMN = "Actions";

    private static final String SAVED_MESSAGE = "Connection %s has been saved!";

    @Test
    void connectionIsConfiguredAndAuthorized()
    {
        // The connection is added and authorized by the OpenProjectFixture.
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        int index = adminPage.getConnectionIndex(CONNECTION_ID);
        assertTrue(index > 0);
        TableLayoutElement livedata = adminPage.getConnectionsLivedata().getTableLayout();
        assertEquals("Authorized", livedata.getCell(AUTHORIZED_COLUMN, index).getText());
        assertEquals(2, livedata.getCell(ACTIONS_COLUMN, index).findElements(By.className("action")).size());
    }

    @Test
    void tryToAddConnectionWithSameName()
    {
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection(CONNECTION_ID, "someurl", "someid", "somesecret");
        adminPage.waitForNotificationErrorMessage(
            String.format("Connection %s already exists. Use another connection name.", CONNECTION_ID));
    }

    @Test
    void addUpdateAndDeleteConnection()
    {
        // Use a connection of its own, so that the shared connection keeps working for the other tests.
        String name = "temporary";
        String newName = "temporary2";
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection(name, this.openProject.getBaseUrl(), "someid", "somesecret");
        adminPage.waitForNotificationSuccessMessage(String.format(SAVED_MESSAGE, name));

        int index = adminPage.getConnectionIndex(name);
        assertTrue(index > 0);
        TableLayoutElement livedata = adminPage.getConnectionsLivedata().getTableLayout();
        assertEquals("Not yet authorized", livedata.getCell(AUTHORIZED_COLUMN, index).getText());
        assertEquals(3, livedata.getCell(ACTIONS_COLUMN, index).findElements(By.className("action")).size());

        adminPage.updateConnection(index, newName, "asd", "asd", "asd");
        adminPage.waitForNotificationSuccessMessage(String.format(SAVED_MESSAGE, newName));
        assertEquals(-1, adminPage.getConnectionIndex(name));
        index = adminPage.getConnectionIndex(newName);
        assertTrue(index > 0);

        adminPage.deleteConnection(index);
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been deleted!", newName));
        assertEquals(-1, adminPage.getConnectionIndex(newName));
        assertTrue(adminPage.getConnectionIndex(CONNECTION_ID) > 0);
    }

    @Test
    void styleSyncJob(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        setup.createPage(testReference,
            String.format("{{openproject instance=\"%s\" id=\"styleSyncJob\"/}}", CONNECTION_ID));

        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.triggerColorSyncJob();
        assertTrue(adminPage.isColorSyncJobTriggered());
        // Workaround to make the scheduler job work during tests. The styles are generated synchronously.
        setup.gotoPage(List.of("StartStylingJob"), "WebHome", "edit", Collections.singletonMap("force", 1));
        WikiEditPage editPage = new WikiEditPage();
        editPage.setContent("{{velocity}}$services.openproject.generateStyling(){{/velocity}}");
        editPage.clickSaveAndView();

        setup.gotoPage(testReference);
        ViewPageWithOpenProjectMacro vp = new ViewPageWithOpenProjectMacro();
        vp.waitUntilPageIsReady();
        List<OpenProjectMacroElement> macros = vp.getOpenProjectMacros();
        assertEquals(1, macros.size());
        macros.get(0).getLivedata().getTableLayout().waitUntilReady();
        // Compare one rendered type property with a known OpenProject color.
        assertEquals("rgb(26, 103, 163)", vp.getPropertyColor("openproject-property-type-Task-" + CONNECTION_ID));
    }

    @Test
    void workPackageSearchMarkerValidation()
    {
        // Markers already used by the editor and markers containing spaces are rejected before saving.
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        String invalidMarkerMessage = "Use 1 to 5 characters without spaces, other than @, /, [[, {{ or img::.";
        for (String invalidMarker : List.of("@", "a b", "")) {
            adminPage.setWorkPackageSearchMarker(invalidMarker, false);
            assertEquals(invalidMarkerMessage, adminPage.getWorkPackageSearchMarkerValidationMessage());
        }

        adminPage.setWorkPackageSearchMarker("!!");
        assertEquals("", adminPage.getWorkPackageSearchMarkerValidationMessage());
    }
}
