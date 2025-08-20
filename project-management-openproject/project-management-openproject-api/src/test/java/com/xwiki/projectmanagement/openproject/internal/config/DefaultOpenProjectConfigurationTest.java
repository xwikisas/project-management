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

import java.lang.reflect.Field;
import java.util.List;

import javax.inject.Named;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.oidc.OAuth2ClientScriptService;
import org.xwiki.contrib.oidc.OAuth2Exception;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.CachingOpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.DefaultOpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class DefaultOpenProjectConfigurationTest
{
    @MockComponent
    @Named("openproject")
    private ConfigurationSource openProjectConfiguration;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private CacheManager cacheManager;

    @Mock
    private Logger logger;

    @MockComponent
    private OAuth2ClientScriptService oauth2Client;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache;

    @InjectMockComponents
    @InjectMocks
    private DefaultOpenProjectConfiguration configuration;

    private List<OpenProjectConnection> connections;

    private static final String ACCESS_TOKEN = "accessToken";

    private static final String REDIRECT_URL = "redirectURL";

    private static final OpenProjectConnection opConnection = new OpenProjectConnection(
        "firstConnection",
        "firstConnectionURL",
        "firstConnectionClientId",
        "firstConnectionClientSecret"
    );

    private static final String GET_OPEN_PROJECT_CLIENT_ERROR_MESSAGE =
        "No client for connection [%s] could be created because the configuration doesn't exist or the access "
            + "token for the current user is not set.";

    @BeforeEach
    void setUp() throws CacheException, ComponentLookupException, OAuth2Exception
    {
        this.connections = List.of(
            opConnection,
            new OpenProjectConnection(
                "secondConnection",
                "secondConnectionURL",
                "secondConnectionClientId",
                "secondConnectionClientSecret"
            ),
            new OpenProjectConnection(
                "thirdConnection",
                "thirdConnectionURL",
                "thirdConnectionClientId",
                "thirdConnectionClientSecret"
            )
        );

        when(this.cacheManager.createNewCache(any(CacheConfiguration.class))).thenReturn((Cache) this.cache);
        when(openProjectConfiguration.getProperty("openprojectConnections")).thenReturn(this.connections);
        when(this.componentManager.getInstance(ScriptService.class, "oauth2client")).thenReturn(oauth2Client);
        when(this.oauth2Client.getAccessToken(opConnection.getConnectionName())).thenReturn(ACCESS_TOKEN);
        doNothing().when(this.logger).warn(anyString(), any(OAuth2Exception.class));
        doNothing().when(this.logger).warn(anyString());
    }

    @Test
    public void getConnectionsTest()
    {
        List<OpenProjectConnection> result = configuration.getOpenProjectConnections();
        assertEquals(this.connections, result);
    }

    @Test
    public void getConnectionTest()
    {
        OpenProjectConnection result = configuration.getConnection(opConnection.getConnectionName());

        assertEquals(opConnection, result);
    }

    @Test
    public void getConnectionFailsTest()
    {
        String connectionName = "invalidConnection";

        OpenProjectConnection result = configuration.getConnection(connectionName);

        assertNull(result);
    }

    @Test
    public void getAccessTokenForConfigurationTest()
    {
        String token = configuration.getAccessTokenForConfiguration(opConnection.getConnectionName());

        assertEquals(ACCESS_TOKEN, token);
    }

    @Test
    public void getAccessTokenForConfigurationReturnsNullTest() throws OAuth2Exception
    {
        String connectionName = "invalidConnection";
        Exception exception = new OAuth2Exception("Failed to retrieve the access token");
        when(oauth2Client.getAccessToken(connectionName)).thenThrow(exception);

        String token = configuration.getAccessTokenForConfiguration(connectionName);

        assertNull(token);

        verify(logger)
            .warn(
                String.format(
                    "Failed to retrieve the access token for instance [%s]. Cause: [%s].",
                    connectionName,
                    ExceptionUtils.getRootCauseMessage(exception)
                ),
                exception
            );

        verify(oauth2Client).getAccessToken(connectionName);
    }

    @Test
    public void createNewTokenTest() throws OAuth2Exception, AuthenticationException
    {
        configuration.createNewToken(opConnection.getConnectionName(), REDIRECT_URL);

        verify(oauth2Client).authorize(opConnection.getConnectionName(), REDIRECT_URL);
    }

    @Test
    public void createNewTokenGeneratesErrorTest() throws OAuth2Exception
    {
        doThrow(OAuth2Exception.class).when(oauth2Client)
            .authorize(opConnection.getConnectionName(), REDIRECT_URL);

        AuthenticationException thrown = assertThrows(AuthenticationException.class,
            () -> configuration.createNewToken(opConnection.getConnectionName(), REDIRECT_URL));

        Assertions
            .assertTrue(thrown.getMessage()
                .contains(
                    "Failed to establish the OAuth2 connection for instance [" + opConnection.getConnectionName() + "]"
                )
            );

        verify(oauth2Client).authorize(opConnection.getConnectionName(), REDIRECT_URL);
    }

    @Test
    public void getOpenProjectApiClientTest() throws NoSuchFieldException, IllegalAccessException
    {
        OpenProjectApiClient client = configuration.getOpenProjectApiClient(opConnection.getConnectionName());

        assertNotNull(client);

        Assertions.assertInstanceOf(CachingOpenProjectApiClient.class, client);

        Field clientField = CachingOpenProjectApiClient.class.getDeclaredField("client");
        Field clientIdField = CachingOpenProjectApiClient.class.getDeclaredField("clientId");
        clientField.setAccessible(true);
        clientIdField.setAccessible(true);
        DefaultOpenProjectApiClient opApiClient = (DefaultOpenProjectApiClient) clientField.get(client);

        Field tokenField = DefaultOpenProjectApiClient.class.getDeclaredField("token");
        Field connectionUrlField = DefaultOpenProjectApiClient.class.getDeclaredField("connectionUrl");
        tokenField.setAccessible(true);
        connectionUrlField.setAccessible(true);

        String clientId = (String) clientIdField.get(client);
        String token = (String) tokenField.get(opApiClient);
        String connectionUrl = (String) connectionUrlField.get(opApiClient);

        assertEquals(ACCESS_TOKEN, token);
        assertEquals(opConnection.getServerURL(), connectionUrl);
        assertEquals(clientId, opConnection.getClientId());
    }

    @Test
    public void getOpenProjectApiClientConnectionIsNullTest()
    {
        when(openProjectConfiguration.getProperty("openprojectConnections")).thenReturn(List.of());

        OpenProjectApiClient client = configuration.getOpenProjectApiClient(opConnection.getConnectionName());

        assertNull(client);

        verify(logger).warn(String.format(
            GET_OPEN_PROJECT_CLIENT_ERROR_MESSAGE, opConnection.getConnectionName()));
    }

    @Test
    public void getOpenProjectApiClientTokenIsEmptyTest() throws OAuth2Exception
    {
        when(oauth2Client.getAccessToken(anyString())).thenReturn(null);

        OpenProjectApiClient client = configuration.getOpenProjectApiClient(opConnection.getConnectionName());

        assertNull(client);

        verify(logger).warn(String.format(
            GET_OPEN_PROJECT_CLIENT_ERROR_MESSAGE, opConnection.getConnectionName()));
    }
}
