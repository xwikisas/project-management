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

import org.openqa.selenium.Keys;
import org.xwiki.ckeditor.test.po.AutocompleteDropdown;
import org.xwiki.ckeditor.test.po.RichTextAreaElement;

/**
 * The OpenProject features of the CKEditor rich text area.
 *
 * @version $Id$
 * @since 1.3.1
 */
public class OpenProjectRichTextArea
{
    private final RichTextAreaElement textArea;

    /**
     * @param textArea the CKEditor rich text area
     */
    public OpenProjectRichTextArea(RichTextAreaElement textArea)
    {
        this.textArea = textArea;
    }

    /**
     * Insert a work package through the work package search: type the query, select the work package in the
     * autocomplete dropdown and wait for the OpenProject macro to be inserted.
     *
     * @param query the text to type, starting with the work package search marker
     * @param workPackage the subject of the work package to select in the autocomplete dropdown
     */
    public void insertWorkPackage(String query, String workPackage)
    {
        this.textArea.sendKeys(query);
        AutocompleteDropdown dropdown = new AutocompleteDropdown();
        dropdown.waitForItemSelected(query, workPackage);
        this.textArea.sendKeys(Keys.ENTER);
        dropdown.waitForItemSubmitted();
        // The macro is inserted asynchronously, after the autocomplete dropdown is closed.
        this.textArea.waitUntilContentContains("startmacro:openproject");
    }
}
