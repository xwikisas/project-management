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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.exception.WorkPackageRetrievalBadRequestException;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectClient;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterHandler;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectSortingHandler;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ComponentTest
@ComponentList(OpenProjectIdentifierResolver.class)
public class OpenProjectClientTest
{
    private static final String QUERY_ID = "27";

    private static final String SAVED_QUERY_IDENTIFIER = "http://open-project-instance/work_packages?query_id=27";

    private static final String SAVED_QUERY_RESULTS_URL = "/api/v3/projects/1/work_packages?offset=1"
        + "&filters=%5B%7B%22status%22%3A%7B%22operator%22%3A%22o%22%2C%22values%22%3A%5B%5D%7D%7D%5D"
        + "&sortBy=%5B%5B%22id%22%2C%22desc%22%5D%5D&pageSize=20";

    private static final String SAVED_QUERY_SORT_BY = "[[\"id\",\"desc\"]]";

    private static final String MERGED_FILTERS = "[{\"status\":{\"operator\":\"o\",\"values\":[]}},"
        + "{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}}]";

    @InjectMockComponents
    private OpenProjectClient openProjectClient;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private ProjectManagementClientExecutionContext executionContext;

    @MockComponent
    private OpenProjectApiClient openProjectApiClient;

    @MockComponent
    private Logger logger;

    private static final Integer NUMBER_OF_WORK_PACKAGES = 10;

    @BeforeEach
    public void setUp() throws ProjectManagementException
    {
        ReflectionUtils.setFieldValue(this.openProjectClient, "logger", this.logger);

        when(executionContext.get("instance")).thenReturn("");
        when(openProjectConfiguration.getOpenProjectApiClient(any())).thenReturn(this.openProjectApiClient);
        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenReturn(
            generateWorkItems());
        when(openProjectApiClient.getProjectWorkPackages(anyString(), anyInt(), anyInt(), anyString(),
            anyString())).thenReturn(generateWorkItems());
        when(openProjectApiClient.getQueryWorkPackages(anyString(), anyInt(), anyInt(), anyString())).thenReturn(
            generateWorkItems());
    }

    @Test
    void getWorkItemsWithSavedQueryAndNoLivedataFilters() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn(SAVED_QUERY_RESULTS_URL);

