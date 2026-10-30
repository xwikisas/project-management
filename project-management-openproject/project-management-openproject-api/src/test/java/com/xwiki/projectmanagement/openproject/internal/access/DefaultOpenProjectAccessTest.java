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
package com.xwiki.projectmanagement.openproject.internal.access;

import java.net.URI;
import java.net.http.HttpRequest;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.oidc.OAuth2ClientScriptService;
import org.xwiki.contrib.oidc.OAuth2Exception;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientFactory;
import com.xwiki.projectmanagement.openproject.auth.OpenProjectAuthenticator;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectAccess}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultOpenProjectAccessTest
{
    private static final String CONNECTION_NAME = "main";

    private static final String SERVER_URL = "https://openproject.example.org";

    private static final String ACCESS_TOKEN = "accessToken";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private DefaultOpenProjectAccess access;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectApiClientFactory openProjectApiClientFactory;

    @MockComponent
    private PublicDataClientProvider publicDataClientProvider;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private OAuth2ClientScriptService oauth2Client;

    @Mock
    private OpenProjectApiClientBuilder builder;

    @Mock
    private OpenProjectApiClient apiClient;

    @Mock
    private OpenProjectApiClient anonymousClient;

    private final OpenProjectConnection connection =
        new OpenProjectConnection(CONNECTION_NAME, SERVER_URL, "clientId", "clientSecret", "instanceId");

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.openProjectConfiguration.getConnection(CONNECTION_NAME)).thenReturn(this.connection);
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getUserReference()).thenReturn(new DocumentReference("wiki", "XWiki", "user"));
        when(this.componentManager.getInstance(ScriptService.class, "oauth2client")).thenReturn(this.oauth2Client);
        when(this.oauth2Client.getAccessToken(CONNECTION_NAME)).thenReturn(ACCESS_TOKEN);

        when(this.openProjectApiClientFactory.builder()).thenReturn(this.builder);
        when(this.builder.serverUrl(any())).thenReturn(this.builder);
        when(this.builder.authentication(any())).thenReturn(this.builder);
        when(this.builder.build()).thenReturn(this.apiClient);
    }

    @Test
    void resolveAuthenticated()
    {
        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.AUTHENTICATED, result.getStatus());
        assertSame(this.apiClient, result.getClient());
        verify(this.builder).serverUrl(SERVER_URL);

        ArgumentCaptor<OpenProjectAuthenticator> authCaptor = ArgumentCaptor.forClass(OpenProjectAuthenticator.class);
        verify(this.builder).authentication(authCaptor.capture());
        // Any valid absolute URL works here; the authenticator only adds headers, it does not depend on the URI.
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create("http://localhost"));
        authCaptor.getValue().authenticate(requestBuilder);
        assertEquals("Bearer " + ACCESS_TOKEN,
            requestBuilder.build().headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void resolveNotConfigured()
    {
        OpenProjectAccessResult result = this.access.resolve("unknown");

        assertEquals(OpenProjectAccessStatus.NOT_CONFIGURED, result.getStatus());
        assertNull(result.getClient());
        verifyNoInteractions(this.oauth2Client, this.openProjectApiClientFactory);
        assertEquals("No OpenProject connection named [unknown] is configured.", this.logCapture.getMessage(0));
    }

    @Test
    void resolveGuest() throws Exception
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.NOT_CONNECTED, result.getStatus());
        assertNull(result.getClient());
        verify(this.oauth2Client, never()).getAccessToken(anyString());
        verifyNoInteractions(this.openProjectApiClientFactory);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "  " })
    void resolveWithoutToken(String token) throws Exception
    {
        when(this.oauth2Client.getAccessToken(CONNECTION_NAME)).thenReturn(token);

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.NOT_CONNECTED, result.getStatus());
        assertNull(result.getClient());
        verifyNoInteractions(this.openProjectApiClientFactory);
    }

    @Test
    void resolveWhenTheTokenLookupFails() throws Exception
    {
        when(this.oauth2Client.getAccessToken(CONNECTION_NAME)).thenThrow(new OAuth2Exception("Token error"));

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.NOT_CONNECTED, result.getStatus());
        assertNull(result.getClient());
        assertEquals("Failed to retrieve the access token for the OpenProject connection [main]. "
            + "Cause: [OAuth2Exception: Token error].", this.logCapture.getMessage(0));
    }

    @Test
    void resolveWhenTheOAuth2ClientIsMissing() throws Exception
    {
        when(this.componentManager.getInstance(ScriptService.class, "oauth2client"))
            .thenThrow(new ComponentLookupException("Missing"));

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.NOT_CONNECTED, result.getStatus());
        assertEquals("Failed to retrieve the access token for the OpenProject connection [main]. "
            + "Cause: [ComponentLookupException: Missing].", this.logCapture.getMessage(0));
    }

    @Test
    void resolveAnonymousForGuests()
    {
        this.connection.setShowPublicData(true);
        when(this.xcontext.getUserReference()).thenReturn(null);
        when(this.publicDataClientProvider.getClient(this.connection)).thenReturn(this.anonymousClient);

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.ANONYMOUS, result.getStatus());
        assertSame(this.anonymousClient, result.getClient());
        assertNull(result.getAuthenticatedClient());
    }

    @Test
    void resolveAnonymousForUsersWithoutToken() throws Exception
    {
        this.connection.setShowPublicData(true);
        when(this.oauth2Client.getAccessToken(CONNECTION_NAME)).thenReturn(null);
        when(this.publicDataClientProvider.getClient(this.connection)).thenReturn(this.anonymousClient);

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.ANONYMOUS, result.getStatus());
        assertSame(this.anonymousClient, result.getClient());
    }

    @Test
    void resolveNotConnectedWhenOpenProjectRejectsAnonymousRequests()
    {
        this.connection.setShowPublicData(true);
        when(this.xcontext.getUserReference()).thenReturn(null);
        when(this.publicDataClientProvider.getClient(this.connection)).thenReturn(null);

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.NOT_CONNECTED, result.getStatus());
        assertNull(result.getClient());
    }

    @Test
    void resolveAuthenticatedEvenWhenShowingPublicData()
    {
        this.connection.setShowPublicData(true);

        OpenProjectAccessResult result = this.access.resolve(CONNECTION_NAME);

        assertEquals(OpenProjectAccessStatus.AUTHENTICATED, result.getStatus());
        assertSame(this.apiClient, result.getAuthenticatedClient());
        verifyNoInteractions(this.publicDataClientProvider);
    }

    @Test
    void resolveNotConnectedWithoutPublicData()
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        assertEquals(OpenProjectAccessStatus.NOT_CONNECTED, this.access.resolve(CONNECTION_NAME).getStatus());
        verifyNoInteractions(this.publicDataClientProvider);
    }

    @Test
    void getAnonymousClient()
    {
        assertSame(this.apiClient, this.access.getAnonymousClient(CONNECTION_NAME));
        verify(this.builder).serverUrl(SERVER_URL);
        verify(this.builder, never()).authentication(any());
        // Whatever the connection shows to the users who aren't connected to it.
        verifyNoInteractions(this.oauth2Client, this.publicDataClientProvider);
    }

    @Test
    void getAnonymousClientWhenNotConfigured()
    {
        assertNull(this.access.getAnonymousClient("unknown"));
        verifyNoInteractions(this.openProjectApiClientFactory);
    }
}
