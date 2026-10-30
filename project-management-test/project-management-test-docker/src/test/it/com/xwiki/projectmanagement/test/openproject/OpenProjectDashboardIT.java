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

import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the OpenProject dashboard.
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
public class OpenProjectDashboardIT extends AbstractOpenProjectIT
{
    @Test
    @Order(1)
    void authorizationWarningMatchesTheSelectedConnection()
    {
        // A second connection, that the current user hasn't authorized.
        String unauthorized = "dashboard-unauthorized";
        OpenProjectAdminPage.gotoPage()
            .addNewConnection(unauthorized, this.openProject.getBaseUrl(), "someid", "somesecret");

        OpenProjectDashboardPage dashboard = OpenProjectDashboardPage.gotoPage();
        if (!CONNECTION_ID.equals(dashboard.getSelectedConnection())) {
            dashboard = dashboard.selectConnection(CONNECTION_ID);
        }
        dashboard.waitUntilGadgetsAreDisplayed();
        assertEquals(List.of(), dashboard.getWarnings());
        assertTrue(dashboard.hasProjectPicker());
        assertThat(dashboard.getGadgetTitles(), containsInAnyOrder("Open VS Closed", "Project Details", "Ticket types",
            "Project members", "Open vs Closed Per Type", "Spent time last 7 days", "Project News", "SubProjects",
            "All Work Packages"));

        // Only the warning of the selected connection is displayed.
        dashboard = dashboard.selectConnection(unauthorized);
        assertEquals(unauthorized, dashboard.getSelectedConnection());
        assertEquals(1, dashboard.getWarnings().size());
        assertEquals(List.of(unauthorized), dashboard.getConnectionsToAuthorize());
        assertFalse(dashboard.hasProjectPicker());

        // The warning of the previous connection doesn't stay.
        dashboard = dashboard.selectConnection(CONNECTION_ID).waitUntilGadgetsAreDisplayed();
        assertEquals(CONNECTION_ID, dashboard.getSelectedConnection());
        assertEquals(List.of(), dashboard.getWarnings());

        // When the selected connection is deleted, the dashboard falls back on the first connection, and doesn't
        // display the warning of the deleted connection.
        dashboard.selectConnection(unauthorized);
        OpenProjectAdminPage.gotoPage().deleteConnection(unauthorized);

        dashboard = OpenProjectDashboardPage.gotoPage();
        // The connection picker is disabled when there is a single connection.
        assertEquals(CONNECTION_ID, dashboard.getSelectedConnection());
        dashboard.waitUntilGadgetsAreDisplayed();
        assertEquals(List.of(), dashboard.getWarnings());
        assertTrue(dashboard.hasProjectPicker());
    }

    @Test
    @Order(2)
    void selectedProjectIsRemembered(TestUtils setup) throws Exception
    {
        OpenProjectDashboardPage dashboard = OpenProjectDashboardPage.gotoPage();
        assertEquals(CONNECTION_ID, dashboard.getSelectedConnection());
        // Not the first project, which the dashboard displays (without saving it) until a project is selected.
        dashboard = dashboard.selectProject("Demo project").waitUntilGadgetsAreDisplayed();
        assertEquals("Demo project", dashboard.getSelectedProject());

        // The name of the project is saved with its id, so that the picker displays it without asking OpenProject.
        DocumentReference dashboardReference = new DocumentReference("xwiki", "OpenProject", "WebHome");
        org.xwiki.rest.model.jaxb.Object config = setup.rest()
            .get(new ObjectReference("OpenProject.Code.DashboardConnectionConfigClass[0]", dashboardReference));
        assertEquals("Demo project",
            TestUtils.RestTestUtils.getProperty("selectedProjectLabel", config, false).getValue());

        dashboard = OpenProjectDashboardPage.gotoPage();
        assertEquals("Demo project", dashboard.getSelectedProject());
    }
}
