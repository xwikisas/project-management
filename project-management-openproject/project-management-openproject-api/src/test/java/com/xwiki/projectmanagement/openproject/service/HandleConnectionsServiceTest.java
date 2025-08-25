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
package com.xwiki.projectmanagement.openproject.service;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.internal.document.DefaultDocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.service.HandleConnectionsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class HandleConnectionsServiceTest
{
    @InjectMockComponents
    private HandleConnectionsService handleConnectionsService;

    @MockComponent
    private Provider<XWikiContext> xContextProvider;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("document")
    UserReferenceResolver<DocumentReference> userReferenceResolver;

    @MockComponent
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactSerializer;

    @MockComponent
    private XWikiContext xContext;

    @MockComponent
    private XWiki xwiki;

    @MockComponent
    private Query query;

    @Mock
    private XWikiDocument doc;

    @Mock
    private DocumentReference userRef;

    @Mock
    private UserReference userReference;

    private OpenProjectConnection openProjectConnection;

    private DocumentAuthors documentAuthors;

    private final String WIKI_NAME = "wiki";

    private final DocumentReference documentReference = new DocumentReference(
        WIKI_NAME,
        List.of("ProjectManagement", "Code"),
        "FirstConnection"
    );

    @BeforeEach
    void setup() throws QueryException, XWikiException
    {
        when(this.queryManager.createQuery(any(), eq(Query.HQL))).thenReturn(this.query);
        when(this.query.bindValue(any(), any())).thenReturn(this.query);
        when(this.query.setWiki(any())).thenReturn(this.query);
        when(this.compactSerializer.serialize(documentReference)).thenReturn("serializedDocRef");

        when(this.xContextProvider.get()).thenReturn(this.xContext);
        when(this.xContext.getWikiId()).thenReturn("wiki");
        when(this.xContext.getWiki()).thenReturn(xwiki);
        when(this.xwiki.getDocument(documentReference, xContext)).thenReturn(doc);

        when(xContext.getUserReference()).thenReturn(userRef);
        when(userReferenceResolver.resolve(any())).thenReturn(userReference);

        documentAuthors = new DefaultDocumentAuthors(doc);
        when(doc.getAuthors()).thenReturn(documentAuthors);

        openProjectConnection = new OpenProjectConnection("connectionName", "serverUrl", "clientId", "clientSecret");
    }

    @Test
    public void handleExistingConnectionsTest() throws QueryException
    {
        when(this.query.execute()).thenReturn(List.of(documentReference));
        assertThrows(ProjectManagementException.class,
            () -> handleConnectionsService.handleConnection(openProjectConnection,
                documentReference));
    }

    @Test
    public void handleConnectionTest() throws QueryException, ProjectManagementException, XWikiException
    {
        BaseObject configObj = new BaseObject();
        BaseObject oidcObj = new BaseObject();

        DocumentReference configClassRef = new DocumentReference(
            WIKI_NAME,
            Arrays.asList("OpenProject", "Code"),
            "OpenProjectConnectionClass"
        );

        DocumentReference oidcClassRef = new DocumentReference(
            WIKI_NAME,
            Arrays.asList("XWiki", "OIDC"),
            "ClientConfigurationClass"
        );

        when(doc.getXObject(eq(configClassRef), eq(true), eq(xContext))).thenReturn(configObj);
        when(doc.getXObject(eq(oidcClassRef), eq(true), eq(xContext))).thenReturn(oidcObj);
        when(this.query.execute()).thenReturn(List.of());

        handleConnectionsService.handleConnection(openProjectConnection, documentReference);

        assertEquals(userReference, documentAuthors.getCreator());
        assertEquals(userReference, documentAuthors.getEffectiveMetadataAuthor());
        assertEquals(userReference, documentAuthors.getContentAuthor());
        assertEquals(userReference, documentAuthors.getOriginalMetadataAuthor());

        assertEquals(openProjectConnection.getConnectionName(), configObj.getStringValue("connectionName"));
        assertEquals(openProjectConnection.getServerURL(), configObj.getStringValue("serverURL"));
        assertEquals(openProjectConnection.getClientId(), configObj.getStringValue("clientId"));
        assertEquals(openProjectConnection.getClientSecret(), configObj.getStringValue("clientSecret"));

        assertEquals(openProjectConnection.getConnectionName(), oidcObj.getStringValue("configurationName"));
        assertEquals(
            String.format("%s/oauth/authorize", openProjectConnection.getServerURL()),
            oidcObj.getStringValue("authorizationEndpoint")
        );
        assertEquals(
            String.format("%s/oauth/token", openProjectConnection.getServerURL()),
            oidcObj.getStringValue("tokenEndpoint")
        );
        assertEquals(openProjectConnection.getClientId(), oidcObj.getStringValue("clientId"));
        assertEquals(openProjectConnection.getClientSecret(), oidcObj.getStringValue("clientSecret"));
        assertEquals("client_secret_basic", oidcObj.getStringValue("tokenEndpointMethod"));
        assertEquals(0, oidcObj.getIntValue("skipped"));
        assertEquals("api_v3", oidcObj.getStringValue("scope"));
        assertEquals("code", oidcObj.getStringValue("responseType"));
        assertEquals(1, oidcObj.getIntValue("enableUser"));
        assertEquals("USER", oidcObj.getStringValue("tokenStorageScope"));

        verify(xwiki).saveDocument(doc, "Saved OpenProject and OIDC config via REST", xContext);
    }
}
