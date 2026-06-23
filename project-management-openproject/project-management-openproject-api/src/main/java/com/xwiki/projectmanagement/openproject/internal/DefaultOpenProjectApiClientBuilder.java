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
package com.xwiki.projectmanagement.openproject.internal;

import java.net.http.HttpClient;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.cache.Cache;

import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientBuilder;
import com.xwiki.projectmanagement.openproject.auth.NoOpAuthenticator;
import com.xwiki.projectmanagement.openproject.auth.OpenProjectAuthenticator;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

/**
 * Default implementation of {@link OpenProjectApiClientBuilder}.
 *
 * @version $Id$
 * @since 1.2
 */
public class DefaultOpenProjectApiClientBuilder implements OpenProjectApiClientBuilder
{
    private String serverUrl;

    private OpenProjectAuthenticator authenticator = NoOpAuthenticator.INSTANCE;

    private HttpClient httpClient = HttpClient.newHttpClient();

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache;

    private String cacheClientId;

    @Override
    public OpenProjectApiClientBuilder serverUrl(String serverUrl)
    {
        this.serverUrl = serverUrl;
        return this;
    }

    @Override
    public OpenProjectApiClientBuilder authentication(OpenProjectAuthenticator authenticator)
    {
        this.authenticator = (authenticator != null) ? authenticator : NoOpAuthenticator.INSTANCE;
        return this;
    }

    @Override
    public OpenProjectApiClientBuilder httpClient(HttpClient httpClient)
    {
        if (httpClient != null) {
            this.httpClient = httpClient;
        }
        return this;
    }

    @Override
    public OpenProjectApiClientBuilder caching(Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache,
        String clientId)
    {
        this.cache = cache;
        this.cacheClientId = clientId;
        return this;
    }

    @Override
    public OpenProjectApiClient build()
    {
        if (StringUtils.isBlank(serverUrl)) {
            throw new IllegalStateException("A server URL is required to build an OpenProjectApiClient.");
        }

        OpenProjectApiClient client = new DefaultOpenProjectApiClient(serverUrl, authenticator, httpClient);
        if (cache != null) {
            client = new CachingOpenProjectApiClient(client, cacheClientId, cache);
        }
        return client;
    }
}
