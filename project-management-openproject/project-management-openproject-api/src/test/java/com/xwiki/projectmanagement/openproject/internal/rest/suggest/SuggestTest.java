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
package com.xwiki.projectmanagement.openproject.internal.rest.suggest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class SuggestTest
{
    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @InjectMockComponents
    private Suggest suggest;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @MockComponent
    private OpenProjectApiClient openProjectApiClient;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String INSTANCE = "instance";

    private static final String WIKI = "wiki";

    private static final String EMPTY_STRING = "";

    private static final String SEARCH_STRING = "searchString";

    @BeforeEach
    public void setUp()
    {
        when(this.openProjectAccess.resolve(INSTANCE))
            .thenReturn(OpenProjectAccessResult.authenticated(this.openProjectApiClient));
    }

    @Test
    public void getSuggestionsForIdentifierTest() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> workPackagePaginatedResult = new PaginatedResult<>();

        WorkPackage workPackage = new WorkPackage();
        workPackage.setId(1);
        workPackage.setName("workPackage");
        workPackage.setSelf(new Linkable("1", "location"));

        workPackagePaginatedResult.setItems(List.of(workPackage));

        when(this.openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(workPackagePaginatedResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", SEARCH_STRING, 10, null);

        verify(openProjectApiClient).getWorkPackages(
            1,
            PAGE_SIZE,
            String.format(
                "[{\"%s\":{\"operator\":\"~\",\"values\":[\"%s\"]}}]",
                "subject",
                SEARCH_STRING.toLowerCase()
            ),
            EMPTY_STRING
        );

        assertInstanceOf(List.class, response.getEntity());
        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();

        assertEquals(1, responseEntity.size());
        assertEquals(String.valueOf(workPackage.getId()), responseEntity.get(0).get("label"));
        assertEquals(String.valueOf(workPackage.getId()), responseEntity.get(0).get("value"));
        assertEquals(workPackage.getName(), responseEntity.get(0).get("hint"));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchingAnId() throws ProjectManagementException
    {
        WorkPackage byId = createWorkPackage(11, "Eleventh");
        WorkPackage bySubject = createWorkPackage(5, "Fix the 11 failing tests");
        PaginatedResult<WorkPackage> idResult = new PaginatedResult<>();
        idResult.setItems(List.of(byId));
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(bySubject, byId));
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenReturn(idResult);
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE,
            "[{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]", EMPTY_STRING)).thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", "11", PAGE_SIZE, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("11", "5"),
            responseEntity.stream().map(suggestion -> suggestion.get("value")).collect(Collectors.toList()));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchedIdFails() throws ProjectManagementException
    {
        WorkPackage bySubject = createWorkPackage(5, "Fix the 11 failing tests");
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(bySubject));
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenThrow(new ProjectManagementException("Invalid filter"));
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE,
            "[{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]", EMPTY_STRING)).thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", "11", PAGE_SIZE, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("5"),
            responseEntity.stream().map(suggestion -> suggestion.get("value")).collect(Collectors.toList()));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchingAnIdLimitsToPageSize() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> idResult = new PaginatedResult<>();
        idResult.setItems(List.of(createWorkPackage(11, "Eleventh")));
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(createWorkPackage(5, "Fix the 11 failing tests")));
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenReturn(idResult);
        when(this.openProjectApiClient.getWorkPackages(1, 1, "[{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]",
            EMPTY_STRING)).thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", "11", 1, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("11"),
            responseEntity.stream().map(suggestion -> suggestion.get("value")).collect(Collectors.toList()));
    }

    @Test
    void getSuggestionsForIdentifierWhenSearchingANumberTooLongForAnId() throws ProjectManagementException
    {
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of());
        when(this.openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(subjectResult);

        suggest.getSuggestions(WIKI, INSTANCE, "id", "12345678901", PAGE_SIZE, null);

        verify(this.openProjectApiClient, never()).getWorkPackages(eq(1), eq(1), anyString(), anyString());
    }

    @Test
    void getSuggestionsForParentWhenSearchingAnId() throws ProjectManagementException
    {
        Type task = new Type();
        task.setId(1);
        Type milestone = new Type();
        milestone.setId(2);
        milestone.setMilestone(true);
        when(this.openProjectApiClient.getTypes()).thenReturn(new PaginatedResult<>(List.of(task, milestone), 0, 2, 2));
        PaginatedResult<WorkPackage> idResult = new PaginatedResult<>();
        idResult.setItems(List.of(createWorkPackage(11, "Eleventh")));
        PaginatedResult<WorkPackage> subjectResult = new PaginatedResult<>();
        subjectResult.setItems(List.of(createWorkPackage(5, "Fix the 11 failing tests")));
        String typeFilter = "{\"type\":{\"operator\":\"=\",\"values\":[\"1\"]}}";
        when(this.openProjectApiClient.getWorkPackages(1, 1,
            "[" + typeFilter + ",{\"id\":{\"operator\":\"=\",\"values\":[\"11\"]}}]", EMPTY_STRING))
            .thenReturn(idResult);
        when(this.openProjectApiClient.getWorkPackages(1, PAGE_SIZE,
            "[" + typeFilter + ",{\"subject\":{\"operator\":\"~\",\"values\":[\"11\"]}}]", EMPTY_STRING))
            .thenReturn(subjectResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "parent", "11", PAGE_SIZE, null);

        List<Map<String, String>> responseEntity = (List<Map<String, String>>) response.getEntity();
        assertEquals(List.of("#11: Eleventh", "#5: Fix the 11 failing tests"),
            responseEntity.stream().map(suggestion -> suggestion.get("label")).collect(Collectors.toList()));
    }

    @Test
    public void getSuggestionsForPrioritiesTest() throws ProjectManagementException
    {
        PaginatedResult<Priority> priorityPaginatedResult = new PaginatedResult<>();

        Priority priority = new Priority();
        priority.setId(1);
        priority.setName("priority");
        priority.setSelf(new Linkable("1", "location"));

        priorityPaginatedResult.setItems(List.of(priority));

        when(this.openProjectApiClient.getPriorities()).thenReturn(priorityPaginatedResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "priorities", SEARCH_STRING, 10, null);

        verify(openProjectApiClient).getPriorities();

        verifySuccessResponse(response, priorityPaginatedResult.getItems());
    }

    @Test
    public void getSuggestionsForStatusesTest() throws ProjectManagementException
    {
        PaginatedResult<Status> statusesPaginatedResult = new PaginatedResult<>();

        Status status = new Status();
        status.setId(1);
        status.setName("status");
        status.setSelf(new Linkable("1", "location"));

        statusesPaginatedResult.setItems(List.of(status));

        when(this.openProjectApiClient.getStatuses()).thenReturn(statusesPaginatedResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "statuses", SEARCH_STRING, 10, null);

        verify(openProjectApiClient).getStatuses();

        verifySuccessResponse(response, statusesPaginatedResult.getItems());
    }

    @Test
    public void getSuggestionsForProjectsTest() throws ProjectManagementException
    {
        PaginatedResult<Project> projectPaginatedResult = new PaginatedResult<>();

        Project project = new Project();
        project.setId(1);
        project.setName("project");
        project.setSelf(new Linkable("1", "location"));

        projectPaginatedResult.setItems(List.of(project));

        when(this.openProjectApiClient.getProjects(anyInt(), anyInt(), anyString())).thenReturn(
            projectPaginatedResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "projects", SEARCH_STRING, 10, null);

        verify(openProjectApiClient).getProjects(
            OFFSET,
            PAGE_SIZE,
            String.format("[{\"%s\":{\"operator\":\"~\",\"values\":[\"%s\"]}}]", "name", SEARCH_STRING.toLowerCase())
        );

        verifySuccessResponse(response, projectPaginatedResult.getItems());
    }

    @Test
    public void getSuggestionsForTypeTest() throws ProjectManagementException
    {
        PaginatedResult<Type> typesPaginatedResult = new PaginatedResult<>();

        Type type = new Type();
        type.setId(1);
        type.setName("type");
        type.setSelf(new Linkable("1", "location"));

        typesPaginatedResult.setItems(List.of(type));

        when(this.openProjectApiClient.getTypes()).thenReturn(typesPaginatedResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "types", SEARCH_STRING, 10, null);
        verify(openProjectApiClient).getTypes();
        verifySuccessResponse(response, typesPaginatedResult.getItems());
    }

    @Test
    public void getSuggestionsForUsersTest() throws ProjectManagementException
    {
        PaginatedResult<User> usersPaginatedResult = new PaginatedResult<>();

        User user = new User();
        user.setId(1);
        user.setName("user");
        user.setSelf(new Linkable("1", "location"));

        usersPaginatedResult.setItems(List.of(user));

        when(this.openProjectApiClient.getUsers(anyInt(), anyInt(), anyString())).thenReturn(usersPaginatedResult);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", SEARCH_STRING, 10, null);

        verify(openProjectApiClient).getUsers(
            OFFSET,
            PAGE_SIZE,
            String.format("[{\"%s\":{\"operator\":\"~\",\"values\":[\"%s\"]}}]", "name", SEARCH_STRING.toLowerCase())
        );

        verifySuccessResponse(response, usersPaginatedResult.getItems());
    }

    @Test
    public void getSuggestionsForUnknownTest()
    {
        Response response = suggest.getSuggestions(WIKI, INSTANCE, "unknown", SEARCH_STRING, 10, null);

        verifySuccessResponse(response, List.of());
    }

    @Test
    public void getSuggestionsServerError() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getTypes()).thenThrow(new ProjectManagementException(
            "Failed to retrieve the OpenProject entities. [internal error details from http://openproject.local]."));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "types", SEARCH_STRING, 10, null);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        // The details of the failure are only logged, never sent to the client.
        assertEquals("Failed to retrieve the suggestions from OpenProject.", response.getEntity());
        assertEquals("Failed to retrieve the [types] suggestions from the OpenProject instance [instance]. Root cause: "
            + "[ProjectManagementException: Failed to retrieve the OpenProject entities. [internal error details from "
            + "http://openproject.local].]", this.logCapture.getMessage(0));
    }

    @Test
    public void noConfigurationWasFoundTest()
    {
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.notConnected());

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "", SEARCH_STRING, 10, null);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void getSuggestionsForVersions() throws ProjectManagementException
    {
        Version version1 = createVersion(1, "1.2.3");
        Version version2 = createVersion(2, "2.0");
        Version version3 = createVersion(3, "1.2.4");
        Version unnamed = new Version();
        unnamed.setId(4);
        when(this.openProjectApiClient.getVersions())
            .thenReturn(new PaginatedResult<>(List.of(version1, version2, version3, unnamed), 0, 4, 4));

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "versions", "1.2", 1, null);

        verifySuccessResponse(response, List.of(version1));
    }

    private static WorkPackage createWorkPackage(int id, String subject)
    {
        WorkPackage workPackage = new WorkPackage();
        workPackage.setId(id);
        workPackage.setName(subject);
        workPackage.setSelf(new Linkable(String.valueOf(id), "location" + id));
        return workPackage;
    }

    private static Version createVersion(int id, String name)
    {
        Version version = new Version();
        version.setId(id);
        version.setName(name);
        version.setSelf(new Linkable(name, "location" + id));
        return version;
    }

    private void verifySuccessResponse(Response response, List<? extends BaseOpenProjectObject> generatedObjects)
    {
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(
            generatedObjects
                .stream()
                .map(
                    (el) -> Map.of(
                        "value", el.getId().toString(),
                        "label", el.getName(),
                        "url", el.getSelf().getLocation()
                    )
                )
                .collect(Collectors.toList()),
            response.getEntity()
        );
    }
}
