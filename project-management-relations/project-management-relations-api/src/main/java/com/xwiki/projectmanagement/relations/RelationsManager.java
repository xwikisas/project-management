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
package com.xwiki.projectmanagement.relations;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;

import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;

/**
 * Class for managing documents containing instances of Relation Class.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Role
public interface RelationsManager
{
    /**
     * Retrieve the relation class of a given document.
     *
     * @param documentReference the reference of the document that has a relation class associated.
     * @param client the value that should match the retrieved relation class instance.
     * @param ancestorLookup whether the lookup should be done in the ancestors of the document or not.
     * @return the relation object attached to the document OR, if ancestorLookup is set to true, returns  relation
     *     object thats belongs to the closest ancestor.
     */
    ProjectManagementRelation getClientRelation(
        DocumentReference documentReference, String client,
        Boolean ancestorLookup);
}
