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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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
        // Work packages of the two demo projects, which have different keys, and a closed one.
        List<JsonNode> demoProjectWorkPackages = getWorkPackages("1");
        JsonNode first = demoProjectWorkPackages.get(0);
        JsonNode second = demoProjectWorkPackages.get(1);
        JsonNode other = getWorkPackages("2").get(0);
        JsonNode closed = getClosedWorkPackage(List.of(first, second, other));

        // Keys mixed with an id, and with a key that doesn't match any work package, which is skipped.
        String identifier =
            String.join(",", getKey(other), first.get("id").asText(), "UNKNOWN-1", getKey(second), getKey(closed));
        String macro = "{{openproject instance=\"%1$s\" identifier=\"%2$s\"%3$s/}}";
        setup.createPage(testReference, String.join("\n\n",
            String.format(macro, CONNECTION, identifier, " id=\"keys\""),
            String.format(macro, CONNECTION, getKey(second), " workItemsDisplayer=\"workItemsSingle\""),
            String.format(macro, CONNECTION, identifier, " workItemsDisplayer=\"workItemsList\""),
            "Fixed issues: " + String.format(macro, CONNECTION, identifier, " workItemsDisplayer=\"workItemsEnum\"")));

        List<OpenProjectMacroElement> macros = new ViewPageWithOpenProjectMacro().getOpenProjectMacros();
        assertEquals(4, macros.size());

        // The work packages are displayed in the order of the identifier.
        TableLayoutElement table = macros.get(0).waitForTableLayout();
        assertEquals(4, table.countRows());
        assertEquals(other.get("id").asText(), table.getCell("ID", 1).getText());
        assertEquals(first.get("id").asText(), table.getCell("ID", 2).getText());
        assertEquals(second.get("id").asText(), table.getCell("ID", 3).getText());
        assertEquals(closed.get("id").asText(), table.getCell("ID", 4).getText());

        OpenProjectSingleDisplayer single = macros.get(1).getSingleWorkItem().waitUntilReady();
        assertThat(single.getHeaderText(), containsString(second.get("subject").asText()));
        // The header shows the key of the work package instead of its id.
        assertThat(single.getHeaderText(), containsString(getKey(second)));
        assertThat(single.getHeaderText(), not(containsString("#" + second.get("id").asText())));
        // The properties are labelled by name, including the fields specific to the instance (e.g. its custom fields),
        // and neither the key, already in the header, nor whether the work package is closed, given by its status,
        // are repeated.
        List<String> propertyNames = single.getPropertyNames();
        assertThat(propertyNames, hasItem("Status"));
        assertThat(propertyNames, everyItem(not(startsWith("openproject.property."))));
        assertThat(propertyNames, not(hasItem("Resolved")));
        assertThat(propertyNames, not(hasItem("Key")));

        // The list and the enumeration display the work packages by key, even those given by id, and strike through
        // the closed ones.
        List<JsonNode> displayed = List.of(other, first, second, closed);
        List<String> keys = displayed.stream().map(OpenProjectWorkPackageKeysIT::getKey).collect(Collectors.toList());
        Set<String> closedStatuses = getClosedStatuses();
        List<JsonNode> closedDisplayed = displayed.stream()
            .filter(workPackage -> closedStatuses.contains(getStatus(workPackage))).collect(Collectors.toList());

        OpenProjectMacroElement list = macros.get(2);
        assertEquals(displayed.stream().map(OpenProjectWorkPackageKeysIT::getListLine).collect(Collectors.toList()),
            list.getListedWorkPackages());
        assertEquals(keys, list.getWorkPackageLinks());
        assertEquals(closedDisplayed.stream().map(OpenProjectWorkPackageKeysIT::getListLine)
            .collect(Collectors.toList()), list.getClosedWorkPackages());

        OpenProjectMacroElement enumeration = macros.get(3);
        assertEquals(String.join(", ", keys), enumeration.getEnumeratedWorkPackages());
        assertEquals(keys, enumeration.getWorkPackageLinks());
        assertEquals(closedDisplayed.stream().map(OpenProjectWorkPackageKeysIT::getKey).collect(Collectors.toList()),
            enumeration.getClosedWorkPackages());
    }

    /**
     * @param excluded the work packages to skip
     * @return a closed work package of the public demo projects, other than the excluded ones
     */
    private JsonNode getClosedWorkPackage(List<JsonNode> excluded) throws Exception
    {
        Set<String> excludedIds = excluded.stream().map(workPackage -> workPackage.get("id").asText())
            .collect(Collectors.toSet());
        JsonNode workPackages = this.semanticOpenProject.getApiResource(String.format(
            "/work_packages?pageSize=20&filters=%s&sortBy=%s",
            URLEncoder.encode("[{\"status\":{\"operator\":\"c\",\"values\":[]}}]", StandardCharsets.UTF_8),
            URLEncoder.encode("[[\"id\",\"asc\"]]", StandardCharsets.UTF_8)));
        for (JsonNode workPackage : workPackages.get("_embedded").get("elements")) {
            if (!excludedIds.contains(workPackage.get("id").asText())) {
                return workPackage;
            }
        }
        throw new IllegalStateException("The demo data has no closed work package.");
    }

    /**
     * @return the names of the statuses of the work packages that are closed
     */
    private Set<String> getClosedStatuses() throws Exception
    {
        Set<String> closedStatuses = new HashSet<>();
        for (JsonNode status : this.semanticOpenProject.getApiResource("/statuses").get("_embedded").get("elements")) {
            if (status.get("isClosed").asBoolean()) {
                closedStatuses.add(status.get("name").asText());
            }
        }
        return closedStatuses;
    }

    private static String getListLine(JsonNode workPackage)
    {
        return getKey(workPackage) + " " + workPackage.get("subject").asText();
    }

    private static String getStatus(JsonNode workPackage)
    {
        return workPackage.get("_links").get("status").get("title").asText();
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