        PaginatedResult<WorkItem> result = openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        // The query applies its own filters and sorting.
        verify(openProjectApiClient).getQueryWorkPackages(QUERY_ID, 1, 10, "");
        verify(openProjectApiClient, never()).getQueryResultsUrl(anyString());
        verify(openProjectApiClient, never()).getWorkPackages(anyInt(), anyInt(), anyString(), anyString());
        assertEquals(NUMBER_OF_WORK_PACKAGES, result.getItems().size());
    }

    @Test
    void getWorkItemsWithSavedQueryAndLivedataSortingButNoFilters() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(SAVED_QUERY_IDENTIFIER);

        openProjectClient.getWorkItems(0, 10, List.of(), List.of(new LiveDataQuery.SortEntry("summary", false)));

        verify(openProjectApiClient).getQueryWorkPackages(QUERY_ID, 1, 10, "[[\"subject\",\"asc\"]]");
        verify(openProjectApiClient, never()).getQueryResultsUrl(anyString());
    }

    @Test
    void getWorkItemsWithSavedQueryAndStoredResultsUrl() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn(SAVED_QUERY_RESULTS_URL);

        PaginatedResult<WorkItem> result =
            openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        // The results link was retrieved before the macro execution, so the query isn't read again.
        verify(openProjectApiClient, never()).getQueryResultsUrl(anyString());
        verify(openProjectApiClient, never()).getQueryWorkPackages(anyString(), anyInt(), anyInt(), anyString());
        verify(openProjectApiClient).getWorkPackages(eq(1), eq(10),
            argThat(actual -> jsonArraysEqualIgnoringOrder(actual, MERGED_FILTERS)), eq(SAVED_QUERY_SORT_BY));
        assertEquals(NUMBER_OF_WORK_PACKAGES, result.getItems().size());
    }

    @Test
    void getWorkItemsWithSavedQueryWithoutStoredResultsUrl() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(openProjectApiClient.getQueryResultsUrl(QUERY_ID)).thenReturn(SAVED_QUERY_RESULTS_URL);

        openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        verify(openProjectApiClient).getQueryResultsUrl(QUERY_ID);
        verify(openProjectApiClient).getWorkPackages(eq(1), eq(10),
            argThat(actual -> jsonArraysEqualIgnoringOrder(actual, MERGED_FILTERS)), eq(SAVED_QUERY_SORT_BY));
    }

    @Test
    void getWorkItemsWithSavedQueryAndLivedataSorting() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn(SAVED_QUERY_RESULTS_URL);

        openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()),
            List.of(new LiveDataQuery.SortEntry("summary", true)));

        // The livedata sorting replaces the one of the query.
        verify(openProjectApiClient).getWorkPackages(eq(1), eq(10), anyString(), eq("[[\"subject\",\"desc\"]]"));
    }

    @Test
    void getWorkItemsWithSavedQueryWithoutResultsLink() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(openProjectApiClient.getQueryResultsUrl(QUERY_ID)).thenReturn("");

        PaginatedResult<WorkItem> result =
            openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        verify(openProjectApiClient, never()).getWorkPackages(anyInt(), anyInt(), anyString(), anyString());
        assertEquals(0, result.getItems().size());
    }

    @Test
    void getWorkItemsWithSavedQueryAndUnreadableStoredFilters() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn("/api/v3/work_packages?filters=%5B%7Bnot-json");

        PaginatedResult<WorkItem> result =
            openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        verify(openProjectApiClient, never()).getWorkPackages(anyInt(), anyInt(), anyString(), anyString());
        assertEquals(0, result.getItems().size());
    }

    @Test
    public void getWorkItemsWithoutIdentifierTest() throws ProjectManagementException
    {
        getWorkItemsTest(null, 1, 0, NUMBER_OF_WORK_PACKAGES);
    }

    @Test
    public void getWorkItemsWithIdentifierAsUrl() throws ProjectManagementException
    {
        getWorkItemsTest("http://open-project-instance/work_packages", 1, 0, NUMBER_OF_WORK_PACKAGES);
    }

    @Test
    public void getWorkItemsWithIdentifierAsIds() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn("1,2,3,4,5,6,7,8,9,10");

        PaginatedResult<WorkItem> result = openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        verify(openProjectApiClient, times(1)).getWorkPackages(anyInt(), anyInt(),
            argThat(actual -> jsonEquals(
                actual,
                "[{\"id\":{\"operator\":\"=\",\"values\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\"]}}]")),
            anyString()
        );

        assertEquals(NUMBER_OF_WORK_PACKAGES, result.getItems().size());
    }

    @Test
    public void getWorkItemsWithIdentifierAndProjectTest() throws ProjectManagementException
    {
        getWorkItemsTest("http://open-project-instance/projects/first-project/work_packages", 0, 1,
            NUMBER_OF_WORK_PACKAGES);
    }

    @Test
    public void getWorkPackagesInvalidFiltersOrSortingTest() throws ProjectManagementException
    {
        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenThrow(
            WorkPackageRetrievalBadRequestException.class);

        getWorkItemsTest("http://open-project-instance/work_packages", 1, 0, 0);
    }

    @Test
    public void getWorkPackagesThrowsProjectManagementExceptionTest() throws ProjectManagementException
    {
        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenThrow(
            ProjectManagementException.class);

        assertThrows(WorkItemRetrievalException.class,
            () -> openProjectClient.getWorkItems(1, 10, List.of(), List.of()));
    }

    @Test
    public void getWorkPackagesWithBadFiltersOrSortingJsonRepresentation()
    {
        try (
            MockedStatic<OpenProjectFilterHandler> filterMock = mockStatic(OpenProjectFilterHandler.class);
            MockedStatic<OpenProjectSortingHandler> sortingMock = mockStatic(OpenProjectSortingHandler.class)
        )
        {
            filterMock.when(() -> OpenProjectFilterHandler.convertFilters(any()))
                .thenThrow(new ProjectManagementException("Invalid filters")
                {
                });

            sortingMock.when(() -> OpenProjectSortingHandler.convertSorting(any()))
                .thenThrow(new ProjectManagementException("Invalid sorting")
                {
                });

            assertThrows(WorkItemRetrievalException.class,
                () -> openProjectClient.getWorkItems(1, 10, List.of(), List.of()));
        }
    }

    @Test
    public void handleNullOpenProjectApiClientTest() throws WorkItemRetrievalException
    {
        when(openProjectConfiguration.getOpenProjectApiClient(anyString())).thenReturn(null);

        assertEquals(0, openProjectClient.getWorkItems(1, 10, List.of(), List.of()).getItems().size());
    }

    private void getWorkItemsTest(String identifier, int expectedWorkPackagesCalls,
        int expectedProjectWorkPackagesCalls, int expectedElements) throws ProjectManagementException
    {

        when(executionContext.get("identifier")).thenReturn(identifier);

        PaginatedResult<WorkItem> result = openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        verify(openProjectApiClient, times(expectedWorkPackagesCalls)).getWorkPackages(anyInt(), anyInt(), anyString(),
            anyString());

        verify(openProjectApiClient, times(expectedProjectWorkPackagesCalls)).getProjectWorkPackages(anyString(),
            anyInt(), anyInt(), anyString(),
            anyString());

        assertEquals(expectedElements, result.getItems().size());
    }

    private PaginatedResult<WorkPackage> generateWorkItems()
    {
        PaginatedResult<WorkPackage> result = new PaginatedResult<>();
        List<WorkPackage> workPackages = new ArrayList<>();
        for (int i = 1; i <= NUMBER_OF_WORK_PACKAGES; i++) {
            WorkPackage workPackage = new WorkPackage();
            workPackage.setId(i);
            workPackages.add(workPackage);
        }
        result.setItems(workPackages);
        return result;
    }

    private LiveDataQuery.Filter getSummaryFilter()
    {
        return new LiveDataQuery.Filter("summary", "contains", "bug");
    }

    /**
     * The merged filters are built from a map, so their order isn't deterministic.
     */
    private boolean jsonArraysEqualIgnoringOrder(String actualJson, String expectedJson)
    {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<JsonNode> actual = new ArrayList<>();
            mapper.readTree(actualJson).forEach(actual::add);
            List<JsonNode> expected = new ArrayList<>();
            mapper.readTree(expectedJson).forEach(expected::add);
            return actual.size() == expected.size() && new HashSet<>(actual).equals(new HashSet<>(expected));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean jsonEquals(String actualJson, String expectedJson)
    {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode actual = mapper.readTree(actualJson);
            JsonNode expected = mapper.readTree(expectedJson);
            return actual.equals(expected);
        } catch (Exception e) {
            return false;
        }
    }
}
