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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the connection configuration for an OpenProject instance.
 *
 * @version $Id$
 */
public class OpenProjectConnection
{
    private String connectionName;

    private String serverURL;

    private String clientId;

    private String clientSecret;

    /**
     * Constructs a new {@link OpenProjectConnection} with the provided values.
     *
     * @param connectionName the name of the configuration
     * @param serverURL the URL of the OpenProject server
     * @param clientId the client ID used for authentication
     * @param clientSecret the client secret used for authentication
     */
    @JsonCreator
    public OpenProjectConnection(
        @JsonProperty("connectionName")
        String connectionName,
        @JsonProperty("serverURL")
        String serverURL,
        @JsonProperty("clientId")
        String clientId,
        @JsonProperty("clientSecret")
        String clientSecret
    )
    {
        this.connectionName = connectionName;
        this.serverURL = serverURL;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * @return the name of this configuration
     */
    public String getConnectionName()
    {
        return connectionName;
    }

    /**
     * Sets the name of this connection configuration.
     *
     * @param connectionName the configuration name
     */
    public void setConnectionName(String connectionName)
    {
        this.connectionName = connectionName;
    }

    /**
     * @return the URL of the OpenProject server
     */
    public String getServerURL()
    {
        return serverURL;
    }

    /**
     * Sets the URL of the OpenProject server.
     *
     * @param serverURL the new server URL
     */
    public void setServerURL(String serverURL)
    {
        this.serverURL = serverURL;
    }

    /**
     * @return the client ID used for authentication
     */
    public String getClientId()
    {
        return clientId;
    }

    /**
     * Sets the client ID used for authentication.
     *
     * @param clientId the new client ID
     */
    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }

    /**
     * @return the client secret used for authentication
     */
    public String getClientSecret()
    {
        return clientSecret;
    }

    /**
     * Sets the client secret used for authentication.
     *
     * @param clientSecret the new client secret
     */
    public void setClientSecret(String clientSecret)
    {
        this.clientSecret = clientSecret;
    }
}
