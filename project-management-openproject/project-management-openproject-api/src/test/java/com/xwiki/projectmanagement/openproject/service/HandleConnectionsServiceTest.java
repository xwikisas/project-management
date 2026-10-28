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
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientFactory;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.service.HandleConnectionsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Mock
    private XWikiContext xContext;

    @Mock
    private XWiki xwiki;

    @Mock
    private Query query;

    @MockComponent
    private OpenProjectApiClientFactory openProjectApiClientFactory;

    @MockComponent
    private AuthorizationManager authorizationManager;

    @MockComponent
    @Named("document")
    private UserReferenceSerializer<DocumentReference> documentUserSerializer;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Mock
    private OpenProjectApiClientBuilder openProjectApiClientBuilder;

    @Mock
    private OpenProjectApiClient openProjectApiClient;

    @Mock
    private XWikiDocument doc;

    @Mock
    private DocumentReference userRef;

    @Mock
    private UserReference userReference;

    private OpenProjectConnection openProjectConnection;

    private DocumentAuthors documentAuthors;

    private static final String WIKI_NAME = "wiki";

    private final DocumentReference documentReference = new DocumentReference(
        WIKI_NAME,
        List.of("ProjectManagement", "Code"),
        "FirstConnection"
    );

    private final DocumentReference otherDocumentReference = new DocumentReference(
        WIKI_NAME,
        List.of("Space"),
        "OtherConnection"
    );

    private static final DocumentReference CONNECTION_CLASS_REFERENCE =
        new DocumentReference(WIKI_NAME, Arrays.asList("OpenProject", "Code"), "OpenProjectConnectionClass");

    private static final DocumentReference OIDC_CLASS_REFERENCE =
        new DocumentReference(WIKI_NAME, Arrays.asList("XWiki", "OIDC"), "ClientConfigurationClass");

    private final DocumentReference adminReference = new DocumentReference(WIKI_NAME, "XWiki", "Admin");

    private final DocumentReference simpleUserReference = new DocumentReference(WIKI_NAME, "XWiki", "User");

    @BeforeEach
    void setup() throws QueryException, XWikiException
    {
        when(this.queryManager.createQuery(any(), eq(Query.HQL))).thenReturn(this.query);
        when(this.query.bindValue(any(), any())).thenReturn(this.query);
        when(this.query.setWiki(any())).thenReturn(this.query);
        when(this.compactSerializer.serialize(documentReference)).thenReturn("serializedDocRef");

        when(this.xContextProvider.get()).thenReturn(this.xContext);
        when(this.xContext.getWikiId()).thenReturn(WIKI_NAME);
        when(this.xContext.getWiki()).thenReturn(xwiki);
        when(this.xwiki.getDocument(documentReference, xContext)).thenReturn(doc);
        when(this.doc.getDocumentReference()).thenReturn(documentReference);
        when(this.doc.isNew()).thenReturn(true);
        when(this.authorizationManager.hasAccess(Right.ADMIN, adminReference, documentReference.getWikiReference()))
            .thenReturn(true);

        when(xContext.getUserReference()).thenReturn(userRef);
        when(userReferenceResolver.resolve(any())).thenReturn(userReference);

        documentAuthors = new DefaultDocumentAuthors(doc);
        when(doc.getAuthors()).thenReturn(documentAuthors);

        openProjectConnection =
            new OpenProjectConnection("connectionName", "serverUrl", "clientId", "clientSecret", "instanceId");
    }

    @Test
    public void handleExistingConnectionsTest() throws Exception
    {
        mockOtherConnectionDocument(adminReference);

        ProjectManagementException exception = assertThrows(ProjectManagementException.class,
            () -> handleConnectionsService.handleConnection(openProjectConnection,
                documentReference));

        assertEquals("Connection connectionName already exists. Use another connection name.",
            exception.getMessage());
        verify(this.query).setWiki(WIKI_NAME);
        verify(xwiki, never()).saveDocument(any(), any(), any());
    }

    @Test
    void handleConnectionIgnoresSameNameConnectionWhoseAuthorIsNotAdmin() throws Exception
    {
        mockOtherConnectionDocument(simpleUserReference);
        mockConnectionObjects();

        handleConnectionsService.handleConnection(openProjectConnection, documentReference);

        verify(xwiki).saveDocument(doc, "Saved OpenProject and OIDC config via REST", xContext);
    }

    @Test
    void handleConnectionRefusesExistingPageWhoseAuthorIsNotAdmin() throws Exception
    {
        when(this.doc.isNew()).thenReturn(false);
        documentAuthors.setEffectiveMetadataAuthor(mockAuthor(simpleUserReference));
        when(this.compactSerializer.serialize(documentReference)).thenReturn("ProjectManagement.Code.FirstConnection");

        ProjectManagementException exception = assertThrows(ProjectManagementException.class,
            () -> handleConnectionsService.handleConnection(openProjectConnection, documentReference));

        assertEquals("The page [ProjectManagement.Code.FirstConnection] can't be updated because it was last "
            + "modified by a user without administration right on the wiki. Delete the connection and create it "
            + "again.", exception.getMessage());
        verify(xwiki, never()).saveDocument(any(), any(), any());
    }

    @Test
    void handleConnectionUpdatesExistingPageWhoseAuthorIsAdmin() throws Exception
    {
        when(this.doc.isNew()).thenReturn(false);
        documentAuthors.setEffectiveMetadataAuthor(mockAuthor(adminReference));
        when(this.query.execute()).thenReturn(List.of());
        mockConnectionObjects();

        handleConnectionsService.handleConnection(openProjectConnection, documentReference);

        verify(xwiki).saveDocument(doc, "Saved OpenProject and OIDC config via REST", xContext);
    }

    @Test
    void handleConnectionKeepsCurrentClientSecretWhenEmpty() throws Exception
    {
        when(this.doc.isNew()).thenReturn(false);
        documentAuthors.setEffectiveMetadataAuthor(mockAuthor(adminReference));
        when(this.query.execute()).thenReturn(List.of());
        mockConnectionObjects();
        BaseObject currentObject = new BaseObject();
        currentObject.setStringValue("clientSecret", "currentSecret");
        when(this.doc.getXObject(CONNECTION_CLASS_REFERENCE)).thenReturn(currentObject);
        BaseObject configObj = new BaseObject();
        BaseObject oidcObj = new BaseObject();
        when(this.doc.getXObject(eq(CONNECTION_CLASS_REFERENCE), eq(true), eq(xContext))).thenReturn(configObj);
        when(this.doc.getXObject(eq(OIDC_CLASS_REFERENCE), eq(true), eq(xContext))).thenReturn(oidcObj);
        openProjectConnection.setClientSecret("");

        handleConnectionsService.handleConnection(openProjectConnection, documentReference);

        assertEquals("currentSecret", configObj.getStringValue("clientSecret"));
        assertEquals("currentSecret", oidcObj.getStringValue("clientSecret"));
        verify(xwiki).saveDocument(doc, "Saved OpenProject and OIDC config via REST", xContext);
    }

    @Test
    void handleConnectionRequiresClientSecretForNewConnection() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of());
        openProjectConnection.setClientSecret(" ");

        ProjectManagementException exception = assertThrows(ProjectManagementException.class,
            () -> handleConnectionsService.handleConnection(openProjectConnection, documentReference));

        assertEquals("The client secret is required.", exception.getMessage());
        verify(xwiki, never()).saveDocument(any(), any(), any());
    }

    private void mockOtherConnectionDocument(DocumentReference author) throws Exception
    {
        when(this.query.execute()).thenReturn(List.of("Space.OtherConnection"));
        when(this.documentReferenceResolver.resolve("Space.OtherConnection", documentReference.getWikiReference()))
            .thenReturn(otherDocumentReference);
        XWikiDocument otherDocument = mock(XWikiDocument.class);
        when(this.xwiki.getDocument(otherDocumentReference, xContext)).thenReturn(otherDocument);
        when(otherDocument.getDocumentReference()).thenReturn(otherDocumentReference);
        DocumentAuthors otherAuthors = mock(DocumentAuthors.class);
        when(otherDocument.getAuthors()).thenReturn(otherAuthors);
        UserReference authorReference = mockAuthor(author);
        when(otherAuthors.getEffectiveMetadataAuthor()).thenReturn(authorReference);
    }

    private UserReference mockAuthor(DocumentReference author)
    {
        UserReference authorReference = mock(UserReference.class);
        when(this.documentUserSerializer.serialize(authorReference)).thenReturn(author);
        return authorReference;
    }

    private void mockConnectionObjects() throws Exception
    {
        when(doc.getXObject(any(DocumentReference.class), eq(true), eq(xContext))).thenReturn(new BaseObject());
        when(this.openProjectApiClientFactory.builder()).thenReturn(this.openProjectApiClientBuilder);
        when(this.openProjectApiClientBuilder.serverUrl(any())).thenReturn(this.openProjectApiClientBuilder);
        when(this.openProjectApiClientBuilder.authentication(any())).thenReturn(this.openProjectApiClientBuilder);
        when(this.openProjectApiClientBuilder.build()).thenReturn(this.openProjectApiClient);
        when(this.openProjectApiClient.getInstanceId()).thenReturn("instanceId");
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

        when(this.openProjectApiClientFactory.builder()).thenReturn(this.openProjectApiClientBuilder);
        when(this.openProjectApiClientBuilder.serverUrl(any())).thenReturn(this.openProjectApiClientBuilder);
        when(this.openProjectApiClientBuilder.authentication(any())).thenReturn(this.openProjectApiClientBuilder);
        when(this.openProjectApiClientBuilder.build()).thenReturn(this.openProjectApiClient);
        when(this.openProjectApiClient.getInstanceId()).thenReturn("instanceId");

        openProjectConnection.setShowPublicData(true);

        handleConnectionsService.handleConnection(openProjectConnection, documentReference);

        assertEquals(userReference, documentAuthors.getCreator());
        assertEquals(userReference, documentAuthors.getEffectiveMetadataAuthor());
        assertEquals(userReference, documentAuthors.getContentAuthor());
        assertEquals(userReference, documentAuthors.getOriginalMetadataAuthor());

        assertEquals(openProjectConnection.getConnectionName(), configObj.getStringValue("connectionName"));
        assertEquals(openProjectConnection.getServerURL(), configObj.getStringValue("serverURL"));
        assertEquals(openProjectConnection.getClientId(), configObj.getStringValue("clientId"));
        assertEquals(openProjectConnection.getClientSecret(), configObj.getStringValue("clientSecret"));
        assertEquals("instanceId", configObj.getStringValue("instanceId"));
        assertEquals(1, configObj.getIntValue("showPublicData"));

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
