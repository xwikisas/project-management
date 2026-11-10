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
package com.xwiki.projectmanagement.openproject.internal.rest.document;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.WikiReference;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * Finds the OpenProject connection of a wiki matching an instance id sent by OpenProject.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component(roles = InstanceConnectionResolver.class)
@Singleton
public class InstanceConnectionResolver
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * @param instance the instance id of an OpenProject server
     * @param wiki the wiki whose connections are used, i.e. the wiki of the page the request is about (the REST
     *     requests are made on the main wiki, while the connections are read from the context wiki)
     * @return the name of the first connection of the wiki to the given OpenProject instance
     * @throws WebApplicationException with a 400 response if no connection of the wiki has the given instance id
     */
    public String getConnectionName(String instance, WikiReference wiki)
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        String currentWiki = xcontext.getWikiId();
        OpenProjectConnection connection;
        try {
            xcontext.setWikiId(wiki.getName());
            connection = this.openProjectConfiguration.getOpenProjectConnections().stream()
                .filter(cfg -> instance.equals(cfg.getInstanceId())).findFirst().orElse(null);
        } finally {
            xcontext.setWikiId(currentWiki);
        }

        if (connection == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity(String.format("Could not find a configured instance for the id [%s].", instance)).build());
        }
        return connection.getConnectionName();
    }
}
