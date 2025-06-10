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
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.oidc.OAuth2ClientScriptService;
import org.xwiki.script.service.ScriptService;

import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.openproject.model.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;

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
    public List<OpenProjectConnection> getOpenProjectConnections() throws AuthenticationException
    {
        return openProjectConfiguration.getProperty("openprojectConnections");
    }

    @Override
    public String getConnectionUrl(String connectionName) throws AuthenticationException
    {
        List<OpenProjectConnection> connections = getOpenProjectConnections();
        OpenProjectConnection searchedConnection = connections
            .stream()
            .filter(connection -> connection.getConnectionName()
                .equals(connectionName))
            .findFirst()
            .orElseThrow();
        return searchedConnection.getServerURL();
    }

    @Override
    public String getTokenForCurrentConfig(String connectionName) throws AuthenticationException
    {
        String accessToken;
        try {
            OAuth2ClientScriptService oauth2Client = componentManager.getInstance(ScriptService.class,
                OAUTH_COMPONENT_NAME);
            accessToken = oauth2Client.getAccessToken(connectionName);
        } catch (Exception e) {
            throw new AuthenticationException("The selected connection" + connectionName + "is not available", e);
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
}
