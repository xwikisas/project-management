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

import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.ui.TestUtils;

/**
 * Shares a single OpenProject instance, along with its OAuth application, between all the functional test classes
 * running in the same JVM, and makes sure that the XWiki instance under test has an authorized connection to it.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public final class OpenProjectFixture
{
    /**
     * The name of the connection to the OpenProject instance that is configured in XWiki.
     */
    public static final String CONNECTION_ID = "test";

    private static OpenProjectInstance instance;

    private static boolean connectionConfigured;

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
        if (instance == null) {
            OpenProjectInstance newInstance = OpenProjectInstance.fromSystemProperties();
            newInstance.startOpenProject(setup, testConfiguration);
            newInstance.setupInstance(setup.getDriver());
            instance = newInstance;
        }
        ensureConnection(setup);
        return instance;
    }

    private static void ensureConnection(TestUtils setup)
    {
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        int index = adminPage.getConnectionIndex(CONNECTION_ID);
        if (index > 0 && connectionConfigured) {
            return;
        }
        if (index > 0) {
            // The connection was left by a previous run and points to another OpenProject instance or OAuth app.
            adminPage.deleteConnection(index);
            adminPage.waitForNotificationSuccessMessage(
                String.format("Connection %s has been deleted!", CONNECTION_ID));
            adminPage = OpenProjectAdminPage.gotoPage();
        }

        adminPage.addNewConnection(CONNECTION_ID, instance.getBaseUrl(), instance.getClientId(),
            instance.getClientSecret());
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been saved!", CONNECTION_ID));
        adminPage.getConnectionsLivedata().getTableLayout()
            .clickAction(adminPage.getConnectionIndex(CONNECTION_ID), "authorize-connection");
        instance.maybeLogin(setup.getDriver(), false);
        instance.maybeClickAuthorization(setup.getDriver());
        new OpenProjectAdminPage().waitUntilPageIsReady();
        connectionConfigured = true;
    }
}
