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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.EntryEvictionConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.oidc.OAuth2ClientScriptService;
import org.xwiki.contrib.oidc.OAuth2Exception;
import org.xwiki.script.service.ScriptService;

import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.DefaultOpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

/**
 * Default implementation of {@link OpenProjectConfiguration}. This class retrieves configuration values and tokens
 * needed to connect to OpenProject instances.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultOpenProjectConfiguration implements OpenProjectConfiguration, Initializable, Disposable
{
    private static final String OAUTH_COMPONENT_NAME = "oauth2client";

    @Inject
    @Named("openproject")
    private ConfigurationSource openProjectConfiguration;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private CacheManager cacheManager;

    @Inject
    private Logger logger;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache;

    @Override
    public void initialize() throws InitializationException
    {
        CacheConfiguration cacheConfig = new CacheConfiguration();
        cacheConfig.setConfigurationId("projectmanagement.openproject");
        LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
        // The access token coming from OpenProject has a lifespan of 2 hours so we probably shouldn't have a cache
        // longer than that either.
        lru.setLifespan(7200);
        lru.setMaxEntries(1000);
        cacheConfig.put(EntryEvictionConfiguration.CONFIGURATIONID, lru);

        try {
            this.cache = this.cacheManager.createNewCache(cacheConfig);
        } catch (Exception e) {
            // Dispose the cache if it has been created.
            if (this.cache != null) {
                this.cache.dispose();
            }
            throw new InitializationException("Failed to create the Open Project client cache.", e);
        }
    }

    @Override
    public List<OpenProjectConnection> getOpenProjectConnections()
    {
        return openProjectConfiguration.getProperty("openprojectConnections");
    }

    @Override
    public OpenProjectConnection getConnection(String connectionName)
    {
        List<OpenProjectConnection> connections = getOpenProjectConnections();
        return connections
            .stream()
            .filter(connection -> connection.getConnectionName()
                .equals(connectionName))
            .findFirst()
            .orElse(null);
    }

    @Override
    public String getAccessTokenForConfiguration(String connectionName)
    {
        String accessToken = null;
        try {
            OAuth2ClientScriptService oauth2Client = componentManager.getInstance(ScriptService.class,
                OAUTH_COMPONENT_NAME);
            accessToken = oauth2Client.getAccessToken(connectionName);
        } catch (ComponentLookupException | OAuth2Exception e) {
            // Shouldn't happen.
            logger.warn(String.format("Failed to retrieve the access token for instance [%s]. Cause: [%s].",
                connectionName, ExceptionUtils.getRootCauseMessage(e)), e);
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
        } catch (ComponentLookupException | OAuth2Exception e) {
            throw new AuthenticationException(
                String.format("Failed to establish the OAuth2 connection for instance [%s]", connectionName), e);
        }
    }

    @Override
    public OpenProjectApiClient getOpenProjectApiClient(String connectionName)
    {
        OpenProjectConnection connection = getConnection(connectionName);
        String accessToken = getAccessTokenForConfiguration(connectionName);
        if (connection == null || StringUtils.isEmpty(accessToken)) {
            logger.warn(String.format(
                "No client for connection [%s] could be created because the configuration doesn't exist or the access "
                    + "token for the current user is not set.", connectionName));
            return null;
        }
        return new DefaultOpenProjectApiClient(connection.getServerURL(), accessToken);
    }

    @Override
    public void dispose() throws ComponentLifecycleException
    {
        if (this.cache != null) {
            this.cache.dispose();
        }
    }

    @Override
    public void cleanCache()
    {
        if (this.cache != null) {
            this.cache.removeAll();
        }
    }
}
