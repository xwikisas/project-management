package com.xwiki.projectmanagement.relations.store;

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
import com.xpn.xwiki.objects.classes.TextAreaClass;

/**
 * Initializes the work package link xclass.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Component
@Named(ProjectManagementRelation.CLASS_FULLNAME)
@Singleton
public class RelationClassDocumentInitializer extends AbstractMandatoryClassInitializer
{
    /**
     * The reference identifying the created XClass.
     */
    public static final LocalDocumentReference CLASS_REFERENCE = new LocalDocumentReference(Arrays.asList(
        "ProjectManagement", "Code"), "RelationClass");

    /**
     * Default constructor.
     */
    public RelationClassDocumentInitializer()
    {
        super(CLASS_REFERENCE, "Project Management Relation Class");
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        xclass.addTextField(ProjectManagementRelation.FIELD_CLIENT, "Project Management Client ID", 20);
        xclass.addTextField(ProjectManagementRelation.FIELD_PROJECT, "Project ID", 20);
        xclass.addTextField(ProjectManagementRelation.FIELD_WORK_ITEM, "Work item ID", 20);
        xclass.addTextAreaField(ProjectManagementRelation.FIELD_CLIENT_PARAMS, "Client Parameters", 40, 20,
            TextAreaClass.EditorType.PURE_TEXT, TextAreaClass.ContentType.PURE_TEXT);
    }
}
