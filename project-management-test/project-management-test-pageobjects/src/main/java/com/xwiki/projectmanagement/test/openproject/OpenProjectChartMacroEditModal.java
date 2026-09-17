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

import org.openqa.selenium.support.ui.Select;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.TestUtils;

/**
 * Models the edit modal of the OpenProject Charts macro.
 *
 * @version $Id$
 * @since 1.3.0-rc-2
 */
public class OpenProjectChartMacroEditModal extends AbstractOpenProjectMacroEditModal
{
    private static final String MACRO_NAME = "OpenProject Charts";

    private static final int EXPECTED_MACRO_COUNT = 1;

    private static final String TYPE_PARAMETER = "type";

    private static final String PROPERTY_PARAMETER = "property";

    public OpenProjectChartMacroEditModal()
    {
    }

    /**
     * Insert an OpenProject Charts macro in the given page and wait for its edit modal to be displayed.
     *
     * @param setup the test setup.
     * @param docRef the page in which the macro will be inserted.
     */
    public OpenProjectChartMacroEditModal(TestUtils setup, DocumentReference docRef)
    {
        super(setup, docRef, MACRO_NAME, EXPECTED_MACRO_COUNT);
    }

    public String getChartType()
    {
        return getTypeSelect().getFirstSelectedOption().getDomAttribute("value");
    }

    public OpenProjectChartMacroEditModal setChartType(String type)
    {
        getTypeSelect().selectByValue(type);
        return this;
    }

    /**
     * Select the property whose values will be grouped by the chart.
     *
     * @param property the technical name of the property.
     * @return this object.
     */
    public OpenProjectChartMacroEditModal setProperty(String property)
    {
        getSuggestInput(PROPERTY_PARAMETER).click().waitForSuggestions().selectByValue(property);
        return this;
    }

    private Select getTypeSelect()
    {
        // We don't use org.xwiki.test.ui.po.Select since it presses Escape after selecting an option, which closes
        // the macro edit modal.
        return new Select(getMacroParameterInput(TYPE_PARAMETER));
    }
}
