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

import javax.naming.OperationNotSupportedException;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.CardLayoutElement;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Models the open project macro present in a view page.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class OpenProjectMacroElement extends BaseElement
{
    private final WebElement self;

    /**
     * @param element the wrapper of the open project macro.
     */
    public OpenProjectMacroElement(WebElement element)
    {
        self = element;
    }

    /**
     * @return true if the warning message is displayed or not. The warning message is displayed when the user seeing
     *     the macro is not authorized to the open project instance from which the work packages are retrieved.
     */
    public boolean isUserAuthorized()
    {
        try {
            self.findElement(By.className("warningmessage"));
            return false;
        } catch (NoSuchElementException e) {
            return true;
        }
    }

    /**
     * Click the link from the warning message, authorizing the user to the open project instance.
     *
     * @return the view page after it the user was authorized.
     */
    public ViewPageWithOpenProjectMacro authorizeUser()
    {
        self.findElement(By.cssSelector(".warningmessage a")).click();
        ViewPageWithOpenProjectMacro vp = new ViewPageWithOpenProjectMacro();
        vp.waitUntilPageIsReady();
        return vp;
    }

    /**
     * @return the livedata table displayer of the open project macro.
     * @throws OperationNotSupportedException if the id parameter of the open project macro was not provided. The
     *     livedata page object requires an id in order to be retrieved.
     */
    public LiveDataElement getLivedata() throws OperationNotSupportedException
    {
        String id = self.findElement(By.className("liveData")).getAttribute("id");
        if (StringUtils.isEmpty(id)) {
            throw new OperationNotSupportedException(
                "Can't retrieve the livedata element if no id was provided to the macro.");
        }
        return new LiveDataElement(id);
    }

    /**
     * @return the livedata cards displayer of the open project macro.
     * @throws OperationNotSupportedException if the id parameter of the open project macro was not provided. The
     *     livedata page object requires an id in order to be retrieved.
     */
    public CardLayoutElement getCards() throws OperationNotSupportedException
    {
        String id = self.getAttribute("id");
        if (StringUtils.isEmpty(id)) {
            throw new OperationNotSupportedException(
                "Can't retrieve the cards element if no id was provided to the macro.");
        }
        return new CardLayoutElement(id);
    }

    /**
     * @return the single displayer of the open project macro.
     */
    public OpenProjectSingleDisplayer getSingleWorkItem()
    {
        return new OpenProjectSingleDisplayer(self);
    }

    /**
     * @return the wrapper of the macro.
     */
    public WebElement getElement()
    {
        return self;
    }
}
