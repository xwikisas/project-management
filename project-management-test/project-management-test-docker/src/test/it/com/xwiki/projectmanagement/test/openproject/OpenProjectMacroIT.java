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
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import com.fasterxml.jackson.databind.JsonNode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the OpenProject macro.
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
public class OpenProjectMacroIT extends AbstractOpenProjectIT
{
    // The demo data changes between OpenProject versions, so the expected results are computed from the OpenProject
    // API, except for these ids, which are the same in all the versions we test.

    private static final String DEMO_PROJECT_ID = "1";

    private static final String TASK_TYPE_ID = "1";

    private static final String NEW_STATUS_ID = "1";

    private static final String IN_PROGRESS_STATUS_ID = "7";

    private static final String ADMIN_USER_ID = "4";

    private static final String NORMAL_PRIORITY_ID = "8";

    private static final String DEMO_PROJECT_PATH = "/projects/demo-project";

    /**
     * The API filters matching the tasks in progress.
     */
    private static final String TASKS_IN_PROGRESS_FILTERS = "[{\"type\":{\"operator\":\"=\",\"values\":[\""
        + TASK_TYPE_ID + "\"]}},{\"status\":{\"operator\":\"=\",\"values\":[\"" + IN_PROGRESS_STATUS_ID + "\"]}}]";

    private static final String ELEMENTS = "elements";

    private static final String EMBEDDED = "_embedded";

    /**
     * The {@code query_props} of an OpenProject work package list URL showing the tasks in progress, sorted by id in
     * the given order ({@code asc} or {@code desc}), with the given columns.
     */
    private static final String TASKS_IN_PROGRESS_QUERY_PROPS = "{\"c\":[%s],\"hi\":false,\"g\":\"\",\"is\":true,"
        + "\"tv\":false,\"hl\":\"none\",\"t\":\"id:%s\",\"f\":[{\"n\":\"type\",\"o\":\"=\",\"v\":[\"" + TASK_TYPE_ID
        + "\"]},{\"n\":\"status\",\"o\":\"=\",\"v\":[\"" + IN_PROGRESS_STATUS_ID
        + "\"]}],\"ts\":\"PT0S\",\"pp\":20,\"pa\":1}";

    private static final String SUBJECT_COLUMN = "Subject";

    private static final String PROPERTIES_PARAMETER = "properties";

    @Test
    @Order(1)
    void defaultOpenprojectMacro(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage wysiwygEditPage = macroModal.getEditPage();

        macroModal.clickSubmit();

        saveAndGetFirstOPMacro(wysiwygEditPage);
        OpenProjectMacroElement macro = new ViewPageWithOpenProjectMacro().getOpenProjectMacros().get(0);
        assertTrue(macro.hasLinkInCell("ID", 1));
        assertTrue(macro.hasLinkInCell(SUBJECT_COLUMN, 1));
        assertTrue(macro.hasLinkInCell("Assignee", 1));

        // The View action displays the work package of the row it was clicked on.
        TableLayoutElement ld = macro.waitForTableLayout();
        for (int row : List.of(2, 1)) {
            String subject = ld.getCell(SUBJECT_COLUMN, row).getText();
            String header = macro.viewWorkPackage(row).getHeaderText();
            assertTrue(header.contains(subject), String.format("[%s] doesn't contain [%s]", header, subject));
            macro.closeWorkPackageView();
        }
    }

