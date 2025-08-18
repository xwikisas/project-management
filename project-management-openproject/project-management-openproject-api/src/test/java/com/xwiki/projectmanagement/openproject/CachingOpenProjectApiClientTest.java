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

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xwiki.cache.Cache;
import org.xwiki.test.junit5.mockito.ComponentTest;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.CachingOpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class CachingOpenProjectApiClientTest
{
    @Mock
    Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache;

    @Mock
    OpenProjectConnection instance;

    @Mock
    private OpenProjectApiClient openProjectApiClient;

    @InjectMocks
    private CachingOpenProjectApiClient cachingOpenProjectApiClient;

    private AutoCloseable closeable;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String FILTERS_STRING = "generatedFiltersString";

    private static final String SORT_BY_STRING = "generatedSortByString";

    private static final String CLIENT_ID = "clientId";

    @BeforeEach
    public void setUp()
    {
        closeable = MockitoAnnotations.openMocks(this);

        when(instance.getClientId()).thenReturn(CLIENT_ID);
        cachingOpenProjectApiClient = new CachingOpenProjectApiClient(openProjectApiClient, instance, "token", cache);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        closeable.close();
    }

    @Test
    public void workPackagesCachingTest() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> expectedResult =
            new PaginatedResult<>(Collections.emptyList(), OFFSET, PAGE_SIZE, 0);

        when(
            openProjectApiClient.getWorkPackages(
                OFFSET,
                PAGE_SIZE,
                FILTERS_STRING,
                SORT_BY_STRING
            )
        )
            .thenReturn(expectedResult);

        String cacheKey = getCacheKey(CLIENT_ID, "workItems", OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);

        when((PaginatedResult<WorkPackage>) cache.get(cacheKey)).thenReturn(expectedResult);

        PaginatedResult<WorkPackage> result = cachingOpenProjectApiClient
            .getWorkPackages(
                OFFSET,
                PAGE_SIZE,
                FILTERS_STRING,
                SORT_BY_STRING
            );

        assertEquals(expectedResult, result);

        verify(openProjectApiClient, times(0))
            .getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);

        when(cache.get(cacheKey)).thenReturn(null);

        result = cachingOpenProjectApiClient.getWorkPackages(
            OFFSET,
            PAGE_SIZE,
            FILTERS_STRING,
            SORT_BY_STRING
        );

        assertEquals(expectedResult, result);
        verify(cache).set(cacheKey, result);

        verify(openProjectApiClient, times(1))
            .getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);
    }

    @Test
    public void projectWorkPackagesCachingTest() throws ProjectManagementException
    {
        String projectName = "projectName";
        PaginatedResult<WorkPackage> expectedResult =
            new PaginatedResult<>(Collections.emptyList(), OFFSET, PAGE_SIZE, 0);

        when(
            openProjectApiClient.getProjectWorkPackages(
                projectName,
                OFFSET,
                PAGE_SIZE,
                FILTERS_STRING,
                SORT_BY_STRING
            )
        )
            .thenReturn(expectedResult);

        String cacheKey = getCacheKey(
            CLIENT_ID,
            String.format("project%sWorkItems", projectName),
            OFFSET,
            PAGE_SIZE,
            FILTERS_STRING,
            SORT_BY_STRING
        );

        when((PaginatedResult<WorkPackage>) cache.get(cacheKey)).thenReturn(expectedResult);

        PaginatedResult<WorkPackage> result = cachingOpenProjectApiClient.getProjectWorkPackages(
            projectName,
            OFFSET,
            PAGE_SIZE,
            FILTERS_STRING,
            SORT_BY_STRING
        );

        assertEquals(expectedResult, result);

        verify(openProjectApiClient, times(0))
            .getProjectWorkPackages(
                projectName,
                OFFSET,
                PAGE_SIZE,
                FILTERS_STRING,
                SORT_BY_STRING
            );

        when(cache.get(cacheKey)).thenReturn(null);

        result = cachingOpenProjectApiClient.getProjectWorkPackages(
            projectName,
            OFFSET,
            PAGE_SIZE,
            FILTERS_STRING,
            SORT_BY_STRING
        );

        assertEquals(expectedResult, result);

        verify(cache).set(cacheKey, result);

        verify(openProjectApiClient, times(1))
            .getProjectWorkPackages(
                projectName,
                OFFSET,
                PAGE_SIZE,
                FILTERS_STRING,
                SORT_BY_STRING
            );
    }

    @Test
    public void usersCachingTest() throws ProjectManagementException
    {
        PaginatedResult<User> expectedResult = new PaginatedResult<>(Collections.emptyList(),
            OFFSET, PAGE_SIZE, 0);

        when(openProjectApiClient.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING)).thenReturn(expectedResult);

        String cacheKey = getCacheKey(CLIENT_ID, "users", OFFSET, PAGE_SIZE, FILTERS_STRING, "");

        when((PaginatedResult<User>) cache.get(cacheKey)).thenReturn(expectedResult);

        PaginatedResult<User> result = cachingOpenProjectApiClient.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(expectedResult, result);

        verify(openProjectApiClient, times(0))
            .getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING);

        when(cache.get(cacheKey)).thenReturn(null);

        result = cachingOpenProjectApiClient.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(expectedResult, result);
        verify(cache).set(cacheKey, result);

        verify(openProjectApiClient, times(1))
            .getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING);
    }

    @Test
    public void projectsCachingTest() throws ProjectManagementException
    {
        PaginatedResult<Project> expectedResult = new PaginatedResult<>(Collections.emptyList(), OFFSET, PAGE_SIZE, 0);

        when(openProjectApiClient.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING)).thenReturn(expectedResult);

        String cacheKey = getCacheKey(CLIENT_ID, "projects", OFFSET, PAGE_SIZE, FILTERS_STRING, "");

        when((PaginatedResult<Project>) cache.get(cacheKey)).thenReturn(expectedResult);

        PaginatedResult<Project> result = cachingOpenProjectApiClient.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(expectedResult, result);

        verify(openProjectApiClient, times(0))
            .getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING);

        when(cache.get(cacheKey)).thenReturn(null);

        result = cachingOpenProjectApiClient.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(expectedResult, result);
        verify(cache).set(cacheKey, result);

        verify(openProjectApiClient, times(1))
            .getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING);
    }

    @Test
    public void typesCachingTest() throws ProjectManagementException
    {
        PaginatedResult<Type> expectedResult = new PaginatedResult<>(Collections.emptyList(),
            OFFSET, PAGE_SIZE, 0);

        when(openProjectApiClient.getTypes()).thenReturn(expectedResult);

        String cacheKey = getCacheKey(CLIENT_ID, "types", 1, Integer.MAX_VALUE, "", "");

        when((PaginatedResult<Type>) cache.get(cacheKey)).thenReturn(expectedResult);

        PaginatedResult<Type> result = cachingOpenProjectApiClient.getTypes();

        assertEquals(expectedResult, result);

        verify(openProjectApiClient, times(0))
            .getTypes();

        when(cache.get(cacheKey)).thenReturn(null);

        result = cachingOpenProjectApiClient.getTypes();

        assertEquals(expectedResult, result);
        verify(cache).set(cacheKey, result);
        verify(openProjectApiClient, times(1)).getTypes();
    }

    @Test
    public void statusesCachingTest() throws ProjectManagementException
    {
        PaginatedResult<Status> expectedResult = new PaginatedResult<>(Collections.emptyList(),
            OFFSET, PAGE_SIZE, 0);

        when(openProjectApiClient.getStatuses()).thenReturn(expectedResult);

        String cacheKey = getCacheKey(CLIENT_ID, "statuses", 1, Integer.MAX_VALUE, "", "");

        when((PaginatedResult<Status>) cache.get(cacheKey)).thenReturn(expectedResult);

        PaginatedResult<Status> result = cachingOpenProjectApiClient.getStatuses();

        assertEquals(expectedResult, result);

        verify(openProjectApiClient, times(0))
            .getStatuses();

        when(cache.get(cacheKey)).thenReturn(null);

        result = cachingOpenProjectApiClient.getStatuses();

        assertEquals(expectedResult, result);
        verify(cache).set(cacheKey, result);
        verify(openProjectApiClient, times(1)).getStatuses();
    }

    @Test
    public void prioritiesCachingTest() throws ProjectManagementException
    {
        PaginatedResult<Priority> expectedResult = new PaginatedResult<>(Collections.emptyList(),
            OFFSET, PAGE_SIZE, 0);

        when(openProjectApiClient.getPriorities()).thenReturn(expectedResult);

        String cacheKey = getCacheKey(CLIENT_ID, "priorities", 1, Integer.MAX_VALUE, "", "");

        when((PaginatedResult<Priority>) cache.get(cacheKey)).thenReturn(expectedResult);

        PaginatedResult<Priority> result = cachingOpenProjectApiClient.getPriorities();

        assertEquals(expectedResult, result);

        verify(openProjectApiClient, times(0))
            .getPriorities();

        when(cache.get(cacheKey)).thenReturn(null);

        result = cachingOpenProjectApiClient.getPriorities();

        assertEquals(expectedResult, result);
        verify(cache).set(cacheKey, result);
        verify(openProjectApiClient, times(1)).getPriorities();
    }

    private String getCacheKey(String clientId, String entity, int offset, int pageSize, String filters, String sortBy)
    {
        return String.format("%s/%s/%d/%d/%s/%s", clientId, entity, offset, pageSize, filters, sortBy);
    }
}
