package com.xwiki.projectmanagement.openproject.model;

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

/**
 * The model of a link to a work package.
 *
 * @version $Id$
 * @since 1.1.0-rc-1
 */
public class WorkPackageLink
{
    private String instance;

    private String workPackage;

    private String project;

    private Boolean primary;

    /**
     * @return the id of the OpenProject project that is linked to a xwiki page.
     */
    public String getProject()
    {
        return project;
    }

    /**
     * @param project see {@link #getProject()}.
     */
    public void setProject(String project)
    {
        this.project = project;
    }

    /**
     * @return the id of the OpenProject work package that is linked to a xwiki page.
     */
    public String getWorkPackage()
    {
        return workPackage;
    }

    /**
     * @param workPackage see {@link #getWorkPackage()}.
     */
    public void setWorkPackage(String workPackage)
    {
        this.workPackage = workPackage;
    }

    /**
     * @return the id of the configured OpenProject instance where the linked work package/project can be found.
     */
    public String getInstance()
    {
        return instance;
    }

    /**
     * @param instance see {@link #getInstance()}.
     */
    public void setInstance(String instance)
    {
        this.instance = instance;
    }

    /**
     * @return whether this link is marked as the primary, most important link of the page.
     */
    public Boolean isPrimary()
    {
        return this.primary;
    }

    /**
     * @param primary see {@link #isPrimary()}.
     */
    public void setPrimary(Boolean primary)
    {
        this.primary = primary;
    }
}
