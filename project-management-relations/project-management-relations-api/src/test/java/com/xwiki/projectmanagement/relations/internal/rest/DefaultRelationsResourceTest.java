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

import java.util.List;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.internal.reference.DefaultStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.relations.RelationsManager;
import com.xwiki.projectmanagement.relations.model.ProjectManagementRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultRelationsResource}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ DefaultStringEntityReferenceSerializer.class, DefaultSymbolScheme.class })
class DefaultRelationsResourceTest
{
    private static final DocumentReference PAGE = new DocumentReference("wiki", List.of("A", "B"), "WebHome");

    private static final DocumentReference OWNER = new DocumentReference("wiki", "A", "WebHome");

    private static final String CLIENT = "openproject";

    @InjectMockComponents
    private DefaultRelationsResource resource;

    @MockComponent
    private RelationsManager relationsManager;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @Test
    void getClientRelationReturnsTheRelationOfTheClosestAncestorWithItsOwner() throws Exception
    {
        allowView(PAGE);
        allowView(OWNER);
        when(this.relationsManager.getClientRelation(PAGE, CLIENT, true)).thenReturn(relation(OWNER));

        ProjectManagementRelation relation = getClientRelation(true);

        assertEquals(CLIENT, relation.getClient());
        assertEquals("Demo", relation.getProject());
        assertEquals("42", relation.getWorkItem());
        assertEquals("{\"instance\":\"main\"}", relation.getClientParams());
        assertEquals("wiki:A.WebHome", relation.getOwner());
    }

    @Test
    void getClientRelationWhenThePageIsNotViewable()
    {
        // Everything else would let the relation through: only the view right on the requested page is missing.
        allowView(OWNER);
        when(this.relationsManager.getClientRelation(PAGE, CLIENT, true)).thenReturn(relation(OWNER));

        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> getClientRelation(true));

        assertEquals(401, exception.getResponse().getStatus());
    }

    @Test
    void getClientRelationWhenTheOwnerIsNotViewable()
    {
        allowView(PAGE);
        when(this.relationsManager.getClientRelation(PAGE, CLIENT, true)).thenReturn(relation(OWNER));

        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> getClientRelation(true));

        assertEquals(401, exception.getResponse().getStatus());
    }

    @Test
    void getClientRelationWhenThereIsNone()
    {
        allowView(PAGE);

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> getClientRelation(false));

        assertEquals(404, exception.getResponse().getStatus());
    }

    private ProjectManagementRelation getClientRelation(boolean ancestors) throws Exception
    {
        return this.resource.getClientRelation("wiki", "A/spaces/B", "WebHome", CLIENT, ancestors);
    }

    private void allowView(DocumentReference reference)
    {
        when(this.authorizationManager.hasAccess(Right.VIEW, reference)).thenReturn(true);
    }

    private com.xwiki.projectmanagement.relations.store.ProjectManagementRelation relation(DocumentReference owner)
    {
        BaseObject xobject = new BaseObject();
        xobject.setDocumentReference(owner);
        com.xwiki.projectmanagement.relations.store.ProjectManagementRelation relation =
            new com.xwiki.projectmanagement.relations.store.ProjectManagementRelation(xobject);
        relation.setClient(CLIENT);
        relation.setProject("Demo");
        relation.setWorkItem("42");
        relation.setClientParams("{\"instance\":\"main\"}");
        return relation;
    }
}
