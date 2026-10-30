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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryExecutor;
import org.xwiki.query.QueryManager;
import org.xwiki.query.internal.DefaultQuery;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultRelationsManager}, on pages stored in the wiki.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class DefaultRelationsManagerTest
{
    private static final String RELATIONS = "from doc.object('ProjectManagement.Code.RelationClass') as obj ";

    private static final String ANCESTORS_WITH_RELATIONS =
        RELATIONS + "where doc.space in (:ancestorList) order by length(doc.space) desc";

    private static final String ANCESTORS_WITH_CLIENT_RELATION =
        RELATIONS + "where doc.space in (:ancestorList)  and obj.client = :client order by length(doc.space) desc";

    private static final DocumentReference PAGE = new DocumentReference("xwiki", List.of("A", "B"), "WebHome");

    private static final DocumentReference ANCESTOR = new DocumentReference("xwiki", "A", "WebHome");

    private static final List<String> ANCESTOR_SPACES = List.of("A", "A.B");

    private static final String CLIENT = "openproject";

    private static final String OTHER_CLIENT = "other";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private DefaultRelationsManager relationsManager;

    @MockComponent
    private QueryManager queryManager;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    /**
     * Saves the given page with one relation per given client, each relation having the page space and its client as
     * work item (e.g. {@code B-openproject}).
     */
    private XWikiDocument savePage(DocumentReference reference, String... clients) throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument document = new XWikiDocument(reference);
        for (String client : clients) {
            BaseObject relation = document.newXObject(ProjectManagementRelation.CLASS_REFERENCE, context);
            relation.setStringValue(ProjectManagementRelation.FIELD_CLIENT, client);
            relation.setStringValue(ProjectManagementRelation.FIELD_WORK_ITEM,
                reference.getLastSpaceReference().getName() + "-" + client);
        }
        this.oldcore.getSpyXWiki().saveDocument(document, context);
        return document;
    }

    /**
     * Answers the ancestor lookup query with the given rows (local page references), but only when it is sent with the
     * given parameters and limited to one result.
     */
    private void stubAncestorQuery(String statement, Map<String, Object> parameters, String... rows)
        throws QueryException
    {
        when(this.queryManager.createQuery(statement, Query.XWQL))
            .thenReturn(new DefaultQuery(statement, Query.XWQL, new QueryExecutor()
            {
                @Override
                @SuppressWarnings("unchecked")
                public <T> List<T> execute(Query query)
                {
                    if (!parameters.equals(query.getNamedParameters()) || query.getLimit() != 1) {
                        return Collections.emptyList();
                    }
                    return (List<T>) List.of(rows);
                }
            }));
    }

    private void stubFailingQuery(String statement) throws QueryException
    {
        when(this.queryManager.createQuery(statement, Query.XWQL))
            .thenReturn(new DefaultQuery(statement, Query.XWQL, new QueryExecutor()
            {
                @Override
                public <T> List<T> execute(Query query) throws QueryException
                {
                    throw new QueryException("error", query, null);
                }
            }));
    }

    private static List<String> workItems(List<ProjectManagementRelation> relations)
    {
        return relations.stream().map(ProjectManagementRelation::getWorkItem).collect(Collectors.toList());
    }

    @Test
    void getRelationsOfThePage() throws Exception
    {
        savePage(PAGE, CLIENT, OTHER_CLIENT);

        assertEquals(List.of("B-openproject", "B-other"),
            workItems(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsWithoutRelationsAndWithoutAncestorLookup() throws Exception
    {
        savePage(ANCESTOR, CLIENT);
        stubAncestorQuery(ANCESTORS_WITH_RELATIONS, Map.of("ancestorList", ANCESTOR_SPACES), "A.WebHome");

        assertEquals(List.of(), this.relationsManager.getRelations(PAGE, false));
    }

    @Test
    void getRelationsWhenNoAncestorHasRelations() throws Exception
    {
        stubAncestorQuery(ANCESTORS_WITH_RELATIONS, Map.of("ancestorList", ANCESTOR_SPACES));

        assertEquals(List.of(), this.relationsManager.getRelations(PAGE, true));
    }

    @Test
    void getRelationsOfTheClosestAncestorWithRelations() throws Exception
    {
        savePage(ANCESTOR, CLIENT);
        stubAncestorQuery(ANCESTORS_WITH_RELATIONS, Map.of("ancestorList", ANCESTOR_SPACES), "A.WebHome");

        assertEquals(List.of("A-openproject"), workItems(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsSkipsTheRemovedObjects() throws Exception
    {
        XWikiDocument page = savePage(PAGE, OTHER_CLIENT, CLIENT);
        // The list of xobjects keeps a null entry in place of each removed object.
        page.removeXObject(page.getXObject(ProjectManagementRelation.CLASS_REFERENCE, 0));
        this.oldcore.getSpyXWiki().saveDocument(page, this.oldcore.getXWikiContext());

        assertEquals(List.of("B-openproject"), workItems(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsOfTheAncestorWhenAllTheRelationsOfThePageWereRemoved() throws Exception
    {
        XWikiDocument page = savePage(PAGE, CLIENT);
        page.removeXObjects(ProjectManagementRelation.CLASS_REFERENCE);
        this.oldcore.getSpyXWiki().saveDocument(page, this.oldcore.getXWikiContext());
        savePage(ANCESTOR, OTHER_CLIENT);
        stubAncestorQuery(ANCESTORS_WITH_RELATIONS, Map.of("ancestorList", ANCESTOR_SPACES), "A.WebHome");

        assertEquals(List.of("A-other"), workItems(this.relationsManager.getRelations(PAGE, true)));
    }

    @Test
    void getRelationsWhenTheAncestorLookupFails() throws Exception
    {
        stubFailingQuery(ANCESTORS_WITH_RELATIONS);

        assertEquals(List.of(), this.relationsManager.getRelations(PAGE, true));
        assertEquals("Failed to retrieve the relation objects for the document [xwiki:A.B.WebHome].",
            this.logCapture.getMessage(0));
    }

    @Test
    void getClientRelationOfThePage() throws Exception
    {
        savePage(PAGE, OTHER_CLIENT, CLIENT);

        assertEquals("B-openproject", this.relationsManager.getClientRelation(PAGE, CLIENT, true).getWorkItem());
    }

    @Test
    void getClientRelationWithoutRelationAndWithoutAncestorLookup() throws Exception
    {
        savePage(PAGE, OTHER_CLIENT);
        savePage(ANCESTOR, CLIENT);
        stubAncestorQuery(ANCESTORS_WITH_CLIENT_RELATION, Map.of("ancestorList", ANCESTOR_SPACES, "client", CLIENT),
            "A.WebHome");

        assertNull(this.relationsManager.getClientRelation(PAGE, CLIENT, false));
    }

    @Test
    void getClientRelationWhenNoAncestorHasOne() throws Exception
    {
        stubAncestorQuery(ANCESTORS_WITH_CLIENT_RELATION, Map.of("ancestorList", ANCESTOR_SPACES, "client", CLIENT));

        assertNull(this.relationsManager.getClientRelation(PAGE, CLIENT, true));
    }

    @Test
    void getClientRelationOfTheClosestAncestorWithOneForTheClient() throws Exception
    {
        savePage(PAGE, OTHER_CLIENT);
        // The ancestor also has a relation for another client, before the one of the requested client.
        savePage(ANCESTOR, OTHER_CLIENT, CLIENT);
        stubAncestorQuery(ANCESTORS_WITH_CLIENT_RELATION, Map.of("ancestorList", ANCESTOR_SPACES, "client", CLIENT),
            "A.WebHome");

        assertEquals("A-openproject", this.relationsManager.getClientRelation(PAGE, CLIENT, true).getWorkItem());
    }

    @Test
    void getClientRelationWhenTheAncestorLookupFails() throws Exception
    {
        stubFailingQuery(ANCESTORS_WITH_CLIENT_RELATION);

        assertNull(this.relationsManager.getClientRelation(PAGE, CLIENT, true));
        assertEquals("Failed to retrieve the relation object for the document [xwiki:A.B.WebHome].",
            this.logCapture.getMessage(0));
    }
}
