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

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.ckeditor.test.po.CKEditor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the OpenProject CKEditor plugin.
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
public class OpenProjectCKEditorIT extends AbstractOpenProjectIT
{
    private static final String MARKER = "!!";

    private static final String SUBJECT = "Send invitation to speakers";

    private static final String CKEDITOR_USER = "CKEditorUser";

    private static final String CKEDITOR_USER_PASSWORD = "pass";

    @Test
    @Order(1)
    void insertInlineWorkPackageWithConfiguredMarker(TestUtils setup, TestReference testReference)
    {
        OpenProjectAdminPage.gotoPage().setWorkPackageSearchMarker(MARKER);

        // The work package search uses the OpenProject instance from the page relation.
        setup.deletePage(testReference);
        setup.createPage(testReference, "");
        setup.addObject(testReference, "ProjectManagement.Code.RelationClass", "client", "openproject",
            "clientParams", String.format("{\"instance\":\"%s\"}", CONNECTION_ID));

        setup.gotoPage(testReference, "edit", "editor=wysiwyg");
        WYSIWYGEditPage editPage = new WYSIWYGEditPage();
        OpenProjectRichTextArea textArea =
            new OpenProjectRichTextArea(new CKEditor("content").waitToLoad().getRichTextArea());
        textArea.searchWorkPackage(MARKER + "speakers", MARKER + "speakers", SUBJECT);
        // The work packages are only searched from 3 characters typed after the marker: the search is closed when
        // the query gets shorter, and opened again when it gets longer.
        assertTrue(textArea.deleteWorkPackageSearchCharacters("eakers".length()),
            "The work package search should be closed with fewer than 3 characters");
        textArea.searchWorkPackage("eakers", MARKER + "speakers", SUBJECT);
        textArea.insertSelectedWorkPackage();
        editPage.clickSaveAndView();

        // The work package is displayed inline: its identifier, linking to OpenProject, followed by its subject.
        List<OpenProjectMacroElement> macros = new ViewPageWithOpenProjectMacro().getOpenProjectMacros();
        assertEquals(1, macros.size());
        assertEquals(SUBJECT, macros.get(0).getInlineWorkPackageSummary());
        List<String> links = macros.get(0).getWorkPackageLinks();
        assertEquals(1, links.size(), links.toString());
        assertThat(links.get(0), startsWith("#"));

        setup.gotoPage(testReference, "edit", "editor=wiki");
        String content = new WikiEditPage().getContent();
        assertThat(content, startsWith("{{openproject "));
        assertThat(content, containsString(String.format("instance=\"%s\"", CONNECTION_ID)));
        assertThat(content, containsString("displayer=\"inline\""));
        // The work package is listed in the macro content, not in the deprecated identifier parameter.
        assertThat(content, containsString("source=\"list\""));
        assertThat(content, containsString("{{/openproject}}"));
        assertThat(content, not(containsString("identifier=")));
        assertThat(content, not(containsString(MARKER)));
    }

