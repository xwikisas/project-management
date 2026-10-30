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
package com.xwiki.projectmanagement.openproject.internal.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryExecutor;
import org.xwiki.query.QueryManager;
import org.xwiki.query.internal.DefaultQuery;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientFactory;
import com.xwiki.projectmanagement.openproject.auth.NoOpAuthenticator;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HandleConnectionsService}, on connection pages stored in the wiki.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class HandleConnectionsServiceTest
{
    private static final String WIKI = "xwiki";

    private static final DocumentReference CONNECTION = new DocumentReference(WIKI,
        List.of("OpenProject", "Code", "OpenProjectConfigurations"), "FirstConnection");

    private static final DocumentReference OTHER_CONNECTION = new DocumentReference(WIKI,
        List.of("OpenProject", "Code", "OpenProjectConfigurations"), "OtherConnection");

    private static final DocumentReference CONNECTION_CLASS =
        new DocumentReference(WIKI, List.of("OpenProject", "Code"), "OpenProjectConnectionClass");

    private static final DocumentReference OIDC_CLASS =
        new DocumentReference(WIKI, List.of("XWiki", "OIDC"), "ClientConfigurationClass");

    private static final DocumentReference ADMIN = new DocumentReference(WIKI, "XWiki", "Admin");

    private static final DocumentReference USER = new DocumentReference(WIKI, "XWiki", "User");

    private static final DocumentReference CURRENT_USER = new DocumentReference(WIKI, "XWiki", "CurrentUser");

    private static final String SERVER_URL = "https://openproject.example.com";

    private static final String CONNECTION_NAME = "connectionName";

    private static final String CLIENT_SECRET = "clientSecret";

    private static final String SAME_NAME_QUERY = "select obj.name from XWikiDocument doc, BaseObject obj, "
        + "StringProperty configName where doc.fullName = obj.name and obj.className = :className "
        + "and obj.id = configName.id.id and configName.id.name = :configFieldName and configName.value = :config "
        + "and doc.fullName <> :serializedDocRef";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private HandleConnectionsService handleConnectionsService;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private OpenProjectApiClientFactory openProjectApiClientFactory;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private UserReferenceResolver<DocumentReference> userResolver;

    private OpenProjectConnection openProjectConnection;

    @BeforeEach
    void setUp() throws Exception
    {
        this.userResolver = this.componentManager.getInstance(UserReferenceResolver.TYPE_DOCUMENT_REFERENCE,
            "document");
        this.oldcore.getXWikiContext().setUserReference(CURRENT_USER);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(Right.ADMIN, ADMIN, new WikiReference(WIKI)))
            .thenReturn(true);
        stubSameNameConnections();
        this.openProjectConnection =
            new OpenProjectConnection(CONNECTION_NAME, SERVER_URL, "clientId", CLIENT_SECRET, "instanceId");
    }

    /**
     * Answers the query looking for the other connections with the same name, on the wiki of the connection, with the
     * given page names.
     */
    private void stubSameNameConnections(String... pages) throws Exception
    {
        Map<String, Object> parameters = Map.of("className", "OpenProject.Code.OpenProjectConnectionClass",
            "configFieldName", CONNECTION_NAME, "config", CONNECTION_NAME,
            "serializedDocRef", "OpenProject.Code.OpenProjectConfigurations.FirstConnection");
        when(this.queryManager.createQuery(SAME_NAME_QUERY, Query.HQL))
            .thenAnswer(invocation -> new DefaultQuery(SAME_NAME_QUERY, Query.HQL, new QueryExecutor()
            {
                @Override
                @SuppressWarnings("unchecked")
                public <T> List<T> execute(Query query)
                {
                    if (!WIKI.equals(query.getWiki()) || !parameters.equals(query.getNamedParameters())) {
                        return Collections.emptyList();
                    }
                    return (List<T>) List.of(pages);
                }
            }));
    }

    /**
     * Answers the instance id request sent to the given server, without authentication.
     */
    private void stubInstanceId(String serverURL) throws Exception
    {
        OpenProjectApiClientBuilder builder = mock(OpenProjectApiClientBuilder.class);
        OpenProjectApiClient client = mock(OpenProjectApiClient.class);
        when(this.openProjectApiClientFactory.builder()).thenReturn(builder);
        when(builder.serverUrl(serverURL)).thenReturn(builder);
        when(builder.authentication(NoOpAuthenticator.INSTANCE)).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.getInstanceId()).thenReturn("instanceId");
    }

    private XWikiDocument saveConnectionPage(DocumentReference reference, DocumentReference author, String name)
        throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument document = new XWikiDocument(reference);
        UserReference authorReference = this.userResolver.resolve(author);
        document.getAuthors().setCreator(authorReference);
        document.getAuthors().setContentAuthor(authorReference);
        document.getAuthors().setEffectiveMetadataAuthor(authorReference);
        document.getAuthors().setOriginalMetadataAuthor(authorReference);
        BaseObject connection = document.newXObject(CONNECTION_CLASS, context);
        connection.setStringValue(CONNECTION_NAME, name);
        connection.setStringValue(CLIENT_SECRET, "currentSecret");
        this.oldcore.getSpyXWiki().saveDocument(document, context);
        return document;
    }

    private XWikiDocument getConnectionPage() throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(CONNECTION, this.oldcore.getXWikiContext());
    }

    private void assertNotSaved() throws Exception
    {
        assertTrue(getConnectionPage().isNew());
    }

    @Test
    void handleConnectionSavesConfigAndOidcObjects() throws Exception
    {
        stubInstanceId(SERVER_URL);
        this.openProjectConnection.setShowPublicData(true);

        this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION);

        XWikiDocument page = getConnectionPage();
        assertTrue(page.isHidden());
        UserReference currentUser = this.userResolver.resolve(CURRENT_USER);
        assertEquals(currentUser, page.getAuthors().getCreator());
        assertEquals(currentUser, page.getAuthors().getContentAuthor());
        assertEquals(currentUser, page.getAuthors().getEffectiveMetadataAuthor());
        assertEquals(currentUser, page.getAuthors().getOriginalMetadataAuthor());

        BaseObject config = page.getXObject(CONNECTION_CLASS);
        assertEquals(CONNECTION_NAME, config.getStringValue(CONNECTION_NAME));
        assertEquals(SERVER_URL, config.getStringValue("serverURL"));
        assertEquals("clientId", config.getStringValue("clientId"));
        assertEquals(CLIENT_SECRET, config.getStringValue(CLIENT_SECRET));
        assertEquals("instanceId", config.getStringValue("instanceId"));
        assertEquals(1, config.getIntValue("showPublicData"));

        BaseObject oidc = page.getXObject(OIDC_CLASS);
        assertEquals(CONNECTION_NAME, oidc.getStringValue("configurationName"));
        assertEquals(SERVER_URL + "/oauth/authorize", oidc.getStringValue("authorizationEndpoint"));
        assertEquals(SERVER_URL + "/oauth/token", oidc.getStringValue("tokenEndpoint"));
        assertEquals("clientId", oidc.getStringValue("clientId"));
        assertEquals(CLIENT_SECRET, oidc.getStringValue(CLIENT_SECRET));
        assertEquals("client_secret_basic", oidc.getStringValue("tokenEndpointMethod"));
        assertEquals(0, oidc.getIntValue("skipped"));
        assertEquals("api_v3", oidc.getStringValue("scope"));
        assertEquals("code", oidc.getStringValue("responseType"));
        assertEquals(1, oidc.getIntValue("enableUser"));
        assertEquals("USER", oidc.getStringValue("tokenStorageScope"));
        assertEquals("Saved OpenProject and OIDC config via REST", page.getComment());
    }

    @Test
    void handleConnectionSavesAnEmptyInstanceIdWhenTheServerDoesNotAnswer() throws Exception
    {
        stubInstanceId(SERVER_URL);
        when(this.openProjectApiClientFactory.builder().build().getInstanceId())
            .thenThrow(new RuntimeException("Connection refused"));

        this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION);

        assertEquals("", getConnectionPage().getXObject(CONNECTION_CLASS).getStringValue("instanceId"));
        assertEquals("There was an issue in communicating with [https://openproject.example.com]. "
            + "Cause: [RuntimeException: Connection refused].", this.logCapture.getMessage(0));
    }

    @Test
    void handleConnectionRefusesExistingConnectionName() throws Exception
    {
        saveConnectionPage(OTHER_CONNECTION, ADMIN, CONNECTION_NAME);
        stubSameNameConnections("OpenProject.Code.OpenProjectConfigurations.OtherConnection");

        ConnectionSaveException exception = assertThrows(ConnectionSaveException.class,
            () -> this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION));

        assertEquals("Connection connectionName already exists. Use another connection name.",
            exception.getMessage());
        assertTrue(exception.isConflict());
        assertNotSaved();
    }

    @Test
    void handleConnectionIgnoresSameNameConnectionWhoseAuthorIsNotAdmin() throws Exception
    {
        saveConnectionPage(OTHER_CONNECTION, USER, CONNECTION_NAME);
        stubSameNameConnections("OpenProject.Code.OpenProjectConfigurations.OtherConnection");
        stubInstanceId(SERVER_URL);

        this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION);

        assertEquals(CONNECTION_NAME, getConnectionPage().getXObject(CONNECTION_CLASS).getStringValue(CONNECTION_NAME));
    }

    @Test
    void handleConnectionRefusesExistingPageWhoseAuthorIsNotAdmin() throws Exception
    {
        saveConnectionPage(CONNECTION, USER, "oldName");

        ConnectionSaveException exception = assertThrows(ConnectionSaveException.class,
            () -> this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION));

        assertEquals("The page [OpenProject.Code.OpenProjectConfigurations.FirstConnection] can't be updated because "
            + "it was last modified by a user without administration right on the wiki. Delete the connection and "
            + "create it again.", exception.getMessage());
        assertTrue(exception.isConflict());
        XWikiDocument page = getConnectionPage();
        assertEquals("oldName", page.getXObject(CONNECTION_CLASS).getStringValue(CONNECTION_NAME));
        assertEquals(this.userResolver.resolve(USER), page.getAuthors().getEffectiveMetadataAuthor());
    }

    @Test
    void handleConnectionUpdatesExistingPageWhoseAuthorIsAdmin() throws Exception
    {
        saveConnectionPage(CONNECTION, ADMIN, "oldName");
        stubInstanceId(SERVER_URL);

        this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION);

        XWikiDocument page = getConnectionPage();
        assertEquals(CONNECTION_NAME, page.getXObject(CONNECTION_CLASS).getStringValue(CONNECTION_NAME));
        assertEquals(CLIENT_SECRET, page.getXObject(CONNECTION_CLASS).getStringValue(CLIENT_SECRET));
        // The connection objects are updated, not added.
        assertEquals(1, page.getXObjects(CONNECTION_CLASS).size());
    }

    @Test
    void handleConnectionKeepsCurrentClientSecretWhenEmpty() throws Exception
    {
        saveConnectionPage(CONNECTION, ADMIN, CONNECTION_NAME);
        stubInstanceId(SERVER_URL);
        this.openProjectConnection.setClientSecret("");

        this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION);

        XWikiDocument page = getConnectionPage();
        assertEquals("currentSecret", page.getXObject(CONNECTION_CLASS).getStringValue(CLIENT_SECRET));
        assertEquals("currentSecret", page.getXObject(OIDC_CLASS).getStringValue(CLIENT_SECRET));
    }

    @Test
    void handleConnectionKeepsCreatorAndContentAuthorOfExistingPage() throws Exception
    {
        saveConnectionPage(CONNECTION, ADMIN, CONNECTION_NAME);
        stubInstanceId(SERVER_URL);

        this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION);

        XWikiDocument page = getConnectionPage();
        UserReference admin = this.userResolver.resolve(ADMIN);
        UserReference currentUser = this.userResolver.resolve(CURRENT_USER);
        assertEquals(admin, page.getAuthors().getCreator());
        assertEquals(admin, page.getAuthors().getContentAuthor());
        assertEquals(currentUser, page.getAuthors().getEffectiveMetadataAuthor());
        assertEquals(currentUser, page.getAuthors().getOriginalMetadataAuthor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "serverUrl", "openproject.example.com", "file:///etc/passwd",
        "ftp://openproject.example.com", "jar:http://openproject.example.com/a.jar!/", "http://", "https:///path",
        "http://open project", " https://openproject.example.com" })
    void handleConnectionRefusesInvalidServerURL(String serverURL) throws Exception
    {
        this.openProjectConnection.setServerURL(serverURL);

        ConnectionSaveException exception = assertThrows(ConnectionSaveException.class,
            () -> this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION));

        assertEquals(String.format("The server URL [%s] is not valid: it must be an absolute HTTP or HTTPS URL, e.g. "
            + "[https://openproject.example.com].", serverURL), exception.getMessage());
        assertFalse(exception.isConflict());
        // No request is sent to an invalid server URL.
        verifyNoInteractions(this.openProjectApiClientFactory);
        assertNotSaved();
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://openproject.example.com", "HTTPS://openproject.example.com:8443/openproject",
        "http://[::1]:8080", "http://172.17.0.1:8082", "https://user@openproject.example.com/" })
    void handleConnectionAcceptsHttpServerURL(String serverURL) throws Exception
    {
        stubInstanceId(serverURL);
        this.openProjectConnection.setServerURL(serverURL);

        this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION);

        BaseObject config = getConnectionPage().getXObject(CONNECTION_CLASS);
        assertEquals(serverURL, config.getStringValue("serverURL"));
        // The instance id is retrieved from the given server.
        assertEquals("instanceId", config.getStringValue("instanceId"));
    }

    @Test
    void handleConnectionRefusesPageOutsideConnectionsSpace() throws Exception
    {
        DocumentReference outsideReference = new DocumentReference(WIKI, List.of("Space"), "Connection");

        ConnectionSaveException exception = assertThrows(ConnectionSaveException.class,
            () -> this.handleConnectionsService.handleConnection(this.openProjectConnection, outsideReference));

        assertEquals("The connection can't be saved on the page [Space.Connection]: the connections must be saved in "
            + "the [OpenProject.Code.OpenProjectConfigurations] space.", exception.getMessage());
        assertFalse(exception.isConflict());
        assertNull(this.oldcore.getDocuments().get(outsideReference));
    }

    @Test
    void handleConnectionRequiresClientSecretForNewConnection() throws Exception
    {
        this.openProjectConnection.setClientSecret(" ");

        ConnectionSaveException exception = assertThrows(ConnectionSaveException.class,
            () -> this.handleConnectionsService.handleConnection(this.openProjectConnection, CONNECTION));

        assertEquals("The client secret is required.", exception.getMessage());
        assertFalse(exception.isConflict());
        assertNotSaved();
    }
}
