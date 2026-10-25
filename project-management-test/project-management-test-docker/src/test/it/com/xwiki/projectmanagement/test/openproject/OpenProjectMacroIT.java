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

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import static org.hamcrest.MatcherAssert.assertThat;
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
    /**
     * The saved "Milestones" query of the demo project, which only filters on the milestone type. The project has 3
     * milestones: "Start of project", "Conference" and "End of project". The id is the one given by the demo data of
     * OpenProject 16.
     */
    private static final String MILESTONES_QUERY_URL = "/projects/demo-project/work_packages?query_id=2";

    // Ids given by the demo data of OpenProject 16.

    private static final String DEMO_PROJECT_ID = "1";

    private static final String TASK_TYPE_ID = "1";

    private static final String NEW_STATUS_ID = "1";

    private static final String IN_PROGRESS_STATUS_ID = "7";

    private static final String ADMIN_USER_ID = "4";

    private static final String NORMAL_PRIORITY_ID = "8";

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
        assertFalse(macroModal.getMacroParameterInput(PROPERTIES_PARAMETER).isDisplayed());
        macroModal.clickSubmit();

        wysiwygEditPage.clickSaveAndView().waitUntilPageIsReady();

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
        assertFalse(modal.getMacroParameterInput(PROPERTIES_PARAMETER).isDisplayed());
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

        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);

        useSuggestFilter(ld, "ID", "1", false); // First work package
        useSuggestFilter(ld, "Type", TASK_TYPE_ID);
        useSuggestFilter(ld, "Assignee", ADMIN_USER_ID);
        useSuggestFilter(ld, "Priority", NORMAL_PRIORITY_ID);
        useSuggestFilter(ld, "Project", DEMO_PROJECT_ID);
        useSuggestFilter(ld, "Status", NEW_STATUS_ID);
    }

    @Test
    @Order(4)
    void macroParameterFilterTest(TestUtils setup, TestReference testReference)
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
        assertEquals("Entries 1 - 25 out of 36", getPaginationEntriesText(macros.get(0)));
    }

    @Test
    @Order(5)
    void useOpenProjUrl(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage editPage = macroModal.getEditPage();
        // Create a filter.
        macroModal.clickMore();
        macroModal.setMacroParameter("identifier", getTasksInProgressUrl("/projects/demo-project/work_packages",
            "asc", "\"id\",\"subject\",\"type\",\"status\",\"assignee\",\"priority\""));
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(2, ld.countRows());

        macroModal = new OpenProjectMacroEditModal(setup, testReference);
        editPage = macroModal.getEditPage();
        macroModal.setMacroParameter("identifier", getTasksInProgressUrl("/work_packages", "desc",
            "\"id\",\"subject\",\"type\",\"status\",\"assignee\",\"priority\",\"project\""));
        macroModal.clickSubmit();
        ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(3, ld.countRows());
        assertEquals("20", ld.getCell("ID", 1).getText());
    }

    @Test
    @Order(6)
    void useSavedQueryUrl(TestUtils setup, TestReference testReference)
        throws OperationNotSupportedException
    {
        String savedQueryUrl = this.openProject.getBaseUrl() + MILESTONES_QUERY_URL;

        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage editPage = macroModal.getEditPage();
        macroModal.clickMore();
        // The macro filters can't be combined with an identifier, so the filters are set from the livedata.
        macroModal.setMacroParameter("identifier", savedQueryUrl);
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(3, ld.countRows());

        ld.assertRow(SUBJECT_COLUMN, "Start of project");
        ld.assertRow(SUBJECT_COLUMN, "Conference");
        ld.assertRow(SUBJECT_COLUMN, "End of project");

        ld.filterColumn(SUBJECT_COLUMN, "end");
        assertEquals(1, ld.countRows());
        ld.assertRow(SUBJECT_COLUMN, "End of project");
    }

    private static void useSuggestFilter(TableLayoutElement ld, String filteredColumn, String selectedValue)
    {
        useSuggestFilter(ld, filteredColumn, selectedValue, true);
    }

    private static void useSuggestFilter(TableLayoutElement ld, String filteredColumn, String selectedValue,
        boolean labelDisplayed)
    {
        SuggestInputElement suggest = new SuggestInputElement(ld.getFilter(filteredColumn));
        suggest.click().waitForSuggestions().selectByValue(selectedValue);
        String selectedLabel = suggest.getSelectedSuggestions().get(0).getLabel().toLowerCase();
        String selectedVal = suggest.getSelectedSuggestions().get(0).getValue().toLowerCase();
        ld.waitUntilReady();
        if (labelDisplayed) {
            assertEquals(selectedLabel, ld.getCell(filteredColumn, 1).getText().toLowerCase());
        } else {
            assertEquals(selectedVal, ld.getCell(filteredColumn, 1).getText().toLowerCase());
        }
        suggest.clear();
        ld.waitUntilReady();
    }

    private String getTasksInProgressUrl(String path, String sortOrder, String columns)
    {
        String queryProps = String.format(TASKS_IN_PROGRESS_QUERY_PROPS, columns, sortOrder);
        return this.openProject.getBaseUrl() + path + "?query_props="
            + URLEncoder.encode(queryProps, StandardCharsets.UTF_8);
    }
}
