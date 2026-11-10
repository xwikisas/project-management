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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.EntryEvictionConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientFactory;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicDataClientProvider}.
 *
 * @version $Id$
 */
@ComponentTest
class PublicDataClientProviderTest
{
    private static final String SERVER_URL = "https://openproject.example.org";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private PublicDataClientProvider provider;

    @MockComponent
    private OpenProjectApiClientFactory openProjectApiClientFactory;

    @MockComponent
    private CacheManager cacheManager;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument configurationDocument;

    @Mock
    private BaseObject configuration;

    @Mock
    private OpenProjectApiClientBuilder builder;

    @Mock
    private OpenProjectApiClient client;

    @Mock
    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache;

    @Mock
    private Cache<Boolean> anonymousAccessCache;

    @Mock
    private PaginatedResult<BaseOpenProjectObject> result;

    private final Map<String, Boolean> anonymousAccessCacheEntries = new HashMap<>();

    private final OpenProjectConnection connection =
        new OpenProjectConnection("main", SERVER_URL, "clientId", "clientSecret", "instanceId");

    @BeforeEach
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void setUp() throws Exception
    {
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWikiId()).thenReturn("wiki");
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.xwiki.getDocument(any(EntityReference.class), same(this.xcontext)))
            .thenReturn(this.configurationDocument);
        when(this.configurationDocument.getXObject(any(EntityReference.class))).thenReturn(this.configuration);
        when(this.configuration.getIntValue("publicDataCacheDuration")).thenReturn(10);
        when(this.cacheManager.createNewCache(any(CacheConfiguration.class))).thenAnswer(
            invocation -> ((CacheConfiguration) invocation.getArgument(0)).getConfigurationId().contains(
                "anonymousaccess") ? this.anonymousAccessCache : this.cache);
        when(this.anonymousAccessCache.get(anyString()))
            .thenAnswer(invocation -> this.anonymousAccessCacheEntries.get(invocation.getArgument(0)));
        doAnswer(invocation -> this.anonymousAccessCacheEntries.put(invocation.getArgument(0),
            invocation.getArgument(1))).when(this.anonymousAccessCache).set(anyString(), any());

        when(this.openProjectApiClientFactory.builder()).thenReturn(this.builder);
        when(this.builder.serverUrl(any())).thenReturn(this.builder);
        when(this.builder.caching(any(), any())).thenReturn(this.builder);
        when(this.builder.build()).thenReturn(this.client);
    }

    @Test
    void getClient() throws Exception
    {
        assertSame(this.client, this.provider.getClient(this.connection));

        verify(this.builder).serverUrl(SERVER_URL);
        // The client uses the cache of the wiki.
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> clientCache = getClientCache();
        clientCache.set("key", this.result);
        verify(this.cache).set("key", this.result);
        doReturn(this.result).when(this.cache).get("key");
        assertSame(this.result, clientCache.get("key"));
        verify(this.builder, never()).authentication(any());
        verify(this.client).getProjects(1, 1, "");
        assertEquals(600, getCacheLifespan());
    }

    @Test
    void getClientReusesTheCachesOfTheWiki() throws Exception
    {
        this.provider.getClient(this.connection);
        this.provider.getClient(this.connection);

        verify(this.cacheManager, times(2)).createNewCache(any());
        // The second call doesn't check again if OpenProject accepts anonymous requests.
        verify(this.client).getProjects(1, 1, "");
    }

    @Test
    void getClientWithTheDefaultCacheDuration() throws Exception
    {
        when(this.configuration.getIntValue("publicDataCacheDuration")).thenReturn(0);

        this.provider.getClient(this.connection);

        assertEquals(PublicDataClientProvider.DEFAULT_CACHE_DURATION * 60, getCacheLifespan());
    }

    @Test
    void getClientWithoutConfiguration() throws Exception
    {
        when(this.configurationDocument.getXObject(any(EntityReference.class))).thenReturn(null);

        this.provider.getClient(this.connection);

        assertEquals(PublicDataClientProvider.DEFAULT_CACHE_DURATION * 60, getCacheLifespan());
    }

    @Test
    void getClientWhenTheConfigurationCantBeRead() throws Exception
    {
        when(this.xwiki.getDocument(any(EntityReference.class), same(this.xcontext)))
            .thenThrow(new XWikiException());

        this.provider.getClient(this.connection);

        assertEquals(PublicDataClientProvider.DEFAULT_CACHE_DURATION * 60, getCacheLifespan());
        assertTrue(this.logCapture.getMessage(0)
            .startsWith("Failed to read the duration of the cache of the OpenProject public data. Cause: "));
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 403 })
    void getClientWhenOpenProjectRejectsAnonymousRequests(int statusCode) throws Exception
    {
        when(this.client.getProjects(1, 1, "")).thenThrow(new WorkItemRetrievalException("Rejected", statusCode));

        assertNull(this.provider.getClient(this.connection));
        // The refusal is cached: OpenProject isn't asked again, and the warning isn't logged again.
        assertNull(this.provider.getClient(this.connection));

        verify(this.client).getProjects(1, 1, "");
        assertEquals(1, this.logCapture.size());
        assertEquals("The OpenProject connection [main] is configured to show its public data, but "
            + "[https://openproject.example.org] doesn't accept anonymous requests. Allow them in the OpenProject "
            + "administration (e.g. by disabling \"Authentication required\") or change the access mode of the "
            + "connection.",
            this.logCapture.getMessage(0));
    }

    @Test
    void getClientWhenTheCheckFails() throws Exception
    {
        when(this.client.getProjects(1, 1, "")).thenThrow(new WorkItemRetrievalException("Server error", 500));

        assertSame(this.client, this.provider.getClient(this.connection));

        assertEquals("Failed to check if [https://openproject.example.org] accepts anonymous requests for the "
            + "OpenProject connection [main]. Cause: [WorkItemRetrievalException: Server error].",
            this.logCapture.getMessage(0));
    }

    @Test
    void getClientWhenOpenProjectIsUnreachable() throws Exception
    {
        when(this.client.getProjects(1, 1, "")).thenThrow(new ProjectManagementException("Unreachable"));

        assertSame(this.client, this.provider.getClient(this.connection));
        assertSame(this.client, this.provider.getClient(this.connection));

        verify(this.client).getProjects(1, 1, "");
        assertEquals(1, this.logCapture.size());
        assertEquals("Failed to check if [https://openproject.example.org] accepts anonymous requests for the "
            + "OpenProject connection [main]. Cause: [ProjectManagementException: Unreachable].",
            this.logCapture.getMessage(0));
    }

    @Test
    void getClientWhenTheCacheCantBeCreated() throws Exception
    {
        when(this.cacheManager.createNewCache(any(CacheConfiguration.class))).thenThrow(new CacheException("Error"));

        assertNull(this.provider.getClient(this.connection));

        assertEquals("Failed to create the cache of the OpenProject public data for the wiki [wiki].",
            this.logCapture.getMessage(0));
    }

    @Test
    void getClientWhenTheAnonymousAccessCacheCantBeCreated() throws Exception
    {
        when(this.cacheManager.createNewCache(any(CacheConfiguration.class))).thenAnswer(invocation -> {
            if (((CacheConfiguration) invocation.getArgument(0)).getConfigurationId().contains("anonymousaccess")) {
                throw new CacheException("Error");
            }
            return this.cache;
        });

        assertNull(this.provider.getClient(this.connection));

        verify(this.cache).dispose();
        assertEquals("Failed to create the cache of the OpenProject public data for the wiki [wiki].",
            this.logCapture.getMessage(0));
    }

    @Test
    void clearCache() throws Exception
    {
        this.provider.getClient(this.connection);

        this.provider.clearCache("wiki");
        this.provider.clearCache("otherWiki");

        verify(this.cache).dispose();
        verify(this.anonymousAccessCache).dispose();
        this.provider.getClient(this.connection);
        verify(this.cacheManager, times(4)).createNewCache(any());
    }

    @Test
    void clearCacheWhileAClientUsesIt() throws Exception
    {
        // A request that got its client before the configuration changed may still use it after its cache is cleared.
        this.provider.getClient(this.connection);
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> clientCache = getClientCache();

        this.provider.clearCache("wiki");

        verify(this.cache).dispose();
        // The disposed cache isn't used anymore: the client acts as if nothing was cached.
        assertNull(clientCache.get("key"));
        clientCache.set("key", this.result);
        clientCache.remove("key");
        clientCache.removeAll();
        clientCache.dispose();
        verify(this.cache, never()).get(anyString());
        verify(this.cache, never()).set(anyString(), any());
        verify(this.cache, never()).remove(anyString());
        verify(this.cache, never()).removeAll();
        verify(this.cache).dispose();
    }

    @Test
    void clearCacheWaitsForTheCurrentUsesOfTheCache() throws Exception
    {
        this.provider.getClient(this.connection);
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> clientCache = getClientCache();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch readEnd = new CountDownLatch(1);
        doAnswer(invocation -> {
            reading.countDown();
            readEnd.await();
            return this.result;
        }).when(this.cache).get("key");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PaginatedResult<? extends BaseOpenProjectObject>> read =
                executor.submit(() -> clientCache.get("key"));
            assertTrue(reading.await(10, TimeUnit.SECONDS));
            Future<?> clear = executor.submit(() -> this.provider.clearCache("wiki"));

            // The cache isn't disposed while a request reads it.
            assertThrows(TimeoutException.class, () -> clear.get(200, TimeUnit.MILLISECONDS));
            verify(this.cache, never()).dispose();

            readEnd.countDown();
            assertSame(this.result, read.get(10, TimeUnit.SECONDS));
            clear.get(10, TimeUnit.SECONDS);
            verify(this.cache).dispose();
        } finally {
            readEnd.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void clearCacheCreatesCachesWithNewIdentifiers() throws Exception
    {
        // The new caches can be created before the old ones are disposed, so their identifiers must differ.
        this.provider.getClient(this.connection);
        this.provider.clearCache("wiki");
        this.provider.getClient(this.connection);

        ArgumentCaptor<CacheConfiguration> captor = ArgumentCaptor.forClass(CacheConfiguration.class);
        verify(this.cacheManager, times(4)).createNewCache(captor.capture());
        assertEquals(List.of("projectmanagement.openproject.publicdata.wiki.1",
            "projectmanagement.openproject.publicdata.anonymousaccess.wiki.1",
            "projectmanagement.openproject.publicdata.wiki.2",
            "projectmanagement.openproject.publicdata.anonymousaccess.wiki.2"),
            captor.getAllValues().stream().map(CacheConfiguration::getConfigurationId).collect(Collectors.toList()));
    }

    @Test
    void dispose() throws Exception
    {
        this.provider.getClient(this.connection);

        this.provider.dispose();

        verify(this.cache).dispose();
        verify(this.anonymousAccessCache).dispose();
    }

    private int getCacheLifespan() throws Exception
    {
        ArgumentCaptor<CacheConfiguration> captor = ArgumentCaptor.forClass(CacheConfiguration.class);
        verify(this.cacheManager, times(2)).createNewCache(captor.capture());
        List<CacheConfiguration> configurations = captor.getAllValues();
        assertEquals("projectmanagement.openproject.publicdata.wiki.1", configurations.get(0).getConfigurationId());
        assertEquals("projectmanagement.openproject.publicdata.anonymousaccess.wiki.1",
            configurations.get(1).getConfigurationId());
        int lifespan = getLifespan(configurations.get(0));
        // Whether OpenProject accepts anonymous requests is cached for as long as the public data.
        assertEquals(lifespan, getLifespan(configurations.get(1)));
        return lifespan;
    }

    @SuppressWarnings("unchecked")
    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> getClientCache()
    {
        ArgumentCaptor<Cache<PaginatedResult<? extends BaseOpenProjectObject>>> captor =
            ArgumentCaptor.forClass(Cache.class);
        verify(this.builder).caching(captor.capture(), eq(SERVER_URL));
        return captor.getValue();
    }

    private int getLifespan(CacheConfiguration configuration)
    {
        return ((LRUEvictionConfiguration) configuration.get(EntryEvictionConfiguration.CONFIGURATIONID))
            .getLifespan();
    }
}
