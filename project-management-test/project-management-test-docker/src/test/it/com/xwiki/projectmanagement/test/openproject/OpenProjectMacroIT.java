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

import java.text.SimpleDateFormat;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test the OpenProject macro.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
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
    private static final String DEMO_PROJECT = "demo-project";

    private static final String MILESTONES_QUERY = "Milestones";

    private static final String SUBJECT_COLUMN = "Subject";

    private static final String PROPERTIES_PARAMETER = "properties";

    @Test
    void defaultOpenprojectMacro(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage wysiwygEditPage = macroModal.getEditPage();

        macroModal.clickSubmit();

        TableLayoutElement ld = saveAndGetFirstOPMacro(wysiwygEditPage);
        // Equivalent to assert since it will throw an exception if not found.
        ld.getCell("ID", 1).findElement(By.tagName("a"));
        ld.getCell("Subject", 1).findElement(By.tagName("a"));
        ld.getCell("Assignee", 1).findElement(By.tagName("a"));
    }

    @Test
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

        // Displayed work package should have a link in the header.
        singleDisplayer.getHeader().findElement(By.tagName("a"));
        assertEquals("Work package from project: Demo project", singleDisplayer.getProject().getText());
        assertDoesNotThrow(() -> {
            singleDisplayer.getProject().findElement(By.tagName("a"));
        });

        assertEquals("MILESTONE", singleDisplayer.getProperty("Type:").getText());
        assertEquals("OpenProject Admin", singleDisplayer.getProperty("Author:").getText());

        // 14/08/2025 12:00:00
        SimpleDateFormat expectedDateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
        assertDoesNotThrow(() -> {
            expectedDateFormat.parse(singleDisplayer.getProperty("Updated At:").getText());
        });
    }

    @Test
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
        useSuggestFilter(ld, "Type", "1"); // Task
        useSuggestFilter(ld, "Assignee", "4"); // Admin
        useSuggestFilter(ld, "Priority", "8"); // Normal
        useSuggestFilter(ld, "Project", "1"); // Demo proj
        useSuggestFilter(ld, "Status", "1"); // New
    }

    @Test
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
    void useOpenProjUrl(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage editPage = macroModal.getEditPage();
        // Create a filter.
        macroModal.clickMore();
        macroModal.setMacroParameter("identifier",
            "http://localhost:8081/projects/demo-project/work_packages?"
                + "query_props=%7B%22c%22%3A%5B%22id%22%2C%22subject%22%2C%22type%22%2C%22status%22%2C%22assignee"
                + "%22%2C%22priority%22%5D%2C%22hi%22%3Afalse%2C%22g%22%3A%22%22%2C%22is%22%3Atrue%2C%22tv%22%3Afalse"
                + "%2C%22hl%22%3A%22none%22%2C%22t%22%3A%22id%3Aasc%22%2C%22f%22%3A%5B%7B%22n%22%3A%22type%22%2C%22o"
                + "%22%3A%22%3D%22%2C%22v%22%3A%5B%221%22%5D%7D%2C%7B%22n%22%3A%22status%22%2C%22o%22%3A%22%3D%22%2C%22"
                + "v%22%3A%5B%227%22%5D%7D%5D%2C%22ts%22%3A%22PT0S%22%2C%22pp%22%3A20%2C%22pa%22%3A1%7D");
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(2, ld.countRows());

        macroModal = new OpenProjectMacroEditModal(setup, testReference);
        editPage = macroModal.getEditPage();
        macroModal.setMacroParameter("identifier",
            "http://localhost:8081/work_packages?query_props=%7B%22c%22%3A%5B%22id%22%2C%22subject"
                + "%22%2C%22type%22%2C%22status%22%2C%22assignee%22%2C%22priority%22%2C%22project%22%5D%2C%22"
                + "hi%22%3Afalse%2C%22g%22%3A%22%22%2C%22is%22%3Atrue%2C%22tv%22%3Afalse%2C%22hl%22%3A%22"
                + "none%22%2C%22t%22%3A%22id%3Adesc%22%2C%22f%22%3A%5B%7B%22n%22%3A%22type%22%2C%22o%22%3A%22%3D%22%"
                + "2C%22v%22%3A%5B%221%22%5D%7D%2C%7B%22n%22%3A%22status%22%2C%22o%22%3A%22%3D%22%2C%22v%22%3A%5B%"
                + "227%22%5D%7D%5D%2C%22ts%22%3A%22PT0S%22%2C%22pp%22%3A20%2C%22pa%22%3A1%7D");
        macroModal.clickSubmit();
        ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(3, ld.countRows());
        assertEquals("20", ld.getCell("ID", 1).getText());
    }

    @Test
    void useSavedQueryUrl(TestUtils setup, TestReference testReference) throws OperationNotSupportedException
    {
        // The demo project comes with a saved "Milestones" query, which only filters on the milestone type. The project
        // has 3 milestones: "Start of project", "Conference" and "End of project".
        String savedQueryUrl = this.openProject.getSavedQueryUrl(setup, DEMO_PROJECT, MILESTONES_QUERY);

        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage editPage = macroModal.getEditPage();
        macroModal.clickMore();
        macroModal.setMacroParameter("identifier", savedQueryUrl);
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(3, ld.countRows());
        ld.assertRow(SUBJECT_COLUMN, "Start of project");
        ld.assertRow(SUBJECT_COLUMN, "Conference");
        ld.assertRow(SUBJECT_COLUMN, "End of project");
    }

    @Test
    void useSavedQueryUrlWithFilters(TestUtils setup, TestReference testReference)
        throws OperationNotSupportedException
    {
        String savedQueryUrl = this.openProject.getSavedQueryUrl(setup, DEMO_PROJECT, MILESTONES_QUERY);

        setup.deletePage(testReference);
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal(setup, testReference);
        selectInstanceFromModal(macroModal, CONNECTION_ID);
        WYSIWYGEditPage editPage = macroModal.getEditPage();
        macroModal.clickMore();
        macroModal.setMacroParameter("identifier", savedQueryUrl);
        // The filters of the macro are added to the ones of the query: only the milestones having "project" in their
        // subject are kept.
        macroModal.getFilterBuilder().addFilter("summary").setValue(1, "project");
        macroModal.clickSubmit();
        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);
        assertEquals(2, ld.countRows());
        ld.assertRow(SUBJECT_COLUMN, "Start of project");
        ld.assertRow(SUBJECT_COLUMN, "End of project");

        // The filters set from the livedata are added as well.
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
}
