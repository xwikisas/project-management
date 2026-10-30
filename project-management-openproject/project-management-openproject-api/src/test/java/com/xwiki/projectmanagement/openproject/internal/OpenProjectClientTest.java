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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.opentest4j.AssertionFailedError;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.exception.WorkPackageRetrievalBadRequestException;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link OpenProjectClient}.
 * <p>
 * Each test stubs the OpenProject API with the exact request it expects and asserts the work items that come out of
 * it. Any other request to the work packages or projects endpoints fails the test with the request that was sent.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(OpenProjectIdentifierResolver.class)
class OpenProjectClientTest
{
    private static final String QUERY_ID = "27";

    private static final String SAVED_QUERY_IDENTIFIER = "http://open-project-instance/work_packages?query_id=27";

    private static final String SAVED_QUERY_RESULTS_URL = "/api/v3/projects/1/work_packages?offset=1"
        + "&filters=%5B%7B%22status%22%3A%7B%22operator%22%3A%22o%22%2C%22values%22%3A%5B%5D%7D%7D%5D"
        + "&sortBy=%5B%5B%22id%22%2C%22desc%22%5D%5D&pageSize=20";

    private static final String GLOBAL_SAVED_QUERY_RESULTS_URL = "/api/v3/work_packages?offset=1"
        + "&filters=%5B%7B%22status%22%3A%7B%22operator%22%3A%22o%22%2C%22values%22%3A%5B%5D%7D%7D%5D"
        + "&sortBy=%5B%5B%22id%22%2C%22desc%22%5D%5D&pageSize=20";

    private static final String SAVED_QUERY_PROJECT = "1";

    private static final String SAVED_QUERY_SORT_BY = "[[\"id\",\"desc\"]]";

    private static final String MERGED_FILTERS = "[{\"status\":{\"operator\":\"o\",\"values\":[]}},"
        + "{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}}]";

    private static final String NO_FILTERS = "[]";

    private static final String NO_SORTING = "[]";

    private static final String IDENTIFIER = "identifier";

    /**
     * The requests that the tests must stub explicitly: they return the work items, or tell where they come from.
     */
    private static final Set<String> CHECKED_REQUESTS = Set.of("getWorkPackages", "getProjectWorkPackages",
        "getQueryWorkPackages", "getQueryResultsUrl", "getProjects");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectClient openProjectClient;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private ProjectManagementClientExecutionContext executionContext;

    private OpenProjectApiClient openProjectApiClient;

    @BeforeEach
    void setUp()
    {
        this.openProjectApiClient =
            mock(OpenProjectApiClient.class, withSettings().defaultAnswer(OpenProjectClientTest::unexpectedRequest));
        when(this.executionContext.get("instance")).thenReturn("");
        when(this.openProjectAccess.resolve(any()))
            .thenReturn(OpenProjectAccessResult.authenticated(this.openProjectApiClient));
    }

    @Test
    void getWorkItemsWithoutIdentifier() throws ProjectManagementException
    {
        doReturn(workPackages(1, 2)).when(this.openProjectApiClient).getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        assertEquals(List.of("1", "2"), getIds(this.openProjectClient.getWorkItems(1, 10, List.of(), List.of())));
    }

    @Test
    void getWorkItemsConvertsTheOffsetToAnOpenProjectPage() throws ProjectManagementException
    {
        doReturn(workPackages(1)).when(this.openProjectApiClient).getWorkPackages(1, 20, NO_FILTERS, NO_SORTING);
        doReturn(workPackages(41)).when(this.openProjectApiClient).getWorkPackages(3, 20, NO_FILTERS, NO_SORTING);
        doReturn(workPackages(21)).when(this.openProjectApiClient).getWorkPackages(3, 10, NO_FILTERS, NO_SORTING);

        assertEquals(List.of("1"), getIds(this.openProjectClient.getWorkItems(0, 20, List.of(), List.of())));
        assertEquals(List.of("41"), getIds(this.openProjectClient.getWorkItems(40, 20, List.of(), List.of())));
        // An offset in the middle of a page retrieves the page that holds it.
        assertEquals(List.of("21"), getIds(this.openProjectClient.getWorkItems(25, 10, List.of(), List.of())));
    }

