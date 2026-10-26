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

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.oidc.OAuth2ClientScriptService;
import org.xwiki.contrib.oidc.OAuth2Exception;
import org.xwiki.script.service.ScriptService;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientFactory;
import com.xwiki.projectmanagement.openproject.auth.BearerTokenAuthenticator;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * Default implementation of {@link OpenProjectAccess}, based on the OAuth2 tokens that the users get when they
 * connect their OpenProject account.
 *
 * @version $Id$
 * @since 1.3.1
 */
@Component
@Singleton
public class DefaultOpenProjectAccess implements OpenProjectAccess
{
    private static final String OAUTH_COMPONENT_NAME = "oauth2client";

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private OpenProjectApiClientFactory openProjectApiClientFactory;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    @Override
    public OpenProjectAccessResult resolve(String connectionName)
    {
        OpenProjectConnection connection = this.openProjectConfiguration.getConnection(connectionName);
        if (connection == null) {
            this.logger.warn("No OpenProject connection named [{}] is configured.", connectionName);
            return OpenProjectAccessResult.notConfigured();
        }

        // Guests have no user to attach an OAuth token to.
        if (this.contextProvider.get().getUserReference() == null) {
            return OpenProjectAccessResult.notConnected();
        }

        String accessToken = getAccessToken(connectionName);
        if (StringUtils.isBlank(accessToken)) {
            this.logger.debug("The current user has no access token for the OpenProject connection [{}].",
                connectionName);
            return OpenProjectAccessResult.notConnected();
        }

        return OpenProjectAccessResult.authenticated(this.openProjectApiClientFactory.builder()
            .serverUrl(connection.getServerURL())
            .authentication(new BearerTokenAuthenticator(accessToken))
            .build());
    }

    private String getAccessToken(String connectionName)
    {
        try {
            OAuth2ClientScriptService oauth2Client =
                this.componentManager.getInstance(ScriptService.class, OAUTH_COMPONENT_NAME);
            return oauth2Client.getAccessToken(connectionName);
        } catch (ComponentLookupException | OAuth2Exception e) {
            this.logger.warn("Failed to retrieve the access token for the OpenProject connection [{}]. Cause: [{}].",
                connectionName, ExceptionUtils.getRootCauseMessage(e));
            return null;
        }
    }
}
