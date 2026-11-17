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

import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.test.ui.po.CreatePagePage;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.SuggestInputElement;

/**
 * Models the form displayed when creating a page of the "OpenProject Space" type, where the user chooses the
 * OpenProject connection, project and parent work package of the new space.
 *
 * @version $Id$
 * @since 1.4.0
 */
public class OpenProjectSpaceEditPage extends InlinePage
{
    private static final String TEMPLATE_PROVIDER = "OpenProject.Code.TemplateProvider";

    private static final By CONNECTION = By.cssSelector(".openproj-relation-client-params-edit select[name='instance']");

    private static final By PROJECT = By.name("ProjectManagement.Code.RelationClass_0_project");

    /**
     * Wait for the form to be ready.
     */
    public OpenProjectSpaceEditPage()
    {
        getDriver().waitUntilElementIsVisible(CONNECTION);
    }

    /**
     * Create a page of the "OpenProject Space" type from the create page form.
     *
     * @param reference the home page of the new space
     * @return the form of the new space
     */
    public static OpenProjectSpaceEditPage create(DocumentReference reference)
    {
        SpaceReference space = reference.getLastSpaceReference();
        // Open the create form with the location of the new page already filled.
        String parent = getUtil().serializeReference(space.getParent().removeParent(reference.getWikiReference()));
        getUtil().gotoPage("Main", "WebHome", "create", "spaceReference=" + getUtil().escapeURL(parent) + "&name="
            + getUtil().escapeURL(space.getName()) + "&tocreate=nonterminal");
        CreatePagePage createPage = new CreatePagePage();
        createPage.setTemplate(TEMPLATE_PROVIDER);
        createPage.clickCreate();
        return new OpenProjectSpaceEditPage();
    }

    /**
     * Choose the OpenProject connection of the new space.
     *
     * @param connection the name of the connection
     * @return this page
     */
    public OpenProjectSpaceEditPage setConnection(String connection)
    {
        // A plain select: its xwikiSelectize class doesn't make it a suggest picker.
        new Select(getDriver().findElement(CONNECTION)).selectByVisibleText(connection);
        // The project and work package pickers are created again, synchronously, for the new connection.
        return this;
    }

    /**
     * Search the projects suggested for the given text, and wait for the search to end.
     *
     * @param text the text to search
     * @return the names of the suggested projects
     */
    public List<String> searchProjects(String text)
    {
        SuggestInputElement projectPicker = getProjectPicker();
        List<String> suggestions = projectPicker.click().sendKeys(text).waitForSuggestions().getSuggestions().stream()
            .map(SuggestInputElement.SuggestionElement::getLabel).collect(Collectors.toList());
        projectPicker.hideSuggestions();
        return suggestions;
    }

    private SuggestInputElement getProjectPicker()
    {
        return new SuggestInputElement(getDriver().findElement(PROJECT));
    }
}