    @Test
    void getWorkItemsWithIdentifierAsUrl() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn("http://open-project-instance/work_packages");
        doReturn(workPackages(3)).when(this.openProjectApiClient).getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        assertEquals(List.of("3"), getIds(this.openProjectClient.getWorkItems(1, 10, List.of(), List.of())));
    }

    @Test
    void getWorkItemsWithIdentifierAndProject() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER))
            .thenReturn("http://open-project-instance/projects/first-project/work_packages");
        doReturn(workPackages(4)).when(this.openProjectApiClient)
            .getProjectWorkPackages("first-project", 1, 10, NO_FILTERS, NO_SORTING);

        assertEquals(List.of("4"), getIds(this.openProjectClient.getWorkItems(1, 10, List.of(), List.of())));
    }

    @Test
    void getWorkItemsWithIdentifierHoldingFiltersAndSorting() throws ProjectManagementException
    {
        String queryProps = "{\"f\":[{\"n\":\"status\",\"o\":\"o\",\"v\":[]}],\"t\":\"id:desc\"}";
        when(this.executionContext.get(IDENTIFIER)).thenReturn("http://open-project-instance/work_packages?query_props="
            + URLEncoder.encode(queryProps, StandardCharsets.UTF_8));
        // The livedata filters and sorting are added to the ones of the URL.
        doReturn(workPackages(5)).when(this.openProjectApiClient).getWorkPackages(eq(1), eq(10), argThat(
            jsonFilters(MERGED_FILTERS)), eq("[[\"subject\",\"asc\"],[\"id\",\"desc\"]]"));

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()),
            List.of(new LiveDataQuery.SortEntry("summary", false)));

        assertEquals(List.of("5"), getIds(result));
    }

    @Test
    void getWorkItemsWithSavedQueryAndNoLivedataFilters() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(this.executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn(SAVED_QUERY_RESULTS_URL);
        // The query applies its own filters and sorting, without its results link being read.
        doReturn(workPackages(1, 2)).when(this.openProjectApiClient).getQueryWorkPackages(QUERY_ID, 1, 10, "");

        assertEquals(List.of("1", "2"), getIds(this.openProjectClient.getWorkItems(0, 10, List.of(), List.of())));
    }

    @Test
    void getWorkItemsWithSavedQueryConvertsTheOffsetToAnOpenProjectPage() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        doReturn(workPackages(31)).when(this.openProjectApiClient).getQueryWorkPackages(QUERY_ID, 3, 15, "");

        assertEquals(List.of("31"), getIds(this.openProjectClient.getWorkItems(30, 15, List.of(), List.of())));
    }

    @Test
    void getWorkItemsWithSavedQueryAndLivedataSortingButNoFilters() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        doReturn(workPackages(6)).when(this.openProjectApiClient)
            .getQueryWorkPackages(QUERY_ID, 1, 10, "[[\"subject\",\"asc\"]]");

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(),
            List.of(new LiveDataQuery.SortEntry("summary", false)));

        assertEquals(List.of("6"), getIds(result));
    }

    @Test
    void getWorkItemsWithSavedQueryAndStoredResultsUrl() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        // The results link was retrieved before the macro execution, so the query isn't read again.
        when(this.executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn(SAVED_QUERY_RESULTS_URL);
        // The query is bound to a project, which keeps its scope.
        doReturn(workPackages(7)).when(this.openProjectApiClient).getProjectWorkPackages(eq(SAVED_QUERY_PROJECT),
            eq(1), eq(10), argThat(jsonFilters(MERGED_FILTERS)), eq(SAVED_QUERY_SORT_BY));

        PaginatedResult<WorkItem> result =
            this.openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        assertEquals(List.of("7"), getIds(result));
    }

    @Test
    void getWorkItemsWithGlobalSavedQueryAndLivedataFilters() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(this.executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn(GLOBAL_SAVED_QUERY_RESULTS_URL);
        doReturn(workPackages(8)).when(this.openProjectApiClient).getWorkPackages(eq(1), eq(10),
            argThat(jsonFilters(MERGED_FILTERS)), eq(SAVED_QUERY_SORT_BY));

        PaginatedResult<WorkItem> result =
            this.openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        assertEquals(List.of("8"), getIds(result));
    }

    @Test
    void getWorkItemsWithSavedQueryWithoutStoredResultsUrl() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        doReturn(SAVED_QUERY_RESULTS_URL).when(this.openProjectApiClient).getQueryResultsUrl(QUERY_ID);
        doReturn(workPackages(9)).when(this.openProjectApiClient).getProjectWorkPackages(eq(SAVED_QUERY_PROJECT),
            eq(1), eq(10), argThat(jsonFilters(MERGED_FILTERS)), eq(SAVED_QUERY_SORT_BY));

        PaginatedResult<WorkItem> result =
            this.openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        assertEquals(List.of("9"), getIds(result));
    }

    @Test
    void getWorkItemsWithSavedQueryAndLivedataSorting() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(this.executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn(SAVED_QUERY_RESULTS_URL);
        // The livedata sorting replaces the one of the query.
        doReturn(workPackages(10)).when(this.openProjectApiClient).getProjectWorkPackages(eq(SAVED_QUERY_PROJECT),
            eq(1), eq(10), argThat(jsonFilters(MERGED_FILTERS)), eq("[[\"subject\",\"desc\"]]"));

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()),
            List.of(new LiveDataQuery.SortEntry("summary", true)));

        assertEquals(List.of("10"), getIds(result));
    }

    @Test
    void getWorkItemsWithSavedQueryWithoutResultsLink() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        doReturn("").when(this.openProjectApiClient).getQueryResultsUrl(QUERY_ID);

        PaginatedResult<WorkItem> result =
            this.openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
        assertEquals("Failed to retrieve work packages: The saved query [27] has no results link.",
            this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItemsWithSavedQueryAndUnreadableStoredFilters() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(SAVED_QUERY_IDENTIFIER);
        when(this.executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL))
            .thenReturn("/api/v3/work_packages?filters=%5B%7Bnot-json");

        PaginatedResult<WorkItem> result =
            this.openProjectClient.getWorkItems(0, 10, List.of(getSummaryFilter()), List.of());

        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
        assertEquals("Failed to retrieve work packages: Failed to read the filters of the saved query",
            this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItemsWithIdentifierAsIds() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn("3,1,2");
        // OpenProject returns the work packages sorted by id.
        doReturn(workPackages(1, 2, 3)).when(this.openProjectApiClient).getWorkPackages(eq(1), eq(3),
            argThat(jsonFilters("[{\"id\":{\"operator\":\"=\",\"values\":[\"3\",\"1\",\"2\"]}}]")), eq(""));

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertEquals(List.of("3", "1", "2"), getIds(result));
    }

    @Test
    void getWorkItemsWithKeysKeepsTheOrderOfTheIdentifier() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn(" XWIKI-123 , xwiki-456,1911,XWIKI-123,XWIKI-404 ");
        when(this.openProjectApiClient.getWorkPackage("XWIKI-123")).thenReturn(createWorkPackage(5));
        when(this.openProjectApiClient.getWorkPackage("xwiki-456")).thenReturn(createWorkPackage(2));
        // OpenProject returns the work packages sorted by id.
        doReturn(workPackages(2, 5, 1911)).when(this.openProjectApiClient).getWorkPackages(eq(1), eq(3),
            argThat(idFilterOf5And2And1911()), eq(""));

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertEquals(List.of("5", "2", "1911"), getIds(result));
        assertEquals(3, result.getTotalItems());
        // Each key is resolved with a single request to OpenProject.
        verify(this.openProjectApiClient).getWorkPackage("XWIKI-123");

        // The work packages are paginated after being ordered.
        result = this.openProjectClient.getWorkItems(1, 1, List.of(), List.of());

        assertEquals(List.of("2"), getIds(result));
        assertEquals(3, result.getTotalItems());
        // The work package at the offset 1 is on the second page.
        assertEquals(2, result.getPage());
    }

    @Test
    void getWorkItemsWithKeysSortedByTheLivedata() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn("XWIKI-123,XWIKI-456,1911");
        when(this.openProjectApiClient.getWorkPackage("XWIKI-123")).thenReturn(createWorkPackage(5));
        when(this.openProjectApiClient.getWorkPackage("XWIKI-456")).thenReturn(createWorkPackage(2));
        // The livedata sorting is applied by OpenProject, which paginates the work packages.
        doReturn(workPackages(1911, 5)).when(this.openProjectApiClient).getWorkPackages(eq(2), eq(10),
            argThat(idFilterOf5And2And1911()), eq("[[\"id\",\"desc\"]]"));

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(10, 10, List.of(),
            List.of(new LiveDataQuery.SortEntry(IDENTIFIER, true)));

        assertEquals(List.of("1911", "5"), getIds(result));
    }

    @Test
    void getWorkItemsWithUnknownKeys() throws ProjectManagementException
    {
        // The keys don't resolve to any work package, so no work packages are requested.
        when(this.executionContext.get(IDENTIFIER)).thenReturn("XWIKI-404,XWIKI-405");

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
    }

    @Test
    void getWorkItemsWithAKeyRejectedByOpenProject() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn("XWIKI-123,1911");
        when(this.openProjectApiClient.getWorkPackage("XWIKI-123"))
            .thenThrow(new WorkItemRetrievalException("Invalid key", 400));
        doReturn(workPackages(1911)).when(this.openProjectApiClient).getWorkPackages(eq(1), eq(1),
            argThat(jsonFilters("[{\"id\":{\"operator\":\"=\",\"values\":[\"1911\"]}}]")), eq(""));

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertEquals(List.of("1911"), getIds(result));
        assertEquals("Failed to resolve the work package key [XWIKI-123]: Invalid key", this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItemsWithAKeyWhenOpenProjectRejectsTheUser() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn("XWIKI-123");
        when(this.openProjectApiClient.getWorkPackage("XWIKI-123"))
            .thenThrow(new WorkItemRetrievalException("Rejected", 401));

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> this.openProjectClient.getWorkItems(0, 10, List.of(), List.of()));

        assertEquals("Rejected", exception.getMessage());
        assertEquals(401, exception.getStatusCode());
    }

    @Test
    void getWorkItemsWithAnInvalidIdentifier()
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn("XWIKI-123,not a key");

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> this.openProjectClient.getWorkItems(0, 10, List.of(), List.of()));

        assertEquals("An error occurred while trying to get the work items", exception.getMessage());
        assertEquals("The provided identifier value is not in a supported format.",
            exception.getCause().getMessage());
    }

    @Test
    void getWorkItemsLogsBadRequestAndReturnsNothing() throws ProjectManagementException
    {
        when(this.executionContext.get(IDENTIFIER)).thenReturn("http://open-project-instance/work_packages");
        doThrow(new WorkPackageRetrievalBadRequestException("Invalid filter")).when(this.openProjectApiClient)
            .getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
        assertEquals("Failed to retrieve work packages: Invalid filter", this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItemsWrapsProjectManagementException() throws ProjectManagementException
    {
        ProjectManagementException failure = new ProjectManagementException("Server error");
        doThrow(failure).when(this.openProjectApiClient).getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> this.openProjectClient.getWorkItems(1, 10, List.of(), List.of()));

        assertEquals("An error occurred while trying to get the work items", exception.getMessage());
        assertEquals(failure, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 403 })
    void getWorkItemsWhenOpenProjectRejectsTheUser(int statusCode) throws ProjectManagementException
    {
        doThrow(new WorkItemRetrievalException("Rejected", statusCode)).when(this.openProjectApiClient)
            .getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> this.openProjectClient.getWorkItems(1, 10, List.of(), List.of()));

        assertEquals("Rejected", exception.getMessage());
        assertEquals(statusCode, exception.getStatusCode());
    }

    @Test
    void getWorkItemsWhenTheUserIsNotConnected()
    {
        when(this.openProjectAccess.resolve(anyString())).thenReturn(OpenProjectAccessResult.notConnected());

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> this.openProjectClient.getWorkItems(1, 10, List.of(), List.of()));

        assertEquals("The current user isn't connected to the OpenProject instance [].", exception.getMessage());
        assertEquals(401, exception.getStatusCode());
    }

    @Test
    void getWorkItemsWhenTheConnectionIsNotConfigured() throws WorkItemRetrievalException
    {
        when(this.openProjectAccess.resolve(anyString())).thenReturn(OpenProjectAccessResult.notConfigured());

        assertEquals(0, this.openProjectClient.getWorkItems(1, 10, List.of(), List.of()).getItems().size());
        assertEquals("Failed to retrieve work packages: No configuration for instance [] was found.",
            this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItemsWithWorkPackageFields() throws ProjectManagementException
    {
        WorkPackageField targetVersions = new WorkPackageField();
        targetVersions.setKey("targetVersions");
        targetVersions.setName("Target versions");
        targetVersions.setFilterKey("targetVersion");
        targetVersions.setFilterValueType("[]Version");
        targetVersions.setSortable(true);
        when(this.openProjectApiClient.getWorkPackageFields())
            .thenReturn(new PaginatedResult<>(List.of(targetVersions), 0, 1, 1));
        Version version = new Version();
        version.setId(19);
        version.setName("1.2.3");
        when(this.openProjectApiClient.getVersions()).thenReturn(new PaginatedResult<>(List.of(version), 0, 1, 1));
        WorkPackage workPackage = createWorkPackage(1);
        Linkable versionLink = new Linkable("1.2.3", "http://open-project-instance/versions/19");
        workPackage.setFieldValues(Map.of("targetVersions", versionLink));
        // The version is filtered by its id, and sorted by the key of the field.
        doReturn(new PaginatedResult<>(List.of(workPackage), 1, 10, 1)).when(this.openProjectApiClient)
            .getWorkPackages(eq(1), eq(10),
                argThat(jsonFilters("[{\"targetVersion\":{\"operator\":\"=\",\"values\":[\"19\"]}}]")),
                eq("[[\"targetVersions\",\"desc\"]]"));

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10,
            List.of(new LiveDataQuery.Filter("targetVersion", "contains", "1.2.3")),
            List.of(new LiveDataQuery.SortEntry("targetVersion", true)));

        assertEquals(List.of(versionLink), result.getItems().get(0).get("targetVersion"));
    }

    @Test
    void getWorkItemsWhenWorkPackageFieldsFail() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getWorkPackageFields()).thenThrow(new ProjectManagementException("Forbidden"));
        doReturn(workPackages(1, 2)).when(this.openProjectApiClient).getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertEquals(List.of("1", "2"), getIds(result));
        assertEquals("Failed to retrieve the work package fields: ProjectManagementException: Forbidden",
            this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItemsMarksClosedWorkItemsAsResolved() throws ProjectManagementException
    {
        Status open = new Status();
        open.setName("New");
        open.setClosed(false);
        Status closed = new Status();
        closed.setName("Closed");
        closed.setClosed(true);
        when(this.openProjectApiClient.getStatuses())
            .thenReturn(new PaginatedResult<>(List.of(open, closed), 0, 2, 2));
        WorkPackage openWorkPackage = createWorkPackage(1);
        openWorkPackage.setStatus(new Linkable("New", "http://open-project-instance/statuses/1/edit"));
        WorkPackage closedWorkPackage = createWorkPackage(2);
        closedWorkPackage.setStatus(new Linkable("Closed", "http://open-project-instance/statuses/2/edit"));
        doReturn(new PaginatedResult<>(List.of(openWorkPackage, closedWorkPackage), 1, 10, 2))
            .when(this.openProjectApiClient).getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertFalse(result.getItems().get(0).isResolved());
        assertTrue(result.getItems().get(1).isResolved());
    }

    @Test
    void getWorkItemsWithKeys() throws ProjectManagementException
    {
        WorkPackage withKey = createWorkPackage(1);
        withKey.setDisplayId("PROJ-1");
        // Without project-based semantic identifiers, OpenProject displays the id.
        WorkPackage withId = createWorkPackage(2);
        withId.setDisplayId("2");
        WorkPackage withoutDisplayId = createWorkPackage(3);
        doReturn(new PaginatedResult<>(List.of(withKey, withId, withoutDisplayId), 1, 10, 3))
            .when(this.openProjectApiClient).getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertEquals("PROJ-1", result.getItems().get(0).getKey());
        assertEquals("1", result.getItems().get(0).getIdentifier().getValue());
        assertNull(result.getItems().get(1).getKey());
        assertNull(result.getItems().get(2).getKey());
    }

    @Test
    void getWorkItemsWhenStatusesFail() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getStatuses()).thenThrow(new ProjectManagementException("Forbidden"));
        doReturn(workPackages(1, 2)).when(this.openProjectApiClient).getWorkPackages(1, 10, NO_FILTERS, NO_SORTING);

        PaginatedResult<WorkItem> result = this.openProjectClient.getWorkItems(0, 10, List.of(), List.of());

        assertEquals(List.of("1", "2"), getIds(result));
        assertFalse(result.getItems().get(0).containsKey(WorkItem.KEY_RESOLVED));
        assertEquals("Failed to retrieve the work package statuses: ProjectManagementException: Forbidden",
            this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItem() throws ProjectManagementException
    {
        doReturn(workPackages(42)).when(this.openProjectApiClient).getWorkPackages(eq(1), eq(1),
            argThat(jsonFilters("[{\"id\":{\"operator\":\"=\",\"values\":[\"42\"]}}]")), eq(NO_SORTING));

        assertEquals("42", this.openProjectClient.getWorkItem("42").getIdentifier().getValue());
    }

    @Test
    void getProjects() throws ProjectManagementException
    {
        doReturn(new PaginatedResult<>(List.of(createProject(7, "Seven"), createProject(8, "Eight")), 2, 2, 4))
            .when(this.openProjectApiClient)
            .getProjects(eq(2), eq(2), argThat(jsonFilters("[{\"name\":{\"operator\":\"~\",\"values\":[\"e\"]}}]")));

        PaginatedResult<com.xwiki.projectmanagement.model.Project> result = this.openProjectClient.projects()
            .get(2, 2, List.of(new LiveDataQuery.Filter("name", "contains", "e")));

        assertEquals(List.of("Seven", "Eight"),
            result.getItems().stream().map(com.xwiki.projectmanagement.model.Project::getName)
                .collect(Collectors.toList()));
        assertEquals("7", result.getItems().get(0).getIdentifier().getValue());
        assertEquals(4, result.getTotalItems());
    }

    @Test
    void getProject() throws ProjectManagementException
    {
        doReturn(new PaginatedResult<>(List.of(createProject(7, "Seven")), 0, 1, 1)).when(this.openProjectApiClient)
            .getProjects(eq(0), eq(1), argThat(jsonFilters("[{\"id\":{\"operator\":\"=\",\"values\":[\"7\"]}}]")));

        assertEquals("Seven", this.openProjectClient.projects().get("7").getName());
    }

    @Test
    void getProjectsWhenOpenProjectFails() throws ProjectManagementException
    {
        ProjectManagementException failure = new ProjectManagementException("Server error");
        doThrow(failure).when(this.openProjectApiClient).getProjects(0, 1, NO_FILTERS);

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> this.openProjectClient.projects().get(0, 1, List.of()));

        assertEquals("Failed to retrieve the OpenProject projects.", exception.getMessage());
        assertEquals(failure, exception.getCause());
    }

    /**
     * Fails on the requests that the test didn't stub, showing the request that was actually sent.
     */
    private static Object unexpectedRequest(InvocationOnMock invocation) throws Throwable
    {
        if (CHECKED_REQUESTS.contains(invocation.getMethod().getName())) {
            throw new AssertionFailedError("Unexpected OpenProject request: " + invocation);
        }
        return Mockito.RETURNS_DEFAULTS.answer(invocation);
    }

    private static PaginatedResult<WorkPackage> workPackages(int... ids)
    {
        List<WorkPackage> workPackages =
            Arrays.stream(ids).mapToObj(OpenProjectClientTest::createWorkPackage).collect(Collectors.toList());
        return new PaginatedResult<>(workPackages, 1, workPackages.size(), workPackages.size());
    }

    private static WorkPackage createWorkPackage(int id)
    {
        WorkPackage workPackage = new WorkPackage();
        workPackage.setId(id);
        workPackage.setSelf(new Linkable(String.valueOf(id), "http://open-project-instance/work_packages/" + id));
        return workPackage;
    }

    private static Project createProject(int id, String name)
    {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setSelf(new Linkable(name, "http://open-project-instance/projects/" + id));
        return project;
    }

    private static List<String> getIds(PaginatedResult<WorkItem> result)
    {
        return result.getItems().stream().map(workItem -> workItem.getIdentifier().getValue())
            .collect(Collectors.toList());
    }

    private static LiveDataQuery.Filter getSummaryFilter()
    {
        return new LiveDataQuery.Filter("summary", "contains", "bug");
    }

    private static ArgumentMatcher<String> idFilterOf5And2And1911()
    {
        return jsonFilters("[{\"id\":{\"operator\":\"=\",\"values\":[\"5\",\"2\",\"1911\"]}}]");
    }

    /**
     * Matches OpenProject filters holding the expected ones in any order (the merged filters are built from a map),
     * each one exactly once.
     */
    private static ArgumentMatcher<String> jsonFilters(String expectedJson)
    {
        List<JsonNode> expected = elements(expectedJson);
        return new ArgumentMatcher<>()
        {
            @Override
            public boolean matches(String actualJson)
            {
                List<JsonNode> actual = elements(actualJson);
                for (JsonNode element : expected) {
                    // Removes a single occurrence, so that duplicated filters don't match.
                    if (!actual.remove(element)) {
                        return false;
                    }
                }
                return actual.isEmpty();
            }

            @Override
            public String toString()
            {
                return expectedJson;
            }
        };
    }

    private static List<JsonNode> elements(String json)
    {
        List<JsonNode> elements = new ArrayList<>();
        try {
            new ObjectMapper().readTree(json).forEach(elements::add);
        } catch (Exception e) {
            elements.add(null);
        }
        return elements;
    }
}
