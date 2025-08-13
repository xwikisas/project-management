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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;
import org.xwiki.ckeditor.test.po.CKEditor;
import org.xwiki.ckeditor.test.po.MacroDialogSelectModal;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.docker.junit5.servletengine.ServletEngine;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;

import static org.junit.jupiter.api.Assertions.assertEquals;

@UITest(
    servletEngineNetworkAliases = "localhost",
    properties = {
        "xwikiDbHbmCommonExtraMappings=notification-filter-preferences.hbm.xml"
    },
    extraJARs = {
        // It's currently not possible to install a JAR contributing a Hibernate mapping file as an Extension. Thus
        // we need to provide the JAR inside WEB-INF/lib. See https://jira.xwiki.org/browse/XWIKI-8271
        "org.xwiki.platform:xwiki-platform-notifications-filters-default:15.10.15",

        // The macro service uses the extension index script service to get the list of uninstalled macros (from
        // extensions) which expects an implementation of the extension index. The extension index script service is a
        // core extension so we need to make the extension index also core.
        "org.xwiki.platform:xwiki-platform-extension-index:15.10.15",
        // Solr search is used to get suggestions for the link quick action.
        "org.xwiki.platform:xwiki-platform-search-solr-query:15.10.15"
    },
    resolveExtraJARs = true
    ,servletEngine = ServletEngine.EXTERNAL
)
// TODO: Consider using AbstractCKEditorIT when upgrading parent to 15.10.
public class OpenProjectIT
{
    private static final String CONNECTION_ID = "test";

    private final LocalDocumentReference page1 = new LocalDocumentReference("Main", "Test");

    //        private OpenProjectInstance openProjectInstance = new OpenProjectInstance();
    private OpenProjectInstance openProjectInstance =
        new ExternalOpenProjectInstance("Admin", "adminadminadmin", "http://172.17.0.1:8081");

