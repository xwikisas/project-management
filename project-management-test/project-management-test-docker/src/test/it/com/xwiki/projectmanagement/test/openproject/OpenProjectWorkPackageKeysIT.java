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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the work package keys (e.g. {@code PROJ-123}) of the OpenProject instances using project-based semantic
 * identifiers.
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
public class OpenProjectWorkPackageKeysIT extends AbstractOpenProjectIT
{
    private static final String CONNECTION = OpenProjectFixture.SEMANTIC_CONNECTION_ID;

    private OpenProjectInstance semanticOpenProject;

    @BeforeAll
    void setUpSemanticOpenProject(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {
        this.semanticOpenProject = OpenProjectFixture.ensureSemanticReady(setup, testConfiguration);
    }

    @AfterAll
    void tearDownSemanticOpenProject()
    {
        // No other test uses this instance.
        OpenProjectFixture.stopSemantic();
    }

    @Test
    @Order(1)
    void displayWorkPackagesFromTheirKeys(TestUtils setup, TestReference testReference) throws Exception
    {
        // Work packages of the two demo projects, which have different keys.
        List<JsonNode> demoProjectWorkPackages = getWorkPackages("1");
        JsonNode first = demoProjectWorkPackages.get(0);
        JsonNode second = demoProjectWorkPackages.get(1);
        JsonNode other = getWorkPackages("2").get(0);

        // Keys mixed with an id, and with a key that doesn't match any work package, which is skipped.
        String identifier = String.join(",", getKey(other), first.get("id").asText(), "UNKNOWN-1", getKey(second));
        setup.createPage(testReference, String.format("{{openproject instance=\"%1$s\" id=\"keys\" identifier=\"%2$s\"/}}"
            + "\n\n{{openproject instance=\"%1$s\" identifier=\"%3$s\" workItemsDisplayer=\"workItemsSingle\"/}}",
            CONNECTION, identifier, getKey(second)));

        List<OpenProjectMacroElement> macros = new ViewPageWithOpenProjectMacro().getOpenProjectMacros();
        assertEquals(2, macros.size());

        // The work packages are displayed in the order of the identifier.
        TableLayoutElement table = macros.get(0).waitForTableLayout();
        assertEquals(3, table.countRows());
        assertEquals(other.get("id").asText(), table.getCell("ID", 1).getText());
        assertEquals(first.get("id").asText(), table.getCell("ID", 2).getText());
        assertEquals(second.get("id").asText(), table.getCell("ID", 3).getText());

        OpenProjectSingleDisplayer single = macros.get(1).getSingleWorkItem().waitUntilReady();
        assertThat(single.getHeaderText(), containsString(second.get("subject").asText()));
    }

    /**
     * @param projectId the id of a project
     * @return the work packages of the project, the oldest first
     */
    private List<JsonNode> getWorkPackages(String projectId) throws Exception
    {
        JsonNode workPackages = this.semanticOpenProject.getApiResource(
            String.format("/projects/%s/work_packages?pageSize=2&filters=%s&sortBy=%s", projectId,
                URLEncoder.encode("[]", StandardCharsets.UTF_8),
                URLEncoder.encode("[[\"id\",\"asc\"]]", StandardCharsets.UTF_8)));
        List<JsonNode> result = new ArrayList<>();
        workPackages.get("_embedded").get("elements").forEach(result::add);
        return result;
    }

    private static String getKey(JsonNode workPackage)
    {
        // The key is the identifier displayed by OpenProject, e.g. DP-1.
        return workPackage.get("displayId").asText();
    }
}
