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
package com.xwiki.projectmanagement.relations.script;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xwiki.projectmanagement.relations.RelationsManager;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;
import com.xwiki.projectmanagement.script.ProjectManagementScriptService;

/**
 * Project management relations script service. Offers useful methods with regards to the relations of xwiki pages with
 * work items coming from different implementers.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named(ProjectManagementScriptService.ROLE_HINT + "." + ProjectManagementRelationsScriptService.ROLE_HINT)
@Singleton
public class ProjectManagementRelationsScriptService implements ScriptService
{
    /**
     * The role hint of this component.
     */
    public static final String ROLE_HINT = "relations";

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    private RelationsManager relationsManager;

    /**
     * Retrieve the relation class of a given document.
     *
     * @param documentReference the reference of the document that has a relation class associated.
     * @param client the value that should match the retrieved relation class instance.
     * @param ancestorLookup whether the lookup should be done in the ancestors of the document or not.
     * @return the relation object attached to the document OR, if ancestorLookup is set to true, returns  relation
     *     object thats belongs to the closest ancestor.
     * @throws AccessDeniedException if the user does not have view rights on the document reference.
     */
    public ProjectManagementRelation getClientRelation(DocumentReference documentReference, String client,
        boolean ancestorLookup) throws AccessDeniedException
    {
        authorizationManager.checkAccess(Right.VIEW, documentReference);
        ProjectManagementRelation relation = relationsManager.getClientRelation(documentReference, client,
            ancestorLookup);
        if (relation == null) {
            return null;
        }
        authorizationManager.checkAccess(Right.VIEW, relation.getDocumentReference());
        return relation;
    }

    /**
     * Retrieve all the relation objects attached to a document or to one of its ancestors.
     *
     * @param documentReference the document reference that might or might not contain relation objects.
     * @param ancestorLookup whether relation objects should be fetched from the closest ancestor (if self does not
     *     have any objects attached).
     * @return a list of all the relation objects attached to the passed document reference or to the first ancestor
     *     that contains said objects. The user should have view rights on the returned references.
     */
    public List<ProjectManagementRelation> getRelations(DocumentReference documentReference, boolean ancestorLookup)
    {
        List<ProjectManagementRelation> relations = relationsManager.getRelations(documentReference, ancestorLookup);
        relations.removeIf(relation -> !authorizationManager.hasAccess(Right.VIEW, relation.getDocumentReference()));
        return relations;
    }
}
