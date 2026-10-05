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

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;
import org.xwiki.ckeditor.test.po.AutocompleteDropdown;
import org.xwiki.ckeditor.test.po.CKEditor;
import org.xwiki.ckeditor.test.po.RichTextAreaElement;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the OpenProject CKEditor plugin.
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
public class OpenProjectCKEditorIT extends AbstractOpenProjectIT
{
    @Test
    void insertInlineWorkPackageWithConfiguredMarker(TestUtils setup, TestReference testReference)
    {
        String marker = "!!";
        OpenProjectAdminPage.gotoPage().setWorkPackageSearchMarker(marker);

        // The work package search uses the OpenProject instance from the page relation.
        setup.deletePage(testReference);
        setup.createPage(testReference, "");
        setup.addObject(testReference, "ProjectManagement.Code.RelationClass", "client", "openproject",
            "clientParams", String.format("{\"instance\":\"%s\"}", CONNECTION_ID));

        setup.gotoPage(testReference, "edit", "editor=wysiwyg");
        WYSIWYGEditPage editPage = new WYSIWYGEditPage();
        RichTextAreaElement textArea = new CKEditor("content").waitToLoad().getRichTextArea();
        String query = marker + "speakers";
        textArea.sendKeys(query);
        AutocompleteDropdown dropdown = new AutocompleteDropdown();
        dropdown.waitForItemSelected(query, "Send invitation to speakers");
        textArea.sendKeys(Keys.ENTER);
        dropdown.waitForItemSubmitted();
        // The macro is inserted asynchronously, after the autocomplete dropdown is closed.
        textArea.waitUntilContentContains("startmacro:openproject");
        editPage.clickSaveAndView();

        setup.gotoPage(testReference, "edit", "editor=wiki");
        String content = new WikiEditPage().getContent();
        assertTrue(content.startsWith("{{openproject "), content);
        assertTrue(content.contains(String.format("instance=\"%s\"", CONNECTION_ID)), content);
        assertTrue(content.contains("workItemsDisplayer=\"workItemInline\""), content);
        assertFalse(content.contains(marker), content);
    }
}
