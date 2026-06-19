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
package com.xwiki.projectmanagement.openproject;

import java.net.http.HttpClient;

import org.xwiki.cache.Cache;

import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.auth.NoOpAuthenticator;
import com.xwiki.projectmanagement.openproject.auth.OpenProjectAuthenticator;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

/**
 * Builder for {@link OpenProjectApiClient} instances. Obtained from an {@link OpenProjectApiClientFactory}, it
 * collects the desired settings and assembles the client when {@link #build()} is called.
 *
 * @version $Id$
 * @since 1.2
 */
public interface OpenProjectApiClientBuilder
{
    /**
     * Sets the base URL of the OpenProject server. This is required.
     *
     * @param serverUrl the base URL of the OpenProject server
     * @return this builder, for chaining
     */
    OpenProjectApiClientBuilder serverUrl(String serverUrl);

    /**
     * Sets the authentication strategy used by the client. When not set, the client uses {@link NoOpAuthenticator} and
     * can only access the public endpoints of the instance.
     *
     * @param authenticator the authentication strategy
     * @return this builder, for chaining
     */
    OpenProjectApiClientBuilder authentication(OpenProjectAuthenticator authenticator);

    /**
     * Sets the {@link HttpClient} used to perform the requests. When not set, a default client is used.
     *
     * @param httpClient the HTTP client to use
     * @return this builder, for chaining
     */
    OpenProjectApiClientBuilder httpClient(HttpClient httpClient);

    /**
     * Wraps the built client with a caching layer using the given cache. When not set, the client is not cached.
     *
     * @param cache the cache used to store and retrieve paginated results
     * @param clientId the identifier of the client, used to namespace the cache keys
     * @return this builder, for chaining
     */
    OpenProjectApiClientBuilder caching(Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache, String clientId);

    /**
     * Assembles the client from the collected settings.
     *
     * @return the configured {@link OpenProjectApiClient}
     */
    OpenProjectApiClient build();
}
