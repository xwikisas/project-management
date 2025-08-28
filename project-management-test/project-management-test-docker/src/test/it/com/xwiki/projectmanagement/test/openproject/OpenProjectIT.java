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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;
import org.xwiki.ckeditor.test.po.CKEditor;
import org.xwiki.ckeditor.test.po.MacroDialogSelectModal;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.EditPage;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the overall functionality of the Open Project integration.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@UITest(
    properties = {
        // Add the Scheduler plugin used by the Style sync job.
        "xwikiCfgPlugins=com.xpn.xwiki.plugin.scheduler.SchedulerPlugin," +
            // Add the jsrx and ssrx plugins used by the app.
            "com.xpn.xwiki.plugin.skinx.JsResourceSkinExtensionPlugin,"
            + "com.xpn.xwiki.plugin.skinx.CssResourceSkinExtensionPlugin"
    },
    extraJARs = {
        // Needed by the scheduler plugin, otherwise it fails.
        "org.xwiki.platform:xwiki-platform-scheduler-api:14.10.2"
    }
//    , servletEngine = ServletEngine.EXTERNAL
)
public class OpenProjectIT
{
    private static final String CONNECTION_ID = "test";

    private final LocalDocumentReference page1 = new LocalDocumentReference("Main", "Test");

    private final LocalDocumentReference page2 = new LocalDocumentReference("Main", "Test2");

    private final OpenProjectInstance openProjectInstance = new OpenProjectInstance();
    // If you use an external instance, make sure to have it started with the same commands that
    // {@link OpenProjectInstance} starts the instance. Namely, watch for doorkeeper.rb file.
//    private OpenProjectInstance openProjectInstance =
//        new ExternalOpenProjectInstance("Admin", "adminadminadmin", "http://172.17.0.1:8082");

    private final WikiReference wiki = new WikiReference("xwiki");

