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
import org.xwiki.test.ui.po.BaseElement;

public class OpenProjectSingleDisplayer extends BaseElement
{
    private final WebElement parent;

    public OpenProjectSingleDisplayer(WebElement parent)
    {
        this.parent = parent;
    }

    public WebElement getProject()
    {
        return parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(2)"));
    }

    public WebElement getDescription()
    {
        return parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(4)"));
    }

    public WebElement getHeader()
    {
        return parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(1)"));
    }

    public WebElement getProperty(String name)
    {
        WebElement propertiesElement = parent.findElement(By.cssSelector(".work-item-page-displayer>div:nth-child(3)"));
        WebElement propertyElem = propertiesElement
            .findElement(By.xpath(
                String.format("//*[contains(@class, 'work-item-property-name') and contains(., '%s')]", name)));
        return propertyElem.findElement(By.xpath("ancestor::tr")).findElement(By.className("work-item-property-value"));
    }
}
