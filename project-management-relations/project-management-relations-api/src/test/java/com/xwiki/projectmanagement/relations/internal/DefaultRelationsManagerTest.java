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
package com.xwiki.projectmanagement.relations.internal;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultRelationsManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultRelationsManagerTest
{
    private static final DocumentReference PAGE = new DocumentReference("wiki", List.of("A", "B"), "WebHome");

    private static final DocumentReference ANCESTOR = new DocumentReference("wiki", "A", "WebHome");

    private static final String CLIENT = "openproject";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private DefaultRelationsManager relationsManager;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    private XWikiContext context;

    private XWiki wiki;

    private XWikiDocument page;

    private Query query;

    @BeforeEach
    void setUp() throws Exception
    {
        this.context = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        when(this.contextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.wiki);

        this.page = mockDocument(PAGE);

        this.query = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), eq(Query.XWQL))).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.setLimit(1)).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());
    }

    @Test
    void getRelationsOfThePage()
    {
        BaseObject relation = relationObject(PAGE);
        when(this.page.getXObjects(ProjectManagementRelation.CLASS_REFERENCE)).thenReturn(List.of(relation));

        assertEquals(List.of(relation), xobjects(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsWithoutRelationsAndWithoutAncestorLookup()
    {
        assertEquals(List.of(), this.relationsManager.getRelations(PAGE, false));
    }

    @Test
    void getRelationsWhenNoAncestorHasRelations()
    {
        assertEquals(List.of(), this.relationsManager.getRelations(PAGE, true));
    }

    @Test
    void getRelationsOfTheClosestAncestorWithRelations() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of("A.WebHome"));
        when(this.documentReferenceResolver.resolve("A.WebHome")).thenReturn(ANCESTOR);
        XWikiDocument ancestor = mockDocument(ANCESTOR);
        BaseObject relation = relationObject(ANCESTOR);
        when(ancestor.getXObjects(ProjectManagementRelation.CLASS_REFERENCE)).thenReturn(List.of(relation));

        assertEquals(List.of(relation), xobjects(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsSkipsTheRemovedObjects()
    {
        // The lists of xobjects keep a null entry in place of each removed object.
        BaseObject relation = relationObject(PAGE);
        when(this.page.getXObjects(ProjectManagementRelation.CLASS_REFERENCE))
            .thenReturn(Arrays.asList(null, relation));

        assertEquals(List.of(relation), xobjects(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsOfTheAncestorWhenAllTheRelationsOfThePageWereRemoved() throws Exception
    {
        when(this.page.getXObjects(ProjectManagementRelation.CLASS_REFERENCE)).thenReturn(Arrays.asList(null, null));
        when(this.query.execute()).thenReturn(List.of("A.WebHome"));
        when(this.documentReferenceResolver.resolve("A.WebHome")).thenReturn(ANCESTOR);
        XWikiDocument ancestor = mockDocument(ANCESTOR);
        BaseObject relation = relationObject(ANCESTOR);
        when(ancestor.getXObjects(ProjectManagementRelation.CLASS_REFERENCE)).thenReturn(List.of(relation));

        assertEquals(List.of(relation), xobjects(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsWhenTheAncestorLookupFails() throws Exception
    {
        when(this.query.execute()).thenThrow(new QueryException("error", this.query, null));

        assertEquals(List.of(), this.relationsManager.getRelations(PAGE, true));
        assertEquals("Failed to retrieve the relation objects for the document [wiki:A.B.WebHome].",
            this.logCapture.getMessage(0));
    }

    @Test
    void getClientRelationOfThePage()
    {
        BaseObject relation = relationObject(PAGE);
        when(this.page.getXObject(ProjectManagementRelation.CLASS_REFERENCE, ProjectManagementRelation.FIELD_CLIENT,
            CLIENT, false)).thenReturn(relation);

        assertSame(relation, this.relationsManager.getClientRelation(PAGE, CLIENT, true).getXobject());
    }

    @Test
    void getClientRelationWithoutRelationAndWithoutAncestorLookup()
    {
        assertNull(this.relationsManager.getClientRelation(PAGE, CLIENT, false));
    }

    @Test
    void getClientRelationWhenNoAncestorHasOne()
    {
        assertNull(this.relationsManager.getClientRelation(PAGE, CLIENT, true));
        verify(this.query).bindValue("client", CLIENT);
    }

    @Test
    void getClientRelationOfTheClosestAncestorWithOneForTheClient() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of("A.WebHome"));
        when(this.documentReferenceResolver.resolve("A.WebHome")).thenReturn(ANCESTOR);
        XWikiDocument ancestor = mockDocument(ANCESTOR);
        // The ancestor also has a relation for another client, before the one of the requested client.
        when(ancestor.getXObject(ProjectManagementRelation.CLASS_REFERENCE)).thenReturn(relationObject(ANCESTOR));
        BaseObject relation = relationObject(ANCESTOR);
        when(ancestor.getXObject(ProjectManagementRelation.CLASS_REFERENCE, ProjectManagementRelation.FIELD_CLIENT,
            CLIENT, false)).thenReturn(relation);

        assertSame(relation, this.relationsManager.getClientRelation(PAGE, CLIENT, true).getXobject());
    }

    @Test
    void getClientRelationWhenTheAncestorLookupFails() throws Exception
    {
        when(this.query.execute()).thenThrow(new QueryException("error", this.query, null));

        assertNull(this.relationsManager.getClientRelation(PAGE, CLIENT, true));
        assertEquals("Failed to retrieve the relation object for the document [wiki:A.B.WebHome].",
            this.logCapture.getMessage(0));
    }

    private XWikiDocument mockDocument(DocumentReference reference) throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getDocumentReference()).thenReturn(reference);
        when(this.wiki.getDocument(reference, this.context)).thenReturn(document);
        return document;
    }

    private BaseObject relationObject(DocumentReference owner)
    {
        BaseObject relation = new BaseObject();
        relation.setDocumentReference(owner);
        return relation;
    }

    private List<BaseObject> xobjects(List<ProjectManagementRelation> relations)
    {
        return relations.stream().map(ProjectManagementRelation::getXobject).collect(Collectors.toList());
    }
}
