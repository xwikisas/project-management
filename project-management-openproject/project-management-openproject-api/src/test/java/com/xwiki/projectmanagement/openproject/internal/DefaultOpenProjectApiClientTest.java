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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.sun.net.httpserver.HttpServer;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.auth.BearerTokenAuthenticator;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.ColoredOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.News;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.UserAvatar;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectApiClient}.
 *
 * @version $Id$
 */
@SuppressWarnings("unchecked")
class DefaultOpenProjectApiClientTest
{
    private DefaultOpenProjectApiClient openProjectApiClient;

    private HttpClient client;

    private HttpResponse<String> response;

    private WorkPackageDescriptionCleaner descriptionCleaner;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String OPEN_PROJECT_CONNECTION_URL = "http://localhost.com";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final String OPEN_PROJECT_TOKEN = "token";

    private static final String PROJECT_NAME = "projectName";

    private static final String FILTERS_STRING = "generatedFiltersString";

    private static final String SORT_BY_STRING = "generatedSortByString";

    @BeforeEach
    void setUp() throws Exception
    {
        client = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        descriptionCleaner = mock(WorkPackageDescriptionCleaner.class);
        when(descriptionCleaner.clean(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        openProjectApiClient = new DefaultOpenProjectApiClient(OPEN_PROJECT_CONNECTION_URL,
            new BearerTokenAuthenticator(OPEN_PROJECT_TOKEN), client, descriptionCleaner);
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(this.response);
    }

    @Test
    void getWorkPackageFields() throws Exception
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getQueryFormResponse());

        List<WorkPackageField> fields = openProjectApiClient.getWorkPackageFields().getItems();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(this.client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(OPEN_PROJECT_CONNECTION_URL + "/api/v3/queries/form", requestCaptor.getValue().uri().toString());
        assertEquals("POST", requestCaptor.getValue().method());

        // Only the columns holding a property are fields, not the relation columns.
        assertEquals(List.of("id", "subject", "responsible", "category", "targetVersions", "observedInVersions",
            "estimatedTime", "customField20", "customField10", "customField5"),
            fields.stream().map(WorkPackageField::getKey).collect(Collectors.toList()));

        WorkPackageField targetVersions = fields.get(4);
        assertEquals("Target versions", targetVersions.getName());
        // The filter of a multi-value field is named in the singular.
        assertEquals("targetVersion", targetVersions.getFilterKey());
        assertEquals("[]Version", targetVersions.getFilterValueType());
        assertEquals(List.of("=", "!", "*", "!*", "o", "l", "c"), targetVersions.getFilterOperators());
        assertTrue(targetVersions.isSortable());
        assertFalse(targetVersions.isCustomField());

        WorkPackageField category = fields.get(3);
        assertNull(category.getFilterKey());
        assertNull(category.getFilterValueType());
        assertEquals(List.of(), category.getFilterOperators());

        WorkPackageField labels = fields.get(9);
        assertEquals("Labels", labels.getName());
        assertEquals("customField5", labels.getFilterKey());
        assertTrue(labels.getFilterOperators().contains("&="));
        assertTrue(labels.isCustomField());

        assertEquals("[1]String", fields.get(8).getFilterValueType());
    }

    @Test
    void getWorkPackagesCleansTheDescription() throws Exception
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getWorkPackagesValidResponse());
        when(this.descriptionCleaner.clean("<p>First description</p>", OPEN_PROJECT_CONNECTION_URL))
            .thenReturn("cleaned");

        assertEquals("cleaned",
            openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", "").getItems().get(0).getDescription());
    }

