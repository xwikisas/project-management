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
package com.xwiki.projectmanagement.openproject.internal.rest.connection;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.service.ConnectionSaveException;
import com.xwiki.projectmanagement.openproject.internal.service.HandleConnectionsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
class HandleConnectionsTest
{
    @InjectMockComponents
    private HandleConnections handleConnections;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @MockComponent
    private HandleConnectionsService handleConnectionsService;

    @MockComponent
    private AuthorizationManager authorizationManager;

    @MockComponent
    private XWikiContext xContext;

    private final WikiReference wikiReference = new WikiReference(WIKI_NAME);

    @Mock
    private DocumentReference userReference;

    private static final OpenProjectConnection openProjectConnection = new OpenProjectConnection(
        "firstConnection",
        "firstConnectionURL",
        "firstConnectionClientId",
        "firstConnectionClientSecret",
        "firstConnectionInstanceId"
    );

    private static final String WIKI_NAME = "wiki";

    private static final String SPACE_NAME = "space";

    private static final String PAGE_NAME = "page";

    private static final DocumentReference PAGE_REFERENCE = new DocumentReference(WIKI_NAME, SPACE_NAME, PAGE_NAME);

    @BeforeEach
    void setUp()
    {
        when(this.xContext.getUserReference()).thenReturn(userReference);
        when(this.xContext.getWikiReference()).thenReturn(wikiReference);
        when(this.authorizationManager.hasAccess(Right.ADMIN, userReference, wikiReference)).thenReturn(true);
        when(this.authorizationManager.hasAccess(Right.EDIT, userReference, PAGE_REFERENCE)).thenReturn(true);
    }

