package com.xwiki.projectmanagement.openproject.internal.store;

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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.AbstractMandatoryClassInitializer;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xwiki.projectmanagement.openproject.store.WorkPackageLink;

/**
 * Initializes the work package link xclass.
 *
 * @version $Id$
 * @since 1.1.0-rc-1
 */
@Component
@Named(WorkPackageLink.CLASS_FULLNAME)
@Singleton
public class WorkPackageLinkClassDocumentInitializer extends AbstractMandatoryClassInitializer
{
    /**
     * The reference identifying the created XClass.
     */
    public static final LocalDocumentReference CLASS_REFERENCE = new LocalDocumentReference(Arrays.asList(
        "OpenProject", "Code"), "WorkPackageLink");

    /**
     * Default constructor.
     */
    public WorkPackageLinkClassDocumentInitializer()
    {
        super(CLASS_REFERENCE, "Open Project Page Link Class");
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        xclass.addTextField(WorkPackageLink.FIELD_PROJECT, "OpenProject Project ID", 20);
        xclass.addTextField(WorkPackageLink.FIELD_WORK_PACKAGE, "OpenProject Work Package ID", 20);
        xclass.addTextField(WorkPackageLink.FIELD_INSTANCE, "Open Project Instance Name", 40);
        xclass.addBooleanField(WorkPackageLink.FIELD_PRIMARY, "Is Primary Link?");
    }
}
