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

package com.xwiki.projectmanagement.openproject.internal.rest.workItems;

import java.io.IOException;
import java.util.List;

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xwiki.licensing.Licensor;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.CreateWorkPackage;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.User;

import utils.OpenProjectTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class HandleWorkItemsTest
{
    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectApiClient openProjectApiClient;

    @MockComponent
    private Licensor licensor;

    @MockComponent
    private XWikiContext xContext;

    @Mock
    private Provider<XWikiContext> xContextProvider;

    @InjectMockComponents
    private HandleWorkItems handleWorkItems;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String INSTANCE = "instance";

    private static final String WIKI = "wiki";

    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp()
    {
        when(this.openProjectConfiguration.getOpenProjectApiClient(INSTANCE)).thenReturn(this.openProjectApiClient);
        when(this.xContextProvider.get()).thenReturn(this.xContext);
        when(this.xContext.getWikiId()).thenReturn(WIKI);
        when(this.licensor.hasLicensure(any(DocumentReference.class))).thenReturn(true);
    }
    // TODO: add test for default values of the json request when creating a work package

    @Test
    public void getAvailableProjectsReturnsConflictWhenTokenIsNullTest() throws ProjectManagementException
    {
        when(this.openProjectConfiguration.getOpenProjectApiClient(INSTANCE)).thenReturn(null);

        Response response = this.handleWorkItems.getAvailableProjects(WIKI, INSTANCE);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    }

    @Test
    public void getAvailableProjectsTest() throws ProjectManagementException, IOException
    {
        List<Project> projects = generateProjects();

        PaginatedResult<Project> paginatedProjects =
            new PaginatedResult<>(projects, OFFSET, PAGE_SIZE, projects.size());

        String jsonResponse = OpenProjectTestUtils.getCreateWorkPackageProjectsFormResponse();
        JsonNode node = this.mapper.readTree(jsonResponse);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(node);
        when(this.openProjectApiClient.getAvailableProjects(anyString(), anyInt(), anyInt(), anyString())).thenReturn(
            paginatedProjects);

        Response response = this.handleWorkItems.getAvailableProjects(WIKI, INSTANCE);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(projects, response.getEntity());
    }

    @Test
    public void getAvailableProjectsThrowsProjectManagementExceptionTest() throws ProjectManagementException,
        JsonProcessingException
    {
        String jsonResponse = "{\"_type\":\"Error\"}";
        JsonNode node = this.mapper.readTree(jsonResponse);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(node);

        assertThrows(
            ProjectManagementException.class,
            () -> this.handleWorkItems.getAvailableProjects(WIKI, INSTANCE)
        );
    }

    @Test
    public void createWorkPackageReturnsForbiddenWhenLicenseIsMissing() throws ProjectManagementException
    {
        when(this.licensor.hasLicensure(any(DocumentReference.class))).thenReturn(false);

        Response response = this.handleWorkItems.createWorkPackage(WIKI, INSTANCE, new CreateWorkPackage());

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }

    @Test
    public void createWorkPackageValidationTest() throws ProjectManagementException, IOException
    {
        CreateWorkPackage createWorkPackage = generateCreateWorkPackage();
        User user1 = generateUser(1, "User 1");
        User user2 = generateUser(2, "User 2");

        JsonNode workPackagesNode = mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageValidationFailsApiResponse()
        );
        when(openProjectApiClient.getWorkPackagesFormResponse(anyString()))
            .thenReturn(workPackagesNode);

        when(openProjectApiClient.getAvailableUsers(anyString(), anyInt(), anyInt(), anyString()))
            .thenReturn(new PaginatedResult<>(List.of(user1, user2), OFFSET, PAGE_SIZE, 2));

        Response response = handleWorkItems.createWorkPackage(WIKI, INSTANCE, createWorkPackage);

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(openProjectApiClient).getWorkPackagesFormResponse(requestCaptor.capture());

        JsonNode expectedRequest = mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageRequestExample()
        );
        JsonNode actualRequest = mapper.readTree(requestCaptor.getValue());
        assertEquals(expectedRequest, actualRequest, "Request JSON should match expected");

        JsonNode expectedResponse = mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageValidationFailsResponse()
        );
        JsonNode actualResponse = mapper.valueToTree(response.getEntity());
        assertEquals(expectedResponse, actualResponse, "Response JSON should match expected");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void createWorkPackageTest() throws ProjectManagementException, IOException
    {
        String validationSuccessResponseJsonString =
            OpenProjectTestUtils.getCreateWorkPackageValidationSuccessResponse();

        JsonNode validationSuccessResponseNode = this.mapper.readTree(validationSuccessResponseJsonString);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(
            validationSuccessResponseNode);
        when(this.openProjectApiClient.createWorkPackage(anyString(), anyString())).thenReturn(this.mapper.createObjectNode());

        Response response = this.handleWorkItems.createWorkPackage(WIKI, INSTANCE, new CreateWorkPackage());

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void createWorkPackageFails() throws ProjectManagementException, IOException
    {
        String validationSuccessResponseJsonString =
            OpenProjectTestUtils.getCreateWorkPackageValidationSuccessResponse();

        JsonNode validationSuccessResponseNode = this.mapper.readTree(validationSuccessResponseJsonString);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(
            validationSuccessResponseNode);
        when(this.openProjectApiClient.createWorkPackage(anyString(), anyString())).thenThrow(
            new ProjectManagementException("Error creating work package"));

        assertThrows(
            ProjectManagementException.class,
            () -> this.handleWorkItems.createWorkPackage(WIKI, INSTANCE, new CreateWorkPackage())
        );
    }

    private List<Project> generateProjects()
    {
        Project firstProject = new Project();
        firstProject.setId(1);
        Project secondProject = new Project();
        secondProject.setId(2);
        return List.of(firstProject, secondProject);
    }

    private CreateWorkPackage generateCreateWorkPackage()
    {
        CreateWorkPackage createWorkPackage = new CreateWorkPackage();
        createWorkPackage.setDescription("description");
        createWorkPackage.setSubject("subject");
        createWorkPackage.setAssignee("/api/v3/users/1");
        createWorkPackage.setProject("/api/v3/projects/1");
        createWorkPackage.setType("/api/v3/types/1");

        return createWorkPackage;
    }

    private User generateUser(Integer id, String name)
    {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setSelf(new Linkable(name, String.format("/api/v3/users/%s", id)));
        return user;
    }
}
