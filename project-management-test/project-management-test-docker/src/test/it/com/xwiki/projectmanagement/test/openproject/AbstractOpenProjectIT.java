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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.naming.OperationNotSupportedException;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class of the OpenProject functional tests. All the test classes share the same OpenProject instance, see
 * {@link OpenProjectFixture}.
 * <p>
 * The {@code @UITest} configuration of the subclasses must be the same, so that the XWiki instance is not restarted
 * when they are run together from {@code AllIT}. Use the constants of this class to declare it.
 *
 * @version $Id$
 */
public abstract class AbstractOpenProjectIT
{
    /**
     * The scheduler UI needs programming rights, and so do the scripts executed by the tests to read the client
     * secret of a connection, and the colors job, a Groovy job that the scheduler only runs with programming rights.
     */
    public static final String PRCHECKER_EXCLUDE_PROPERTY = "xwikiPropertiesAdditionalProperties="
        + "test.prchecker.excludePattern="
        + "xwiki:(Scheduler\\.WebHome|Test\\.Execute\\..*|OpenProject\\.Code\\.StylingSetupJob)";

    /**
     * Add the Scheduler plugin used by the Style sync job and the jsrx and ssrx plugins used by the app.
     */
    public static final String PLUGINS_PROPERTY = "xwikiCfgPlugins=com.xpn.xwiki.plugin.scheduler.SchedulerPlugin,"
        + "com.xpn.xwiki.plugin.skinx.JsResourceSkinExtensionPlugin,"
        + "com.xpn.xwiki.plugin.skinx.CssResourceSkinExtensionPlugin";

    /**
     * Because of https://jira.xwiki.org/browse/XWIKI-17972 we need to install the jython jar manually in WEB-INF/lib.
     */
    public static final String JYTHON_JAR = "org.python:jython-slim:2.7.3";

    /**
     * Needed by the scheduler plugin, otherwise it fails. Its version must be the XWiki version under test, the
     * {@code platform.version} of the parent POM: an extra JAR without version gets the version of this project.
     */
    public static final String SCHEDULER_API_JAR = "org.xwiki.platform:xwiki-platform-scheduler-api:15.10";

    protected static final String CONNECTION_ID = OpenProjectFixture.CONNECTION_ID;

    /**
     * The space where the administration saves the connections.
     */
    protected static final List<String> CONNECTIONS_SPACE = List.of("OpenProject", "Code", "OpenProjectConfigurations");

    private static final String SUPER_ADMIN = TestUtils.SUPER_ADMIN_CREDENTIALS.getUserName();

    protected OpenProjectInstance openProject;

    /**
     * Whether the current test enabled the public data of the connection, see {@link #enablePublicData(TestUtils)}.
     */
    private boolean publicDataEnabled;

