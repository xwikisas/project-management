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

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.BaseModal;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.SuggestInputElement.SuggestionElement;

/**
 * Models the form creating one OpenProject work package per row of a table selected in a page.
 *
 * @version $Id$
 * @since 1.4.0
 */
public class CreateWorkPackagesModal extends BaseModal
{
    private static final By MAPPING_ROWS = By.className("mapping-row");

    private static final int LOAD_TIMEOUT = 30;

    private static final By PROJECT_PICKER_INPUT =
        By.cssSelector("#project-select-multiple-work-packages-container .selectize-input");

    /**
     * Wait for the form to be displayed.
     */
    public CreateWorkPackagesModal()
    {
        super(By.id("create-work-packages-modal"));
        // Bootstrap gives the focus to the modal once it's fully displayed, which would take it from a field focused
        // before, e.g. closing the project picker opened by a click.
        getDriver().waitUntilCondition(driver -> this.container.equals(driver.switchTo().activeElement()));
    }

    /**
     * @param connection the name of the OpenProject connection to create the work packages with
     * @return this form
     */
    public CreateWorkPackagesModal selectConnection(String connection)
    {
        Select select = new Select(this.container.findElement(By.id("connection-select-multiple-work-packages")));
        // The connection is already selected (and the selector disabled) when there is only one.
        if (select.getFirstSelectedOption() == null
            || !connection.equals(select.getFirstSelectedOption().getText()))
        {
            select.selectByVisibleText(connection);
        }
        return this;
    }

    /**
     * Select the project of the work packages, and wait for the form to propose to map the table columns to the work
     * package fields.
     *
     * @param project the name of the project
     * @return this form
     */
    public CreateWorkPackagesModal selectProject(String project)
    {
        WebElement projectInput = this.container.findElement(By.id("project-select-multiple-work-packages"));
        WebElement mapping = this.container.findElement(By.id("work-packages-table-mapping-container"));
        // The projects and then the fields of the work packages are read from OpenProject, which can be slow to answer.
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            // The picker loads the projects of the connection as soon as it's displayed (the constructor waits for
            // them), and lists them when it gets the focus.
            SuggestInputElement picker = new SuggestInputElement(projectInput);
            // SuggestInputElement#click() clicks the top left corner of the picker, which misses this picker.
            this.container.findElement(PROJECT_PICKER_INPUT).click();
            // The suggestions are displayed outside of the modal, where the tested browsers can't click them: the
            // project is chosen with the keyboard instead, once it's the only suggestion.
            picker.sendKeys(project).waitForSuggestions();
            getDriver().waitUntilCondition(driver -> {
                List<String> labels = picker.getSuggestions().stream().map(SuggestionElement::getLabel)
                    .collect(Collectors.toList());
                return List.of(project).equals(labels);
            });
            picker.sendKeys(Keys.ENTER);
            getDriver().waitUntilCondition(driver -> mapping.isDisplayed());
        } finally {
            getDriver().setTimeout(timeout);
        }
        return this;
    }

    /**
     * Add a row mapping a table column to a work package field.
     *
     * @return this form
     */
    public CreateWorkPackagesModal addMapping()
    {
        int count = this.container.findElements(MAPPING_ROWS).size();
        this.container.findElement(By.id("add-mapping")).click();
        getDriver().waitUntilCondition(driver -> this.container.findElements(MAPPING_ROWS).size() > count);
        return this;
    }

    /**
     * @return the table columns proposed by the last mapping row, as displayed
     */
    public List<String> getMappingTableColumns()
    {
        return getMappingOptions("table-key");
    }

    /**
     * @return the work package fields proposed by the last mapping row, as displayed
     */
    public List<String> getMappingWorkPackageFields()
    {
        return getMappingOptions("op-key");
    }

    private List<String> getMappingOptions(String selectClass)
    {
        List<WebElement> rows = this.container.findElements(MAPPING_ROWS);
        WebElement select = rows.get(rows.size() - 1).findElement(By.className(selectClass));
        // Leave out the placeholder option, which has no value.
        return new Select(select).getOptions().stream()
            .filter(option -> StringUtils.isNotEmpty(option.getAttribute("value")))
            .map(option -> option.getAttribute("textContent"))
            .collect(Collectors.toList());
    }
}
