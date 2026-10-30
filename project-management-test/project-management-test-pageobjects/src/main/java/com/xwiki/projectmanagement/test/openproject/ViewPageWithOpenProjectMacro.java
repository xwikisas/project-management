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
import java.util.UUID;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Models a page containing one or more OpenProject macros.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class ViewPageWithOpenProjectMacro extends ViewPage
{
    private static final int LOAD_TIMEOUT = 30;

    /**
     * @return a list of OpenProject macros present in the page.
     */
    public List<OpenProjectMacroElement> getOpenProjectMacros()
    {
        return getDriver().findElements(By.className("open-project-macro"))
            .stream()
            .map(OpenProjectMacroElement::new)
            .collect(Collectors.toList());
    }

    /**
     * @return {@code true} if the page displays the warning asking the current user to connect to OpenProject, which
     *     the OpenProject macros display instead of the work packages when the user can't see them
     * @since 1.4.0
     */
    public boolean isConnectWarningDisplayed()
    {
        return getDriver().hasElementWithoutWaiting(By.cssSelector("#xwikicontent .box.warningmessage"));
    }

    /**
     * @param property the work package property, e.g. {@code type}
     * @param value the value of the property, e.g. {@code Task}
     * @param instance the name of the OpenProject connection the work packages come from
     * @return the text color of the first displayed property value, as computed by the browser
     * @since 1.4.0
     */
    public String getPropertyColor(String property, String value, String instance)
    {
        String cssClass = String.format("openproject-property-%s-%s-%s", property, value, instance);
        return getDriver().findElement(By.className(cssClass)).getCssValue("color");
    }

    /**
     * Wait for the given number of macro errors to be displayed, since some macros are rendered asynchronously.
     *
     * @param count the expected number of macro errors
     * @return the messages of the macro errors displayed in the page content
     * @since 1.3.0-rc-2
     */
    public List<String> waitForMacroErrors(int count)
    {
        By errors = By.cssSelector("#xwikicontent .xwikirenderingerror");
        getDriver().waitUntilCondition(driver -> driver.findElements(errors).size() >= count);
        return getDriver().findElements(errors).stream().map(WebElement::getText).collect(Collectors.toList());
    }

    /**
     * Display a work package at the end of the page content with the given displayer, like the View action of the
     * OpenProject Live Data does in its modal: the work package is requested from the REST resource that renders it
     * asynchronously, and the returned placeholder is replaced once the rendering is done.
     *
     * @param displayer the work items displayer, e.g. {@code liveData}
     * @param instance the name of the OpenProject connection
     * @param workPackageId the id of the work package
     * @return the displayed work package, once its asynchronous rendering has been inserted in the page
     * @since 1.4.0
     */
    public OpenProjectMacroElement displayWorkPackage(String displayer, String instance, String workPackageId)
    {
        String id = "openproject-work-package-" + UUID.randomUUID();
        getDriver().executeScript("const container = document.createElement('div');"
            + "container.id = arguments[0];"
            + "document.getElementById('xwikicontent').appendChild(container);"
            + "const url = XWiki.contextPath + '/rest/wikis/' + encodeURIComponent(XWiki.currentWiki)"
            + "  + '/openproject/async/displayer/' + encodeURIComponent(arguments[1])"
            + "  + '/instance/' + encodeURIComponent(arguments[2])"
            + "  + '/workpackage/' + encodeURIComponent(arguments[3]) + '?outputSyntax=plain';"
            + "require(['jquery'], function($) {"
            + "  $.get(url).done(function(response) {"
            + "    $(container).append(response);"
            + "  });"
            + "});", id, displayer, instance, workPackageId);
        By container = By.id(id);
        int timeout = getDriver().getTimeout();
        getDriver().setTimeout(Math.max(timeout, LOAD_TIMEOUT));
        try {
            // Wait for the asynchronous placeholder to be inserted, then replaced by the rendered work package.
            getDriver().waitUntilCondition(driver -> {
                WebElement element = driver.findElement(container);
                return !element.findElements(By.xpath("*")).isEmpty()
                    && element.findElements(By.className("xwiki-async")).isEmpty();
            });
        } finally {
            getDriver().setTimeout(timeout);
        }
        return new OpenProjectMacroElement(getDriver().findElement(container));
    }
}
