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
package com.xwiki.projectmanagement.openproject.internal.processing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.EntryEvictionConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkPackageFieldsCache}.
 *
 * @version $Id$
 */
@ComponentTest
class WorkPackageFieldsCacheTest
{
    private static final String CONNECTION = "myConnection";

    private static final DocumentReference ALICE = new DocumentReference("wiki", "XWiki", "Alice");

    private static final DocumentReference BOB = new DocumentReference("wiki", "XWiki", "Bob");

    @InjectMockComponents
    private WorkPackageFieldsCache fieldsCache;

    @MockComponent
    private CacheManager cacheManager;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    private final XWikiContext xcontext = mock(XWikiContext.class);

    private final List<CacheConfiguration> cacheConfigurations = new ArrayList<>();

    private final OpenProjectApiClient client = mock(OpenProjectApiClient.class);

    @BeforeComponent
    void configure() throws Exception
    {
        when(this.cacheManager.createNewCache(any(CacheConfiguration.class))).thenAnswer(invocation -> {
            this.cacheConfigurations.add(invocation.getArgument(0));
            return mapCache();
        });
    }

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWikiId()).thenReturn("wiki");
        when(this.xcontext.getUserReference()).thenReturn(ALICE);
        when(this.client.getWorkPackageFields()).thenAnswer(invocation -> fields(
            field("customField1", "[1]String"), field("targetVersions", "[]Version"), field("customField5", "[]Option")));
    }

    @Test
    void cachesExpire()
    {
        assertEquals(2, this.cacheConfigurations.size());
        LRUEvictionConfiguration eviction = (LRUEvictionConfiguration) this.cacheConfigurations.get(0)
            .get(EntryEvictionConfiguration.CONFIGURATIONID);
        assertEquals(WorkPackageFieldsCache.LIFESPAN, eviction.getLifespan());
    }

    @Test
    void getFieldsIsCachedPerUser() throws Exception
    {
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.client);

        OpenProjectWorkItemFields fields = this.fieldsCache.getFields(CONNECTION, access);

        assertEquals(Set.of("customField1", "targetVersions", "customField5"), keys(fields));
        assertSame(fields, this.fieldsCache.getFields(CONNECTION, access));
        verify(this.client).getWorkPackageFields();

        // Another user may see other custom fields.
        when(this.xcontext.getUserReference()).thenReturn(BOB);
        this.fieldsCache.getFields(CONNECTION, access);
        verify(this.client, times(2)).getWorkPackageFields();

        // Another connection may point to another instance.
        this.fieldsCache.getFields("otherConnection", access);
        verify(this.client, times(3)).getWorkPackageFields();
    }

    @Test
    void getFieldsIsSharedByTheUsersWithoutAccount() throws Exception
    {
        OpenProjectAccessResult access = OpenProjectAccessResult.anonymous(this.client);

        OpenProjectWorkItemFields fields = this.fieldsCache.getFields(CONNECTION, access);
        when(this.xcontext.getUserReference()).thenReturn(null);

        assertSame(fields, this.fieldsCache.getFields(CONNECTION, access));
        verify(this.client).getWorkPackageFields();
    }

    @Test
    void getFieldsSharesTheRequestOfConcurrentCalls() throws Exception
    {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch responseReceived = new CountDownLatch(1);
        when(this.client.getWorkPackageFields()).thenAnswer(invocation -> {
            requestStarted.countDown();
            responseReceived.await(10, TimeUnit.SECONDS);
            return fields(field("customField1", "[1]String"));
        });
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.client);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CompletableFuture<OpenProjectWorkItemFields> first = CompletableFuture.supplyAsync(() -> get(access), executor);
        assertTrue(requestStarted.await(10, TimeUnit.SECONDS));
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        CompletableFuture<OpenProjectWorkItemFields> second = CompletableFuture.supplyAsync(() -> {
            secondThread.set(Thread.currentThread());
            return get(access);
        }, executor);
        // Wait for the second call to wait for the request of the first one.
        long deadline = System.currentTimeMillis() + 10_000;
        while ((secondThread.get() == null || secondThread.get().getState() != Thread.State.WAITING)
            && System.currentTimeMillis() < deadline)
        {
            Thread.onSpinWait();
        }
        responseReceived.countDown();

        assertSame(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        verify(this.client).getWorkPackageFields();
        executor.shutdown();
    }

    @Test
    void getFieldsDoesntCacheFailures() throws Exception
    {
        when(this.client.getWorkPackageFields()).thenThrow(new ProjectManagementException("Unavailable"))
            .thenAnswer(invocation -> fields(field("customField1", "[1]String")));
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.client);

        ProjectManagementException exception =
            assertThrows(ProjectManagementException.class, () -> this.fieldsCache.getFields(CONNECTION, access));
        assertEquals("Unavailable", exception.getMessage());

        assertEquals(Set.of("customField1"), keys(this.fieldsCache.getFields(CONNECTION, access)));
    }

    @Test
    void getMultiValueFieldsIsCachedPerConnection() throws Exception
    {
        when(this.client.getMultiValueWorkPackageFields(Set.of("targetVersions", "customField5")))
            .thenReturn(Map.of("targetVersions", true));
        OpenProjectWorkItemFields fields =
            this.fieldsCache.getFields(CONNECTION, OpenProjectAccessResult.authenticated(this.client));

        // Only the list fields can be multi-value. The fields missing from the schemas are single-value.
        assertEquals(Set.of("targetVersions"), this.fieldsCache.getMultiValueFields(CONNECTION, this.client, fields));

        // The schemas are the same for all the users.
        when(this.xcontext.getUserReference()).thenReturn(BOB);
        OpenProjectApiClient otherClient = mock(OpenProjectApiClient.class);
        assertEquals(Set.of("targetVersions"), this.fieldsCache.getMultiValueFields(CONNECTION, otherClient, fields));
        verify(otherClient, never()).getMultiValueWorkPackageFields(any());
        verify(this.client).getMultiValueWorkPackageFields(any());
    }

    @Test
    void getMultiValueFieldsLooksUpOnlyTheNewFields() throws Exception
    {
        when(this.client.getMultiValueWorkPackageFields(any())).thenReturn(Map.of("targetVersions", true));
        this.fieldsCache.getMultiValueFields(CONNECTION, this.client,
            new OpenProjectWorkItemFields(fields(field("targetVersions", "[]Version")).getItems()));

        // E.g. a custom field that the user who looked up the fields first couldn't see.
        when(this.client.getMultiValueWorkPackageFields(Set.of("customField9"))).thenReturn(Map.of("customField9",
            true));
        Set<String> multiValueFields = this.fieldsCache.getMultiValueFields(CONNECTION, this.client,
            new OpenProjectWorkItemFields(
                fields(field("targetVersions", "[]Version"), field("customField9", "[]Option")).getItems()));

        assertEquals(Set.of("targetVersions", "customField9"), multiValueFields);
        verify(this.client).getMultiValueWorkPackageFields(Set.of("targetVersions"));
        verify(this.client).getMultiValueWorkPackageFields(Set.of("customField9"));
    }

    @Test
    void getMultiValueFieldsWithoutListFields() throws Exception
    {
        assertEquals(Set.of(), this.fieldsCache.getMultiValueFields(CONNECTION, this.client,
            new OpenProjectWorkItemFields(fields(field("customField1", "[1]String")).getItems())));

        verify(this.client, never()).getMultiValueWorkPackageFields(any());
    }

    @Test
    void clear() throws Exception
    {
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.client);
        when(this.client.getMultiValueWorkPackageFields(any())).thenReturn(Map.of());
        OpenProjectWorkItemFields fields = this.fieldsCache.getFields(CONNECTION, access);
        this.fieldsCache.getMultiValueFields(CONNECTION, this.client, fields);

        this.fieldsCache.clear();

        this.fieldsCache.getMultiValueFields(CONNECTION, this.client, this.fieldsCache.getFields(CONNECTION, access));
        verify(this.client, times(2)).getWorkPackageFields();
        verify(this.client, times(2)).getMultiValueWorkPackageFields(any());
    }

    private OpenProjectWorkItemFields get(OpenProjectAccessResult access)
    {
        try {
            return this.fieldsCache.getFields(CONNECTION, access);
        } catch (ProjectManagementException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> keys(OpenProjectWorkItemFields fields)
    {
        Set<String> keys = new HashSet<>();
        fields.getFields().values().forEach(field -> keys.add(field.getKey()));
        return keys;
    }

    private static PaginatedResult<WorkPackageField> fields(WorkPackageField... fields)
    {
        return new PaginatedResult<>(List.of(fields), 0, fields.length, fields.length);
    }

    private static WorkPackageField field(String key, String filterValueType)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(key);
        field.setFilterKey(key);
        field.setFilterValueType(filterValueType);
        field.setFilterOperators(List.of("="));
        return field;
    }

    @SuppressWarnings("unchecked")
    private static <T> Cache<T> mapCache()
    {
        Map<String, T> entries = new ConcurrentHashMap<>();
        Cache<T> cache = mock(Cache.class);
        when(cache.get(anyString())).thenAnswer(invocation -> entries.get(invocation.<String>getArgument(0)));
        doAnswer(invocation -> entries.put(invocation.getArgument(0), invocation.getArgument(1))).when(cache)
            .set(anyString(), any());
        doAnswer(invocation -> {
            entries.clear();
            return null;
        }).when(cache).removeAll();
        return cache;
    }
}
