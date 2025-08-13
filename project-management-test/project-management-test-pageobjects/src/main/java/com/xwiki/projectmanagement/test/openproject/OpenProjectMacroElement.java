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

public class OpenProjectMacroElement extends BaseElement
{
    private final WebElement self;

    public OpenProjectMacroElement(WebElement element)
    {
        self = element;
    }

    public boolean isUserAuthorized()
    {
        try {
            self.findElement(By.className("warningmessage"));
            return false;
        } catch (NoSuchElementException e) {
            return true;
        }
    }

    public ViewPageWithOpenProjectMacro authorizeUser()
    {
        self.findElement(By.cssSelector(".warningmessage a")).click();
        ViewPageWithOpenProjectMacro vp = new ViewPageWithOpenProjectMacro();
        vp.waitUntilPageIsReady();
        return vp;
    }

    public LiveDataElement getLivedata() throws OperationNotSupportedException
    {
        String id = self.getAttribute("id");
        if (StringUtils.isEmpty(id)) {
            throw new OperationNotSupportedException(
                "Can't retrieve the livedata element if no id was provided to the macro.");
        }
        return new LiveDataElement(id);
    }

    public CardLayoutElement getCards() throws OperationNotSupportedException
    {
        String id = self.getAttribute("id");
        if (StringUtils.isEmpty(id)) {
            throw new OperationNotSupportedException(
                "Can't retrieve the cards element if no id was provided to the macro.");
        }
        return new CardLayoutElement(id);
    }

    public OpenProjectSingleDisplayer getSingleWorkItem()
    {
        return new OpenProjectSingleDisplayer(self);
    }

    public WebElement getElement()
    {
        return self;
    }
}
