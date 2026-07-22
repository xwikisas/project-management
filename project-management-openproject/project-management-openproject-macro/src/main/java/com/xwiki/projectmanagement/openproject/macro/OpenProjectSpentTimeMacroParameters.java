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
package com.xwiki.projectmanagement.openproject.macro;

/**
 * Parameters for the OpenProject Spent Time macro.
 *
 * @version $Id$
 * @since 1.2
 */
public class OpenProjectSpentTimeMacroParameters extends BaseDirectOpenProjectMacroParameters
{
    private int count = 25;

    private int days = 7;

    /**
     * @return the maximum number of time entries to retrieve.
     */
    public int getCount()
    {
        return count;
    }

    /**
     * @param count see {@link #getCount()}.
     */
    public void setCount(int count)
    {
        this.count = count;
    }

    /**
     * @return the number of past days to include. A value of 0 means no date filter is applied.
     */
    public int getDays()
    {
        return days;
    }

    /**
     * @param days see {@link #getDays()}.
     */
    public void setDays(int days)
    {
        this.days = days;
    }
}
