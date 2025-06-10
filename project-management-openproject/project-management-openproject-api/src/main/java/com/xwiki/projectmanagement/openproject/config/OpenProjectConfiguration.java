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

package com.xwiki.projectmanagement.openproject.config;

import java.util.List;

import org.xwiki.component.annotation.Role;

import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.openproject.model.OpenProjectConnection;

/**
 * Provides methods for retrieving OpenProject configuration and access tokens.
 *
 * @version $Id$
 */
@Role
public interface OpenProjectConfiguration
{
    /**
     * Retrieves a list of available OpenProject connections.
     *
     * @return a list of {@code OpenProjectConnection} instances;
     * @throws AuthenticationException when the connection list cannot be retrieved
     */
    List<OpenProjectConnection> getOpenProjectConnections() throws AuthenticationException;

    /**
     * Retrieves the server URL associated with a given connection name.
     *
     * @param connectionName the name of the OpenProject connection configuration
     * @return the server URL associated with the given connection
     * @throws  AuthenticationException when the connection URL cannot be retrieved
     */
    String getConnectionUrl(String connectionName) throws AuthenticationException;

    /**
     * Retrieves the server URL associated with a given connection name.
     *
     * @param connectionName the name of the OpenProject connection configuration
     * @return the server URL associated with the given connection
     * @throws AuthenticationException when the connection does not exist.
     */
    String getTokenForCurrentConfig(String connectionName) throws AuthenticationException;

    /**
     * Creates a new OAuth token using the specified connection name and redirect URL.
     *
     * @param connectionName the name of the connection to use for creating the OAuth token
     * @param redirectUrl the URL to which the OAuth provider will redirect after authorization
     * @throws AuthenticationException when a new token cannot be created.
     */
    void createNewToken(String connectionName, String redirectUrl) throws AuthenticationException;
}
