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

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.service.HandleConnectionsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ComponentTest
public class HandleConnectionsTest
{
    @InjectMockComponents
    private HandleConnections handleConnections;

    @MockComponent
    private HandleConnectionsService handleConnectionsService;

    @MockComponent
    private AuthorizationManager authorizationManager;

    @MockComponent
    private XWikiContext xContext;

    @Mock
    private WikiReference wikiReference;

    @Mock
    private Provider<XWikiContext> xContextProvider;

    @Mock
    private DocumentReference userReference;

    private static final OpenProjectConnection openProjectConnection = new OpenProjectConnection(
        "firstConnection",
        "firstConnectionURL",
        "firstConnectionClientId",
        "firstConnectionClientSecret"
    );

    private static final String WIKI_NAME = "wiki";

    private static final String SPACE_NAME = "space";

    private static final String PAGE_NAME = "page";

    @BeforeEach
    public void setUp()
    {
        when(this.xContextProvider.get()).thenReturn(this.xContext);
        when(this.xContext.getUserReference()).thenReturn(userReference);
        when(this.xContext.getWikiReference()).thenReturn(wikiReference);
        when(this.authorizationManager.hasAccess(Right.ADMIN, userReference, wikiReference)).thenReturn(true);
    }

    @Test
    public void handleConnectionSuccessTest()
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
    public void handleConnectionAccessRightsForbiddenTest()
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
    public void handleConflictTest() throws ProjectManagementException
    {
        doThrow(ProjectManagementException.class)
            .when(this.handleConnectionsService)
            .handleConnection(any(OpenProjectConnection.class), any(DocumentReference.class));

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

        assertEquals(Response.Status.CONFLICT.getStatusCode(), createResponse.getStatus());
        assertEquals(Response.Status.CONFLICT.getStatusCode(), updateResponse.getStatus());
    }

    @Test
    public void handleInternalServerErrorTest()
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
