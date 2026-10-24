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
package com.xwiki.projectmanagement.calendar.internal.macro;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.http.client.utils.URIBuilder;
import org.xwiki.component.annotation.Component;

import com.xwiki.projectmanagement.calendar.macro.CalendarMacroParameters;

/**
 * Implementation of the abstract project management calendar macro for testing purposes.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("testcalendar")
public class TestCalendarMacro extends AbstractProjectManagementCalendarMacro<CalendarMacroParameters>
{
    private boolean excludeWorkItems;

    /**
     * Default constructor.
     */
    public TestCalendarMacro()
    {
        super("Test Calendar", "Test calendar macro", CalendarMacroParameters.class);
    }

    /**
     * @param excludeWorkItems whether the macro should ask the REST resource to skip the standard work items
     */
    public void setExcludeWorkItems(boolean excludeWorkItems)
    {
        this.excludeWorkItems = excludeWorkItems;
    }

    @Override
    protected void updateUrl(URIBuilder ub, CalendarMacroParameters parameters)
    {
        ub.addParameter("extra", "value");
        if (this.excludeWorkItems) {
            excludeWorkItems(ub);
        }
    }
}