    @BeforeAll
    void setUpOpenProject(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {
        setup.loginAsSuperAdmin();
        this.openProject = OpenProjectFixture.ensureReady(setup, testConfiguration);
    }

    /**
     * Start each test as superadmin, whoever the previous test left logged in when it failed before switching back.
     *
     * @param setup the test setup
     */
    @BeforeEach
    void startAsSuperAdmin(TestUtils setup)
    {
        String displayedUser = setup.getLoggedInUserName();
        if (displayedUser != null && !SUPER_ADMIN.equals(displayedUser)) {
            switchToSuperAdmin(setup);
        } else {
            // Doesn't load any page when the current page displays superadmin.
            setup.loginAsSuperAdmin();
        }
    }

    /**
     * Restore the access mode of the connection after a test that enabled the public data with
     * {@link #enablePublicData(TestUtils)}, whether it passed or not. Unlike a {@code finally} block, a failure here
     * doesn't hide the failure of the test, and the screenshot and video of a failing test are taken before.
     *
     * @param setup the test setup
     */
    @AfterEach
    void restoreAccessMode(TestUtils setup)
    {
        if (this.publicDataEnabled) {
            // The test instance is shared by all the tests of the class.
            this.publicDataEnabled = false;
            switchToSuperAdmin(setup);
            OpenProjectAdminPage.gotoPage().setAccessMode(CONNECTION_ID, "USER_ACCOUNTS");
        }
    }

    /**
     * Let the users who aren't connected to OpenProject see its public data through the shared connection, until the
     * end of the current test.
     *
     * @param setup the test setup
     */
    protected void enablePublicData(TestUtils setup)
    {
        OpenProjectAdminPage.gotoPage().setAccessMode(CONNECTION_ID, "PUBLIC_DATA_AND_USER_ACCOUNTS");
        this.publicDataEnabled = true;
    }

    /**
     * Log in as another user, whoever is logged in.
     * <p>
     * {@link TestUtils#loginAndGotoPage(String, String, String)} logs in on the session of the current user, which
     * XWiki invalidates. A request of the current page that the server handles after the login, with the cookies of the
     * current user (e.g. a Live Data or avatar request, or the unlock beacon of the in-place editor), is then
     * authenticated again from these cookies, in a new session whose cookie replaces the one of the new user: the
     * browser stays logged in as the current user. Dropping the cookies first makes the login start a new session, and
     * leaves the session of the current user valid for its pending requests, which then don't change the session
     * cookie.
     *
     * @param setup the test setup
     * @param username the user to log in
     * @param password the password of the user
     * @param pageURL the page to go to after logging in, or {@code null} for a page that loads fast
     */
    protected static void switchUser(TestUtils setup, String username, String password, String pageURL)
    {
        // Also reloads the current page as guest, so that the login isn't skipped because the page displays the user.
        setup.forceGuestUser();
        setup.loginAndGotoPage(username, password, pageURL);
    }

    /**
     * Log in as superadmin after a test logged in as another user, see
     * {@link #switchUser(TestUtils, String, String, String)}.
     *
     * @param setup the test setup
     */
    protected static void switchToSuperAdmin(TestUtils setup)
    {
        switchUser(setup, SUPER_ADMIN, TestUtils.SUPER_ADMIN_CREDENTIALS.getPassword(), null);
    }

    /**
     * @param pageName the name of the page holding a connection
     * @return the reference of the page holding the connection, in the space where the administration saves them
     */
    protected static DocumentReference getConnectionReference(String pageName)
    {
        return new DocumentReference("xwiki", CONNECTIONS_SPACE, pageName);
    }

    /**
     * Add or update a connection to the shared OpenProject instance, using its OAuth application, through the REST
     * endpoint used by the administration. Use it to build the connections a test needs without filling the
     * connection form, which {@code OpenProjectAdministrationIT} tests.
     *
     * @param setup the test setup
     * @param reference the page holding the connection
     * @param name the name of the connection
     * @throws Exception if the connection isn't saved
     */
    protected void saveConnection(TestUtils setup, DocumentReference reference, String name) throws Exception
    {
        saveConnection(setup, reference, name, this.openProject.getClientId(), this.openProject.getClientSecret());
    }

    /**
     * Add or update a connection to the shared OpenProject instance through the REST endpoint used by the
     * administration, which takes JSON, unlike the REST API of the test utilities. Like the connection form, it creates
     * the connection with {@code POST} and updates an existing one with {@code PUT}.
     *
     * @param setup the test setup
     * @param reference the page holding the connection
     * @param name the name of the connection
     * @param clientId the client id of the OAuth application
     * @param clientSecret the client secret of the OAuth application, or an empty string to keep the current one
     * @throws Exception if the connection isn't saved
     */
    protected void saveConnection(TestUtils setup, DocumentReference reference, String name, String clientId,
        String clientSecret) throws Exception
    {
        // The URL of the REST API as seen from the tests, which may differ from the one seen by the browser.
        String webAppURL = StringUtils.removeEnd(StringUtils.removeEnd(setup.rest().getBaseURL(), "/"), "rest");
        String url = String.format("%srest/wikis/%s/spaces/%s/pages/%s/openproject/configurations", webAppURL,
            reference.getWikiReference().getName(), String.join("/spaces/", reference.getSpaceReferences().stream()
                .map(EntityReference::getName).toArray(String[]::new)), reference.getName());
        String body = new ObjectMapper().writeValueAsString(Map.of("connectionName", name, "serverURL",
            this.openProject.getBaseUrl(), "clientId", clientId, "clientSecret", clientSecret));
        String credentials = TestUtils.SUPER_ADMIN_CREDENTIALS.getUserName() + ':'
            + TestUtils.SUPER_ADMIN_CREDENTIALS.getPassword();
        String method = setup.rest().exists(reference) ? "PUT" : "POST";
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url))
            .header("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() < 300, response::body);
    }

    /**
     * Delete the page holding a connection, if it exists.
     *
     * @param setup the test setup
     * @param reference the page holding the connection
     * @throws Exception if the page can't be deleted
     */
    protected static void deleteConnection(TestUtils setup, DocumentReference reference) throws Exception
    {
        if (setup.rest().exists(reference)) {
            setup.rest().delete(reference);
        }
    }

    protected static TableLayoutElement saveAndGetFirstOPMacro(WYSIWYGEditPage editPage)
        throws OperationNotSupportedException
    {
        editPage.clickSaveAndView();
        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();

        List<OpenProjectMacroElement> macros = page.getOpenProjectMacros();
        assertEquals(1, macros.size());
        return macros.get(0).waitForTableLayout();
    }

    protected static String getPaginationEntriesText(OpenProjectMacroElement macro)
    {
        try {
            macro.waitForTableLayout();
        } catch (OperationNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        return macro.getPaginationEntriesText();
    }

    protected static void selectInstanceFromModal(OpenProjectMacroEditModal macroModal, String connection)
    {
        macroModal.selectInstance(connection);
        // The Live Data page object needs the macro to have an id.
        macroModal.setRandomId();
    }
}
