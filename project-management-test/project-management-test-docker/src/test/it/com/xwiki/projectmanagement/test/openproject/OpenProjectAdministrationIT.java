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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the administration section of the OpenProject integration.
 *
 * @version $Id$
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

    private static final String SAVED_MESSAGE = "Connection %s has been saved!";

    private static final String CONNECTIONS_SPACE = "OpenProject.Code.OpenProjectConfigurations";

    @Test
    @Order(1)
    void connectionIsConfiguredAndAuthorized()
    {
        // The connection is added and authorized by the OpenProjectFixture.
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        int index = adminPage.getConnectionIndex(CONNECTION_ID);
        assertThat(index, greaterThan(0));
        TableLayoutElement livedata = adminPage.getConnectionsLivedata().getTableLayout();
        assertEquals("Authorized", livedata.getCell(AUTHORIZED_COLUMN, index).getText());
        assertEquals(2, adminPage.getConnectionActionCount(index));
    }

    @Test
    @Order(2)
    void tryToAddInvalidConnection()
    {
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection(CONNECTION_ID, this.openProject.getBaseUrl(), "someid", "somesecret");
        adminPage.waitForNotificationErrorMessage(
            String.format("Connection %s already exists. Use another connection name.", CONNECTION_ID));

        // The server URL must be an absolute HTTP or HTTPS URL.
        adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection("invalidURL", "someurl", "someid", "somesecret");
        adminPage.waitForNotificationErrorMessage("The server URL [someurl] is not valid: it must be an absolute HTTP "
            + "or HTTPS URL, e.g. [https://openproject.example.com].");
        adminPage = OpenProjectAdminPage.gotoPage();
        assertEquals(-1, adminPage.getConnectionIndex("invalidURL"));
    }

    @Test
    @Order(3)
    void addUpdateAndDeleteConnection()
    {
        // Use a connection of its own, so that the shared connection keeps working for the other tests.
        String name = "temporary";
        String newName = "temporary2";
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection(name, this.openProject.getBaseUrl(), "someid", "somesecret");
        adminPage.waitForNotificationSuccessMessage(String.format(SAVED_MESSAGE, name));

        int index = adminPage.getConnectionIndex(name);
        assertThat(index, greaterThan(0));
        TableLayoutElement livedata = adminPage.getConnectionsLivedata().getTableLayout();
        assertEquals("Not yet authorized", livedata.getCell(AUTHORIZED_COLUMN, index).getText());
        assertEquals(3, adminPage.getConnectionActionCount(index));

        adminPage.updateConnection(index, newName, this.openProject.getBaseUrl(), "asd", "asd");
        adminPage.waitForNotificationSuccessMessage(String.format(SAVED_MESSAGE, newName));
        assertEquals(-1, adminPage.getConnectionIndex(name));
        index = adminPage.getConnectionIndex(newName);
        assertThat(index, greaterThan(0));

        adminPage.deleteConnection(index);
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been deleted!", newName));
        assertEquals(-1, adminPage.getConnectionIndex(newName));
        assertThat(adminPage.getConnectionIndex(CONNECTION_ID), greaterThan(0));
    }

    @Test
    @Order(4)
    void styleSyncJob(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        setup.createPage(testReference,
            String.format("{{openproject instance=\"%s\" id=\"styleSyncJob\"/}}", CONNECTION_ID));

        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.triggerColorSyncJob();
        assertTrue(adminPage.isColorSyncJobTriggered());
        // The scheduler doesn't run the job during the test, so the styles are generated synchronously, by a page
        // that is rendered when saved.
        setup.createPage(List.of("StartStylingJob"), "WebHome",
            "{{velocity}}$services.openproject.generateStyling(){{/velocity}}", "");

        setup.gotoPage(testReference);
        ViewPageWithOpenProjectMacro vp = new ViewPageWithOpenProjectMacro();
        vp.waitUntilPageIsReady();
        List<OpenProjectMacroElement> macros = vp.getOpenProjectMacros();
        assertEquals(1, macros.size());
        macros.get(0).getLivedata().getTableLayout().waitUntilReady();
        // Compare one rendered type property with a known OpenProject color.
        assertEquals("rgb(26, 103, 163)", vp.getPropertyColor("type", "Task", CONNECTION_ID));
    }

    @Test
    @Order(5)
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

    @Test
    @Order(6)
    void connectionPageIsOnlyAvailableToAdministrators(TestUtils setup) throws Exception
    {
        String name = "restricted";
        String clientSecret = "initialSecret";
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection(name, this.openProject.getBaseUrl(), "someid", clientSecret);
        adminPage.waitForNotificationSuccessMessage(String.format(SAVED_MESSAGE, name));
        // The form explains that the client secret is not displayed when editing a connection.
        assertEquals("", adminPage.getNewConnectionClientSecretHint());
        assertEquals("The current client secret is not displayed, but it is still set. Leave this field empty to keep "
            + "it, or enter a new value to replace it.",
            adminPage.getEditConnectionClientSecretHint(adminPage.getConnectionIndex(name)));
        // Leaving the client secret empty keeps the current one.
        adminPage.updateConnection(adminPage.getConnectionIndex(name), name, this.openProject.getBaseUrl(), "someid",
            "");
        adminPage.waitForNotificationSuccessMessage(String.format(SAVED_MESSAGE, name));

        // Reading the client secret from a script requires programming right (see PRCHECKER_EXCLUDE_PROPERTY).
        UsernamePasswordCredentials credentials = setup.setDefaultCredentials(TestUtils.SUPER_ADMIN_CREDENTIALS);
        String connectionInfo;
        try {
            connectionInfo = setup.executeWiki(String.join("\n",
                "{{velocity}}",
                "#set ($statement = 'from doc.object(OpenProject.Code.OpenProjectConnectionClass) as connection "
                    + "where connection.connectionName = :name')",
                "#foreach ($reference in $services.query.xwql($statement).bindValue('name', '" + name + "').execute())",
                "#set ($connectionDocument = $xwiki.getDocument($reference))",
                "$reference|$connectionDocument.getObject('OpenProject.Code.OpenProjectConnectionClass')"
                    + ".getValue('clientSecret')|$connectionDocument.getObject('XWiki.OIDC.ClientConfigurationClass')"
                    + ".getValue('clientSecret')",
                "#end",
                "{{/velocity}}"), Syntax.XWIKI_2_1);
        } finally {
            setup.setDefaultCredentials(credentials);
        }
        // Remove the paragraph markup around the result.
        String[] connection = connectionInfo.replaceAll("<[^>]+>", "").trim().split("\\|");
        assertEquals(3, connection.length);
        assertEquals(CONNECTIONS_SPACE, connection[0].substring(0, connection[0].lastIndexOf('.')));
        assertEquals(clientSecret, connection[1]);
        assertEquals(clientSecret, connection[2]);

        DocumentReference connectionReference = new DocumentReference("xwiki",
            List.of("OpenProject", "Code", "OpenProjectConfigurations"),
            connection[0].substring(CONNECTIONS_SPACE.length() + 1));
        // The client secret is not returned by the REST API.
        org.xwiki.rest.model.jaxb.Object connectionObject =
            setup.rest().get(new ObjectReference("OpenProject.Code.OpenProjectConnectionClass[0]",
                connectionReference));
        assertNotEquals(clientSecret, TestUtils.RestTestUtils.getProperty("clientSecret", connectionObject, false)
            .getValue());

        // Guests can't view the connection page. Use a client without the session of the administrator.
        HttpClient guestClient = HttpClient.newHttpClient();
        // The URL of the REST API as seen from the tests, which may differ from the one seen by the browser.
        String webAppURL = StringUtils.removeEnd(StringUtils.removeEnd(setup.rest().getBaseURL(), "/"), "rest");
        String pageName = connectionReference.getName();
        HttpResponse<String> response = guestClient.send(HttpRequest.newBuilder(URI.create(webAppURL
            + "rest/wikis/xwiki/spaces/OpenProject/spaces/Code/spaces/OpenProjectConfigurations/pages/" + pageName
            + "/objects/OpenProject.Code.OpenProjectConnectionClass/0")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertFalse(response.body().contains(clientSecret));
        // Guests are redirected to the login page.
        response = guestClient.send(HttpRequest.newBuilder(URI.create(webAppURL
            + "bin/view/OpenProject/Code/OpenProjectConfigurations/" + pageName + "?xpage=xml")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElse("").contains("/login/"));
        assertFalse(response.body().contains(clientSecret));

        adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.deleteConnection(adminPage.getConnectionIndex(name));
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been deleted!", name));
    }

    @Test
    @Order(7)
    void codePagesCanOnlyBeEditedByAdministrators(TestUtils setup) throws Exception
    {
        String userName = "CodeEditor";
        setup.createUser(userName, "pass", null);

        String rights = setup.executeWiki(String.join("\n",
            "{{velocity}}",
            "#set ($user = $services.model.resolveDocument('XWiki." + userName + "'))",
            "#foreach ($page in ['OpenProject.Code.Template.WebHome', "
                + "'OpenProject.Code.Template.Project Specifications.WebHome', 'OpenProject.Code.TemplateProvider', "
                + "'OpenProject.Code.StylingSetupJob', 'OpenProject.Code.StyleSheets.main', 'Main.WebHome'])",
            "#set ($reference = $services.model.resolveDocument($page))",
            "$page=$services.security.authorization.hasAccess('edit', $user, $reference)"
                + "/$services.security.authorization.hasAccess('view', $user, $reference)",
            "#end",
            "{{/velocity}}"), Syntax.XWIKI_2_1);

        // Remove the markup around the result.
        String result = rights.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        assertEquals("OpenProject.Code.Template.WebHome=false/true "
            + "OpenProject.Code.Template.Project Specifications.WebHome=false/true "
            + "OpenProject.Code.TemplateProvider=false/true OpenProject.Code.StylingSetupJob=false/true "
            + "OpenProject.Code.StyleSheets.main=false/true Main.WebHome=true/true", result);
    }
}
