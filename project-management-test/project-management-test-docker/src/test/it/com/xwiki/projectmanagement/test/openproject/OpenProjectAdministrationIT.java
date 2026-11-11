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

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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
    private static final List<String> STYLE_SHEETS_SPACE = List.of("OpenProject", "Code", "StyleSheets");

    @Test
    @Order(1)
    void connectionIsConfiguredAndAuthorized()
    {
        // The connection is added and authorized by the OpenProjectFixture.
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        assertTrue(adminPage.isConnectionListed(CONNECTION_ID));
        assertEquals("Authorized", adminPage.getConnectionAuthorization(CONNECTION_ID));
        assertEquals(2, adminPage.getConnectionActionCount(CONNECTION_ID));
    }

    @Test
    @Order(2)
    void tryToAddInvalidConnection()
    {
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.submitNewConnection(CONNECTION_ID, this.openProject.getBaseUrl(), "someid", "somesecret");
        adminPage.waitForNotificationErrorMessage(
            String.format("Connection %s already exists. Use another connection name.", CONNECTION_ID));

        // The server URL must be an absolute HTTP or HTTPS URL.
        adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.submitNewConnection("invalidURL", "someurl", "someid", "somesecret");
        adminPage.waitForNotificationErrorMessage("The server URL [someurl] is not valid: it must be an absolute HTTP "
            + "or HTTPS URL, e.g. [https://openproject.example.com].");
        adminPage = OpenProjectAdminPage.gotoPage();
        assertFalse(adminPage.isConnectionListed("invalidURL"));
    }

    @Test
    @Order(3)
    void addUpdateAndDeleteConnection(TestUtils setup)
    {
        // Use a connection of its own, so that the shared connection keeps working for the other tests.
        String name = "temporary";
        String newName = "temporary2";
        resetDefaultConnection(setup);
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        // No connection is chosen as the default one while there is a single connection.
        assertEquals("", adminPage.getDefaultConnection());
        // The connection actions wait for the saved or deleted connection to be listed or removed.
        adminPage.addNewConnection(name, this.openProject.getBaseUrl(), "someid", "somesecret");
        // Adding a second connection makes the first one the default connection, and the choices of the default
        // connection are updated without reloading the page.
        assertEquals(CONNECTION_ID, adminPage.getDefaultConnection());
        assertTrue(adminPage.isConnectionListed(name));
        assertEquals("Not yet authorized", adminPage.getConnectionAuthorization(name));
        assertEquals(3, adminPage.getConnectionActionCount(name));

        adminPage.updateConnection(name, newName, this.openProject.getBaseUrl(), "asd", "asd");
        assertFalse(adminPage.isConnectionListed(name));
        assertTrue(adminPage.isConnectionListed(newName));

        adminPage.deleteConnection(newName);
        assertFalse(adminPage.isConnectionListed(newName));
        assertTrue(adminPage.isConnectionListed(CONNECTION_ID));
    }

    @Test
    @Order(4)
    void styleSyncJob(TestUtils setup, TestReference testReference) throws Exception
    {
        // Saving a connection runs the colors job. The current user isn't connected to this connection: its statuses
        // and types are read without credentials.
        String notConnected = "styles-not-connected";
        DocumentReference notConnectedReference = getConnectionReference("StylesNotConnectedConnection");
        saveConnection(setup, notConnectedReference, notConnected);
        try {
            DocumentReference stylesReference = new DocumentReference("xwiki", STYLE_SHEETS_SPACE, notConnected);
            // Compare one generated type color with a known OpenProject color.
            String taskColor = "color: #1a67a3";
            assertThat(waitForStyleSheet(setup, stylesReference), containsString(taskColor));

            // The "Sync colors" button runs the same job: it generates the colors again once they're deleted.
            setup.rest().delete(stylesReference);
            OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
            adminPage.triggerColorSyncJob();
            assertTrue(adminPage.isColorSyncJobTriggered());
            assertThat(waitForStyleSheet(setup, stylesReference), containsString(taskColor));

            // The job generates the colors of every connection, and the macros display them.
            waitForStyleSheet(setup, new DocumentReference("xwiki", STYLE_SHEETS_SPACE, CONNECTION_ID));
            setup.createPage(testReference,
                String.format("{{openproject instance=\"%s\" id=\"styleSyncJob\"/}}", CONNECTION_ID));
            assertEquals("rgb(26, 103, 163)",
                new ViewPageWithOpenProjectMacro().getPropertyColor("type", "Task", CONNECTION_ID));
        } finally {
            deleteConnection(setup, notConnectedReference);
        }
    }

    /**
     * The colors job runs in a scheduler thread: wait for the style sheet it generates.
     *
     * @return the lowercase code of the style sheet
     */
    private static String waitForStyleSheet(TestUtils setup, DocumentReference stylesReference) throws Exception
    {
        ObjectReference styleSheetReference = new ObjectReference("XWiki.StyleSheetExtension[0]", stylesReference);
        long deadline = System.currentTimeMillis() + 60_000;
        org.xwiki.rest.model.jaxb.Object styleSheet = setup.rest().get(styleSheetReference, false);
        while (styleSheet == null) {
            assertTrue(System.currentTimeMillis() < deadline,
                String.format("The colors job didn't generate the style sheet [%s].", styleSheetReference));
            Thread.sleep(500);
            styleSheet = setup.rest().get(styleSheetReference, false);
        }
        return TestUtils.RestTestUtils.getProperty("code", styleSheet, false).getValue().toLowerCase();
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
        DocumentReference connectionReference = getConnectionReference("RestrictedConnection");
        saveConnection(setup, connectionReference, name, "someid", clientSecret);
        try {
            OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
            // The form explains that the client secret is not displayed when editing a connection.
            assertEquals("", adminPage.getNewConnectionClientSecretHint());
            assertEquals("Leave empty to keep the current secret.", adminPage.getEditConnectionClientSecretHint(name));
            // Leaving the client secret empty keeps the current one.
            adminPage.updateConnection(name, name, this.openProject.getBaseUrl(), "someid", "");

            // Reading the client secret from a script requires programming right (see PRCHECKER_EXCLUDE_PROPERTY).
            UsernamePasswordCredentials credentials = setup.setDefaultCredentials(TestUtils.SUPER_ADMIN_CREDENTIALS);
            String connectionInfo;
            try {
                connectionInfo = setup.executeWiki(String.join("\n",
                    "{{velocity}}",
                    "#set ($connectionDocument = $xwiki.getDocument('" + connectionReference + "'))",
                    "$connectionDocument.getObject('OpenProject.Code.OpenProjectConnectionClass')"
                        + ".getValue('clientSecret')"
                        + "|$connectionDocument.getObject('XWiki.OIDC.ClientConfigurationClass')"
                        + ".getValue('clientSecret')",
                    "{{/velocity}}"), Syntax.XWIKI_2_1);
            } finally {
                setup.setDefaultCredentials(credentials);
            }
            // Remove the paragraph markup around the result.
            String[] secrets = connectionInfo.replaceAll("<[^>]+>", "").trim().split("\\|");
            assertEquals(List.of(clientSecret, clientSecret), List.of(secrets));

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
                + "/objects/OpenProject.Code.OpenProjectConnectionClass/0")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
            assertFalse(response.body().contains(clientSecret));
            // Guests are redirected to the login page.
            response = guestClient.send(HttpRequest.newBuilder(URI.create(webAppURL
                + "bin/view/OpenProject/Code/OpenProjectConfigurations/" + pageName + "?xpage=xml")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(302, response.statusCode());
            assertTrue(response.headers().firstValue("Location").orElse("").contains("/login/"));
            assertFalse(response.body().contains(clientSecret));
        } finally {
            deleteConnection(setup, connectionReference);
        }
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

    @Test
    @Order(8)
    void defaultConnection(TestUtils setup, TestReference testReference) throws Exception
    {
        // Start from the shared connection alone, without default connection: the connections added by the other
        // tests made it the default one.
        resetDefaultConnection(setup);

        // A macro that doesn't set its instance parameter uses the only connection.
        setup.createPage(testReference, "{{openproject id=\"defaultConnection\"/}}");
        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();
        assertFalse(page.getOpenProjectMacros().get(0).waitForLiveDataRows().isEmpty());

        // The other connections are added, renamed and removed through the REST endpoint of the administration, whose
        // form is tested by addUpdateAndDeleteConnection.
        String otherConnection = "other";
        String renamedConnection = "renamed";
        String thirdConnection = "third";
        DocumentReference otherReference = getConnectionReference("DefaultConnectionOther");
        DocumentReference thirdReference = getConnectionReference("DefaultConnectionThird");
        try {
            // Adding a second connection makes the first one the default connection, so that the macro keeps
            // using it.
            saveConnection(setup, otherReference, otherConnection);
            OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
            assertEquals(CONNECTION_ID, adminPage.getDefaultConnection());
            setup.gotoPage(testReference);
            page = new ViewPageWithOpenProjectMacro();
            assertFalse(page.getOpenProjectMacros().get(0).waitForLiveDataRows().isEmpty());

            // The default connection stays the default one when it's renamed, and when another connection is added.
            adminPage = OpenProjectAdminPage.gotoPage();
            adminPage.setDefaultConnection(otherConnection);
            saveConnection(setup, otherReference, renamedConnection);
            saveConnection(setup, thirdReference, thirdConnection);
            adminPage = OpenProjectAdminPage.gotoPage();
            assertEquals(renamedConnection, adminPage.getDefaultConnection());

            // Deleting the default connection warns that the macros that don't specify a connection stop working, and
            // clears the default connection.
            assertEquals("", adminPage.getConnectionDeletionWarning(thirdConnection));
            assertEquals("This is the default connection: the OpenProject macros that don't specify a connection "
                + "will stop working until another default connection is chosen.",
                adminPage.getConnectionDeletionWarning(renamedConnection));
            adminPage.deleteConnection(renamedConnection);
            assertEquals("", adminPage.getDefaultConnection());
            setup.gotoPage(testReference);
            page = new ViewPageWithOpenProjectMacro();
            List<String> warnings = page.getWarnings();
            assertEquals(1, warnings.size());
            assertThat(warnings.get(0), startsWith("Choose the OpenProject connection to use with the instance "
                + "parameter, among: "));
            assertThat(warnings.get(0), containsString(CONNECTION_ID));
            assertThat(warnings.get(0), containsString(thirdConnection));

            // When a single connection would remain, the macros would use it.
            adminPage = OpenProjectAdminPage.gotoPage();
            adminPage.setDefaultConnection(thirdConnection);
            adminPage = OpenProjectAdminPage.gotoPage();
            assertEquals(String.format("This is the default connection: the OpenProject macros without a connection "
                + "will then display the data of the %s connection.", CONNECTION_ID),
                adminPage.getConnectionDeletionWarning(thirdConnection));
        } finally {
            deleteConnection(setup, otherReference);
            deleteConnection(setup, thirdReference);
            resetDefaultConnection(setup);
        }
    }

    private static void resetDefaultConnection(TestUtils setup)
    {
        setup.updateObject(List.of("OpenProject", "Code"), "OpenProjectConfiguration",
            "OpenProject.Code.OpenProjectConfigurationClass", 0, "defaultConnection", "");
    }
}
