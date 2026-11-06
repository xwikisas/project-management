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

import org.apache.commons.lang3.StringUtils;

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

    private String instanceId;

    private OpenProjectAccessMode accessMode = OpenProjectAccessMode.USER_ACCOUNTS;

    /**
     * Constructs a new {@link OpenProjectConnection} with the provided values.
     *
     * @param connectionName the name of the configuration
     * @param serverURL the URL of the OpenProject server, its trailing slashes being ignored
     * @param clientId the client ID used for authentication
     * @param clientSecret the client secret used for authentication
     * @param instanceId the identifier of the OpenProject instance
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
        String clientSecret,
        @JsonProperty("instanceId")
        String instanceId
    )
    {
        this.connectionName = connectionName;
        this.serverURL = normalizeServerURL(serverURL);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.instanceId = instanceId;
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
     * @return the URL of the OpenProject server, without a trailing slash, so that paths can be appended to it
     */
    public String getServerURL()
    {
        return serverURL;
    }

    /**
     * Sets the URL of the OpenProject server.
     *
     * @param serverURL the new server URL, its trailing slashes being ignored
     */
    public void setServerURL(String serverURL)
    {
        this.serverURL = normalizeServerURL(serverURL);
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

    /**
     * @return the identifier of the OpenProject instance 
     * @since 1.2
     */
    public String getInstanceId()
    {
        return instanceId;
    }

    /**
     * Sets the identifier of the OpenProject instance.
     *
     * @param instanceId the instance identifier
     * @since 1.2
     */
    public void setInstanceId(String instanceId)
    {
        this.instanceId = instanceId;
    }

    /**
     * @return who can see the data of this connection, and how; never {@code null}
     * @since 1.4.0
     */
    public OpenProjectAccessMode getAccessMode()
    {
        return accessMode;
    }

    /**
     * Sets who can see the data of this connection, and how.
     *
     * @param accessMode the access mode, {@code null} for the default one ({@link OpenProjectAccessMode#USER_ACCOUNTS})
     * @since 1.4.0
     */
    public void setAccessMode(OpenProjectAccessMode accessMode)
    {
        this.accessMode = accessMode != null ? accessMode : OpenProjectAccessMode.USER_ACCOUNTS;
    }

    private static String normalizeServerURL(String serverURL)
    {
        // The links to OpenProject are built by appending paths such as "/versions/21" to the server URL, which would
        // otherwise contain a double slash when the configured URL ends with a slash.
        String normalizedURL = StringUtils.stripEnd(serverURL, "/");
        // A URL without host, e.g. "http://", is invalid anyway: keep it as entered, for the error message.
        return StringUtils.endsWith(normalizedURL, ":") ? serverURL : normalizedURL;
    }
}
