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

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.objects.BaseObject;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * Default implementation of {@link OpenProjectConfiguration}, reading the connections configured in the
 * administration.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultOpenProjectConfiguration implements OpenProjectConfiguration
{
    private static final List<String> CODE_SPACE = Arrays.asList("OpenProject", "Code");

    private static final LocalDocumentReference CONFIGURATION_DOCUMENT =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConfiguration");

    private static final LocalDocumentReference CONFIGURATION_CLASS =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConfigurationClass");

    @Inject
    @Named("openproject")
    private ConfigurationSource openProjectConfiguration;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

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
    public OpenProjectConnection getDefaultConnection()
    {
        List<OpenProjectConnection> connections = getOpenProjectConnections();
        if (connections == null || connections.isEmpty()) {
            return null;
        } else if (connections.size() == 1) {
            return connections.get(0);
        }
        String defaultConnection = getDefaultConnectionName();
        return StringUtils.isBlank(defaultConnection) ? null : getConnection(defaultConnection);
    }

    private String getDefaultConnectionName()
    {
        XWikiContext xcontext = this.contextProvider.get();
        try {
            BaseObject configuration = xcontext.getWiki().getDocument(CONFIGURATION_DOCUMENT, xcontext)
                .getXObject(CONFIGURATION_CLASS);
            return configuration != null ? configuration.getStringValue("defaultConnection") : null;
        } catch (XWikiException e) {
            this.logger.warn("Failed to read the default OpenProject connection. Cause: [{}].",
                ExceptionUtils.getRootCauseMessage(e));
            return null;
        }
    }
}
