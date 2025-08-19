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
import org.xwiki.test.ui.po.ViewPage;

/**
 * Models a page containing one or more open project macros.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class ViewPageWithOpenProjectMacro extends ViewPage
{
    /**
     * @return a list of open project macros present in the page.
     */
    public List<OpenProjectMacroElement> getOpenProjectMacros()
    {
        return getDriver().findElements(By.className("open-project-macro"))
            .stream()
            .map(OpenProjectMacroElement::new)
            .collect(Collectors.toList());
    }
}
