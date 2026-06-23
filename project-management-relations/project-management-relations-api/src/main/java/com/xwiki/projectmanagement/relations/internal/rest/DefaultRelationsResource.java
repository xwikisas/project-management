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
package com.xwiki.projectmanagement.relations.internal.rest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xwiki.projectmanagement.relations.RelationsManager;
import com.xwiki.projectmanagement.relations.model.ProjectManagementRelation;
import com.xwiki.projectmanagement.relations.rest.RelationsResource;

/**
 * Default implementation of {@link RelationsResource}.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named("com.xwiki.projectmanagement.relations.internal.rest.DefaultRelationsResource")
public class DefaultRelationsResource extends XWikiResource implements RelationsResource
{
    @Inject
    private RelationsManager relationsManager;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Override
    public ProjectManagementRelation getClientRelation(String wikiName, String spaces, String pageName, String clientId,
        Boolean ancestors) throws XWikiRestException
    {
        DocumentReference documentReference = new DocumentReference(wikiName, parseSpaceSegments(spaces), pageName);
        if (!authorizationManager.hasAccess(Right.VIEW, documentReference)) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        com.xwiki.projectmanagement.relations.store.ProjectManagementRelation relation =
            relationsManager.getClientRelation(documentReference, clientId, ancestors);

        if (relation == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }
        ProjectManagementRelation model = relation.toModel();
        if (!authorizationManager.hasAccess(Right.VIEW, relation.getDocumentReference())) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        model.setOwner(serializer.serialize(relation.getDocumentReference()));
        return model;
    }
}
