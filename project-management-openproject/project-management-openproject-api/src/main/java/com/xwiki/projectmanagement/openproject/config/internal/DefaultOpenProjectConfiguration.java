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
package com.xwiki.projectmanagement.openproject.config.internal;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.oidc.OAuth2ClientScriptService;
import org.xwiki.contrib.oidc.OAuth2Exception;
import org.xwiki.script.service.ScriptService;

import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.openproject.apiclient.internal.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * Default implementation of {@link OpenProjectConfiguration}. This class retrieves configuration values and tokens
 * needed to connect to OpenProject instances.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultOpenProjectConfiguration implements OpenProjectConfiguration
{
    private static final String OAUTH_COMPONENT_NAME = "oauth2client";

    @Inject
    @Named("openproject")
    private ConfigurationSource openProjectConfiguration;

    @Inject
    private ComponentManager componentManager;

    @Override
    public List<OpenProjectConnection> getOpenProjectConnections()
    {
        return openProjectConfiguration.getProperty("openprojectConnections");
    }

    @Override
    public String getConnectionUrl(String connectionName)
    {
        List<OpenProjectConnection> connections = getOpenProjectConnections();
        return connections
            .stream()
            .filter(connection -> connection.getConnectionName()
                .equals(connectionName))
            .findFirst()
            .map(OpenProjectConnection::getServerURL)
            .orElse(null);
    }

    @Override
    public String getAccessTokenForConfiguration(String connectionName) throws AuthenticationException
    {
        String accessToken;
        try {
            OAuth2ClientScriptService oauth2Client = componentManager.getInstance(ScriptService.class,
                OAUTH_COMPONENT_NAME);
            accessToken = oauth2Client.getAccessToken(connectionName);
        } catch (ComponentLookupException e) {
            throw new AuthenticationException("OAuth component not available for connection " + connectionName, e);
        } catch (OAuth2Exception e) {
            throw new AuthenticationException("Failed to retrieve access for " + connectionName,
                e);
        }
        return accessToken;
    }

    @Override
    public void createNewToken(String connectionName, String redirectUrl) throws AuthenticationException
    {
        try {
            OAuth2ClientScriptService oauth2Client = componentManager.getInstance(ScriptService.class,
                OAUTH_COMPONENT_NAME);
            oauth2Client.authorize(connectionName, redirectUrl);
        } catch (Exception e) {
            throw new AuthenticationException("Cannot connect to the open project instance", e);
        }
    }

    @Override
    public OpenProjectApiClient getOpenProjectApiClient(String connectionName) throws AuthenticationException
    {
        return new OpenProjectApiClient(getConnectionUrl(connectionName),
            getAccessTokenForConfiguration(connectionName));
    }
}
