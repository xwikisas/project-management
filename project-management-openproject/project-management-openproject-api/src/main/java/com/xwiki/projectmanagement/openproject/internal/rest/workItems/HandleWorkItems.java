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
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
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
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.internal.DefaultOpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.CreateWorkPackage;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.rest.WorkItemsResource;

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
public class HandleWorkItems extends XWikiResource implements WorkItemsResource
{
    private static final String HREF = "href";

    private static final String LINKS = "_links";

    private static final String EMBEDDED = "_embedded";

    private static final String PROJECT = "project";

    private static final String SUBJECT = "subject";

    private static final String OP_RESPONSE_ID = "id";

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Response getWorkItem(String wiki, String projectManagementHint, String workItemId)
    {
        return null;
    }

    @Override
    public Response getWorkItems(String wiki, String projectManagementHint, int page, int pageSize)
    {
        return null;
    }

    @Override
    public Response createWorkItem(String wiki, String projectManagementHint, WorkItem workItem)
    {
        return null;
    }

    @Override
    public Response updateWorkItem(String wiki, String projectManagementHint, WorkItem workItem)
    {
        return null;
    }

    @Override
    public Response deleteWorkItem(String wiki, String projectManagementHint, String workItemId)
    {
        return null;
    }

    /**
     * The resource that exposes CRUD operations over the work items of the different project management
     * implementations.
     *
     * @param workPackage the work package that needs to be created.
     * @param wiki the wiki from where the project management implementation can retrieve. Depending on the
     *     implementation, different wikis can have different configurations or the wiki might be irrelevant to the
     *     query.
     * @param instance the hint of the project management implementation.
     * @return response,
     * @since 1.0
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/form")
    public Response getFormForCreatingWorkPackage(@PathParam("wikiName") String wiki,
        @PathParam("instance") String instance, CreateWorkPackage workPackage)
    {
        Map<String, Object> formRequest = new HashMap<>();
        Map<String, Object> links = new HashMap<>();

        if (workPackage.getProject() != null) {
            links.put(PROJECT, Map.of(HREF, workPackage.getProject()));
        }
        if (workPackage.getAssignee() != null) {
            links.put("assignee", Map.of(HREF, workPackage.getAssignee()));
        }
        if (workPackage.getType() != null) {
            links.put("type", Map.of(HREF, workPackage.getType()));
        }
        if (workPackage.getStatus() != null) {
            links.put("status", Map.of(HREF, workPackage.getStatus()));
        }
        if (workPackage.getPriority() != null) {
            links.put("priority", Map.of(HREF, workPackage.getPriority()));
        }

        if (workPackage.getStartDate() != null) {
            formRequest.put("startDate", workPackage.getStartDate());
        }

        if (workPackage.getDueDate() != null) {
            formRequest.put("dueDate", workPackage.getDueDate());
        }

        if (workPackage.getDescription() != null) {
            formRequest.put("description", Map.of("raw", workPackage.getDescription()));
        }

        formRequest.put(LINKS, links);
        formRequest.put(SUBJECT, workPackage.getSubject());

        DefaultOpenProjectApiClient apiClient =
            (DefaultOpenProjectApiClient) openProjectConfiguration.getDefaultOpenProjectApiClient(instance);

        JsonNode response;
        try {
            response = apiClient.getWorkPackagesFormResponse(objectMapper.writeValueAsString(formRequest));

            String responseType = response.path("_type").asText();

            if (!responseType.equals("Form")) {
                throw new RuntimeException("Unexpected response type: " + responseType);
            }

            JsonNode validationErrors = response.path(EMBEDDED).path("validationErrors");

            if (!validationErrors.isEmpty()) {
                JsonNode schemaNode = response.path(EMBEDDED).path("schema");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(convertFormResponseToOptionsResponse(schemaNode, apiClient)).build();
            }

            String commitLink = response.path(LINKS).path("commit").path(HREF).asText();
            JsonNode payload = response.path(EMBEDDED).path("payload");

            apiClient.createWorkPackage(commitLink, objectMapper.writeValueAsString(payload));
            return Response.ok(response).build();
        } catch (JsonProcessingException | ProjectManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> convertFormResponseToOptionsResponse(JsonNode schemaNode,
        OpenProjectApiClient apiClient)
    {
        Map<String, Object> optionsResponse = new HashMap<>();

        List<Type> types = new ArrayList<>();

        for (JsonNode typeNode : schemaNode.path("type").path(EMBEDDED).path("allowedValues")) {
            Type type = new Type();
            int id = typeNode.path("id").asInt();
            String name = typeNode.path("name").asText();
            String color = typeNode.path("color").asText();
            String href = typeNode.path(LINKS).path("self").path(HREF).asText();
            type.setName(name);
            type.setId(id);
            type.setColor(color);
            type.setSelf(new Linkable("", href));
            types.add(type);
        }

        List<Status> statuses = new ArrayList<>();
        for (JsonNode statusNode : schemaNode.path("status").path(EMBEDDED).path("allowedValues")) {
            Status status = new Status();
            int id = statusNode.path(OP_RESPONSE_ID).asInt();
            String labelName = statusNode.path("name").asText();
            String color = statusNode.path("color").asText();
            String href = statusNode.path(LINKS).path("self").path(HREF).asText();
            status.setId(id);
            status.setName(labelName);
            status.setColor(color);
            status.setSelf(new Linkable("", href));
            statuses.add(status);
        }

        List<Priority> priorities = new ArrayList<>();
        for (JsonNode priorityNode : schemaNode.path("priority").path(EMBEDDED).path("allowedValues")) {
            Priority priority = new Priority();
            int id = priorityNode.path("id").asInt();
            String name = priorityNode.path("name").asText();
            String color = priorityNode.path("color").asText();
            String href = priorityNode.path(LINKS).path("self").path(HREF).asText();
            priority.setId(id);
            priority.setName(name);
            priority.setSelf(new Linkable("", href));
            priority.setColor(color);
            priorities.add(priority);
        }

        String assigneeUrl = schemaNode.path("assignee").path(LINKS).path("allowedValues").path(
            "href").asText();

        String projectUrl = schemaNode.path("project").path(LINKS).path("allowedValues").path(
            "href").asText();

        try {
            List<User> assignees;
            if (assigneeUrl != null && !assigneeUrl.isEmpty()) {
                PaginatedResult<User> usersPaginatedResult = apiClient.getAvailableUsers(assigneeUrl, 0, 0,
                    "");
                assignees = usersPaginatedResult.getItems();
            } else {
                assignees = new ArrayList<>();
            }

            optionsResponse.put("assignee", createInputOptions(false, "select", "Assignee", assignees));

            List<Project> projects;
            if (projectUrl != null && !projectUrl.isEmpty()) {
                PaginatedResult<com.xwiki.projectmanagement.openproject.model.Project> projectsPaginatedResult =
                    apiClient.getAvailableProjects(projectUrl, 0, 0, "");
                projects = projectsPaginatedResult.getItems();
            } else {
                projects = new ArrayList<>();
            }
            optionsResponse.put("project", createInputOptions(true, "select", "Project", projects));
        } catch (ProjectManagementException e) {
            throw new RuntimeException(e);
        }

        optionsResponse.put("type", createInputOptions(false, "select", "Type", types));

        optionsResponse.put("status", createInputOptions(false, "select", "Status", statuses));

        optionsResponse.put("priority", createInputOptions(false, "select", "Priority", priorities));

        optionsResponse.put("description", createInputOptions(false, "textarea", "Description", null));

        optionsResponse.put("subject", createInputOptions(true, "text", "Subject", null));

        optionsResponse.put("startDate", createInputOptions(false, "date", "Start Date", null));

        optionsResponse.put("dueDate", createInputOptions(false, "date", "Due Date", null));

        return optionsResponse;
    }

    private Map<String, Object> createInputOptions(boolean required, String type, String label,
        List<? extends BaseOpenProjectObject> allowedValues)
    {
        Map<String, Object> fieldOptions = new HashMap<>();
        fieldOptions.put("required", required);
        fieldOptions.put("type", type);
        fieldOptions.put("label", label);
        fieldOptions.put("allowedValues", allowedValues);
        return fieldOptions;
    }
}
