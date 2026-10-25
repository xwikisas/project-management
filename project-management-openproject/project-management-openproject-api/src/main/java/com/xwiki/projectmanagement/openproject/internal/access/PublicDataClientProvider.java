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

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.EntryEvictionConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientFactory;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

/**
 * Provides the clients that call OpenProject without credentials, to show the public data of a connection to the users
 * who can't call it with their own account. Their results are cached: they are the same for every user, unlike the
 * results of the requests made with a user's token, which are never cached. There is one cache per wiki, since the
 * duration of the cache is configured per wiki.
 *
 * @version $Id$
 * @since 1.3.1
 */
@Component(roles = PublicDataClientProvider.class)
@Singleton
public class PublicDataClientProvider implements Disposable
{
    /**
     * The default duration of the cache, in minutes, used when the configuration doesn't set a valid one.
     */
    static final int DEFAULT_CACHE_DURATION = 5;

    private static final List<String> CODE_SPACE = Arrays.asList("OpenProject", "Code");

    private static final LocalDocumentReference CONFIGURATION_DOCUMENT =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConfiguration");

    private static final LocalDocumentReference CONFIGURATION_CLASS =
        new LocalDocumentReference(CODE_SPACE, "OpenProjectConfigurationClass");

    private static final String CACHE_DURATION_PROPERTY = "publicDataCacheDuration";

    private static final int MAX_CACHE_ENTRIES = 1000;

    private static final int SECONDS_PER_MINUTE = 60;

    @Inject
    private OpenProjectApiClientFactory openProjectApiClientFactory;

    @Inject
    private CacheManager cacheManager;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    private final Map<String, Cache<PaginatedResult<? extends BaseOpenProjectObject>>> caches =
        new ConcurrentHashMap<>();

    /**
     * @param connection a connection configured in the current wiki
     * @return a client calling the given connection without credentials, with cached results, or {@code null} if the
     *     OpenProject instance doesn't accept anonymous requests (e.g. when it requires users to log in)
     */
    public OpenProjectApiClient getClient(OpenProjectConnection connection)
    {
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache =
            getCache(this.contextProvider.get().getWikiId());
        if (cache == null) {
            return null;
        }

        // The results of anonymous requests only depend on the OpenProject instance, not on the connection.
        OpenProjectApiClient client = this.openProjectApiClientFactory.builder()
            .serverUrl(connection.getServerURL())
            .caching(cache, connection.getServerURL())
            .build();

        return acceptsAnonymousRequests(client, connection) ? client : null;
    }

    /**
     * Removes the cached results of the anonymous requests made for the given wiki, and takes into account a new
     * duration of the cache.
     *
     * @param wikiId the identifier of the wiki whose OpenProject configuration changed
     */
    public void clearCache(String wikiId)
    {
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache = this.caches.remove(wikiId);
        if (cache != null) {
            cache.dispose();
        }
    }

    @Override
    public void dispose()
    {
        this.caches.values().forEach(Cache::dispose);
        this.caches.clear();
    }

    private boolean acceptsAnonymousRequests(OpenProjectApiClient client, OpenProjectConnection connection)
    {
        try {
            // A cheap request, whose result is cached, so that OpenProject is only asked once per cache duration.
            client.getProjects(1, 1, "");
            return true;
        } catch (WorkItemRetrievalException e) {
            Integer statusCode = e.getStatusCode();
            if (statusCode != null && (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED
                || statusCode == HttpURLConnection.HTTP_FORBIDDEN))
            {
                this.logger.warn("The OpenProject connection [{}] is configured to show its public data, but [{}] "
                    + "doesn't accept anonymous requests. Allow them in the OpenProject administration (e.g. by "
                    + "disabling \"Login required\") or disable the option of the connection.",
                    connection.getConnectionName(), connection.getServerURL());
                return false;
            }
            return logRequestFailure(connection, e);
        } catch (ProjectManagementException e) {
            return logRequestFailure(connection, e);
        }
    }

    private boolean logRequestFailure(OpenProjectConnection connection, Exception e)
    {
        // The instance may only be unreachable for now: keep the client, so that its requests fail as for the users
        // calling OpenProject with their own account.
        this.logger.warn("Failed to check if [{}] accepts anonymous requests for the OpenProject connection [{}]. "
            + "Cause: [{}].", connection.getServerURL(), connection.getConnectionName(),
            ExceptionUtils.getRootCauseMessage(e));
        return true;
    }

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> getCache(String wikiId)
    {
        return this.caches.computeIfAbsent(wikiId, this::createCache);
    }

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> createCache(String wikiId)
    {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        cacheConfiguration.setConfigurationId("projectmanagement.openproject.publicdata." + wikiId);
        LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
        lru.setLifespan(getCacheDuration() * SECONDS_PER_MINUTE);
        lru.setMaxEntries(MAX_CACHE_ENTRIES);
        cacheConfiguration.put(EntryEvictionConfiguration.CONFIGURATIONID, lru);
        try {
            return this.cacheManager.createNewCache(cacheConfiguration);
        } catch (CacheException e) {
            this.logger.error("Failed to create the cache of the OpenProject public data for the wiki [{}].", wikiId,
                e);
            return null;
        }
    }

    private int getCacheDuration()
    {
        XWikiContext xcontext = this.contextProvider.get();
        try {
            BaseObject configuration = xcontext.getWiki().getDocument(CONFIGURATION_DOCUMENT, xcontext)
                .getXObject(CONFIGURATION_CLASS);
            int duration = configuration != null ? configuration.getIntValue(CACHE_DURATION_PROPERTY) : 0;
            return duration > 0 ? duration : DEFAULT_CACHE_DURATION;
        } catch (XWikiException e) {
            this.logger.warn("Failed to read the duration of the cache of the OpenProject public data. Cause: [{}].",
                ExceptionUtils.getRootCauseMessage(e));
            return DEFAULT_CACHE_DURATION;
        }
    }
}
