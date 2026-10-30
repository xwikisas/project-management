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

package com.xwiki.projectmanagement.openproject.internal.rest.workPackages;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectTestUtils;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.CreateWorkPackage;
import com.xwiki.projectmanagement.openproject.model.Project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ComponentTest
class HandleWorkPackagesTest
{
    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private OpenProjectApiClient openProjectApiClient;

    @InjectMockComponents
    private HandleWorkPackages handleWorkPackages;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String INSTANCE = "instance";

    private static final String WIKI = "wiki";

    private static final String DEFAULT_VALUE = "defaultValue";

    private static final String EMBEDDED = "_embedded";

    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp()
    {
        when(this.openProjectAccess.resolve(INSTANCE))
            .thenReturn(OpenProjectAccessResult.authenticated(this.openProjectApiClient));
    }

    @Test
    void getAvailableProjectsReturnsConflictWhenNotConnected() throws ProjectManagementException
    {
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.notConnected());

        Response response = this.handleWorkPackages.getAvailableProjects(WIKI, INSTANCE, "", OFFSET, PAGE_SIZE, null);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    }

    @Test
    void getAvailableProjectsReturnsConflictWithAnonymousAccess() throws ProjectManagementException
    {
        when(this.openProjectAccess.resolve(INSTANCE))
            .thenReturn(OpenProjectAccessResult.anonymous(this.openProjectApiClient));

        Response response = this.handleWorkPackages.getAvailableProjects(WIKI, INSTANCE, "", OFFSET, PAGE_SIZE, null);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.openProjectApiClient);
    }

    @Test
    void getAvailableAssignees() throws ProjectManagementException
    {
        when(this.openProjectApiClient.getAvailableUsers(anyString(), anyInt(), anyInt(), anyString()))
            .thenReturn(new PaginatedResult<>(List.of(), 0, PAGE_SIZE, 0));

        Response response =
            this.handleWorkPackages.getAvailableAssignees(WIKI, INSTANCE, "/api/v3/projects/demo-1", "", PAGE_SIZE,
                null);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(this.openProjectApiClient).getAvailableUsers("/api/v3/projects/demo-1/available_assignees", 0,
            PAGE_SIZE, "");
    }

    @Test
    void getAvailableAssigneesWithSearch() throws Exception
    {
        when(this.openProjectApiClient.getAvailableUsers(anyString(), anyInt(), anyInt(), anyString()))
            .thenReturn(new PaginatedResult<>(List.of(), 0, PAGE_SIZE, 0));
        String search = "a\"]}},{\"id\":{\"operator\":\"!\",\"values\":[\"0\\";

        this.handleWorkPackages.getAvailableAssignees(WIKI, INSTANCE, "/api/v3/projects/demo-1", search, PAGE_SIZE,
            null);

        ArgumentCaptor<String> filters = ArgumentCaptor.forClass(String.class);
        verify(this.openProjectApiClient).getAvailableUsers(eq("/api/v3/projects/demo-1/available_assignees"),
            eq(0), eq(PAGE_SIZE), filters.capture());
        JsonNode filter = this.mapper.readTree(filters.getValue());
        assertEquals(1, filter.size());
        assertEquals(search, filter.get(0).get("name").get("values").get(0).asText());
        assertEquals("~", filter.get(0).get("name").get("operator").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = { "@other.example/api/v3/projects/1", "/api/v3/projects/1?x=",
        "/api/v3/projects/1/../../users", "/api/v3/users/1", "api/v3/projects/1" })
    void getAvailableAssigneesWithInvalidProject(String project) throws ProjectManagementException
    {
        Response response =
            this.handleWorkPackages.getAvailableAssignees(WIKI, INSTANCE, project, "", PAGE_SIZE, null);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verify(this.openProjectApiClient, never()).getAvailableUsers(anyString(), anyInt(), anyInt(), anyString());
    }

    @Test
    void getAvailableProjectsReturnsValueLabelPairs() throws ProjectManagementException, IOException
    {
        List<Project> projects = generateProjects();

        PaginatedResult<Project> paginatedProjects =
            new PaginatedResult<>(projects, OFFSET, PAGE_SIZE, projects.size());

        String jsonResponse = OpenProjectTestUtils.getCreateWorkPackageProjectsFormResponse();
        JsonNode node = this.mapper.readTree(jsonResponse);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(node);
        when(this.openProjectApiClient.getAvailableProjects(anyString(), anyInt(), anyInt(), anyString())).thenReturn(
            paginatedProjects);

        Response response = this.handleWorkPackages.getAvailableProjects(WIKI, INSTANCE, "", OFFSET, PAGE_SIZE, null);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<Map<String, String>> expected = List.of(
            Map.of("value", "/api/v3/projects/1", "label", "Project 1"),
            Map.of("value", "/api/v3/projects/2", "label", "Project 2")
        );
        assertEquals(expected, response.getEntity());
    }

    @Test
    void getAvailableProjectsThrowsOnErrorFormResponse() throws ProjectManagementException,
        JsonProcessingException
    {
        String jsonResponse = "{\"_type\":\"Error\"}";
        JsonNode node = this.mapper.readTree(jsonResponse);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(node);

        assertThrows(
            ProjectManagementException.class,
            () -> this.handleWorkPackages.getAvailableProjects(WIKI, INSTANCE, "", OFFSET, PAGE_SIZE, null)
        );
    }

    @Test
    void createWorkPackageReturnsValidationErrors() throws ProjectManagementException, IOException
    {
        CreateWorkPackage createWorkPackage = generateCreateWorkPackage();

        createWorkPackage.setFormOnly(true);

        JsonNode workPackagesNode = mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageValidationFailsApiResponse()
        );
        when(openProjectApiClient.getWorkPackagesFormResponse(anyString()))
            .thenReturn(workPackagesNode);

        Response response = handleWorkPackages.createWorkPackage(WIKI, INSTANCE, createWorkPackage);

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
    void createWorkPackageCommitsValidatedPayload() throws ProjectManagementException, IOException
    {
        String validationSuccessResponseJsonString =
            OpenProjectTestUtils.getCreateWorkPackageValidationSuccessResponse();

        JsonNode validationSuccessResponseNode = this.mapper.readTree(validationSuccessResponseJsonString);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(
            validationSuccessResponseNode);
        JsonNode createdWorkPackage = this.mapper.readTree("{\"id\":42}");
        when(this.openProjectApiClient.createWorkPackage(anyString(), anyString())).thenReturn(createdWorkPackage);

        Response response = this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, new CreateWorkPackage());

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertSame(createdWorkPackage, response.getEntity());
        // The validated payload is committed to the link given by the form.
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.openProjectApiClient).createWorkPackage(eq("/api/v3/work_packages"), payloadCaptor.capture());
        assertEquals(validationSuccessResponseNode.path("_embedded").path("payload"),
            this.mapper.readTree(payloadCaptor.getValue()));
    }

    @Test
    void createWorkPackageFails() throws ProjectManagementException, IOException
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
            () -> this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, new CreateWorkPackage())
        );
    }

    @Test
    void getEditFormPrefillsTheCurrentValues() throws Exception
    {
        when(this.openProjectApiClient.getWorkPackageFormResponse("42", "")).thenReturn(editForm());

        Response response = this.handleWorkPackages.getEditForm(WIKI, INSTANCE, "42");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Map<String, Object> options = getOptions(response);
        assertEquals("Fix the login", field(options, "subject").get(DEFAULT_VALUE));
        assertEquals("The login fails.", field(options, "description").get(DEFAULT_VALUE));
        assertEquals("/api/v3/types/2", field(options, "type").get(DEFAULT_VALUE));
        // The current priority is no longer allowed: the default of the schema is proposed instead.
        assertEquals("/api/v3/priorities/8", field(options, "priority").get(DEFAULT_VALUE));
        assertEquals(false, field(options, "status").get("writable"));
        assertEquals("/api/v3/users/4", field(options, "assignee").get(DEFAULT_VALUE));
        assertEquals("Alice", field(options, "assignee").get("defaultLabel"));
        assertEquals("/api/v3/work_packages/7", field(options, "parent").get(DEFAULT_VALUE));
        assertEquals("#7: Epic", field(options, "parent").get("defaultLabel"));
        assertEquals("2026-10-01", field(options, "startDate").get(DEFAULT_VALUE));
        assertNull(field(options, "dueDate").get(DEFAULT_VALUE));
        assertEquals(3, options.get("lockVersion"));
        assertEquals("/api/v3/projects/5", options.get("projectHref"));
    }

    @Test
    void getEditFormWhenTheResponseIsNotAForm() throws Exception
    {
        when(this.openProjectApiClient.getWorkPackageFormResponse("42", ""))
            .thenReturn(this.mapper.readTree("{\"_type\":\"Error\"}"));

        ProjectManagementException exception = assertThrows(ProjectManagementException.class,
            () -> this.handleWorkPackages.getEditForm(WIKI, INSTANCE, "42"));

        assertEquals("Failed to retrieve the edit form for work package [42]", exception.getMessage());
        assertEquals("Unexpected response type: Error", exception.getCause().getMessage());
    }

    @Test
    void editingRequiresAnAuthenticatedConnection() throws Exception
    {
        when(this.openProjectAccess.resolve(INSTANCE))
            .thenReturn(OpenProjectAccessResult.anonymous(this.openProjectApiClient));

        Response editForm = this.handleWorkPackages.getEditForm(WIKI, INSTANCE, "42");
        Response update = this.handleWorkPackages.updateWorkPackage(WIKI, INSTANCE, "42", new CreateWorkPackage());

        assertEquals(Response.Status.CONFLICT.getStatusCode(), editForm.getStatus());
        assertEquals(Response.Status.CONFLICT.getStatusCode(), update.getStatus());
        assertEquals("You must authenticate to the OpenProject instance from XWiki before creating or editing a "
            + "work package.", update.getEntity());
        verifyNoInteractions(this.openProjectApiClient);
    }

    @Test
    void updateWorkPackageCommitsTheValidatedPayload() throws Exception
    {
        when(this.openProjectApiClient.getWorkPackageFormResponse(eq("42"), jsonEq("{\"subject\":\"New subject\","
            + "\"lockVersion\":3,\"description\":{\"raw\":\"New description\"},"
            + "\"_links\":{\"type\":{\"href\":\"/api/v3/types/1\"}}}"))).thenReturn(editForm());
        // The empty links of the validated payload are not committed.
        ObjectNode committedPayload = (ObjectNode) editForm().path(EMBEDDED).path("payload");
        ((ObjectNode) committedPayload.path("_links")).remove(List.of("category", "version"));
        JsonNode updated = this.mapper.readTree("{\"id\":42}");
        when(this.openProjectApiClient.updateWorkPackage(eq("/api/v3/work_packages/42"),
            jsonEq(committedPayload.toString()))).thenReturn(updated);
        CreateWorkPackage changes = new CreateWorkPackage();
        changes.setSubject("New subject");
        changes.setDescription("New description");
        changes.setType("/api/v3/types/1");
        changes.setAssignee("");
        changes.setLockVersion(3);

        Response response = this.handleWorkPackages.updateWorkPackage(WIKI, INSTANCE, "42", changes);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertSame(updated, response.getEntity());
    }

    @Test
    void updateWorkPackageReturnsTheValidationErrorWithoutCommitting() throws Exception
    {
        ObjectNode form = editForm();
        ((ObjectNode) form.path(EMBEDDED)).set("validationErrors", this.mapper.readTree(
            "{\"subject\":{\"_type\":\"Error\",\"message\":\"Subject can't be blank.\"}}"));
        when(this.openProjectApiClient.getWorkPackageFormResponse(eq("42"), anyString())).thenReturn(form);

        Response response = this.handleWorkPackages.updateWorkPackage(WIKI, INSTANCE, "42", new CreateWorkPackage());

        assertEquals(422, response.getStatus());
        Map<String, Object> options = getOptions(response);
        assertEquals("Subject can't be blank.", options.get("validationMessage"));
        // The form is sent back filled with the values of the work package.
        assertEquals("Fix the login", field(options, "subject").get(DEFAULT_VALUE));
        // A refused update must not be committed.
        verify(this.openProjectApiClient, never()).updateWorkPackage(anyString(), anyString());
    }

    @Test
    void updateWorkPackageFails() throws Exception
    {
        when(this.openProjectApiClient.getWorkPackageFormResponse(eq("42"), anyString())).thenReturn(editForm());
        when(this.openProjectApiClient.updateWorkPackage(anyString(), anyString()))
            .thenThrow(new ProjectManagementException("Conflict"));

        ProjectManagementException exception = assertThrows(ProjectManagementException.class,
            () -> this.handleWorkPackages.updateWorkPackage(WIKI, INSTANCE, "42", new CreateWorkPackage()));

        assertEquals("The Work Package update failed", exception.getMessage());
        assertEquals("Conflict", exception.getCause().getMessage());
    }

    private ObjectNode editForm() throws IOException
    {
        return (ObjectNode) this.mapper.readTree(OpenProjectTestUtils.getEditWorkPackageFormResponse());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOptions(Response response)
    {
        return (Map<String, Object>) response.getEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> field(Map<String, Object> options, String name)
    {
        return (Map<String, Object>) options.get(name);
    }

    private String jsonEq(String expected)
    {
        return argThat(actual -> {
            try {
                return this.mapper.readTree(expected).equals(this.mapper.readTree(actual));
            } catch (JsonProcessingException e) {
                return false;
            }
        });
    }

    private List<Project> generateProjects()
    {
        Project firstProject = new Project();
        firstProject.setId(1);
        firstProject.setName("Project 1");
        firstProject.setSelf(new Linkable("Project 1", "/api/v3/projects/1"));
        Project secondProject = new Project();
        secondProject.setId(2);
        secondProject.setName("Project 2");
        secondProject.setSelf(new Linkable("Project 2", "/api/v3/projects/2"));
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

}