    @BeforeAll
    void beforeAll(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {
        testConfiguration.setVerbose(true);
//        openProjectInstance.startOpenProject(setup, testConfiguration);
        setup.loginAsSuperAdmin();
        /* Things that need to be tested:
        // Configuration setup
            add new configuration
            add configuration with name of existing one
            delete, edit, authorize actions work
            color sync button works
        macro:
            warning message with auth link works
            suggesters work
            pasting link from openproject works
            filter builder works
            instance, properties, sorting pickers work
            viewing as guest user does not work
            test different displayers
            test livedata filters
        */
    }

    @Test
    @Order(0)
    void setupInstanceConnection(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {

        // Go to admin page.

        // Press add new connection

        // Fill in the modal

        // Click save

        // Assert that the livedata was updated

        // Assert that the user is not yet authorized
        openProjectInstance.startOpenProject(setup, testConfiguration);
        openProjectInstance.setupInstance(setup.getDriver());

        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();

        String instancePort =
            openProjectInstance.getBaseUrl().substring(openProjectInstance.getBaseUrl().lastIndexOf(":") + 1);

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

//    @Test
//    @Order(10)
//    void tryToAddConnectionWithSameName(WikiReference wiki, TestUtils setup)
//    {
//        OpenProjectAdminPage adminPage = OpenProjectAdminPage.gotoPage();
//        adminPage.addNewConnection(CONNECTION_ID, openProjectInstance.getBaseUrl(), openProjectInstance.getClientId(),
//            openProjectInstance.getClientSecret());
//        adminPage.waitForNotificationErrorMessage(
//            String.format("Connection %s already exists. Use another connection name.", CONNECTION_ID));
//    }
//
//    @Test
//    @Order(20)
//    void defaultOpenprojectMacro(WikiReference wiki, TestUtils setup) throws OperationNotSupportedException
//    {
//        setup.setCurrentWiki(wiki.getName());
//        DocumentReference docRef = new DocumentReference(page1, wiki);
//
//        WYSIWYGEditPage wysiwygEditPage = openMacro(setup, docRef);
//        OpenProjectMacroEditModal macroModal = selectInstanceFromModal(CONNECTION_ID, setup);
//
//        macroModal.clickSubmit();
//
//        wysiwygEditPage.clickSaveAndView();
//        ViewPageWithOpenProjectMacro pageWithMacro = new ViewPageWithOpenProjectMacro();
//
//        List<OpenProjectMacroElement> opMacros = pageWithMacro.getOpenProjectMacros();
//        assertEquals(1, opMacros.size());
//
//        LiveDataElement opMacro = opMacros.get(0).getLivedata();
//        // Equivalent to assert since it will throw an exception if not found.
//        opMacro.getTableLayout().getCell("Identifier", 1).findElement(By.tagName("a"));
//        opMacro.getTableLayout().getCell("Subject", 1).findElement(By.tagName("a"));
//        opMacro.getTableLayout().getCell("Assignee", 1).findElement(By.tagName("a"));
//    }
//
//    @Test
//    @Order(30)
//    void checkLivedataSuggesters(WikiReference wiki, TestUtils setup) throws OperationNotSupportedException
//    {
//        setup.setCurrentWiki(wiki.getName());
//        DocumentReference docRef = new DocumentReference(page1, wiki);
//        WYSIWYGEditPage editPage = openMacro(setup, docRef);
//        OpenProjectMacroEditModal modal = new OpenProjectMacroEditModal();
//        modal.clickMore();
//        modal.getSuggestInput("properties")
//            .clear()
//            .selectByVisibleText("Identifier")
//            .selectByVisibleText("Type")
//            .selectByVisibleText("Start Date")
//            .selectByVisibleText("Assignee")
//            .selectByVisibleText("Priority")
//            .selectByVisibleText("Project")
//            .selectByVisibleText("Status");
//        modal.clickSubmit();
//        editPage.clickSaveAndView();
//
//        ViewPageWithOpenProjectMacro viewPage = new ViewPageWithOpenProjectMacro();
//        assertEquals(1, viewPage.getOpenProjectMacros().size());
//        OpenProjectMacroElement macro = viewPage.getOpenProjectMacros().get(0);
//        TableLayoutElement ld = macro.getLivedata().getTableLayout();
//        SuggestInputElement idSuggest = new SuggestInputElement(ld.getFilter("Identifier"));
//        SuggestInputElement typeSuggest = new SuggestInputElement(ld.getFilter("Type"));
//        SuggestInputElement assigneeSuggest = new SuggestInputElement(ld.getFilter("Assignee"));
//        SuggestInputElement prioritySuggest = new SuggestInputElement(ld.getFilter("Priority"));
//        SuggestInputElement projectSuggest = new SuggestInputElement(ld.getFilter("Project"));
//        SuggestInputElement statusSuggest = new SuggestInputElement(ld.getFilter("Status"));
//
//        useSuggestFilter(idSuggest, ld, "Identifier");
//        useSuggestFilter(typeSuggest, ld, "Type");
//        useSuggestFilter(assigneeSuggest, ld, "Assignee");
//        useSuggestFilter(prioritySuggest, ld, "Priority");
//        useSuggestFilter(projectSuggest, ld, "Project");
//        useSuggestFilter(statusSuggest, ld, "Status");
//
//        // TODO: Test date pickers once the parent gets upgraded to 15.10 and use DateRangePicker PO.
//        WebElement startDateFilter = ld.getFilter("Start Date");
//    }
//
//    @Test
//    @Order(40)
//    void macroParameterFilterTest(WikiReference wiki, TestUtils setup) throws OperationNotSupportedException
//    {
//        setup.setCurrentWiki(wiki.getName());
//        DocumentReference docRef = new DocumentReference(page1, wiki);
//        WYSIWYGEditPage editPage = openMacro(setup, docRef);
//        OpenProjectMacroEditModal modal = new OpenProjectMacroEditModal();
//        // Create a filter.
//        FilterBuilderParameter filterBuilderParameter = modal.getFilterBuilder();
//        filterBuilderParameter.addFilter("assignee.value").setSuggestValue(1, "OpenProject Admin");
//        filterBuilderParameter.addFilter("startDate").setOperator(1, "This week");
//        filterBuilderParameter.addFilter("subject.value").setValue(1, "sp");
//        filterBuilderParameter.addFilter("status")
//            .addConstraint()
//            .setSuggestValue(1, "In Progress")
//            .setSuggestValue(2, "New");
//        // Submit modal and view page.
//        modal.clickSubmit();
//        editPage.clickSaveAndView();
//        // Expect the livedata to show 3 elements.
//        ViewPageWithOpenProjectMacro page = new ViewPageWithOpenProjectMacro();
//        List<OpenProjectMacroElement> macros = page.getOpenProjectMacros();
//        assertEquals(1, macros.size());
//        macros.get(0).getLivedata().getTableLayout().waitUntilReady();
//        String entries = macros.get(0).getElement().findElement(By.className("pagination-current-entries")).getText();
//        assertEquals("Entries 1 - 3 out of 3", entries);
//        // Open modal and expect the builder to contain the added filters.
//        editPage = openMacro(setup, docRef);
//        modal = new OpenProjectMacroEditModal();
//        filterBuilderParameter = modal.getFilterBuilder();
//        List<FilterBuilderFilter> filters = filterBuilderParameter.getFilters();
//        assertEquals(4, filters.size());
//        // Clear the filters and expect the macro to display all the entries.
//        filterBuilderParameter.clearFilters();
//        modal.clickSubmit();
//        editPage.clickSaveAndView();
//        page = new ViewPageWithOpenProjectMacro();
//        macros = page.getOpenProjectMacros();
//        assertEquals(1, macros.size());
//        entries = macros.get(0).getElement().findElement(By.className("pagination-current-entries")).getText();
//        assertEquals("Entries 1 - 5 out of 36", entries);
//    }

    //    private static void useSuggestFilter(SuggestInputElement suggest, TableLayoutElement ld, String filteredColumn)
//    {
//        suggest.click().waitForSuggestions().selectByIndex(1);
//        String selectedVal = suggest.getSelectedSuggestions().get(0).getLabel();
//        ld.waitUntilReady();
//        assertEquals(selectedVal, ld.getCell(filteredColumn, 1).getText());
//        suggest.clear();
//    }
//
    private WYSIWYGEditPage openMacro(TestUtils setup, DocumentReference docRef)
    {
        ViewPage page = setup.gotoPage(docRef);
        WYSIWYGEditPage wysiwygEditPage = page.editWYSIWYG();
        CKEditor editor = new CKEditor("content").waitToLoad();
        MacroDialogSelectModal modal = openMacrosModal(setup);
        modal.filterByText("Open Project", 1);
        modal.clickSelect().waitUntilReady();
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
        return new MacroDialogSelectModal().waitUntilReady();
    }
}