    @Test
    @Order(2)
    void insertInlineWorkPackageWithDefaultConnection(TestUtils setup, TestReference testReference) throws Exception
    {
        // On a page that isn't linked to an OpenProject project, the work packages are searched in the default
        // connection, which is the only connection.
        resetDefaultConnection(setup);
        setup.deletePage(testReference);
        setup.createPage(testReference, "");
        setup.gotoPage(testReference, "edit", "editor=wysiwyg");
        WYSIWYGEditPage editPage = new WYSIWYGEditPage();
        new OpenProjectRichTextArea(new CKEditor("content").waitToLoad().getRichTextArea())
            .insertWorkPackage(MARKER + "speakers", SUBJECT);
        editPage.clickSaveAndView();

        List<OpenProjectMacroElement> macros = new ViewPageWithOpenProjectMacro().getOpenProjectMacros();
        assertEquals(1, macros.size());
        assertEquals(SUBJECT, macros.get(0).getInlineWorkPackageSummary());
        // The inserted macro doesn't set its connection, so that it follows the default connection.
        setup.gotoPage(testReference, "edit", "editor=wiki");
        String content = new WikiEditPage().getContent();
        assertThat(content, startsWith("{{openproject "));
        assertThat(content, containsString("source=\"list\""));
        assertThat(content, not(containsString("instance=")));

        // With several connections and no default one, the search tells what to do, and its entry inserts nothing.
        DocumentReference otherReference = getConnectionReference("CKEditorOtherConnection");
        try {
            saveConnection(setup, otherReference, "other");
            resetDefaultConnection(setup);
            setup.deletePage(testReference);
            setup.createPage(testReference, "");
            setup.gotoPage(testReference, "edit", "editor=wysiwyg");
            editPage = new WYSIWYGEditPage();
            new OpenProjectRichTextArea(new CKEditor("content").waitToLoad().getRichTextArea())
                .selectWorkPackageSearchEntry(MARKER + "speakers", "No OpenProject connection to search: link this "
                    + "page to an OpenProject project, or ask an administrator to choose a default connection.");
            editPage.clickSaveAndView();

            setup.gotoPage(testReference, "edit", "editor=wiki");
            content = new WikiEditPage().getContent();
            assertThat(content, containsString(MARKER + "speakers"));
            assertThat(content, not(containsString("{{openproject")));
        } finally {
            deleteConnection(setup, otherReference);
            resetDefaultConnection(setup);
        }
    }

    @Test
    @Order(3)
    void stopWorkPackageSearch(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);
        setup.createPage(testReference, "");
        setup.addObject(testReference, "ProjectManagement.Code.RelationClass", "client", "openproject",
            "clientParams", String.format("{\"instance\":\"%s\"}", CONNECTION_ID));
        setup.gotoPage(testReference, "edit", "editor=wysiwyg");
        OpenProjectRichTextArea textArea =
            new OpenProjectRichTextArea(new CKEditor("content").waitToLoad().getRichTextArea());
        textArea.recordWorkPackageSearches();

        // No work package matches the query.
        textArea.type(MARKER + "zzz");
        assertEquals(List.of("zzz"), textArea.waitForWorkPackageSearch("zzz"));
        // The text that extends a query without match isn't searched, since it can't match either.
        textArea.type(" is");
        // A query that doesn't extend it is searched.
        textArea.deleteCharacters(4);
        textArea.type("s");
        assertEquals(List.of("zzz", "zzs"), textArea.waitForWorkPackageSearch("zzs"));
        // The marker typed again, on the same line, starts a new search.
        textArea.type(" and " + MARKER + "zzu");
        assertEquals(List.of("zzz", "zzs", "zzu"), textArea.waitForWorkPackageSearch("zzu"));

