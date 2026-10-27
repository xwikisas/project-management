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
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The model of the single page displayer of the OpenProject macro.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class OpenProjectSingleDisplayer extends BaseElement
{
    private final WebElement parent;

    /**
     * @param parent the wrapper of the OpenProject macro.
     */
    public OpenProjectSingleDisplayer(WebElement parent)
    {
        this.parent = parent;
    }

    /**
     * @return the element that contains the project name and link.
     */
    public WebElement getProject()
    {
        return parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(2)"));
    }

    /**
     * @return the element that contains the work item description.
     */
    public WebElement getDescription()
    {
        return parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(4)"));
    }

    /**
     * @return the header container that contains information such as the work package id, name and link.
     */
    public WebElement getHeader()
    {
        return parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(1)"));
    }

    /**
     * @param name the work package property pretty name.
     * @return the element containing the said property value.
     */
    public WebElement getProperty(String name)
    {
        WebElement propertiesElement = parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(3)"));
        WebElement propertyElem = propertiesElement
            .findElement(By.xpath(
                String.format("//*[contains(@class, 'work-item-property-name') and contains(., '%s')]", name)));
        return propertyElem.findElement(By.xpath("ancestor::tr")).findElement(By.className("work-item-property-value"));
    }

    /**
     * @return {@code true} if the header links to the work package
     * @since 1.3.1
     */
    public boolean hasWorkPackageLink()
    {
        return !getHeader().findElements(By.tagName("a")).isEmpty();
    }

    /**
     * @return the text of the header, which includes the subject of the work package
     * @since 1.3.1
     */
    public String getHeaderText()
    {
        return getHeader().getText();
    }

    /**
     * @return the text displayed about the project of the work package
     * @since 1.3.1
     */
    public String getProjectText()
    {
        return getProject().getText();
    }

    /**
     * @return {@code true} if the project of the work package is displayed as a link
     * @since 1.3.1
     */
    public boolean hasProjectLink()
    {
        return !getProject().findElements(By.tagName("a")).isEmpty();
    }

    /**
     * @param name the work package property pretty name
     * @return the displayed value of the property
     * @since 1.3.1
     */
    public String getPropertyValue(String name)
    {
        return getProperty(name).getText();
    }

    /**
     * @return the labels of the displayed work package properties, without the trailing colon
     * @since 1.3.1
     */
    public List<String> getPropertyNames()
    {
        return parent.findElements(By.cssSelector(".work-item-page-displayer td.work-item-property-name")).stream()
            .map(element -> StringUtils.removeEnd(element.getText().trim(), ":"))
            .collect(Collectors.toList());
    }

    /**
     * @return waits for the single displayer to render completely, since it renders asynchronously.
     */
    public OpenProjectSingleDisplayer waitUntilReady()
    {
        getDriver().waitUntilElementIsVisible(parent, By.className("work-item-header"));
        return this;
    }
}
