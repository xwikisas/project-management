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

import java.util.Arrays;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.AbstractMandatoryClassInitializer;
import com.xpn.xwiki.objects.classes.BaseClass;

/**
 * Hello there.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named(OpenProjectMentionClassInitializer.CLASS_NAME)
@Singleton
public class OpenProjectMentionClassInitializer extends AbstractMandatoryClassInitializer
{
    /**
     * asda.
     */
    public static final String PROP_WORK_PACKAGE_ID = "workPackageId";

    /**
     * asdads.
     */
    public static final String PROP_INSTANCE = "instance";

    /**
     * Adasdas.
     */
    public static final String CLASS_NAME = "OpenProject.Code.MentionClass";

    /**
     * Reference of the xwiki class.
     */
    public static final LocalDocumentReference REFERENCE = new LocalDocumentReference(Arrays.asList("OpenProject",
        "Code"), "MentionClass");

    /**
     * Default constructor.
     */
    public OpenProjectMentionClassInitializer()
    {
        super(REFERENCE, "Open Project Work Package Mention");
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        xclass.addTextField(PROP_WORK_PACKAGE_ID, "Identifier of the WorkPackage", 20);
        xclass.addTextField(PROP_INSTANCE, "The name of the configured OpenProject instance", 20);
    }
}