    @Test
    void handleConnectionWithoutEditRightOnPageIsForbidden() throws ProjectManagementException
    {
        when(this.authorizationManager.hasAccess(Right.EDIT, userReference, PAGE_REFERENCE)).thenReturn(false);

        Response createResponse = handleConnections.createConnection(WIKI_NAME, SPACE_NAME, PAGE_NAME,
            openProjectConnection);
        Response updateResponse = handleConnections.updateConnection(WIKI_NAME, SPACE_NAME, PAGE_NAME,
            openProjectConnection);

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), createResponse.getStatus());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), updateResponse.getStatus());
        verify(this.handleConnectionsService, never()).handleConnection(any(), any());
    }

    @Test
    void handleConnectionChecksRightsOnTargetWikiNotContextWiki() throws ProjectManagementException
    {
        WikiReference otherWiki = new WikiReference("otherWiki");
        DocumentReference otherPage = new DocumentReference("otherWiki", SPACE_NAME, PAGE_NAME);
        when(this.authorizationManager.hasAccess(Right.ADMIN, userReference, wikiReference)).thenReturn(false);
        when(this.authorizationManager.hasAccess(Right.ADMIN, userReference, otherWiki)).thenReturn(true);
        when(this.authorizationManager.hasAccess(Right.EDIT, userReference, otherPage)).thenReturn(true);

        Response createResponse = handleConnections.createConnection("otherWiki", SPACE_NAME, PAGE_NAME,
            openProjectConnection);

        assertEquals(Response.Status.CREATED.getStatusCode(), createResponse.getStatus());
        verify(this.handleConnectionsService).handleConnection(openProjectConnection, otherPage);
    }

    @Test
    void createAndUpdateConnectionSucceed()
    {
        Response createResponse = handleConnections.createConnection(
            WIKI_NAME,
            SPACE_NAME,
            PAGE_NAME,
            openProjectConnection
        );

        Response updateResponse = handleConnections.updateConnection(
            WIKI_NAME,
            SPACE_NAME,
            PAGE_NAME,
            openProjectConnection
        );

        assertEquals(Response.Status.CREATED.getStatusCode(), createResponse.getStatus());
        assertEquals(Response.Status.OK.getStatusCode(), updateResponse.getStatus());
    }

    @Test
    void createAndUpdateConnectionWithoutAdminRightIsForbidden()
    {
        when(this.authorizationManager.hasAccess(Right.ADMIN, userReference, wikiReference)).thenReturn(false);

        Response createResponse = handleConnections.createConnection(
            WIKI_NAME,
            SPACE_NAME,
            PAGE_NAME,
            openProjectConnection
        );

        Response updateResponse = handleConnections.updateConnection(
            WIKI_NAME,
            SPACE_NAME,
            PAGE_NAME,
            openProjectConnection
        );

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), createResponse.getStatus());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), updateResponse.getStatus());
    }

    @Test
    void handleConnectionOnWikiNotAdministeredIsForbidden() throws ProjectManagementException
    {
        when(this.authorizationManager.hasAccess(Right.ADMIN, userReference, new WikiReference("otherWiki")))
            .thenReturn(false);

        Response createResponse = handleConnections.createConnection("otherWiki", SPACE_NAME, PAGE_NAME,
            openProjectConnection);
        Response updateResponse = handleConnections.updateConnection("otherWiki", SPACE_NAME, PAGE_NAME,
            openProjectConnection);

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), createResponse.getStatus());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), updateResponse.getStatus());
        verify(this.handleConnectionsService, never()).handleConnection(any(), any());
    }

    @Test
    void handleInvalidConnection() throws ProjectManagementException
    {
        doThrow(ConnectionSaveException.invalid("The client secret is required."))
            .when(this.handleConnectionsService)
            .handleConnection(any(OpenProjectConnection.class), any(DocumentReference.class));

        Response createResponse = this.handleConnections.createConnection(WIKI_NAME, SPACE_NAME, PAGE_NAME,
            openProjectConnection);
        Response updateResponse = this.handleConnections.updateConnection(WIKI_NAME, SPACE_NAME, PAGE_NAME,
            openProjectConnection);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), createResponse.getStatus());
        assertEquals("The client secret is required.", createResponse.getEntity());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), updateResponse.getStatus());
    }

    @Test
    void handleConflict() throws ProjectManagementException
    {
        doThrow(ConnectionSaveException.conflict("Connection firstConnection already exists."))
            .when(this.handleConnectionsService)
            .handleConnection(any(OpenProjectConnection.class), any(DocumentReference.class));

        Response createResponse = this.handleConnections.createConnection(WIKI_NAME, SPACE_NAME, PAGE_NAME,
            openProjectConnection);
        Response updateResponse = this.handleConnections.updateConnection(WIKI_NAME, SPACE_NAME, PAGE_NAME,
            openProjectConnection);

        assertEquals(Response.Status.CONFLICT.getStatusCode(), createResponse.getStatus());
        assertEquals("Connection firstConnection already exists.", createResponse.getEntity());
        assertEquals(Response.Status.CONFLICT.getStatusCode(), updateResponse.getStatus());
    }

    @Test
    void handleSaveFailure() throws ProjectManagementException
    {
        doThrow(new ProjectManagementException("There was a problem while saving the connection",
            new RuntimeException("Database error")))
            .when(this.handleConnectionsService)
            .handleConnection(any(OpenProjectConnection.class), any(DocumentReference.class));

        Response response = this.handleConnections.createConnection(WIKI_NAME, SPACE_NAME, PAGE_NAME,
            openProjectConnection);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("There was a problem while saving the connection", response.getEntity());
        assertEquals("There was a problem while saving the connection", this.logCapture.getMessage(0));
    }

    @Test
    void createAndUpdateConnectionOnInvalidPageFail()
    {
        Response createResponse = handleConnections.createConnection(
            "",
            "",
            PAGE_NAME,
            openProjectConnection
        );

        Response updateResponse = handleConnections.updateConnection(
            "",
            "",
            PAGE_NAME,
            openProjectConnection
        );

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), createResponse.getStatus());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), updateResponse.getStatus());
    }
}
