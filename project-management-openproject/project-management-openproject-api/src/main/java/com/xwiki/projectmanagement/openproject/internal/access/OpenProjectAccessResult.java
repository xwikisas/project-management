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

import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;

/**
 * The outcome of resolving how the current user reaches an OpenProject connection: a status, and the client to use
 * when that status allows calling OpenProject.
 *
 * @version $Id$
 * @since 1.3.1
 */
public final class OpenProjectAccessResult
{
    private static final OpenProjectAccessResult NOT_CONNECTED =
        new OpenProjectAccessResult(OpenProjectAccessStatus.NOT_CONNECTED, null);

    private static final OpenProjectAccessResult NOT_CONFIGURED =
        new OpenProjectAccessResult(OpenProjectAccessStatus.NOT_CONFIGURED, null);

    private final OpenProjectAccessStatus status;

    private final OpenProjectApiClient client;

    private OpenProjectAccessResult(OpenProjectAccessStatus status, OpenProjectApiClient client)
    {
        this.status = status;
        this.client = client;
    }

    /**
     * @param client the client calling OpenProject with the current user's token
     * @return a result with the {@link OpenProjectAccessStatus#AUTHENTICATED} status
     */
    public static OpenProjectAccessResult authenticated(OpenProjectApiClient client)
    {
        return new OpenProjectAccessResult(OpenProjectAccessStatus.AUTHENTICATED, client);
    }

    /**
     * @param client the client calling OpenProject without credentials
     * @return a result with the {@link OpenProjectAccessStatus#ANONYMOUS} status
     */
    public static OpenProjectAccessResult anonymous(OpenProjectApiClient client)
    {
        return new OpenProjectAccessResult(OpenProjectAccessStatus.ANONYMOUS, client);
    }

    /**
     * @return a result with the {@link OpenProjectAccessStatus#NOT_CONNECTED} status and no client
     */
    public static OpenProjectAccessResult notConnected()
    {
        return NOT_CONNECTED;
    }

    /**
     * @return a result with the {@link OpenProjectAccessStatus#NOT_CONFIGURED} status and no client
     */
    public static OpenProjectAccessResult notConfigured()
    {
        return NOT_CONFIGURED;
    }

    /**
     * @return how the current user can reach the connection
     */
    public OpenProjectAccessStatus getStatus()
    {
        return this.status;
    }

    /**
     * @return the client to call OpenProject with the current user's own account, or {@code null} when the status is
     *     not {@link OpenProjectAccessStatus#AUTHENTICATED}; to use for the requests that anonymous users can't make,
     *     such as creating or updating work packages
     */
    public OpenProjectApiClient getAuthenticatedClient()
    {
        return this.status == OpenProjectAccessStatus.AUTHENTICATED ? this.client : null;
    }

    /**
     * @return the client to call OpenProject with, or {@code null} when the status doesn't allow calling OpenProject;
     *     with the {@link OpenProjectAccessStatus#ANONYMOUS} status, the client can only read what OpenProject shows to
     *     anonymous users
     */
    public OpenProjectApiClient getClient()
    {
        return this.client;
    }
}
