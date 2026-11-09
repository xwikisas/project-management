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

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the creation of OpenProject work packages from the content of a page.
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
public class OpenProjectCreateWorkPackagesIT extends AbstractOpenProjectIT
{
    @Test
    @Order(1)
    void createWorkPackagesFromTable(TestUtils setup, TestReference testReference) throws Exception
    {
        // A column name that breaks the HTML of the form when it isn't escaped.
        String column = "<b>Owner</b> & \"Due\" 'date'";
        setup.rest().savePage(testReference, String.format("|=Subject|=%s\n|First task|Alice", column), "");
        setup.gotoPage(testReference);

        CreateWorkPackagesModal form = new ViewPageWithOpenProjectMacro().openCreateWorkPackagesFromTable()
            .selectConnection(CONNECTION_ID)
            .selectProject("Demo project")
            .addMapping();
        assertEquals(List.of("Subject", column), form.getMappingTableColumns());
        List<String> fields = form.getMappingWorkPackageFields();
        assertTrue(fields.contains("Subject"), fields::toString);
    }

    @Test
    @Order(2)
    void createWorkPackagesWithConnectionNameHavingSpecialCharacters(TestUtils setup, TestReference testReference)
        throws Exception
    {
        // Characters that break the URLs of the requests of the form when the connection name isn't encoded.
        String connection = CONNECTION_ID + " #2 & co";
        DocumentReference connectionReference = new DocumentReference("xwiki",
            List.of("OpenProject", "Code", "OpenProjectConfigurations"), "SpecialCharactersConnection");
        addConnection(setup, connectionReference, connection);
        try {
            OpenProjectAdminPage.gotoPage().authorizeConnection(connection);
            this.openProject.authorize(setup.getDriver());
            setup.rest().savePage(testReference, "|=Subject\n|First task", "");
            setup.gotoPage(testReference);

            CreateWorkPackagesModal form = new ViewPageWithOpenProjectMacro().openCreateWorkPackagesFromTable()
                .selectConnection(connection)
                .selectProject("Demo project")
                .addMapping();
            List<String> fields = form.getMappingWorkPackageFields();
            assertTrue(fields.contains("Subject"), fields::toString);
        } finally {
            setup.rest().delete(connectionReference);
        }
    }

    /**
     * Add a connection to the shared OpenProject instance through the REST endpoint used by the administration, which
     * takes JSON, unlike the REST API of the test utilities.
     */
    private void addConnection(TestUtils setup, DocumentReference reference, String name) throws Exception
    {
        // The URL of the REST API as seen from the tests, which may differ from the one seen by the browser.
        String webAppURL = StringUtils.removeEnd(StringUtils.removeEnd(setup.rest().getBaseURL(), "/"), "rest");
        String url = String.format("%srest/wikis/%s/spaces/%s/pages/%s/openproject/configurations", webAppURL,
            reference.getWikiReference().getName(), String.join("/spaces/", reference.getSpaceReferences().stream()
                .map(space -> space.getName()).toArray(String[]::new)), reference.getName());
        String body = new ObjectMapper().writeValueAsString(Map.of("connectionName", name, "serverURL",
            this.openProject.getBaseUrl(), "clientId", this.openProject.getClientId(), "clientSecret",
            this.openProject.getClientSecret()));
        String credentials = TestUtils.SUPER_ADMIN_CREDENTIALS.getUserName() + ':'
            + TestUtils.SUPER_ADMIN_CREDENTIALS.getPassword();
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url))
            .header("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() < 300, response::body);
    }
}