    @BeforeAll
    void setup(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {
        testConfiguration.setVerbose(true);
        openProjectInstance.startOpenProject(setup, testConfiguration);

        setup.loginAsSuperAdmin();
        setup.deletePage(new DocumentReference(page1, wiki));
        setup.deletePage(new DocumentReference(page2, wiki));

        // OpenProject/Code/OpenProjectConfigurations/
        DocumentReference configsHome =
            new DocumentReference(wiki.getName(), Arrays.asList("OpenProject", "Code", "OpenProjectConfigurations"),
                "WebHome");
        setup.createPage(configsHome, "");
        setup.deletePage(configsHome, true);

        /* Things that need to be tested:
        // Configuration setup
         x   add new configuration
         x   add configuration with name of existing one
         x   delete, x edit, x authorize actions work
          -  modifying connection name/deleting should not work anymore
            test that is works on subwiki
           x color sync button works
        macro:
            warning message with auth link works
           x suggesters work
           x pasting link from openproject works
           x filter builder works
            x instance,x properties, sorting pickers work
            viewing as guest user does not work
            x test different displayers
           x test livedata filters/sorting
        */
    }

    @Test
    @Order(0)
    void setupInstanceConnection(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {
        openProjectInstance.setupInstance(setup.getDriver());

        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection(CONNECTION_ID, openProjectInstance.getBaseUrl(), openProjectInstance.getClientId(),
            openProjectInstance.getClientSecret());
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been saved!", CONNECTION_ID));

        TableLayoutElement livedata = adminPage.getConnectionsLivedata().getTableLayout();
        livedata.waitUntilReady();
        assertEquals(1, livedata.countRows());
        assertEquals(CONNECTION_ID, livedata.getCell("Connection name", 1).getText());
        assertEquals("Not yet authorized", livedata.getCell("Authorized?", 1).getText());
        assertEquals(3, livedata.getCell("Actions", 1).findElements(By.className("action")).size());

        livedata.clickAction(1, "authorize-connection");

        openProjectInstance.maybeLogin(setup.getDriver(), false);
        openProjectInstance.maybeClickAuthorization(setup.getDriver());

        adminPage = new OpenProjectAdminPage();
        adminPage.waitUntilPageIsReady();

        livedata = adminPage.getConnectionsLivedata().getTableLayout();
        assertEquals("Authorized", livedata.getCell("Authorized?", 1).getText());
        assertEquals(2, livedata.getCell("Actions", 1).findElements(By.className("action")).size());
    }

    @Test
    @Order(10)
    void tryToAddConnectionWithSameName(TestUtils setup)
    {
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.addNewConnection(CONNECTION_ID, "someurl", "someid", "somesecret");
        adminPage.waitForNotificationErrorMessage(
            String.format("Connection %s already exists. Use another connection name.", CONNECTION_ID));
    }

    @Test
    @Order(20)
    void defaultOpenprojectMacro(TestUtils setup) throws OperationNotSupportedException
    {
        setup.setCurrentWiki(wiki.getName());
        DocumentReference docRef = new DocumentReference(page1, wiki);

        WYSIWYGEditPage wysiwygEditPage = openMacro(setup, docRef);
        OpenProjectMacroEditModal macroModal = selectInstanceFromModal(CONNECTION_ID, setup);

        macroModal.clickSubmit();

        TableLayoutElement ld = saveAndGetFirstOPMacro(wysiwygEditPage);
        // Equivalent to assert since it will throw an exception if not found.
        ld.getCell("Identifier", 1).findElement(By.tagName("a"));
        ld.getCell("Subject", 1).findElement(By.tagName("a"));
        ld.getCell("Assignee", 1).findElement(By.tagName("a"));
    }

    @Test
    @Order(30)
    void testStyleSyncJob(TestUtils setup) throws OperationNotSupportedException, InterruptedException
    {
        setup.gotoPage(Arrays.asList("OpenProject", "Code"), "StylingSetupJob", "edit",
            Collections.singletonMap("force", 1));
        EditPage editPage = new EditPage();
        editPage.clickSaveAndView();
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        ViewPage schedulerPage = adminPage.triggerColorSyncJob();
        schedulerPage.waitUntilPageIsReady();
        setup.getDriver()
            .findElement(By.xpath(
                "//div[contains(@class, 'infomessage')]/p[text() = 'Job Open Project Styling Updater triggered']"));
        // Wait 1 sec for the job to execute. Might need a better way to check this.
        Thread.sleep(1000);
        DocumentReference docRef = new DocumentReference(page1, wiki);
        setup.gotoPage(docRef);
        ViewPageWithOpenProjectMacro vp = new ViewPageWithOpenProjectMacro();
        vp.waitUntilPageIsReady();
        List<OpenProjectMacroElement> macros = vp.getOpenProjectMacros();
        assertEquals(1, macros.size());
        // Get one rendered type property with value "type" and compare it with a known open project color.
        String taskStatusColor =
            setup.getDriver().findElement(By.className("openproject-property-type-Task-test")).getCssValue("color");
        assertEquals("rgb(26, 103, 163)", taskStatusColor);
    }

    @Test
    @Order(40)
    void singleWorkPackageDisplayer(TestUtils setup)
    {
        setup.setCurrentWiki(wiki.getName());
        DocumentReference docRef = new DocumentReference(page1, wiki);

        WYSIWYGEditPage wysiwygEditPage = openMacro(setup, docRef);
        OpenProjectMacroEditModal macroModal = selectInstanceFromModal(CONNECTION_ID, setup);

        macroModal.clickMore();
        macroModal.selectDisplayer("Single item");
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
    @Order(50)
    void checkLivedataSuggesters(TestUtils setup) throws OperationNotSupportedException
    {
        setup.setCurrentWiki(wiki.getName());
        DocumentReference docRef = new DocumentReference(page1, wiki);
        WYSIWYGEditPage editPage = openMacro(setup, docRef);
        OpenProjectMacroEditModal modal = new OpenProjectMacroEditModal();
        modal.clickMore();
        modal.getSuggestInput("properties")
            .clear()
            .selectByValue("identifier.value")
            .selectByValue("type")
            .selectByValue("assignees")
            .selectByValue("priority")
            .selectByValue("project.value")
            .selectByValue("status")
            .selectByValue("startDate")
            .sendKeys(Keys.ESCAPE);
        modal.selectDisplayer("Live Data table");
        modal.clickSubmit();

        TableLayoutElement ld = saveAndGetFirstOPMacro(editPage);

        useSuggestFilter(ld, "Identifier", "1", false); // First work package
        useSuggestFilter(ld, "Type", "1"); // Task
        useSuggestFilter(ld, "Assignee", "4"); // Admin
        useSuggestFilter(ld, "Priority", "8"); // Normal
        useSuggestFilter(ld, "Project", "1"); // Demo proj
        useSuggestFilter(ld, "Status", "1"); // New

        // TODO: Test date pickers once the parent gets upgraded to 15.10 and use DateRangePicker PO.
        WebElement startDateFilter = ld.getFilter("Start Date");
    }

    @Test
    @Order(60)
    void macroParameterFilterTest(TestUtils setup) throws OperationNotSupportedException
    {
        setup.setCurrentWiki(wiki.getName());
        DocumentReference docRef = new DocumentReference(page1, wiki);
        WYSIWYGEditPage editPage = openMacro(setup, docRef);
        OpenProjectMacroEditModal modal = new OpenProjectMacroEditModal();
        // Create a filter.
        modal.clickMore();
        FilterBuilderParameter filterBuilderParameter = modal.getFilterBuilder();
        filterBuilderParameter.addFilter("assignees").setSuggestValue(1, "OpenProject Admin");
        filterBuilderParameter.addFilter("summary.value").setValue(1, "sp");
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
        macros.get(0).getLivedata().getTableLayout().waitUntilReady();
        String entries = macros.get(0).getElement().findElement(By.className("pagination-current-entries")).getText();
        assertEquals("Entries 1 - 3 out of 3", entries);
        // Open modal and expect the builder to contain the added filters.
        editPage = openMacro(setup, docRef);
        modal = new OpenProjectMacroEditModal();
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
        entries = macros.get(0).getElement().findElement(By.className("pagination-current-entries")).getText();
        assertEquals("Entries 1 - 5 out of 36", entries);
    }

    @Test
    @Order(70)
    void useOpenProjUrl(TestUtils setup) throws OperationNotSupportedException
    {
        DocumentReference docRef = new DocumentReference(page2, wiki);
        WYSIWYGEditPage editPage = openMacro(setup, docRef);
        OpenProjectMacroEditModal macroModal = selectInstanceFromModal(CONNECTION_ID, setup);
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

        editPage = openMacro(setup, docRef);
        macroModal = new OpenProjectMacroEditModal();
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
        assertEquals("20", ld.getCell("Identifier", 1).getText());
    }

    @Test
    @Order(80)
    void deleteAndUpdateConfiguredInstance()
    {
        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
        adminPage.updateConnection(1, "test2", "asd", "asd", "asd");
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been saved!", "test2"));
        TableLayoutElement livedata = adminPage.getConnectionsLivedata().getTableLayout();
        livedata.waitUntilReady();
        assertEquals(1, livedata.countRows());
        assertEquals("test2", livedata.getCell("Connection name", 1).getText());

        adminPage.deleteConnection(1);
        adminPage.waitForNotificationSuccessMessage(String.format("Connection %s has been deleted!", "test2"));
        livedata.waitUntilReady();
        assertEquals(0, livedata.countRows());
    }

    private static TableLayoutElement saveAndGetFirstOPMacro(WYSIWYGEditPage editPage)
        throws OperationNotSupportedException
    {

        editPage.clickSaveAndView();
        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();

        List<OpenProjectMacroElement> macros = page.getOpenProjectMacros();
        assertEquals(1, macros.size());
        TableLayoutElement ld = macros.get(0).getLivedata().getTableLayout();
        ld.waitUntilReady();
        return ld;
    }

    private static void useSuggestFilter(TableLayoutElement ld,
        String filteredColumn, String selectedValue)
    {
        useSuggestFilter(ld, filteredColumn, selectedValue, true);
    }

    private static void useSuggestFilter(TableLayoutElement ld,
        String filteredColumn, String selectedValue,
        boolean labelDisplayed)
    {
        SuggestInputElement suggest = new SuggestInputElement(ld.getFilter(filteredColumn));
        suggest.click().waitForSuggestions().selectByValue(selectedValue);
        String selectedVal = suggest.getSelectedSuggestions().get(0).getLabel().toLowerCase();
        String selctedValVal = suggest.getSelectedSuggestions().get(0).getValue().toLowerCase();
        ld.waitUntilReady();
        if (labelDisplayed) {
            assertEquals(selectedVal, ld.getCell(filteredColumn, 1).getText().toLowerCase());
        } else {
            assertEquals(selctedValVal, ld.getCell(filteredColumn, 1).getText().toLowerCase());
        }
        suggest.clear();
        ld.waitUntilReady();
    }

    private WYSIWYGEditPage openMacro(TestUtils setup, DocumentReference docRef)
    {
        ViewPage page = setup.gotoPage(docRef);
        WYSIWYGEditPage wysiwygEditPage = page.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();
        MacroDialogSelectModal modal = openMacrosModal(setup);
        if (!setup.getDriver().hasElement(By.cssSelector(".macro-editor-modal .macro-name"))) {
            modal.waitUntilReady();
            modal.filterByText("Open Project", 1);
//        modal.clickSelect().waitUntilReady();
            setup.getDriver().findElement(By.cssSelector(".macro-selector-modal .modal-footer .btn-primary")).click();
        }
        setup.getDriver().waitUntilElementIsVisible(By.cssSelector(".macro-editor-modal .macro-name"));
        return wysiwygEditPage;
    }

    private static OpenProjectMacroEditModal selectInstanceFromModal(String connection, TestUtils setup)
    {
        OpenProjectMacroEditModal macroModal = new OpenProjectMacroEditModal();

        SuggestInputElement instanceSuggest = macroModal.getSuggestInput("instance").waitForSuggestions();
        assertEquals(1, instanceSuggest.getSuggestions().size());
        assertEquals(connection, instanceSuggest.getSuggestions().get(0).getLabel());

        instanceSuggest.selectByValue(connection);
        // We set the id parameter because the livedata page object needs to have an id present.
        String js = String.format("arguments[0].value = '%d';", RandomUtils.nextInt());
        setup.getDriver()
            .executeJavascript(js, macroModal.getMacroParameterInput("id"));
        return macroModal;
    }

    private MacroDialogSelectModal openMacrosModal(TestUtils setup)
    {
        setup.getDriver().findElement(By.xpath("//a[contains(@class, 'cke_button') and contains(@title, 'Insert')]"))
            .click();
        setup.getDriver().waitUntilElementIsVisible(By.className("cke_panel_frame"));
        WebElement panelIframe = setup.getDriver().findElement(By.className("cke_panel_frame"));
        setup.getDriver().switchTo().frame(panelIframe);
        setup.getDriver()
            .findElement(By.xpath("//span[@class='cke_menubutton_label' and contains(text(), 'Other Macros')]"))
            .click();
        setup.getDriver().switchTo().defaultContent();
        // Modal opened to a macro.
        return new MacroDialogSelectModal();
    }
}
