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
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class SuggestTest
{
    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @InjectMockComponents
    private Suggest suggest;

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
        when(this.openProjectConfiguration.getOpenProjectApiClient(INSTANCE)).thenReturn(this.openProjectApiClient);
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

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "id", SEARCH_STRING, 10);

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
        assertEquals(responseEntity.get(0).get("label"), String.valueOf(workPackage.getId()));
        assertEquals(responseEntity.get(0).get("value"), String.valueOf(workPackage.getId()));
        assertEquals(responseEntity.get(0).get("hint"), workPackage.getName());
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

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "priorities", SEARCH_STRING, 10);

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

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "statuses", SEARCH_STRING, 10);

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

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "projects", SEARCH_STRING, 10);

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

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "types", SEARCH_STRING, 10);
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

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "users", SEARCH_STRING, 10);

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
        Response response = suggest.getSuggestions(WIKI, INSTANCE, "unknown", SEARCH_STRING, 10);

        verifySuccessResponse(response, List.of());
    }

    @Test
    public void getSuggestionsServerError() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getTypes()).thenThrow(ProjectManagementException.class);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "types", SEARCH_STRING, 10);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

        assertInstanceOf(ProjectManagementException.class, response.getEntity());
    }

    @Test
    public void noConfigurationWasFoundTest()
    {
        when(this.openProjectConfiguration.getOpenProjectApiClient(INSTANCE)).thenReturn(null);

        Response response = suggest.getSuggestions(WIKI, INSTANCE, "", SEARCH_STRING, 10);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
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
