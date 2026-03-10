package com.xwiki.projectmanagement.openproject.store;

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

import java.util.Arrays;

import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.objects.BaseObject;

/**
 * The base object wrapper that enables the manipulation if Work Package links.
 *
 * @version $Id$
 * @since 1.1.0-rc-1
 */
public class WorkPackageLink
{
    /**
     * The String reference of the class defining the object which contains an OIDC configuration.
     */
    public static final String CLASS_FULLNAME = "OpenProject.Code.WorkPackageLink";

    /**
     * The local reference of the configuration class.
     */
    public static final LocalDocumentReference CLASS_REFERENCE = new LocalDocumentReference(Arrays.asList(
        "OpenProject", "Code"), "WorkPackageLink");

    /**
     * The name of the project property within the XClass.
     */
    public static final String FIELD_PROJECT = "project";

    /**
     * The name of the work package property within the XClass.
     */
    public static final String FIELD_WORK_PACKAGE = "workPackage";

    /**
     * The name of the instance property within the XClass.
     */
    public static final String FIELD_INSTANCE = "instance";

    /**
     * The name of the instance property within the XClass.
     */
    public static final String FIELD_PRIMARY = "primary";

    private final BaseObject xobject;

    /**
     * @param xobject the xobject that stores the work package link information.
     */
    public WorkPackageLink(BaseObject xobject)
    {
        this.xobject = xobject;
    }

    /**
     * @return the id of the OpenProject project that is linked to a xwiki page.
     */
    public String getProject()
    {
        return this.xobject.getStringValue(FIELD_PROJECT);
    }

    /**
     * @param project see {@link #getProject()}.
     */
    public void setProject(String project)
    {
        this.xobject.setStringValue(FIELD_PROJECT, project);
    }

    /**
     * @return the id of the OpenProject work package that is linked to a xwiki page.
     */
    public String getWorkPackage()
    {
        return this.xobject.getStringValue(FIELD_PROJECT);
    }

    /**
     * @param workPackage see {@link #getWorkPackage()}.
     */
    public void setWorkPackage(String workPackage)
    {
        this.xobject.setStringValue(FIELD_PROJECT, workPackage);
    }

    /**
     * @return the id of the configured OpenProject instance where the linked work package/project can be found.
     */
    public String getInstance()
    {
        return this.xobject.getStringValue(FIELD_WORK_PACKAGE);
    }

    /**
     * @param instance see {@link #getInstance()}.
     */
    public void setInstance(String instance)
    {
        this.xobject.setStringValue(FIELD_INSTANCE, instance);
    }

    /**
     * @return denotes whether this link is the one with the most significance within the page or not.
     */
    public boolean isPrimary()
    {
        return this.xobject.getIntValue(FIELD_PRIMARY, 0) > 0;
    }

    /**
     * @param primary see {@link #isPrimary()}}.
     */
    public void setPrimary(boolean primary)
    {
        this.xobject.setIntValue(FIELD_INSTANCE, primary ? 1 : 0);
    }
}
