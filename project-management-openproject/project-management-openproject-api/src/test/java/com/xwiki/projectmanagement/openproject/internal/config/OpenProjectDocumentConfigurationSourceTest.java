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
package com.xwiki.projectmanagement.openproject.internal.config;

import java.util.List;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ComponentTest
public class OpenProjectDocumentConfigurationSourceTest
{
    @MockComponent
    private XWikiContext xContext;

    @MockComponent
    private Provider<XWikiContext> xContextProvider;

    @MockComponent
    private XWiki xwiki;

    @MockComponent
    private Query query;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private EntityReferenceResolver<String> referenceResolver;

    @MockComponent
    protected WikiDescriptorManager wikiManager;

    @Mock
    private XWikiDocument xwikiDocument1;

    @Mock
    private XWikiDocument xwikiDocument2;

    @InjectMocks
    private OpenProjectDocumentConfigurationSource openProjectDocumentConfigurationSource;

    private static final String WIKI_NAME = "wiki";

    @BeforeEach
    void setUp() throws QueryException, XWikiException
    {
        String query = "from doc.object(OpenProject.Code.OpenProjectConnectionClass) as cfg";

        when(queryManager.createQuery(query, Query.XWQL)).thenReturn(this.query);
        when(this.query.setWiki(anyString())).thenReturn(this.query);
        when(this.query.execute())
            .thenReturn(List.of("OpenProject.Code.FirstDocument", "OpenProject.Code.SecondDocument"));
        when(this.xContextProvider.get()).thenReturn(this.xContext);
        when(this.xContext.getWikiId()).thenReturn(WIKI_NAME);
        when(this.xContext.getWiki()).thenReturn(xwiki);
        when(this.wikiManager.getCurrentWikiId()).thenReturn("wiki");

        EntityReference entityRef1 = new DocumentReference(WIKI_NAME, "OpenProject", "Doc1");
        EntityReference entityRef2 = new DocumentReference(WIKI_NAME, "OpenProject", "Doc2");

        when(this.referenceResolver.resolve(eq("OpenProject.Code.FirstDocument"), eq(EntityType.DOCUMENT),
            any(WikiReference.class))).thenReturn(entityRef1);
        when(this.referenceResolver.resolve(eq("OpenProject.Code.SecondDocument"), eq(EntityType.DOCUMENT),
            any(WikiReference.class))).thenReturn(entityRef2);

        DocumentReference docRef1 = new DocumentReference(WIKI_NAME, "OpenProject", "Doc1");
        DocumentReference docRef2 = new DocumentReference(WIKI_NAME, "OpenProject", "Doc2");
        when(xwiki.getDocument(eq(docRef1), any())).thenReturn(xwikiDocument1);
        when(xwiki.getDocument(eq(docRef2), any())).thenReturn(xwikiDocument2);

        BaseObject baseObject1 = new BaseObject();
        baseObject1.setStringValue("connectionName", "connection1");
        baseObject1.setStringValue("serverURL", "serverURL1");
        baseObject1.setStringValue("clientId", "clientId1");
        baseObject1.setStringValue("clientSecret", "clientSecret1");

        BaseObject baseObject2 = new BaseObject();
        baseObject2.setStringValue("connectionName", "connection2");
        baseObject2.setStringValue("serverURL", "serverURL2");
        baseObject2.setStringValue("clientId", "clientId2");
        baseObject2.setStringValue("clientSecret", "clientSecret2");

        when(xwikiDocument1.getXObject(any(LocalDocumentReference.class))).thenReturn(baseObject1);
        when(xwikiDocument2.getXObject(any(LocalDocumentReference.class))).thenReturn(baseObject2);
    }

    @Test
    public void getBasePropertyTest() throws XWikiException
    {
        List<OpenProjectConnection> connections =
            (List<OpenProjectConnection>) openProjectDocumentConfigurationSource.getBaseProperty(
                "openprojectConnections", false);

        OpenProjectConnection connection = connections.get(0);

        assertEquals("connection1", connection.getConnectionName());
        assertEquals("serverURL1", connection.getServerURL());
        assertEquals("clientId1", connection.getClientId());
        assertEquals("clientSecret1", connection.getClientSecret());

        connection = connections.get(1);

        assertEquals("connection2", connection.getConnectionName());
        assertEquals("serverURL2", connection.getServerURL());
        assertEquals("clientId2", connection.getClientId());
        assertEquals("clientSecret2", connection.getClientSecret());
    }
}