        // Escape stops the search, until the marker is typed again.
        textArea.newParagraph();
        textArea.searchWorkPackage(MARKER + "speakers", MARKER + "speakers", SUBJECT);
        textArea.closeWorkPackageSearch();
        textArea.type(" to");
        textArea.searchWorkPackage(" and " + MARKER + "speakers", MARKER + "speakers", SUBJECT);
        textArea.closeWorkPackageSearch();
        textArea.newParagraph();
        textArea.type(MARKER + "zzt");
        List<String> queries = textArea.waitForWorkPackageSearch("zzt");
        assertTrue(queries.stream().noneMatch(query -> query.startsWith("speakers ")), queries.toString());
        new WYSIWYGEditPage().clickCancel();
    }

    @Test
    @Order(4)
    void searchWorkPackageWithoutOpenProjectAccount(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.deletePage(testReference);
        setup.createPage(testReference, "");
        setup.addObject(testReference, "ProjectManagement.Code.RelationClass", "client", "openproject",
            "clientParams", String.format("{\"instance\":\"%s\"}", CONNECTION_ID));
        setup.createUser(CKEDITOR_USER, CKEDITOR_USER_PASSWORD, null);
        String editURL = setup.getURL(testReference, "edit", "editor=wysiwyg");
        switchUser(setup, CKEDITOR_USER, CKEDITOR_USER_PASSWORD, editURL);

        // The user never connected their OpenProject account: the search tells them to, as the macros do, with a link
        // that comes back to the page once the account is connected.
        String link = new OpenProjectRichTextArea(new CKEditor("content").waitToLoad().getRichTextArea())
            .searchWorkPackageMessage(MARKER + "speakers", "You need to be authenticated to OpenProject in order to "
                + "view the work packages. Click here to authenticate.");
        assertThat(link, containsString("/OpenProject/Code/RenewOAuthConnection"));
        String decodedLink = URLDecoder.decode(link, StandardCharsets.UTF_8);
        assertThat(decodedLink, containsString("connectionName=" + CONNECTION_ID));
        // XWiki writes the URL of a space home page without its final "WebHome".
        String pagePath = new URL(setup.getURL(testReference)).getPath().replaceFirst("WebHome$", "");
        assertThat(decodedLink, containsString("redirectUrl=" + pagePath));
        new WYSIWYGEditPage().clickCancel();
    }

    @Test
    @Order(5)
    void insertMacroWithQuickAction(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);
        setup.createPage(testReference, "");
        setup.gotoPage(testReference, "edit", "editor=wysiwyg");
        WYSIWYGEditPage editPage = new WYSIWYGEditPage();

        // The OpenProject macros are inserted through their edit modal, although none of their parameters is
        // mandatory, so that the parent project can be chosen.
        OpenProjectDirectMacroEditModal modal =
            new OpenProjectRichTextArea(new CKEditor("content").waitToLoad().getRichTextArea())
                .insertMacroWithQuickAction("subprojects", "OpenProject - Subprojects");
        // The macro uses the default connection, i.e. the only one.
        assertEquals(String.format("Default connection (%s)", CONNECTION_ID), modal.getSelectedInstance());
        modal.clickSubmit();
        editPage.clickSaveAndView();

        // Without a project, the macro asks for one.
        assertThat(new ViewPageWithOpenProjectMacro().getContent(),
            containsString("Choose the project whose subprojects to display with the project parameter."));
        setup.gotoPage(testReference, "edit", "editor=wiki");
        String content = new WikiEditPage().getContent();
        assertThat(content, startsWith("{{openproject-subprojects"));
        assertThat(content, not(containsString("instance=")));
    }

    @Test
    @Order(6)
    void editProjectMacroTwice(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);
        setup.createPage(testReference, "{{openproject-subprojects project=\"1\"/}}");

        // Opening the edit modal of a macro again in the same editor keeps its project.
        OpenProjectDirectMacroEditModal modal =
            new OpenProjectDirectMacroEditModal(setup, testReference, "OpenProject - Subprojects");
        modal.cancelAndEditAgain().clickSubmit();
        modal.getEditPage().clickSaveAndView();

        setup.gotoPage(testReference, "edit", "editor=wiki");
        assertThat(new WikiEditPage().getContent(), containsString("project=\"1\""));
    }

    @Test
    @Order(7)
    void editMacroWithDeprecatedCount(TestUtils setup, TestReference testReference)
    {
        setup.deletePage(testReference);
        setup.createPage(testReference, String.format("{{openproject-news instance=\"%s\" count=\"3\"/}}",
            CONNECTION_ID));

        // The editor migrates the deprecated count parameter to the limit one, and doesn't display its field, which
        // would be a second, empty, "Maximum News Items" field.
        OpenProjectDirectMacroEditModal modal =
            new OpenProjectDirectMacroEditModal(setup, testReference, "OpenProject - Project News");
        modal.clickMore();
        assertFalse(modal.isMacroParameterDisplayed("count"));
        assertTrue(modal.isMacroParameterDisplayed("limit"));
        modal.clickSubmit();
        modal.getEditPage().clickSaveAndView();

        setup.gotoPage(testReference, "edit", "editor=wiki");
        String content = new WikiEditPage().getContent();
        assertThat(content, containsString("limit=\"3\""));
        assertThat(content, not(containsString("count=")));
    }
}
