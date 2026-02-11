package com.xwiki.projectmanagement.openproject.internal.rest.workItems;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.CreateWorkPackage;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;

/**
 * The resource that exposes CRUD operations over the work items of the different project management implementations.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.workItems.HandleWorkItems")
@Singleton
@Path("/wikis/{wikiName}/openproject/instance/{instance}/workPackages")
public class HandleWorkItems extends XWikiResource
{
    private static final String HREF = "href";

    private static final String LINKS = "_links";

    private static final String EMBEDDED = "_embedded";

    private static final String PROJECT = "project";

    private static final String PROJECT_LABEL = "Project";

    private static final String SUBJECT = "subject";

    private static final String SUBJECT_LABEL = "Subject";

    private static final String TYPE = "type";

    private static final String TYPE_LABEL = "Type";

    private static final String PRIORITY = "priority";

    private static final String PRIORITY_LABEL = "Priority";

    private static final String ASSIGNEE = "assignee";

    private static final String ASSIGNEE_LABEL = "Assignee";

    private static final String DESCRIPTION = "description";

    private static final String DESCRIPTION_LABEL = "Description";

    private static final String STATUS = "status";

    private static final String STATUS_LABEL = "Status";

    private static final String START_DATE = "startDate";

    private static final String START_DATE_LABEL = "Start Date";

    private static final String DUE_DATE = "dueDate";

    private static final String DUE_DATE_LABEL = "Due Date";

    private static final String ALLOWED_VALUES = "allowedValues";

    private static final String SELECT = "select";

    private static final String DATE = "date";

    private static final String TEXT = "text";

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * This endpoint exposes the available projects for creating a work package.
     *
     * @param wiki the wiki that contains the configured client.
     * @param instance the open project client where to search for work item suggestions.
     * @return a list of projects that can be used for creating a work package.
     */
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/availableProjects")
    public Response getAvailableProjects(@PathParam("wikiName") String wiki,
        @PathParam("instance") String instance) throws ProjectManagementException
    {
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instance);

        if (apiClient == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        JsonNode response;
        try {
            response = apiClient.getWorkPackagesFormResponse("{}");

            validateResponseType(response);

            JsonNode schemaNode = getSchemaNode(response);
            List<Project> projects = getRemoteProjects(apiClient, schemaNode);
            return Response.ok(projects).build();
        } catch (ProjectManagementException e) {
            throw new ProjectManagementException("Failed to retrieve available projects for creating a work package",
                e);
        }
    }

    /**
     * The resource that exposes the available options for creating a work package and creates the work package if the
     * provided options are valid.
     *
     * @param workPackage the work package that needs to be created.
     * @param wiki the wiki that contains the configured client.
     * @param instance the open project client where to search for work item suggestions.
     * @return the form response containing the validation errors if the creation failed or the created work package if
     *     the creation succeeded.
     * @since 1.0
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/form")
    public Response getFormForCreatingWorkPackage(@PathParam("wikiName") String wiki,
        @PathParam("instance") String instance, CreateWorkPackage workPackage) throws ProjectManagementException
    {
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        Map<String, Object> formRequest = createRequestForOpenProjectFormRequest(workPackage);

        JsonNode response;
        try {
            response = apiClient.getWorkPackagesFormResponse(objectMapper.writeValueAsString(formRequest));

            validateResponseType(response);

            JsonNode validationErrors = response.path(EMBEDDED).path("validationErrors");

            if (!validationErrors.isEmpty()) {
                JsonNode schemaNode = getSchemaNode(response);
                return Response.status(Response.Status.OK)
                    .entity(convertFormResponseToOptionsResponse(schemaNode, apiClient)).build();
            }

            String commitLink = response.path(LINKS).path("commit").path(HREF).asText();
            JsonNode payload = response.path(EMBEDDED).path("payload");

            apiClient.createWorkPackage(commitLink, objectMapper.writeValueAsString(payload));
            return Response.ok(response).build();
        } catch (JsonProcessingException | ProjectManagementException e) {
            throw new ProjectManagementException("The Work Package creation failed", e);
        }
    }

    private void validateResponseType(JsonNode response) throws ProjectManagementException
    {
        String responseType = response.path("_type").asText();

        if (!responseType.equals("Form")) {
            throw new ProjectManagementException("Unexpected response type: " + responseType);
        }
    }

    private JsonNode getSchemaNode(JsonNode response)
    {
        return response.path(EMBEDDED).path("schema");
    }

    private Map<String, Object> convertFormResponseToOptionsResponse(JsonNode schemaNode,
        OpenProjectApiClient apiClient)
    {
        Map<String, Object> optionsResponse = new LinkedHashMap<>();

        List<Type> types = new ArrayList<>();
        for (JsonNode typeNode : schemaNode.path(TYPE).path(EMBEDDED).path(ALLOWED_VALUES)) {
            types.add(new Type(typeNode, null));
        }

        List<Status> statuses = new ArrayList<>();
        for (JsonNode statusNode : schemaNode.path(STATUS).path(EMBEDDED).path(ALLOWED_VALUES)) {
            statuses.add(new Status(statusNode, null));
        }

        List<Priority> priorities = new ArrayList<>();
        for (JsonNode priorityNode : schemaNode.path(PRIORITY).path(EMBEDDED).path(ALLOWED_VALUES)) {
            priorities.add(new Priority(priorityNode, null));
        }

        optionsResponse.put(SUBJECT, createInputOptions(true, TEXT, SUBJECT_LABEL, null));
        optionsResponse.put(DESCRIPTION, createInputOptions(false, TEXT, DESCRIPTION_LABEL, null));
        optionsResponse.put(TYPE, createInputOptions(false, SELECT, TYPE_LABEL, types));
        optionsResponse.put(PRIORITY, createInputOptions(false, SELECT, PRIORITY_LABEL, priorities));
        optionsResponse.put(STATUS, createInputOptions(false, SELECT, STATUS_LABEL, statuses));
        setAssigneeOptions(schemaNode, apiClient, optionsResponse);
        optionsResponse.put(START_DATE, createInputOptions(false, DATE, START_DATE_LABEL, null));
        optionsResponse.put(DUE_DATE, createInputOptions(false, DATE, DUE_DATE_LABEL, null));

        return optionsResponse;
    }

    private void setAssigneeOptions(JsonNode schemaNode, OpenProjectApiClient apiClient,
        Map<String, Object> optionsResponse)
    {
        String assigneeUrl = schemaNode.path(ASSIGNEE).path(LINKS).path(ALLOWED_VALUES).path(
            HREF).asText();

        try {
            List<User> assignees;

            if (assigneeUrl != null && !assigneeUrl.isEmpty()) {
                PaginatedResult<User> usersPaginatedResult = apiClient.getAvailableUsers(assigneeUrl, 0, 0,
                    "");
                assignees = usersPaginatedResult.getItems();
            } else {
                assignees = new ArrayList<>();
            }

            optionsResponse.put(ASSIGNEE, createInputOptions(false, SELECT, ASSIGNEE_LABEL, assignees));
        } catch (ProjectManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> createInputOptions(boolean required, String type, String label,
        List<? extends BaseOpenProjectObject> allowedValues)
    {
        Map<String, Object> fieldOptions = new HashMap<>();
        fieldOptions.put("required", required);
        fieldOptions.put(TYPE, type);
        fieldOptions.put("label", label);
        fieldOptions.put(ALLOWED_VALUES, allowedValues);
        return fieldOptions;
    }

    private Map<String, Object> createRequestForOpenProjectFormRequest(CreateWorkPackage workPackage)
    {
        Map<String, Object> formRequest = new HashMap<>();
        Map<String, Object> links = new HashMap<>();

        Map<String, String> linkMappings = new HashMap<>();

        linkMappings.put(PROJECT, workPackage.getProject());
        linkMappings.put(ASSIGNEE, workPackage.getAssignee());
        linkMappings.put(TYPE, workPackage.getType());
        linkMappings.put(STATUS, workPackage.getStatus());
        linkMappings.put(PRIORITY, workPackage.getPriority());

        for (Map.Entry<String, String> entry : linkMappings.entrySet()) {
            if (entry.getValue() != null) {
                links.put(entry.getKey(), Map.of(HREF, entry.getValue()));
            }
        }

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put(START_DATE, workPackage.getStartDate());
        fieldMappings.put(DUE_DATE, workPackage.getDueDate());

        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            if (entry.getValue() != null) {
                formRequest.put(entry.getKey(), entry.getValue());
            }
        }

        if (workPackage.getDescription() != null) {
            formRequest.put(DESCRIPTION, Map.of("raw", workPackage.getDescription()));
        }

        formRequest.put(LINKS, links);
        formRequest.put(SUBJECT, workPackage.getSubject());

        return formRequest;
    }

    private List<Project> getRemoteProjects(OpenProjectApiClient apiClient, JsonNode schemaNode)
        throws ProjectManagementException
    {
        String projectsUrl = schemaNode.path(PROJECT).path(LINKS).path(ALLOWED_VALUES).path(
            HREF).asText();

        if (projectsUrl != null && !projectsUrl.isEmpty()) {
            PaginatedResult<Project> projectsPaginatedResult =
                apiClient.getAvailableProjects(projectsUrl, 0, 0, "");
            return projectsPaginatedResult.getItems();
        } else {
            return new ArrayList<>();
        }
    }
}
