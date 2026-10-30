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

import java.util.HashMap;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
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

    private static final String OTHER_URL = "otherUrl";

    private static final String OTHER_FILTERS = "otherFilters";

    private static final String PROJECT = "project";

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

    /**
     * For each cached method: a request, followed by requests differing from it by a single parameter, which must not
     * share its cache entry.
     */
    private static Stream<Arguments> cachedCalls()
    {
        return Stream.of(
            cachedCall("getWorkPackages", c -> c.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING),
                c -> c.getWorkPackages(2, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING),
                c -> c.getWorkPackages(OFFSET, 20, FILTERS_STRING, SORT_BY_STRING),
                c -> c.getWorkPackages(OFFSET, PAGE_SIZE, OTHER_FILTERS, SORT_BY_STRING),
                c -> c.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, "otherSortBy"),
                c -> c.getProjectWorkPackages(PROJECT, OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING)),
            cachedCall("getProjectWorkPackages",
                c -> c.getProjectWorkPackages(PROJECT, OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING),
                c -> c.getProjectWorkPackages("otherProject", OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING),
                c -> c.getProjectWorkPackages(PROJECT, 2, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING),
                c -> c.getProjectWorkPackages(PROJECT, OFFSET, 20, FILTERS_STRING, SORT_BY_STRING),
                c -> c.getProjectWorkPackages(PROJECT, OFFSET, PAGE_SIZE, OTHER_FILTERS, SORT_BY_STRING),
                c -> c.getProjectWorkPackages(PROJECT, OFFSET, PAGE_SIZE, FILTERS_STRING, "otherSortBy")),
            cachedCall("getQueryWorkPackages", c -> c.getQueryWorkPackages("27", OFFSET, PAGE_SIZE, SORT_BY_STRING),
                c -> c.getQueryWorkPackages("28", OFFSET, PAGE_SIZE, SORT_BY_STRING),
                c -> c.getQueryWorkPackages("27", 2, PAGE_SIZE, SORT_BY_STRING),
                c -> c.getQueryWorkPackages("27", OFFSET, 20, SORT_BY_STRING),
                c -> c.getQueryWorkPackages("27", OFFSET, PAGE_SIZE, "otherSortBy")),
            cachedCall("getUsers", c -> c.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getUsers(2, PAGE_SIZE, FILTERS_STRING),
                c -> c.getUsers(OFFSET, 20, FILTERS_STRING),
                c -> c.getUsers(OFFSET, PAGE_SIZE, OTHER_FILTERS)),
            cachedCall("getAvailableUsers", c -> c.getAvailableUsers(URL, OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getAvailableUsers(OTHER_URL, OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getAvailableUsers(URL, 2, PAGE_SIZE, FILTERS_STRING),
                c -> c.getAvailableUsers(URL, OFFSET, 20, FILTERS_STRING),
                c -> c.getAvailableUsers(URL, OFFSET, PAGE_SIZE, OTHER_FILTERS),
                c -> c.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getProjects", c -> c.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getProjects(2, PAGE_SIZE, FILTERS_STRING),
                c -> c.getProjects(OFFSET, 20, FILTERS_STRING),
                c -> c.getProjects(OFFSET, PAGE_SIZE, OTHER_FILTERS)),
            cachedCall("getAvailableProjects", c -> c.getAvailableProjects(URL, OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getAvailableProjects(OTHER_URL, OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getAvailableProjects(URL, 2, PAGE_SIZE, FILTERS_STRING),
                c -> c.getAvailableProjects(URL, OFFSET, 20, FILTERS_STRING),
                c -> c.getAvailableProjects(URL, OFFSET, PAGE_SIZE, OTHER_FILTERS),
                c -> c.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getNews", c -> c.getNews(OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getNews(2, PAGE_SIZE, FILTERS_STRING),
                c -> c.getNews(OFFSET, 20, FILTERS_STRING),
                c -> c.getNews(OFFSET, PAGE_SIZE, OTHER_FILTERS)),
            cachedCall("getMemberships", c -> c.getMemberships(OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getMemberships(2, PAGE_SIZE, FILTERS_STRING),
                c -> c.getMemberships(OFFSET, 20, FILTERS_STRING),
                c -> c.getMemberships(OFFSET, PAGE_SIZE, OTHER_FILTERS),
                c -> c.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getSprints", c -> c.getSprints(OFFSET, PAGE_SIZE, FILTERS_STRING),
                c -> c.getSprints(2, PAGE_SIZE, FILTERS_STRING),
                c -> c.getSprints(OFFSET, 20, FILTERS_STRING),
                c -> c.getSprints(OFFSET, PAGE_SIZE, OTHER_FILTERS)),
            cachedCall("getProjectSprints", c -> c.getProjectSprints(OFFSET, PAGE_SIZE, FILTERS_STRING, 1),
                c -> c.getProjectSprints(OFFSET, PAGE_SIZE, FILTERS_STRING, 2),
                c -> c.getProjectSprints(2, PAGE_SIZE, FILTERS_STRING, 1),
                c -> c.getProjectSprints(OFFSET, 20, FILTERS_STRING, 1),
                c -> c.getProjectSprints(OFFSET, PAGE_SIZE, OTHER_FILTERS, 1),
                c -> c.getSprints(OFFSET, PAGE_SIZE, FILTERS_STRING)),
            cachedCall("getVersions", OpenProjectApiClient::getVersions),
            cachedCall("getProjectVersions", c -> c.getProjectVersions(1),
                c -> c.getProjectVersions(2),
                OpenProjectApiClient::getVersions),
            cachedCall("getTypes", OpenProjectApiClient::getTypes),
            cachedCall("getStatuses", OpenProjectApiClient::getStatuses),
            cachedCall("getPriorities", OpenProjectApiClient::getPriorities),
            cachedCall("getWorkPackageFields", OpenProjectApiClient::getWorkPackageFields)
        );
    }

    private static Arguments cachedCall(String name, CachedCall call, CachedCall... otherCalls)
    {
        return Arguments.of(Named.of(name, call), otherCalls);
    }

    @ParameterizedTest
    @MethodSource("cachedCalls")
    void resultIsCachedPerRequest(CachedCall call, CachedCall[] otherCalls) throws ProjectManagementException
    {
        // OpenProject returns a new result for each request.
        OpenProjectApiClient backend = mock(OpenProjectApiClient.class, invocation -> new PaginatedResult<>());
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> mapBackedCache = mock(Cache.class);
        useMapBackedCache(mapBackedCache);
        CachingOpenProjectApiClient cachingClient = new CachingOpenProjectApiClient(backend, CLIENT_ID, mapBackedCache);

        PaginatedResult<? extends BaseOpenProjectObject> result = call.call(cachingClient);

        // The same request is served from the cache, without reaching OpenProject.
        assertSame(result, call.call(cachingClient));
        assertEquals(1, mockingDetails(backend).getInvocations().size());
        // A request that differs by any parameter gets its own result.
        for (int i = 0; i < otherCalls.length; i++) {
            assertNotSame(result, otherCalls[i].call(cachingClient),
                String.format("The other request at index [%d] shares the cache entry of the first one.", i));
        }
        assertEquals(1 + otherCalls.length, mockingDetails(backend).getInvocations().size());
    }

    @Test
    void createWorkPackageIsNotCached() throws ProjectManagementException, JsonProcessingException
    {
        useMapBackedCache(cache);
        String jsonBody = "{}";
        JsonNode firstNode = mapper.readTree("{\"id\":1}");
        JsonNode secondNode = mapper.readTree("{\"id\":2}");
        when(openProjectApiClient.createWorkPackage(URL, jsonBody)).thenReturn(firstNode, secondNode);

        // Each call creates a work package.
        assertEquals(firstNode, cachingOpenProjectApiClient.createWorkPackage(URL, jsonBody));
        assertEquals(secondNode, cachingOpenProjectApiClient.createWorkPackage(URL, jsonBody));
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
