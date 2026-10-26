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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
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
        when(this.cacheManager.createNewCache(any(CacheConfiguration.class))).thenReturn((Cache) this.cache);

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
        verify(this.builder).caching(this.cache, SERVER_URL);
        verify(this.builder, never()).authentication(any());
        verify(this.client).getProjects(1, 1, "");
        assertEquals(600, getCacheLifespan());
    }

    @Test
    void getClientReusesTheCacheOfTheWiki() throws Exception
    {
        this.provider.getClient(this.connection);
        this.provider.getClient(this.connection);

        verify(this.cacheManager, times(1)).createNewCache(any());
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

        assertEquals("The OpenProject connection [main] is configured to show its public data, but "
            + "[https://openproject.example.org] doesn't accept anonymous requests. Allow them in the OpenProject "
            + "administration (e.g. by disabling \"Login required\") or disable the option of the connection.",
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
    void clearCache() throws Exception
    {
        this.provider.getClient(this.connection);

        this.provider.clearCache("wiki");
        this.provider.clearCache("otherWiki");

        verify(this.cache).dispose();
        this.provider.getClient(this.connection);
        verify(this.cacheManager, times(2)).createNewCache(any());
    }

    @Test
    void dispose() throws Exception
    {
        this.provider.getClient(this.connection);

        this.provider.dispose();

        verify(this.cache).dispose();
    }

    private int getCacheLifespan() throws Exception
    {
        ArgumentCaptor<CacheConfiguration> captor = ArgumentCaptor.forClass(CacheConfiguration.class);
        verify(this.cacheManager).createNewCache(captor.capture());
        assertEquals("projectmanagement.openproject.publicdata.wiki", captor.getValue().getConfigurationId());
        return ((LRUEvictionConfiguration) captor.getValue().get(EntryEvictionConfiguration.CONFIGURATIONID))
            .getLifespan();
    }
}
