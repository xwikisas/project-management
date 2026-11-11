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

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.ckeditor.test.po.MacroDialogEditModal;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.TestUtils;

/**
 * The edit modal of an OpenProject macro that has no page object of its own, e.g. the Subprojects macro.
 *
 * @version $Id$
 * @since 1.4.0
 */
public class OpenProjectDirectMacroEditModal extends AbstractOpenProjectMacroEditModal
{
    /**
     * Model an edit modal that is being opened, and wait for it to be ready.
     */
    public OpenProjectDirectMacroEditModal()
    {
        waitUntilReady();
    }

    /**
     * Edit the given page with the WYSIWYG editor and open the edit modal of the macro it holds (or insert the given
     * macro, if the page holds none).
     *
     * @param setup the test setup
     * @param docRef the edited page
     * @param macroName the pretty name of the macro, as it is displayed in the macro selector
     */
    public OpenProjectDirectMacroEditModal(TestUtils setup, DocumentReference docRef, String macroName)
    {
        super(setup, docRef, macroName, 1);
    }

    @Override
    public MacroDialogEditModal waitUntilReady()
    {
        super.waitUntilReady();
        waitForDeprecatedCountMigration();
        return this;
    }

    @Override
    public void clickSubmit()
    {
        waitForDeprecatedCountMigration();
        super.clickSubmit();
    }

    /**
     * The modal is ready before the script of the instance parameter is loaded. That script moves the value of the
     * deprecated count parameter to the limit one, clears the deprecated parameter and hides its field, so wait until
     * the count field is empty or hidden (it is always hidden for the macros that don't set it).
     */
    private void waitForDeprecatedCountMigration()
    {
        getDriver().waitUntilCondition(driver -> getDriver().findElementsWithoutWaiting(
            By.cssSelector(".macro-editor input[name='count']")).stream()
            .allMatch(input -> !input.isDisplayed() || isEmpty(input)));
    }

    private static boolean isEmpty(WebElement input)
    {
        String value = input.getAttribute("value");
        return value == null || value.isEmpty();
    }
}
