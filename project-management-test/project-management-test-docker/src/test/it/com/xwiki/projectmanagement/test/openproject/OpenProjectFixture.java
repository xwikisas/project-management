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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.ui.TestUtils;

/**
 * Shares the OpenProject instances, along with their OAuth application, between all the functional test classes
 * running in the same JVM, and makes sure that the XWiki instance under test has an authorized connection to them.
 * <p>
 * Most tests use the same instance, see {@link #ensureReady(TestUtils, TestConfiguration)}. The tests of the "Project
 * based semantic identifiers" use another one, see {@link #ensureSemanticReady(TestUtils, TestConfiguration)}, since
 * this setting applies to the whole instance and changes the identifiers of its projects.
 *
 * @version $Id$
 */
public final class OpenProjectFixture
{
    /**
     * The name of the connection to the OpenProject instance that is configured in XWiki.
     */
    public static final String CONNECTION_ID = "test";

    /**
     * The name of the connection to the OpenProject instance using project-based semantic identifiers.
     */
    public static final String SEMANTIC_CONNECTION_ID = "semantic";

    private static final Map<String, OpenProjectInstance> INSTANCES = new HashMap<>();

    private static final Set<String> CONFIGURED_CONNECTIONS = new HashSet<>();

    private OpenProjectFixture()
    {
    }

    /**
     * Start and set up the OpenProject instance the first time it is called, then make sure the current XWiki instance
     * has an authorized connection to it, named {@value #CONNECTION_ID}. The connection is checked on each call because
     * a test class run on its own starts a new XWiki instance.
     *
     * @param setup the test setup, with a user that is allowed to administer the wiki already logged in
     * @param testConfiguration the configuration of the current test, used to start the OpenProject container
     * @return the shared OpenProject instance
     * @throws Exception if the OpenProject instance fails to start
     */
    public static synchronized OpenProjectInstance ensureReady(TestUtils setup, TestConfiguration testConfiguration)
        throws Exception
    {
        OpenProjectInstance instance = INSTANCES.get(CONNECTION_ID);
        if (instance == null) {
            instance = OpenProjectInstance.fromSystemProperties();
            instance.startOpenProject(setup, testConfiguration);
            instance.setupInstance(setup);
            INSTANCES.put(CONNECTION_ID, instance);
        }
        ensureConnection(setup, CONNECTION_ID, instance);
        return instance;
    }

    /**
     * Same as {@link #ensureReady(TestUtils, TestConfiguration)} for an OpenProject instance, always started in a
     * Docker container, that uses project-based semantic identifiers, with a connection named
     * {@value #SEMANTIC_CONNECTION_ID}.
     *
     * @param setup the test setup, with a user that is allowed to administer the wiki already logged in
     * @param testConfiguration the configuration of the current test, used to start the OpenProject container
     * @return the OpenProject instance using semantic identifiers
     * @throws Exception if the OpenProject instance fails to start
     */
    public static synchronized OpenProjectInstance ensureSemanticReady(TestUtils setup,
        TestConfiguration testConfiguration) throws Exception
    {
        OpenProjectInstance instance = INSTANCES.get(SEMANTIC_CONNECTION_ID);
        if (instance == null) {
            instance = new OpenProjectInstance("openproject-semantic");
            instance.startOpenProject(setup, testConfiguration);
            instance.setupInstance(setup);
            instance.useSemanticIdentifiers(setup.getDriver());
            INSTANCES.put(SEMANTIC_CONNECTION_ID, instance);
        }
        ensureConnection(setup, SEMANTIC_CONNECTION_ID, instance);
        return instance;
    }

    /**
     * Stop the OpenProject instance using project-based semantic identifiers, to free its resources once the tests
     * using it are done. It is started again by the next call to
     * {@link #ensureSemanticReady(TestUtils, TestConfiguration)}.
     */
    public static synchronized void stopSemantic()
    {
        OpenProjectInstance instance = INSTANCES.remove(SEMANTIC_CONNECTION_ID);
        CONFIGURED_CONNECTIONS.remove(SEMANTIC_CONNECTION_ID);
        if (instance != null) {
            instance.stop();
        }
    }

    private static void ensureConnection(TestUtils setup, String connectionId, OpenProjectInstance instance)
    {
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        int index = adminPage.getConnectionIndex(connectionId);
        if (index > 0 && CONFIGURED_CONNECTIONS.contains(connectionId)) {
            return;
        }
        if (index > 0) {
            // The connection was left by a previous run and points to another OpenProject instance or OAuth app.
            adminPage.deleteConnection(index);
            adminPage.waitForNotificationSuccessMessage(
                String.format("Connection %s has been deleted!", connectionId));
            adminPage = OpenProjectAdminPage.gotoPage();
        }

        adminPage.addNewConnection(connectionId, instance.getBaseUrl(), instance.getClientId(),
            instance.getClientSecret());
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been saved!", connectionId));
        adminPage.getConnectionsLivedata().getTableLayout()
            .clickAction(adminPage.getConnectionIndex(connectionId), "authorize-connection");
        instance.authorize(setup.getDriver());
        new OpenProjectAdminPage().waitUntilPageIsReady();
        CONFIGURED_CONNECTIONS.add(connectionId);
    }
}
