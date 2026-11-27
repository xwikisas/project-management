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
package com.xwiki.projectmanagement.openproject.internal.mentions;

/**
 * A work package mentioned in a page, by an {@code openproject} macro displaying it.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class WorkPackageMention
{
    private final String workPackageId;

    private final String instance;

    /**
     * @param workPackageId the numeric id of the work package
     * @param instance the name of the OpenProject instance holding the work package
     */
    public WorkPackageMention(String workPackageId, String instance)
    {
        this.workPackageId = workPackageId;
        this.instance = instance;
    }

    /**
     * @return the numeric id of the work package
     */
    public String getWorkPackageId()
    {
        return this.workPackageId;
    }

    /**
     * @return the name of the OpenProject instance holding the work package
     */
    public String getInstance()
    {
        return this.instance;
    }
}