    @Test
    void getWorkPackagesWithFieldValues() throws Exception
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getWorkPackagesValidResponse());

        Map<String, Object> values =
            openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", "").getItems().get(0).getFieldValues();

        assertEquals(List.of(new Linkable("1.6.1", OPEN_PROJECT_CONNECTION_URL + "/versions/19"),
            new Linkable("1.7", OPEN_PROJECT_CONNECTION_URL + "/versions/20")), values.get("targetVersions"));
        // The categories and the custom options have no page in OpenProject.
        assertEquals(new Linkable("Generic", null), values.get("category"));
        assertEquals(List.of(new Linkable("Usability", null)), values.get("customField5"));
        assertEquals("1.5h", values.get("estimatedTime"));
        assertEquals("3d", values.get("duration"));
        assertEquals("JIRA-1", values.get("customField10"));
        assertEquals("Long text", values.get("customField11"));
        // The empty values and the links without a title, i.e. the actions, are ignored.
        assertFalse(values.containsKey("customField12"));
        assertFalse(values.containsKey("customField20"));
        assertFalse(values.containsKey("responsible"));
        assertFalse(values.containsKey("update"));
    }

    @Test
    void getWorkPackagesParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getWorkPackagesValidResponse());

        PaginatedResult<WorkPackage> workPackages = openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", "");

        assertEquals(3, workPackages.getTotalItems());
        assertEquals(PAGE_SIZE, workPackages.getPageSize());
        assertEquals(OFFSET, workPackages.getPage());

        assertGeneratedWorkPackages(workPackages.getItems());
        // OpenProject versions older than 17.x don't provide the displayed identifier.
        assertNull(workPackages.getItems().get(0).getDisplayId());
    }

    @Test
    void getProjectWorkPackagesParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getWorkPackagesValidResponse());

        PaginatedResult<WorkPackage> projectWorkPackages = openProjectApiClient.getProjectWorkPackages(
            PROJECT_NAME,
            OFFSET,
            PAGE_SIZE, "",
            ""
        );

        assertEquals(3, projectWorkPackages.getTotalItems());
        assertEquals(PAGE_SIZE, projectWorkPackages.getPageSize());
        assertEquals(OFFSET, projectWorkPackages.getPage());

        assertGeneratedWorkPackages(projectWorkPackages.getItems());
    }

    @Test
    void getUsersParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getUsersValidResponse());

        PaginatedResult<User> users = openProjectApiClient.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(2, users.getTotalItems());
        assertEquals(PAGE_SIZE, users.getPageSize());
        assertEquals(OFFSET, users.getPage());

        assertGeneratedObjects(
            users.getItems(),
            List.of("First User", "Second User"),
            String.format("%s/users/%%s", OPEN_PROJECT_CONNECTION_URL)
        );
    }

    @Test
    void getAvailableUsersParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getUsersValidResponse());

        PaginatedResult<User> users = openProjectApiClient.getAvailableUsers("", OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(2, users.getTotalItems());
        assertEquals(PAGE_SIZE, users.getPageSize());
        assertEquals(OFFSET, users.getPage());

        assertGeneratedObjects(
            users.getItems(),
            List.of("First User", "Second User"),
            "/api/v3/users/%s"
        );
    }

    @Test
    void getProjectsParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getProjectsValidResponse());

        PaginatedResult<Project> projects = openProjectApiClient.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(3, projects.getTotalItems());
        assertEquals(PAGE_SIZE, projects.getPageSize());
        assertEquals(OFFSET, projects.getPage());

        assertGeneratedObjects(
            projects.getItems(),
            List.of("First project", "Second project", "Third project"),
            String.format("%s/projects/%%s", OPEN_PROJECT_CONNECTION_URL)
        );
    }

    @Test
    void getNewsLinksToTheProjectOfEachNews() throws IOException, ProjectManagementException
    {
        // The id of the news differs from the id of its project.
        when(this.response.body()).thenReturn("{\"_embedded\":{\"elements\":[{\"id\":7,\"title\":\"Release\","
            + "\"summary\":\"\",\"description\":{\"raw\":\"\"},\"createdAt\":\"2026-10-01T10:00:00Z\","
            + "\"_links\":{\"self\":{\"href\":\"/api/v3/news/7\",\"title\":\"Release\"},"
            + "\"project\":{\"href\":\"/api/v3/projects/3\",\"title\":\"Third project\"},"
            + "\"author\":{\"href\":\"/api/v3/users/1\",\"title\":\"Admin\"}}}]}}");

        PaginatedResult<News> news = openProjectApiClient.getNews(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(1, news.getItems().size());
        assertEquals(new Linkable("Third project", OPEN_PROJECT_CONNECTION_URL + "/projects/3"),
            news.getItems().get(0).getProjectLink());
    }

    @Test
    void getAvailableProjectsParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getProjectsValidResponse());

        PaginatedResult<Project> projects =
            openProjectApiClient.getAvailableProjects("", OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(3, projects.getTotalItems());
        assertEquals(PAGE_SIZE, projects.getPageSize());
        assertEquals(OFFSET, projects.getPage());

        assertGeneratedObjects(
            projects.getItems(),
            List.of("First project", "Second project", "Third project"),
            "/api/v3/projects/%s"
        );
    }

    @Test
    void getTypesParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getTypesValidResponse());

        PaginatedResult<Type> types = openProjectApiClient.getTypes();

        assertEquals(2, types.getTotalItems());
        assertEquals(2, types.getPageSize());
        assertEquals(0, types.getPage());

        assertGeneratedTypes(types.getItems());
    }

    @Test
    void getStatusesParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getStatusesValidResponse());

        PaginatedResult<Status> statuses = openProjectApiClient.getStatuses();

        assertEquals(2, statuses.getTotalItems());
        assertEquals(2, statuses.getPageSize());
        assertEquals(0, statuses.getPage());

        assertGeneratedStatuses(statuses.getItems());
    }

    @Test
    void getPrioritiesParsesResponse() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(OpenProjectTestUtils.getPrioritiesValidResponse());

        PaginatedResult<Priority> priorities = openProjectApiClient.getPriorities();

        assertEquals(2, priorities.getTotalItems());
        assertEquals(2, priorities.getPageSize());
        assertEquals(0, priorities.getPage());

        assertGeneratedPriorities(priorities.getItems());
    }

    @Test
    void getUserAvatarNormalizesContentType() throws ProjectManagementException
    {
        when(response.statusCode()).thenReturn(200);
        HttpHeaders headers = HttpHeaders.of(Map.of("Content-Type", List.of("Image/PNG; charset=binary")),
            (a, b) -> true);
        when(response.headers()).thenReturn(headers);
        UserAvatar userAvatar = openProjectApiClient.getUserAvatar("test");

        assertEquals("image/png", userAvatar.getContentType());
        assertNotNull(userAvatar.getStreamingOutput());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "text/html", "image/svg+xml", "application/octet-stream", "text/plain;image/png" })
    void getAvatarWithUnsupportedContentType(String contentType)
    {
        when(response.statusCode()).thenReturn(200);
        Map<String, List<String>> headerValues = contentType.isEmpty() ? Map.of()
            : Map.of("Content-Type", List.of(contentType));
        when(response.headers()).thenReturn(HttpHeaders.of(headerValues, (a, b) -> true));

        assertThrows(WorkItemRetrievalException.class, () -> openProjectApiClient.getUserAvatar("test"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "@other.example/api/v3/users", ".other.example/api/v3/users",
        ":8080/api/v3/users", "/api/v3/projects/1/../../../users", "/api/v3/%2e%2E/users" })
    void requestsOnlyTargetTheConfiguredInstance(String path) throws Exception
    {
        assertThrows(ProjectManagementException.class,
            () -> openProjectApiClient.getAvailableUsers(path, OFFSET, PAGE_SIZE, ""));
        assertThrows(ProjectManagementException.class,
            () -> openProjectApiClient.getAvailableProjects(path, OFFSET, PAGE_SIZE, ""));
        assertThrows(ProjectManagementException.class, () -> openProjectApiClient.createWorkPackage(path, "{}"));
        assertThrows(ProjectManagementException.class, () -> openProjectApiClient.updateWorkPackage(path, "{}"));

        verify(this.client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void valuesAreEncodedInTheRequestPath() throws Exception
    {
        when(this.response.statusCode()).thenReturn(200);
        when(this.response.body()).thenReturn("{\"_type\":\"Form\"}");
        when(this.response.headers()).thenReturn(
            HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));

        openProjectApiClient.getProjectWorkPackages("demo/../users?a=b c", null, null, "", "");
        openProjectApiClient.getQueryWorkPackages("1#x", null, null, "");
        openProjectApiClient.getWorkPackageFormResponse("1/../../users", "{}");
        openProjectApiClient.getUserAvatar("1?x");
        openProjectApiClient.getWorkPackage("A/B");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(this.client, times(5)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(List.of(
            OPEN_PROJECT_CONNECTION_URL + "/api/v3/projects/demo%2F..%2Fusers%3Fa%3Db%20c/work_packages",
            OPEN_PROJECT_CONNECTION_URL + "/api/v3/queries/1%23x",
            OPEN_PROJECT_CONNECTION_URL + "/api/v3/work_packages/1%2F..%2F..%2Fusers/form",
            OPEN_PROJECT_CONNECTION_URL + "/api/v3/users/1%3Fx/avatar",
            OPEN_PROJECT_CONNECTION_URL + "/api/v3/work_packages/A%2FB"),
            captor.getAllValues().stream().map(request -> request.uri().toString()).collect(Collectors.toList()));
    }

    @Test
    void pathSegmentsMadeOfDotsAreRejected() throws Exception
    {
        assertThrows(ProjectManagementException.class,
            () -> openProjectApiClient.getProjectWorkPackages("..", null, null, "", ""));
        assertThrows(ProjectManagementException.class, () -> openProjectApiClient.getUserAvatar("."));
        assertThrows(ProjectManagementException.class,
            () -> openProjectApiClient.getAvailableUsers("/api/v3/..;/users", OFFSET, PAGE_SIZE, ""));

        verify(this.client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void requestsStayUnderThePathOfTheConfiguredInstance() throws Exception
    {
        DefaultOpenProjectApiClient apiClient = new DefaultOpenProjectApiClient("https://example.org/openproject",
            new BearerTokenAuthenticator(OPEN_PROJECT_TOKEN), this.client, this.descriptionCleaner);
        when(this.response.statusCode()).thenReturn(200);
        when(this.response.body()).thenReturn("{}");

        assertThrows(ProjectManagementException.class,
            () -> apiClient.getAvailableUsers("x/api/v3/users", OFFSET, PAGE_SIZE, ""));
        verify(this.client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        apiClient.getAvailableUsers("/api/v3/users", null, null, "");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(this.client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://example.org/openproject/api/v3/users", captor.getValue().uri().toString());
    }

    @Test
    void getAvatarAndGet404() throws ProjectManagementException
    {
        when(response.statusCode()).thenReturn(404);
        assertThrows(WorkItemNotFoundException.class, () -> {
            openProjectApiClient.getUserAvatar("test");
        });
    }

    @Test
    void getWorkPackagesFormResponseParsesBody() throws ProjectManagementException
    {
        String jsonBody = "{}";
        when(this.response.body()).thenReturn(jsonBody);

        assertNotNull(openProjectApiClient.getWorkPackagesFormResponse("{}"));
    }

    @Test
    void getWorkPackagesFormResponseAndGet404()
    {
        when(response.statusCode()).thenReturn(404);
        when(this.response.body()).thenReturn("{}");
        assertThrows(WorkItemRetrievalException.class, () -> {
            openProjectApiClient.getWorkPackagesFormResponse("{}");
        });
    }

    @Test
    void getAvatarAndGetOtherResponseCodeWithLongBody() throws Exception
    {
        HttpResponse<byte[]> avatarResponse = mock(HttpResponse.class);
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
            avatarResponse);
        when(avatarResponse.statusCode()).thenReturn(500);
        when(avatarResponse.body()).thenReturn("a".repeat(5000).getBytes(StandardCharsets.UTF_8));

        WorkItemRetrievalException exception =
            assertThrows(WorkItemRetrievalException.class, () -> openProjectApiClient.getUserAvatar("1"));
        assertEquals("Failed to get the user avatar with code [500] and message [" + "a".repeat(1024) + "]",
            exception.getMessage());
    }

    @Test
    void getAvatarAndGetOtherResponseCode() throws ProjectManagementException, IOException, InterruptedException
    {
        HttpResponse<byte[]> avatarResponse = mock(HttpResponse.class);
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
            avatarResponse);
        when(avatarResponse.statusCode()).thenReturn(400);
        when(avatarResponse.body()).thenReturn("error".getBytes(StandardCharsets.UTF_8));
        assertThrows(WorkItemRetrievalException.class, () -> {
            openProjectApiClient.getUserAvatar("test");
        });
    }

    @Test
    void getProjectWorkPackagesBuildsUrlParameters()
        throws ProjectManagementException, IOException, InterruptedException
    {
        when(this.response.body()).thenReturn("{}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        openProjectApiClient.getProjectWorkPackages(PROJECT_NAME, OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);

        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = captor.getValue();
        String generatedUri = request.uri().toString();
        assertThat(generatedUri, startsWith(String.format("%s/api/v3/projects/%s/work_packages",
            OPEN_PROJECT_CONNECTION_URL, PROJECT_NAME)));
        assertThat(generatedUri, containsString("offset=" + OFFSET));
        assertThat(generatedUri, containsString("pageSize=" + PAGE_SIZE));
        assertThat(generatedUri, containsString("filters=" + FILTERS_STRING));
        assertThat(generatedUri, containsString("sortBy=" + SORT_BY_STRING));
    }

    @Test
    void getWorkPackagesBuildsUrlParameters() throws ProjectManagementException, IOException, InterruptedException
    {
        when(this.response.body()).thenReturn("{}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);

        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = captor.getValue();
        String generatedUri = request.uri().toString();
        assertThat(generatedUri, startsWith(String.format("%s/api/v3/work_packages", OPEN_PROJECT_CONNECTION_URL)));
        assertThat(generatedUri, containsString("offset=" + OFFSET));
        assertThat(generatedUri, containsString("pageSize=" + PAGE_SIZE));
        assertThat(generatedUri, containsString("filters=" + FILTERS_STRING));
        assertThat(generatedUri, containsString("sortBy=" + SORT_BY_STRING));
    }

    @Test
    void getWorkPackageByKey() throws ProjectManagementException, IOException, InterruptedException
    {
        when(this.response.statusCode()).thenReturn(200);
        when(this.response.body())
            .thenReturn("{\"id\":1911,\"displayId\":\"XWIKI-123\",\"subject\":\"Fix the bug\"}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        WorkPackage workPackage = openProjectApiClient.getWorkPackage("XWIKI-123");

        assertEquals(1911, workPackage.getId());
        assertEquals("Fix the bug", workPackage.getSubject());
        assertEquals("XWIKI-123", workPackage.getDisplayId());
        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(String.format("%s/api/v3/work_packages/XWIKI-123", OPEN_PROJECT_CONNECTION_URL),
            captor.getValue().uri().toString());
    }

    @Test
    void getWorkPackageThatDoesNotExist() throws ProjectManagementException
    {
        when(this.response.statusCode()).thenReturn(404);
        when(this.response.body()).thenReturn("{}");

        assertNull(openProjectApiClient.getWorkPackage("XWIKI-123"));
    }

    @Test
    void getWorkPackageWhenTheUserIsRejected()
    {
        when(this.response.statusCode()).thenReturn(401);
        when(this.response.body()).thenReturn("{}");

        WorkItemRetrievalException exception =
            assertThrows(WorkItemRetrievalException.class, () -> openProjectApiClient.getWorkPackage("XWIKI-123"));
        assertEquals(401, exception.getStatusCode());
    }

    @Test
    void requestsHaveATimeout() throws ProjectManagementException, IOException, InterruptedException
    {
        when(this.response.body()).thenReturn("{}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);

        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(Optional.of(Duration.ofSeconds(20)), captor.getValue().timeout());
    }

    @Test
    void requestTimeoutIsReportedAsProjectManagementException() throws IOException, InterruptedException
    {
        HttpTimeoutException timeout = new HttpTimeoutException("request timed out");
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(timeout);

        ProjectManagementException exception = assertThrows(ProjectManagementException.class,
            () -> openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", ""));
        assertSame(timeout, exception.getCause());
    }

    @Test
    void interruptedRequestRestoresTheInterruptFlag() throws IOException, InterruptedException
    {
        InterruptedException interrupted = new InterruptedException();
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(interrupted);

        try {
            ProjectManagementException exception = assertThrows(ProjectManagementException.class,
                () -> openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", ""));
            assertSame(interrupted, exception.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Clear the flag so that it doesn't leak to the other tests.
            Thread.interrupted();
        }
    }

    @Test
    void getUserAvatarReturnsTheAvatarBytes() throws Exception
    {
        HttpResponse<byte[]> avatarResponse = mock(HttpResponse.class);
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
            avatarResponse);
        when(avatarResponse.statusCode()).thenReturn(200);
        when(avatarResponse.headers())
            .thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));
        when(avatarResponse.body()).thenReturn(new byte[] { 1, 2, 3 });

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        openProjectApiClient.getUserAvatar("test").getStreamingOutput().write(output);

        assertArrayEquals(new byte[] { 1, 2, 3 }, output.toByteArray());
    }

    @Test
    void tooLargeResponseIsReportedAsProjectManagementException() throws Exception
    {
        // 17 MB, above the 16 MB limit of the JSON responses, without a declared length so that it's read.
        HttpServer server = startServer(0, 17 * 1024 * 1024);
        try {
            ProjectManagementException exception = assertThrows(ProjectManagementException.class,
                () -> createRealClient(server).getWorkPackages(OFFSET, PAGE_SIZE, "", ""));
            assertThat(exception.getCause().getMessage(),
                containsString("larger than the maximum allowed size of [16777216] bytes"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tooLargeAvatarIsReportedAsRetrievalFailure() throws Exception
    {
        // 6 MB, above the 5 MB limit of the avatars, declared in the Content-Length so that it's not read.
        int size = 6 * 1024 * 1024;
        HttpServer server = startServer(size, size);
        try {
            WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
                () -> createRealClient(server).getUserAvatar("1"));
            assertEquals("The avatar of user [1] is too large.", exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void avatarTimeoutIsReportedAsRetrievalFailure() throws Exception
    {
        HttpTimeoutException timeout = new HttpTimeoutException("timed out");
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(timeout);

        WorkItemRetrievalException exception =
            assertThrows(WorkItemRetrievalException.class, () -> openProjectApiClient.getUserAvatar("1"));
        assertSame(timeout, exception.getCause());
    }

    @Test
    void avatarNetworkFailureIsReportedAsProjectManagementException() throws Exception
    {
        IOException failure = new IOException("connection reset");
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(failure);

        ProjectManagementException exception =
            assertThrows(ProjectManagementException.class, () -> openProjectApiClient.getUserAvatar("1"));
        assertSame(failure, exception.getCause());
    }

    @Test
    void errorBodiesAreAbbreviatedInTheExceptionMessages()
    {
        when(this.response.body()).thenReturn("a".repeat(5000));

        when(this.response.statusCode()).thenReturn(400);
        WorkItemRetrievalException invalid = assertThrows(WorkItemRetrievalException.class,
            () -> openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", ""));
        assertEquals("The request to the OpenProject API was invalid. [" + "a".repeat(1021) + "...]",
            invalid.getMessage());

        when(this.response.statusCode()).thenReturn(500);
        ProjectManagementException failed = assertThrows(ProjectManagementException.class,
            () -> openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", ""));
        assertEquals("Failed to retrieve the OpenProject entities. [" + "a".repeat(1021) + "...].",
            failed.getMessage());
    }

    /**
     * @param declaredLength the Content-Length of the responses, 0 for none
     * @param size the number of bytes of the responses
     * @return a started local server returning a body of {@code size} bytes to all the requests
     */
    private static HttpServer startServer(long declaredLength, int size) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, declaredLength);
            byte[] chunk = new byte[1024 * 1024];
            try (OutputStream body = exchange.getResponseBody()) {
                for (int written = 0; written < size; written += chunk.length) {
                    body.write(chunk, 0, Math.min(chunk.length, size - written));
                }
            } catch (IOException e) {
                // The client closes the connection once the limit is exceeded.
            }
        });
        server.start();
        return server;
    }

    private DefaultOpenProjectApiClient createRealClient(HttpServer server)
    {
        InetSocketAddress address = server.getAddress();
        return new DefaultOpenProjectApiClient("http://" + address.getHostString() + ':' + address.getPort(),
            new BearerTokenAuthenticator(OPEN_PROJECT_TOKEN), HTTP_CLIENT, this.descriptionCleaner);
    }

    @Test
    void allApiMethodsThrowProjectManagementExceptionOnHttpFailure() throws IOException, InterruptedException
    {
        when(response.statusCode()).thenReturn(500);

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", "")
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getProjectWorkPackages(PROJECT_NAME, OFFSET, PAGE_SIZE, "", "")
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING)
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getAvailableUsers("", OFFSET, PAGE_SIZE, FILTERS_STRING)
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING)
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getAvailableProjects("", OFFSET, PAGE_SIZE, FILTERS_STRING)
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getTypes()
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getStatuses()
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getPriorities()
        );

        assertThrows(ProjectManagementException.class, () ->
            openProjectApiClient.getWorkPackagesFormResponse("{}"));
    }

    private <T extends BaseOpenProjectObject> void assertGeneratedObjects(
        List<T> objects,
        List<String> expectedNames,
        String urlFormat
    )
    {
        assertEquals(expectedNames.size(), objects.size());

        for (int i = 0; i < expectedNames.size(); i++) {
            assertOpenProjectBaseObject(
                buildExpectedOpenProjectBaseObject(
                    i + 1,
                    expectedNames.get(i),
                    String.format(urlFormat, i + 1),
                    null
                ),
                objects.get(i)
            );
        }
    }

    private void assertGeneratedTypes(List<Type> types)
    {
        assertEquals(2, types.size());

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                1,
                "First type",
                String.format("%s/types/%s/edit/settings", OPEN_PROJECT_CONNECTION_URL, 1),
                Map.of(
                    "color", "#1A67A3"
                )
            ),
            types.get(0)
        );

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                2,
                "Second type",
                String.format("%s/types/%s/edit/settings", OPEN_PROJECT_CONNECTION_URL, 2),
                Map.of(
                    "color", "#35C53F"
                )
            ),
            types.get(1)
        );
    }

    private void assertGeneratedStatuses(List<Status> statuses)
    {
        assertEquals(2, statuses.size());

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                1,
                "First status",
                String.format("%s/statuses/%s/edit", OPEN_PROJECT_CONNECTION_URL, 1),
                Map.of(
                    "color", "#1098AD"
                )
            ),
            statuses.get(0)
        );

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                2,
                "Second status",
                String.format("%s/statuses/%s/edit", OPEN_PROJECT_CONNECTION_URL, 2),
                Map.of(
                    "color", "#A5D8FF"
                )
            ),
            statuses.get(1)
        );
        assertFalse(statuses.get(0).isClosed());
        assertTrue(statuses.get(1).isClosed());
    }

    private void assertGeneratedPriorities(List<Priority> priorities)
    {
        assertEquals(2, priorities.size());

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                1,
                "First priority",
                String.format("%s/priorities/%s/edit", OPEN_PROJECT_CONNECTION_URL, 1),
                Map.of(
                    "color", "#C5F6FA"
                )
            ),
            priorities.get(0)
        );

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                2,
                "Second priority",
                String.format("%s/priorities/%s/edit", OPEN_PROJECT_CONNECTION_URL, 2),
                Map.of(
                    "color", "#74C0FC"
                )
            ),
            priorities.get(1)
        );
    }

    private void assertGeneratedWorkPackages(List<WorkPackage> workPackages)
    {
        assertEquals(3, workPackages.size());

        assertWorkPackage(
            buildExpectedWorkPackage(
                1,
                "First subject",
                "<p>First description</p>",
                30,
                "Task",
                1,
                "Normal",
                1,
                "First project",
                1,
                "In progress",
                1,
                "First User",
                1
            ),
            workPackages.get(0)
        );

        assertWorkPackage(
            buildExpectedWorkPackage(
                2,
                "Second subject",
                "<p>Second description</p>",
                60,
                "Bug",
                2,
                "Normal",
                2,
                "Second project",
                2,
                "Ready",
                2,
                "First User",
                1
            ),
            workPackages.get(1)
        );

        assertWorkPackage(
            buildExpectedWorkPackage(
                3,
                "Third subject",
                "<p>Third description</p>",
                40,
                "Task",
                1,
                "Normal",
                1,
                "Second project",
                2,
                "Ready",
                2,
                "First User",
                1
            ),
            workPackages.get(2)
        );
    }

    private Map<String, Object> buildExpectedOpenProjectBaseObject(int id, String name, String url, Map<String,
        Object> otherProperties)
    {
        Map<String, Object> expected = new HashMap<>();
        expected.put("id", id);
        expected.put("name", name);
        expected.put(
            "self",
            new Linkable(
                name, url
            )
        );

        if (otherProperties != null) {
            expected.putAll(otherProperties);
        }

        return expected;
    }

    private void assertOpenProjectBaseObject(Map<String, Object> expected, BaseOpenProjectObject actualObject)
    {
        assertEquals(expected.get("id"), actualObject.getId());
        assertEquals(expected.get("name"), actualObject.getName());
        assertEquals(expected.get("self"), actualObject.getSelf());

        if (actualObject instanceof ColoredOpenProjectObject) {
            assertEquals(expected.get("color"), ((ColoredOpenProjectObject) actualObject).getColor());
        }
    }

    private Map<String, Object> buildExpectedWorkPackage(
        int id,
        String subject,
        String description,
        int percentageDone,
        String typeName,
        int typeId,
        String priorityName,
        int priorityId,
        String projectName,
        int projectId,
        String statusName,
        int statusId,
        String userName,
        int userId
    )
    {
        Map<String, Object> expected = new HashMap<>();
        expected.put("id", id);
        expected.put("subject", subject);
        expected.put("description", description);
        expected.put("startDate", new GregorianCalendar(2025, GregorianCalendar.AUGUST, 13).getTime());
        expected.put("dueDate", new GregorianCalendar(2025, GregorianCalendar.AUGUST, 19).getTime());
        expected.put("derivedStartDate", new GregorianCalendar(2025, GregorianCalendar.AUGUST, 14).getTime());
        expected.put("derivedDueDate", new GregorianCalendar(2025, GregorianCalendar.AUGUST, 18).getTime());
        expected.put("percentageDone", percentageDone);
        expected.put(
            "type",
            new Linkable(typeName, String.format("%s/types/%d/edit", OPEN_PROJECT_CONNECTION_URL, typeId))
        );
        expected.put(
            "priority",
            new Linkable(priorityName, String.format("%s/work_packages/%d/activity", OPEN_PROJECT_CONNECTION_URL, id))
        );
        expected.put(
            "project",
            new Linkable(projectName, String.format("%s/projects/%d", OPEN_PROJECT_CONNECTION_URL, projectId))
        );
        expected.put(
            "status",
            new Linkable(statusName, String.format("%s/statuses/%d/edit", OPEN_PROJECT_CONNECTION_URL, statusId))
        );
        expected.put(
            "author",
            new Linkable(userName, String.format("%s/users/%d", OPEN_PROJECT_CONNECTION_URL, userId))
        );
        expected.put(
            "assignee",
            new Linkable(userName, String.format("%s/users/%d", OPEN_PROJECT_CONNECTION_URL, userId))
        );
        expected.put(
            "self",
            new Linkable(subject, String.format("%s/work_packages/%d/activity", OPEN_PROJECT_CONNECTION_URL, id))
        );
        return expected;
    }

    private void assertWorkPackage(Map<String, Object> expected, WorkPackage actual)
    {
        assertEquals(expected.get("id"), actual.getId());
        assertEquals(expected.get("subject"), actual.getSubject());
        assertEquals(expected.get("description"), actual.getDescription());
        assertEquals(expected.get("startDate"), actual.getStartDate());
        assertEquals(expected.get("dueDate"), actual.getDueDate());
        assertEquals(expected.get("derivedStartDate"), actual.getDerivedStartDate());
        assertEquals(expected.get("derivedDueDate"), actual.getDerivedDueDate());
        assertEquals(expected.get("percentageDone"), actual.getPercentageDone());
        assertEquals(expected.get("type"), actual.getTypeOfWorkPackage());
        assertEquals(expected.get("priority"), actual.getPriority());
        assertEquals(expected.get("project"), actual.getProject());
        assertEquals(expected.get("status"), actual.getStatus());
        assertEquals(expected.get("author"), actual.getAuthor());
        assertEquals(expected.get("assignee"), actual.getAssignee());
        assertEquals(expected.get("self"), actual.getSelf());
    }
}
