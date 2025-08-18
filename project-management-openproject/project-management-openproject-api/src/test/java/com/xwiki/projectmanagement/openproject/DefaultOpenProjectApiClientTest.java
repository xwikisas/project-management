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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xwiki.test.junit5.mockito.ComponentTest;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.internal.DefaultOpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import utils.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class DefaultOpenProjectApiClientTest
{
    private final TestUtils testUtils = new TestUtils();

    @Mock
    private HttpClient client;

    @Mock
    private HttpResponse<String> response;

    @InjectMocks
    private DefaultOpenProjectApiClient openProjectApiClient;

    private AutoCloseable closeable;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String OPEN_PROJECT_CONNECTION_URL = "http://localhost.com";

    private static final String OPEN_PROJECT_TOKEN = "token";

    private static final String PROJECT_NAME = "projectName";

    private static final String FILTERS_STRING = "generatedFiltersString";

    private static final String SORT_BY_STRING = "generatedSortByString";

    private static final String SELECTED_ELEMENTS_STRING = "selectedElementsString";

    @BeforeEach
    void setUp() throws Exception
    {
        closeable = MockitoAnnotations.openMocks(this);
        openProjectApiClient = new DefaultOpenProjectApiClient(OPEN_PROJECT_CONNECTION_URL, OPEN_PROJECT_TOKEN, client);
        when(this.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(this.response);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        closeable.close();
    }

    @Test
    public void getWorkPackagesTest() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(testUtils.getWorkPackagesValidResponse());

        PaginatedResult<WorkPackage> workPackages = openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, "", "");

        assertEquals(3, workPackages.getTotalItems());
        assertEquals(PAGE_SIZE, workPackages.getPageSize());
        assertEquals(OFFSET, workPackages.getPage());

        assertGeneratedWorkPackages(workPackages.getItems());
    }

    @Test
    public void getProjectWorkPackagesTest() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(testUtils.getWorkPackagesValidResponse());

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
    public void getUsersTest() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(testUtils.getUsersValidResponse());

        PaginatedResult<User> users = openProjectApiClient.getUsers(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(2, users.getTotalItems());
        assertEquals(PAGE_SIZE, users.getPageSize());
        assertEquals(OFFSET, users.getPage());

        assertGeneratedUsers(users.getItems());
    }

    @Test
    void getProjectsTest() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(testUtils.getProjectsValidResponse());

        PaginatedResult<Project> projects = openProjectApiClient.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING);

        assertEquals(3, projects.getTotalItems());
        assertEquals(PAGE_SIZE, projects.getPageSize());
        assertEquals(OFFSET, projects.getPage());

        assertGeneratedProjects(projects.getItems());
    }

    @Test
    void getTypesTest() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(testUtils.getTypesValidResponse());

        PaginatedResult<Type> types = openProjectApiClient.getTypes();

        assertEquals(2, types.getTotalItems());
        assertEquals(2, types.getPageSize());
        assertEquals(OFFSET, 1);

        assertGeneratedTypes(types.getItems());
    }

    @Test
    public void getStatusesTest() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(testUtils.getStatusesValidResponse());

        PaginatedResult<Status> statuses = openProjectApiClient.getStatuses();

        assertEquals(2, statuses.getTotalItems());
        assertEquals(2, statuses.getPageSize());
        assertEquals(OFFSET, 1);

        assertGeneratedStatuses(statuses.getItems());
    }

    @Test
    public void getPrioritiesTest() throws IOException, ProjectManagementException
    {
        when(this.response.body()).thenReturn(testUtils.getPrioritiesValidResponse());

        PaginatedResult<Priority> priorities = openProjectApiClient.getPriorities();

        assertEquals(2, priorities.getTotalItems());
        assertEquals(2, priorities.getPageSize());
        assertEquals(OFFSET, 1);

        assertGeneratedPriorities(priorities.getItems());
    }

    @Test
    public void projectWorkPackagesUrlParametersTest()
        throws ProjectManagementException, IOException, InterruptedException
    {
        when(this.response.body()).thenReturn("{}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        openProjectApiClient.getProjectWorkPackages(PROJECT_NAME, OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);

        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = captor.getValue();
        String generatedUri = request.uri().toString();
        assertTrue(generatedUri.startsWith(String.format("%s/api/v3/projects/%s/work_packages",
            OPEN_PROJECT_CONNECTION_URL, PROJECT_NAME)));
        assertTrue(generatedUri.contains("offset=" + OFFSET));
        assertTrue(generatedUri.contains("pageSize=" + PAGE_SIZE));
        assertTrue(generatedUri.contains("filters=" + FILTERS_STRING));
        assertTrue(generatedUri.contains("sortBy=" + SORT_BY_STRING));
        assertFalse(generatedUri.contains("selectedElements=" + SELECTED_ELEMENTS_STRING));
    }

    @Test
    public void workPackagesUrlParametersTest() throws ProjectManagementException, IOException, InterruptedException
    {
        when(this.response.body()).thenReturn("{}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        openProjectApiClient.getWorkPackages(OFFSET, PAGE_SIZE, FILTERS_STRING, SORT_BY_STRING);

        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = captor.getValue();
        String generatedUri = request.uri().toString();
        assertTrue(generatedUri.startsWith(String.format("%s/api/v3/work_packages", OPEN_PROJECT_CONNECTION_URL)));
        assertTrue(generatedUri.contains("offset=" + OFFSET));
        assertTrue(generatedUri.contains("pageSize=" + PAGE_SIZE));
        assertTrue(generatedUri.contains("filters=" + FILTERS_STRING));
        assertTrue(generatedUri.contains("sortBy=" + SORT_BY_STRING));
        assertFalse(generatedUri.contains("selectedElements=" + SELECTED_ELEMENTS_STRING));
    }

    @Test
    public void allApiMethodsThrowProjectManagementExceptionOnHttpFailure() throws IOException, InterruptedException
    {
        when(
            this.client.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
            )
        )
            .thenThrow(IOException.class);

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
            openProjectApiClient.getProjects(OFFSET, PAGE_SIZE, FILTERS_STRING)
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
    }

    private void assertGeneratedUsers(List<User> users)
    {
        assertEquals(2, users.size());

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                1,
                "First user",
                String.format("%s/users/%s", OPEN_PROJECT_CONNECTION_URL, 1),
                null
            ),
            users.get(0)
        );

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                2,
                "Second user",
                String.format("%s/users/%s", OPEN_PROJECT_CONNECTION_URL, 2),
                null
            ),
            users.get(1)
        );
    }

    private void assertGeneratedProjects(List<Project> projects)
    {
        assertEquals(3, projects.size());

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                1,
                "First project",
                String.format("%s/projects/%s", OPEN_PROJECT_CONNECTION_URL, 1),
                null
            ),
            projects.get(0)
        );

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                2,
                "Second project",
                String.format("%s/projects/%s", OPEN_PROJECT_CONNECTION_URL, 2),
                null
            ),
            projects.get(1)
        );

        assertOpenProjectBaseObject(
            buildExpectedOpenProjectBaseObject(
                3,
                "Third project",
                String.format("%s/projects/%s", OPEN_PROJECT_CONNECTION_URL, 3),
                null
            ),
            projects.get(2)
        );
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
                "", url
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

        if (actualObject instanceof Type) {
            Type typeObject = (Type) actualObject;
            assertEquals(expected.get("color"), typeObject.getColor());
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
