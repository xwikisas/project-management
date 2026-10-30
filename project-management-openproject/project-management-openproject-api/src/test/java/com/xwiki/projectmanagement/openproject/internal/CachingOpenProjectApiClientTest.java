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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.cache.Cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CachingOpenProjectApiClient}.
 *
 * @version $Id$
 */
@SuppressWarnings("unchecked")
class CachingOpenProjectApiClientTest
{
    private CachingOpenProjectApiClient cachingOpenProjectApiClient;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache;

    private OpenProjectApiClient openProjectApiClient;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String FILTERS_STRING = "generatedFiltersString";

    private static final String SORT_BY_STRING = "generatedSortByString";

    private static final String CLIENT_ID = "clientId";

    private static final String URL = "url";

    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp()
    {
        openProjectApiClient = mock(OpenProjectApiClient.class);
        cache = mock(Cache.class);
        cachingOpenProjectApiClient = new CachingOpenProjectApiClient(openProjectApiClient, CLIENT_ID, cache);
    }

    @FunctionalInterface
    private interface CachedCall
    {
        PaginatedResult<? extends BaseOpenProjectObject> call(OpenProjectApiClient client)
            throws ProjectManagementException;
    }

    private static Stream<Arguments> cachedCalls()
    {
        return Stream.of(
            cachedCall("getWorkPackages", c -> c.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING)),
            cachedCall("getProjectWorkPackages",
                c -> c.getProjectWorkPackages("projectName", OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING)),
            cachedCall("getUsers", c -> c.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getAvailableUsers", c -> c.getAvailableUsers(URL, OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getProjects", c -> c.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getAvailableProjects", c -> c.getAvailableProjects(URL, OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getTypes", OpenProjectApiClient::getTypes),
            cachedCall("getStatuses", OpenProjectApiClient::getStatuses),
            cachedCall("getPriorities", OpenProjectApiClient::getPriorities)
        );
    }

    private static Arguments cachedCall(String name, CachedCall call)
    {
        return Arguments.of(Named.of(name, call));
    }

    @ParameterizedTest
    @MethodSource("cachedCalls")
    void resultIsServedFromCacheOrFetchedAndCached(CachedCall call) throws ProjectManagementException
    {
        PaginatedResult<? extends BaseOpenProjectObject> expectedResult =
            new PaginatedResult<>(Collections.emptyList(), OFFSET, PAGE_SIZE, 0);

        call.call(doReturn(expectedResult).when(openProjectApiClient));

        doReturn(expectedResult).when(cache).get(anyString());

        PaginatedResult<? extends BaseOpenProjectObject> result = call.call(cachingOpenProjectApiClient);

        assertEquals(expectedResult, result);

        call.call(verify(openProjectApiClient, times(0)));

        when(cache.get(anyString())).thenReturn(null);

        result = call.call(cachingOpenProjectApiClient);

        assertEquals(expectedResult, result);
        verify(cache).set(anyString(), eq(result));
        call.call(verify(openProjectApiClient, times(1)));
    }

    @Test
    void createWorkPackageIsNotCached() throws ProjectManagementException, JsonProcessingException
    {
        String jsonBody = "{}";
        JsonNode expectedNode = mapper.readTree("{}");

        when(openProjectApiClient.createWorkPackage(URL, jsonBody))
            .thenReturn(expectedNode);

        JsonNode result = cachingOpenProjectApiClient
            .createWorkPackage(URL, jsonBody);

        assertEquals(expectedNode, result);

        verify(openProjectApiClient, times(1))
            .createWorkPackage(URL, jsonBody);

        verify(cache, times(0)).get(anyString());
        verify(cache, times(0)).set(anyString(), any());
    }

    @Test
    void queryWorkPackagesResultIsCached() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> expectedResult =
            new PaginatedResult<>(Collections.emptyList(), OFFSET, PAGE_SIZE, 0);
        when(openProjectApiClient.getQueryWorkPackages("27", OFFSET, PAGE_SIZE, SORT_BY_STRING))
            .thenReturn(expectedResult);

        assertEquals(expectedResult,
            cachingOpenProjectApiClient.getQueryWorkPackages("27", OFFSET, PAGE_SIZE, SORT_BY_STRING));

        verify(cache).set(anyString(), eq(expectedResult));
        verify(openProjectApiClient).getQueryWorkPackages("27", OFFSET, PAGE_SIZE, SORT_BY_STRING);
    }

    @Test
    void workPackageCaching() throws ProjectManagementException
    {
        useMapBackedCache(cache);
        WorkPackage workPackage = new WorkPackage();
        when(openProjectApiClient.getWorkPackage("XWIKI-123")).thenReturn(workPackage);

        assertSame(workPackage, cachingOpenProjectApiClient.getWorkPackage("XWIKI-123"));
        assertSame(workPackage, cachingOpenProjectApiClient.getWorkPackage("XWIKI-123"));
        // Missing work packages are cached too.
        assertNull(cachingOpenProjectApiClient.getWorkPackage("XWIKI-456"));
        assertNull(cachingOpenProjectApiClient.getWorkPackage("XWIKI-456"));

        verify(openProjectApiClient).getWorkPackage("XWIKI-123");
        verify(openProjectApiClient).getWorkPackage("XWIKI-456");
    }

    @Test
    void cachedResultsDependOnTheRequestParameters() throws ProjectManagementException
    {
        Map<String, PaginatedResult<? extends BaseOpenProjectObject>> cacheContent = useMapBackedCache(cache);
        when(openProjectApiClient.getWorkPackages(any(), any(), any(), any()))
            .thenAnswer(invocation -> new PaginatedResult<WorkPackage>());
        when(openProjectApiClient.getProjectWorkPackages(any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> new PaginatedResult<WorkPackage>());

        PaginatedResult<WorkPackage> result =
            cachingOpenProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);
        // The same request is served from the cache.
        assertSame(result,
            cachingOpenProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING));
        // Any other parameter value makes a different request.
        assertNotSame(result, cachingOpenProjectApiClient.getWorkPackages(2, PAGE_SIZE, FILTERS_STRING,
            SORT_BY_STRING));
        assertNotSame(result, cachingOpenProjectApiClient.getWorkPackages(OFFSET, 20, FILTERS_STRING,
            SORT_BY_STRING));
        assertNotSame(result, cachingOpenProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "otherFilters",
            SORT_BY_STRING));
        assertNotSame(result, cachingOpenProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING,
            "otherSortBy"));
        assertNotSame(result, cachingOpenProjectApiClient.getProjectWorkPackages("project", OFFSET, PAGE_SIZE,
            FILTERS_STRING, SORT_BY_STRING));

        verify(openProjectApiClient, times(5)).getWorkPackages(any(), any(), any(), any());
        assertEquals(6, cacheContent.size());
    }

    @Test
    void cachedResultsAreNotSharedBetweenClients() throws ProjectManagementException
    {
        useMapBackedCache(cache);
        OpenProjectApiClient otherApiClient = mock(OpenProjectApiClient.class);
        CachingOpenProjectApiClient otherCachingClient =
            new CachingOpenProjectApiClient(otherApiClient, "otherClientId", cache);
        PaginatedResult<WorkPackage> firstResult = new PaginatedResult<>();
        PaginatedResult<WorkPackage> otherResult = new PaginatedResult<>();
        when(openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING))
            .thenReturn(firstResult);
        when(otherApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING))
            .thenReturn(otherResult);

        assertSame(firstResult,
            cachingOpenProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING));
        assertSame(otherResult,
            otherCachingClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING));
    }

    @Test
    void workPackageFieldsAreCached() throws ProjectManagementException
    {
        useMapBackedCache(cache);
        PaginatedResult<WorkPackageField> expectedResult =
            new PaginatedResult<>(List.of(new WorkPackageField()), 0, 1, 1);
        when(openProjectApiClient.getWorkPackageFields()).thenReturn(expectedResult);

        assertSame(expectedResult, cachingOpenProjectApiClient.getWorkPackageFields());
        assertSame(expectedResult, cachingOpenProjectApiClient.getWorkPackageFields());

        verify(openProjectApiClient, times(1)).getWorkPackageFields();
    }

    private static Map<String, PaginatedResult<? extends BaseOpenProjectObject>> useMapBackedCache(
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache)
    {
        Map<String, PaginatedResult<? extends BaseOpenProjectObject>> content = new HashMap<>();
        when(cache.get(anyString())).thenAnswer(invocation -> content.get(invocation.<String>getArgument(0)));
        doAnswer(invocation -> content.put(invocation.getArgument(0), invocation.getArgument(1))).when(cache)
            .set(anyString(), any());
        return content;
    }
}
