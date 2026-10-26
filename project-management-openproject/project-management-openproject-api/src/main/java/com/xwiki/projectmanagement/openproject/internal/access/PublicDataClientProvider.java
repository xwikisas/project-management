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
 * results of the requests made with a user's token, which are never cached. Whether an OpenProject instance accepts
 * anonymous requests is cached for the same duration, so that an instance refusing them isn't asked again on each
 * request. There is one cache of each kind per wiki, since the duration of the cache is configured per wiki.
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

    private static final String CACHE_ID_PREFIX = "projectmanagement.openproject.publicdata.";

    private static final String ANONYMOUS_ACCESS_CACHE_ID_PREFIX = CACHE_ID_PREFIX + "anonymousaccess.";

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

    private final Map<String, WikiCaches> caches = new ConcurrentHashMap<>();

    /**
     * @param connection a connection configured in the current wiki
     * @return a client calling the given connection without credentials, with cached results, or {@code null} if the
     *     OpenProject instance doesn't accept anonymous requests (e.g. when it requires users to log in)
     */
    public OpenProjectApiClient getClient(OpenProjectConnection connection)
    {
        WikiCaches wikiCaches = this.caches.computeIfAbsent(this.contextProvider.get().getWikiId(), this::createCaches);
        if (wikiCaches == null) {
            return null;
        }

        // The results of anonymous requests only depend on the OpenProject instance, not on the connection.
        OpenProjectApiClient client = this.openProjectApiClientFactory.builder()
            .serverUrl(connection.getServerURL())
            .caching(wikiCaches.results, connection.getServerURL())
            .build();

        Boolean acceptsAnonymousRequests = wikiCaches.anonymousAccess.get(connection.getServerURL());
        if (acceptsAnonymousRequests == null) {
            acceptsAnonymousRequests = acceptsAnonymousRequests(client, connection);
            wikiCaches.anonymousAccess.set(connection.getServerURL(), acceptsAnonymousRequests);
        }
        return acceptsAnonymousRequests ? client : null;
    }

    /**
     * Removes the cached results of the anonymous requests made for the given wiki, and takes into account a new
     * duration of the cache.
     *
     * @param wikiId the identifier of the wiki whose OpenProject configuration changed
     */
    public void clearCache(String wikiId)
    {
        WikiCaches wikiCaches = this.caches.remove(wikiId);
        if (wikiCaches != null) {
            wikiCaches.dispose();
        }
    }

    @Override
    public void dispose()
    {
        this.caches.values().forEach(WikiCaches::dispose);
        this.caches.clear();
    }

    private boolean acceptsAnonymousRequests(OpenProjectApiClient client, OpenProjectConnection connection)
    {
        try {
            // A cheap request, whose outcome is cached by the caller.
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

    private WikiCaches createCaches(String wikiId)
    {
        int lifespan = getCacheDuration() * SECONDS_PER_MINUTE;
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> results = null;
        try {
            results = createCache(CACHE_ID_PREFIX + wikiId, lifespan);
            return new WikiCaches(results, createCache(ANONYMOUS_ACCESS_CACHE_ID_PREFIX + wikiId, lifespan));
        } catch (CacheException e) {
            if (results != null) {
                results.dispose();
            }
            this.logger.error("Failed to create the cache of the OpenProject public data for the wiki [{}].", wikiId,
                e);
            return null;
        }
    }

    private <T> Cache<T> createCache(String cacheId, int lifespan) throws CacheException
    {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        cacheConfiguration.setConfigurationId(cacheId);
        LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
        lru.setLifespan(lifespan);
        lru.setMaxEntries(MAX_CACHE_ENTRIES);
        cacheConfiguration.put(EntryEvictionConfiguration.CONFIGURATIONID, lru);
        return this.cacheManager.createNewCache(cacheConfiguration);
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

    /**
     * The caches of a wiki.
     */
    private static final class WikiCaches
    {
        private final Cache<PaginatedResult<? extends BaseOpenProjectObject>> results;

        /**
         * Whether each OpenProject instance, identified by its URL, accepts anonymous requests.
         */
        private final Cache<Boolean> anonymousAccess;

        WikiCaches(Cache<PaginatedResult<? extends BaseOpenProjectObject>> results, Cache<Boolean> anonymousAccess)
        {
            this.results = results;
            this.anonymousAccess = anonymousAccess;
        }

        void dispose()
        {
            this.results.dispose();
            this.anonymousAccess.dispose();
        }
    }
}
