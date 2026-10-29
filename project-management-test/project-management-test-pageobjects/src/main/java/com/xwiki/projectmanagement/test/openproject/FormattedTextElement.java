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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * A formatted text displayed from OpenProject, e.g. the description of a work package.
 *
 * @version $Id$
 * @since 1.3.1
 */
public class FormattedTextElement extends BaseElement
{
    private final WebElement container;

    /**
     * @param container the element holding the formatted text
     */
    public FormattedTextElement(WebElement container)
    {
        this.container = container;
    }

    /**
     * @return the displayed text
     */
    public String getText()
    {
        return this.container.getText();
    }

    /**
     * @return the text of the displayed headings
     */
    public List<String> getHeadings()
    {
        return getTexts(By.cssSelector("h1, h2, h3, h4, h5, h6"));
    }

    /**
     * @return the texts displayed in bold
     */
    public List<String> getBoldTexts()
    {
        return getTexts(By.cssSelector("strong, b"));
    }

    /**
     * @return the text of the displayed list items
     */
    public List<String> getListItems()
    {
        return getTexts(By.tagName("li"));
    }

    /**
     * @return the URL of the displayed links, by label, in the order they are displayed
     */
    public Map<String, String> getLinks()
    {
        Map<String, String> links = new LinkedHashMap<>();
        for (WebElement link : this.container.findElements(By.tagName("a"))) {
            links.put(link.getText(), link.getAttribute("href"));
        }
        return links;
    }

    private List<String> getTexts(By selector)
    {
        return this.container.findElements(selector).stream().map(WebElement::getText).collect(Collectors.toList());
    }
}