    @Test
    @Order(2)
    void singleWorkPackageDisplayer(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage wysiwygEditPage = macroModal.getEditPage();

        macroModal.clickMore();
        macroModal.selectDisplayer("Single item");
        // Assert that "sort", "offset", "limit", "properties" params are hidden.
        assertFalse(macroModal.isMacroParameterDisplayed(PROPERTIES_PARAMETER));
        macroModal.clickSubmit();

        wysiwygEditPage.clickSaveAndView();

        ViewPageWithOpenProjectMacro vp = new ViewPageWithOpenProjectMacro();
        List<OpenProjectMacroElement> macros = vp.getOpenProjectMacros();
        assertEquals(1, macros.size());

        OpenProjectSingleDisplayer singleDisplayer = macros.get(0).getSingleWorkItem().waitUntilReady();

        assertTrue(singleDisplayer.hasWorkPackageLink());
        assertEquals("Work package from project: Demo project", singleDisplayer.getProjectText());
        assertTrue(singleDisplayer.hasProjectLink());

        assertEquals("MILESTONE", singleDisplayer.getPropertyValue("Type:"));
        assertEquals("OpenProject Admin", singleDisplayer.getPropertyValue("Author:"));

        // The date is displayed with the default date format of the wiki, e.g. 14/08/2025 15:00:00.
        SimpleDateFormat expectedDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        expectedDateFormat.setLenient(false);
        String updatedAt = singleDisplayer.getPropertyValue("Updated At:");
        assertDoesNotThrow(() -> expectedDateFormat.parse(updatedAt), updatedAt);
        assertThat(updatedAt, matchesPattern("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @Order(3)
    void checkLivedataSuggesters(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        // Start from a macro that uses the single item displayer.
        setup.createPage(testReference, String.format(
            "{{openproject instance=\"%s\" id=\"checkLivedataSuggesters\" workItemsDisplayer=\"workItemsSingle\"/}}",
            CONNECTION_ID));
        OpenProjectMacroEditModal modal = new OpenProjectMacroEditModal(setup, testReference);
        WYSIWYGEditPage editPage = modal.getEditPage();
        modal.clickMore();
        // Assert that "sort", "offset", "limit", "properties" params are still hidden.
        assertFalse(modal.isMacroParameterDisplayed(PROPERTIES_PARAMETER));
        modal.selectDisplayer("Live Data table");
        modal = new OpenProjectMacroEditModal();
        // Assert that all props are visible.
        modal.scrollToFooter();
        modal.getSuggestInput(PROPERTIES_PARAMETER)
            .clearSelectedSuggestions()
            .selectByValue("identifier")
            .selectByValue("type")
            .selectByValue("assignees")
            .selectByValue("priority")
            .selectByValue("project")
            .selectByValue("status")
            .selectByValue("startDate")
            .sendKeys(Keys.ESCAPE);
        modal.clickSubmit();

        saveAndGetFirstOPMacro(editPage);
        OpenProjectMacroElement macro = new ViewPageWithOpenProjectMacro().getOpenProjectMacros().get(0);

        useSuggestFilter(macro, "ID", "1", false); // First work package
        useSuggestFilter(macro, "Type", TASK_TYPE_ID);
        useSuggestFilter(macro, "Assignee", ADMIN_USER_ID);
        useSuggestFilter(macro, "Priority", NORMAL_PRIORITY_ID);
        useSuggestFilter(macro, "Project", DEMO_PROJECT_ID);
        useSuggestFilter(macro, "Status", NEW_STATUS_ID);
    }

    @Test
    @Order(4)
    void macroParameterFilterTest(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.createPage(testReference,
            String.format("{{openproject instance=\"%s\" id=\"macroParameterFilterTest\"/}}", CONNECTION_ID));
        OpenProjectMacroEditModal modal = new OpenProjectMacroEditModal(setup, testReference);
        WYSIWYGEditPage editPage = modal.getEditPage();
        // Create a filter.
        modal.clickMore();
        FilterBuilderParameter filterBuilderParameter = modal.getFilterBuilder();
        filterBuilderParameter.addFilter("assignees").setSuggestValue(1, "OpenProject Admin");
        filterBuilderParameter.addFilter("summary").setValue(1, "sp");
        filterBuilderParameter.addFilter("status")
            .addConstraint()
            .setSuggestValue(1, "In progress")
            .setSuggestValue(2, "New");
        // Submit modal and view page.
        modal.clickSubmit();
        editPage.clickSaveAndView();
        // Expect the livedata to show 3 elements.
        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();
        List<OpenProjectMacroElement> macros = page.getOpenProjectMacros();
        assertEquals(1, macros.size());
        assertEquals("Entries 1 - 3 out of 3", getPaginationEntriesText(macros.get(0)));
        // Open modal and expect the builder to contain the added filters.
        modal = new OpenProjectMacroEditModal(setup, testReference);
        editPage = modal.getEditPage();
        filterBuilderParameter = modal.getFilterBuilder();
        List<FilterBuilderFilter> filters = filterBuilderParameter.getFilters();
        assertEquals(3, filters.size());
        // Clear the filters and expect the macro to display all the entries.
        filterBuilderParameter.clearFilters();
        modal.clickSubmit();
        editPage.clickSaveAndView();
        page = new ViewPageWithOpenProjectMacro();
        macros = page.getOpenProjectMacros();
        assertEquals(1, macros.size());
        // Without filters, OpenProject lists the closed work packages too.
        int total = this.openProject.getApiResource("/work_packages?pageSize=1&filters=" + encode("[]"))
            .get("total").asInt();
        assertEquals(String.format("Entries 1 - %d out of %d", Math.min(25, total), total),
            getPaginationEntriesText(macros.get(0)));
    }

    @Test
    @Order(5)
    void useOpenProjUrl(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage editPage = macroModal.getEditPage();
        // Create a filter.
        macroModal.clickMore();
        macroModal.setMacroParameter("identifier", getTasksInProgressUrl(DEMO_PROJECT_PATH + "/work_packages",
            "asc", "\"id\",\"subject\",\"type\",\"status\",\"assignee\",\"priority\""));
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(getTasksInProgressIds(DEMO_PROJECT_PATH).size(), ld.countRows());

        // The tasks in progress of all the projects, the most recent first.
        macroModal = new OpenProjectMacroEditModal(setup, testReference);
        editPage = macroModal.getEditPage();
        macroModal.setMacroParameter("identifier", getTasksInProgressUrl("/work_packages", "desc",
            "\"id\",\"subject\",\"type\",\"status\",\"assignee\",\"priority\",\"project\""));
        macroModal.clickSubmit();
        ld = saveAndGetFirstOPMacro(editPage);
        List<String> taskIds = getTasksInProgressIds("");
        assertEquals(taskIds.size(), ld.countRows());
        assertEquals(taskIds.get(0), ld.getCell("ID", 1).getText());
    }

    @Test
    @Order(6)
    void useSavedQueryUrl(TestUtils setup, TestReference testReference) throws Exception
    {
        // The saved "Milestones" query of the demo project, which only filters on the milestone type.
        JsonNode queries = this.openProject.getApiResource("/queries?filters="
            + encode("[{\"project\":{\"operator\":\"=\",\"values\":[\"" + DEMO_PROJECT_ID + "\"]}}]"));
        int queryId = StreamSupport.stream(queries.get(EMBEDDED).get(ELEMENTS).spliterator(), false)
            .filter(query -> "Milestones".equals(query.get("name").asText())).findFirst().orElseThrow()
            .get("id").asInt();
        List<String> milestones = getSubjects(
            this.openProject.getApiResource("/queries/" + queryId).get(EMBEDDED).get("results"));
        String savedQueryUrl =
            this.openProject.getBaseUrl() + DEMO_PROJECT_PATH + "/work_packages?query_id=" + queryId;

        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage editPage = macroModal.getEditPage();
        macroModal.clickMore();
        // The macro filters can't be combined with an identifier, so the filters are set from the livedata.
        macroModal.setMacroParameter("identifier", savedQueryUrl);
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(milestones.size(), ld.countRows());
        milestones.forEach(milestone -> ld.assertRow(SUBJECT_COLUMN, milestone));

        String lastMilestone = milestones.get(milestones.size() - 1);
        ld.filterColumn(SUBJECT_COLUMN, lastMilestone);
        assertEquals(1, ld.countRows());
        ld.assertRow(SUBJECT_COLUMN, lastMilestone);
    }

    @Test
    @Order(7)
    void showPublicDataToGuests(TestUtils setup, TestReference testReference) throws Exception
    {
        // The demo project is public and OpenProject doesn't require users to log in, so anonymous users can see its
        // work packages.
        setup.createPage(testReference,
            String.format("{{openproject instance=\"%s\" id=\"showPublicData\" identifier=\"%s\"/}}", CONNECTION_ID,
                getTasksInProgressUrl(DEMO_PROJECT_PATH + "/work_packages", "asc",
                    "\"id\",\"subject\",\"type\",\"status\"")));
        // Without the option, guests are asked to connect to OpenProject.
        setup.forceGuestUser();
        setup.gotoPage(testReference);
        assertTrue(new ViewPageWithOpenProjectMacro().isConnectWarningDisplayed());

        setup.loginAsSuperAdmin();
        OpenProjectAdminPage.gotoPage().setShowPublicData(CONNECTION_ID, true);
        try {
            setup.forceGuestUser();
            setup.gotoPage(testReference);
            TableLayoutElement ld =
                new ViewPageWithOpenProjectMacro().getOpenProjectMacros().get(0).waitForTableLayout();
            assertEquals(getTasksInProgressIds(DEMO_PROJECT_PATH).size(), ld.countRows());
            // The type is displayed in upper case.
            assertEquals("task", ld.getCell("Type", 1).getText().toLowerCase());
        } finally {
            setup.loginAsSuperAdmin();
            OpenProjectAdminPage.gotoPage().setShowPublicData(CONNECTION_ID, false);
        }
    }

    @Test
    @Order(8)
    void filterAndDisplayTargetVersion(TestUtils setup, TestReference testReference) throws Exception
    {
        // The target version is called "version" or "targetVersions", depending on the OpenProject version.
        String versionColumn = getFirstExistingId("/queries/columns/", "targetVersions", "version");
        String versionFilter = getFirstExistingId("/queries/filters/", "targetVersion", "version");
        String versionColumnName = this.openProject.getApiResource("/queries/columns/" + versionColumn)
            .get("name").asText();

        // The demo version with the most work packages. The macro references it by name, which matches all the
        // versions with this name.
        String versionName = null;
        List<String> expectedSubjects = List.of();
        JsonNode versions = this.openProject.getApiResource("/versions").get(EMBEDDED).get(ELEMENTS);
        for (JsonNode version : versions) {
            String name = version.get("name").asText();
            List<String> versionIds = StreamSupport.stream(versions.spliterator(), false)
                .filter(other -> name.equals(other.get("name").asText()))
                .map(other -> '"' + other.get("id").asText() + '"').collect(Collectors.toList());
            List<String> subjects = getSubjects(this.openProject.getApiResource("/work_packages?pageSize=100&sortBy="
                + encode("[[\"id\",\"asc\"]]") + "&filters=" + encode(String.format(
                    "[{\"%s\":{\"operator\":\"=\",\"values\":[%s]}}]", versionFilter,
                    String.join(",", versionIds)))));
            if (subjects.size() > expectedSubjects.size()) {
                versionName = name;
                expectedSubjects = subjects;
            }
        }
        assertTrue(versionName != null, "The demo data has no work package with a target version.");

        setup.createPage(testReference, String.format("{{openproject instance=\"%s\" id=\"targetVersion\" "
            + "properties=\"identifier,summary,targetVersion\" sort=\"identifier:asc\" limit=\"100\" "
            + "filters=\"{~\"query~\":{~\"filters~\":[{~\"property~\":~\"targetVersion~\",~\"constraints~\":"
            + "[{~\"operator~\":~\"=~\",~\"value~\":~\"%s~\"}]}]}}\"/}}", CONNECTION_ID, versionName));
        setup.gotoPage(testReference);

        TableLayoutElement ld =
            new ViewPageWithOpenProjectMacro().getOpenProjectMacros().get(0).waitForTableLayout();
        assertEquals(expectedSubjects.size(), ld.countRows());
        expectedSubjects.forEach(subject -> ld.assertRow(SUBJECT_COLUMN, subject));
        // The target version column is named after the OpenProject field.
        ld.assertRow(versionColumnName, versionName);
    }

    @Test
    @Order(9)
    void displayWorkPackageAsynchronously(TestUtils setup, TestReference testReference) throws Exception
    {
        // The work package is displayed the way the View action of the Live Data displays it in its modal.
        String workPackageId = "1";
        String subject = this.openProject.getApiResource("/work_packages/" + workPackageId).get("subject").asText();
        setup.createPage(testReference, "");
        setup.gotoPage(testReference);
        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();

        List<String> rows = page.displayWorkPackage("liveData", CONNECTION_ID, workPackageId).waitForLiveDataRows();
        assertEquals(1, rows.size(), rows.toString());
        assertThat(rows.get(0), containsString(subject));

        setup.gotoPage(testReference);
        page = new ViewPageWithOpenProjectMacro();
        List<String> cards =
            page.displayWorkPackage("liveDataCards", CONNECTION_ID, workPackageId).waitForLiveDataCards();
        assertEquals(1, cards.size(), cards.toString());
        assertThat(cards.get(0), containsString(subject));

        setup.gotoPage(testReference);
        page = new ViewPageWithOpenProjectMacro();
        OpenProjectSingleDisplayer single = page.displayWorkPackage("workItemsSingle", CONNECTION_ID, workPackageId)
            .getSingleWorkItem().waitUntilReady();
        assertThat(single.getHeaderText(), containsString(subject));

        // Guests aren't connected to OpenProject, so the rendering fails: the error is displayed with its cause, and
        // its details are displayed when clicking on it.
        setup.forceGuestUser();
        try {
            setup.gotoPage(testReference);
            OpenProjectMacroElement failure = new ViewPageWithOpenProjectMacro().displayWorkPackage("workItemsSingle",
                CONNECTION_ID, workPackageId);
            String message = failure.waitForErrorMessage();
            assertThat(message, containsString("Failed to display the work items."));
            assertThat(message, containsString(
                String.format("The current user isn't connected to the OpenProject instance [%s].", CONNECTION_ID)));
            assertFalse(failure.isErrorDetailsDisplayed());

            failure.toggleErrorDetails();
            String details = failure.getErrorDetails();
            assertThat(details, containsString("MacroExecutionException"));
            assertThat(details, containsString("at com.xwiki.projectmanagement."));
        } finally {
            setup.loginAsSuperAdmin();
        }
    }

    @Test
    @Order(10)
    void displayFormattedDescription(TestUtils setup, TestReference testReference) throws Exception
    {
        JsonNode workPackages = this.openProject.getApiResource(String.format(
            "/projects/%s/work_packages?pageSize=3&sortBy=%s", DEMO_PROJECT_ID, encode("[[\"id\",\"asc\"]]")));
        String referencedId = workPackages.get(EMBEDDED).get(ELEMENTS).get(0).get("id").asText();
        String describedId = workPackages.get(EMBEDDED).get(ELEMENTS).get(1).get("id").asText();
        String quickInfoId = workPackages.get(EMBEDDED).get(ELEMENTS).get(2).get("id").asText();
        String reference = "#" + referencedId;

        // A description with a heading (to which OpenProject adds a permalink), a reference to another work package
        // (which OpenProject renders as a relative link), a "quick info" reference to a work package (which
        // OpenProject renders as an empty element filled by its JavaScript), a task list and some formatting.
        String previousDescription = this.openProject.setWorkPackageDescription(setup.getDriver(), describedId,
            String.format("# Release notes%n%nThe **new** feature is tracked by %s and depends on ##%s.%n%n"
                + "* first%n* second%n%n- [x] done item%n- [ ] open item%n", reference, quickInfoId));
        try {
            setup.createPage(testReference, String.format("{{openproject instance=\"%1$s\" identifier=\"%2$s\" "
                + "workItemsDisplayer=\"workItemsSingle\"/}}%n%n{{openproject instance=\"%1$s\" id=\"description\" "
                + "identifier=\"%2$s\" properties=\"identifier,description\"/}}", CONNECTION_ID, describedId));

            List<OpenProjectMacroElement> macros = new ViewPageWithOpenProjectMacro().getOpenProjectMacros();
            // The quick info reference is displayed as a link to the work package.
            Map<String, String> expectedLinks =
                Map.of(reference, this.openProject.getBaseUrl() + "/work_packages/" + referencedId,
                    "#" + quickInfoId, this.openProject.getBaseUrl() + "/work_packages/" + quickInfoId);
            // The task list items show whether they're done.
            List<String> expectedListItems = List.of("first", "second", "\u2611 done item", "\u2610 open item");

            FormattedTextElement description =
                macros.get(0).getSingleWorkItem().waitUntilReady().getFormattedDescription();
            assertEquals(List.of("Release notes"), description.getHeadings());
            assertEquals(List.of("new"), description.getBoldTexts());
            assertEquals(expectedListItems, description.getListItems());
            // The heading permalink is not displayed and the reference links to OpenProject.
            assertEquals(expectedLinks, description.getLinks());

            FormattedTextElement cell = macros.get(1).getFormattedCell("Description", 1);
            assertEquals(List.of("Release notes"), cell.getHeadings());
            assertEquals(List.of("new"), cell.getBoldTexts());
            assertEquals(expectedListItems, cell.getListItems());
            assertEquals(expectedLinks, cell.getLinks());
        } finally {
            this.openProject.setWorkPackageDescription(setup.getDriver(), describedId, previousDescription);
        }
    }

    /**
     * @return the first of the given ids that identifies an existing resource under the given path
     */
    private String getFirstExistingId(String path, String... ids) throws Exception
    {
        for (String id : ids) {
            try {
                this.openProject.getApiResource(path + id);
                return id;
            } catch (IllegalStateException e) {
                // Try the next id.
            }
        }
        throw new IllegalStateException(String.format("None of %s exists under [%s].", List.of(ids), path));
    }

    private static void useSuggestFilter(OpenProjectMacroElement macro, String filteredColumn, String selectedValue)
        throws OperationNotSupportedException
    {
        useSuggestFilter(macro, filteredColumn, selectedValue, true);
    }

    /**
     * Filter a column with one of the values its filter suggests, and check that all the displayed work packages
     * have this value.
     *
     * @param labelDisplayed {@code true} if the column displays the label of the suggested value, {@code false} if it
     *     displays the value itself
     */
    private static void useSuggestFilter(OpenProjectMacroElement macro, String filteredColumn, String selectedValue,
        boolean labelDisplayed) throws OperationNotSupportedException
    {
        String selectedLabel = macro.filterColumnBySuggestion(filteredColumn, selectedValue);
        String expected = (labelDisplayed ? selectedLabel : selectedValue).toLowerCase();
        List<String> displayed = macro.getColumnValues(filteredColumn);
        assertFalse(displayed.isEmpty(), "No work package matches the filter on " + filteredColumn);
        displayed.forEach(value -> assertEquals(expected, value.toLowerCase(), displayed.toString()));
        macro.clearColumnFilter(filteredColumn);
    }

    private String getTasksInProgressUrl(String path, String sortOrder, String columns)
    {
        String queryProps = String.format(TASKS_IN_PROGRESS_QUERY_PROPS, columns, sortOrder);
        return this.openProject.getBaseUrl() + path + "?query_props=" + encode(queryProps);
    }

    /**
     * @param projectPath the path of the project ({@code /projects/<identifier>}), or an empty string for all the
     *     projects
     * @return the ids of the tasks in progress, the most recent first
     */
    private List<String> getTasksInProgressIds(String projectPath) throws Exception
    {
        JsonNode workPackages = this.openProject.getApiResource(projectPath + "/work_packages?pageSize=100&sortBy="
            + encode("[[\"id\",\"desc\"]]") + "&filters=" + encode(TASKS_IN_PROGRESS_FILTERS));
        return StreamSupport.stream(workPackages.get(EMBEDDED).get(ELEMENTS).spliterator(), false)
            .map(workPackage -> workPackage.get("id").asText()).collect(Collectors.toList());
    }

    private static List<String> getSubjects(JsonNode workPackages)
    {
        return StreamSupport.stream(workPackages.get(EMBEDDED).get(ELEMENTS).spliterator(), false)
            .map(workPackage -> workPackage.get("subject").asText()).collect(Collectors.toList());
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
