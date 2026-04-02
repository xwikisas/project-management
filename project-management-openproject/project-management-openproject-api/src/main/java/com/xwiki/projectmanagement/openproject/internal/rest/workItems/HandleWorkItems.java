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
import java.util.Arrays;
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
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xwiki.licensing.Licensor;
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
 * Resource that exposes REST endpoints useful for creating Work Items in OpenProject.
 *
 * @version $Id$
 * @since 1.1
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

    private static final String SUBJECT = "subject";

    private static final String TYPE = "type";

    private static final String PRIORITY = "priority";

    private static final String ASSIGNEE = "assignee";

    private static final String DESCRIPTION = "description";

    private static final String STATUS = "status";

    private static final String START_DATE = "startDate";

    private static final String DUE_DATE = "dueDate";

    private static final String ALLOWED_VALUES = "allowedValues";

    private static final String IS_DEFAULT = "isDefault";

    private static final String DEFAULT_VALUE = "defaultValue";

    private static final String SELECT = "select";

    private static final String DATE = "date";

    private static final String TEXT = "text";

    private static final String REQUIRED = "required";

    private static final String NAME = "name";

    private static final List<String> OPEN_PROJECT_CODE_SPACE = Arrays.asList("OpenProject", "Code");

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private Licensor licensor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * This endpoint exposes the available projects for creating a work package.
     *
     * @param wiki the wiki that contains the configured client.
     * @param instance the open project client where to search for work item suggestions.
     * @return a list of projects that can be used for creating a work package.
     * @since 1.1
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
            return Response
                .status(Response.Status.CONFLICT)
                .entity(
                    "You must authenticate to the OpenProject instance from XWiki before"
                        + " being able to retrieve the available projects for creating a work package.")
                .build();
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
     * @since 1.1
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/create")
    public Response createWorkPackage(@PathParam("wikiName") String wiki,
        @PathParam("instance") String instance, CreateWorkPackage workPackage) throws ProjectManagementException
    {
        XWikiContext xContext = getXWikiContext();

        if (!licensor.hasLicensure(
            new DocumentReference(xContext.getWikiId(), OPEN_PROJECT_CODE_SPACE, "OpenProjectConnectionClass")))
        {
            return Response
                .status(Response.Status.FORBIDDEN)
                .entity(
                    "You must have a valid license for the OpenProject integration "
                        + "in order to create work packages.")
                .build();
        }

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

            JsonNode createWorkPackageResponse = apiClient.createWorkPackage(commitLink,
                objectMapper.writeValueAsString(payload));
            return Response.ok(createWorkPackageResponse).build();
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
        Type defaultType = null;
        for (JsonNode typeNode : schemaNode.path(TYPE).path(EMBEDDED).path(ALLOWED_VALUES)) {
            Type type = new Type(typeNode);
            types.add(type);
            if (defaultType == null && typeNode.path(IS_DEFAULT).booleanValue()) {
                defaultType = type;
            }
        }

        List<Status> statuses = new ArrayList<>();
        Status defaultStatus = null;
        for (JsonNode statusNode : schemaNode.path(STATUS).path(EMBEDDED).path(ALLOWED_VALUES)) {
            Status status = new Status(statusNode);
            statuses.add(status);
            if (defaultStatus == null && statusNode.path(IS_DEFAULT).booleanValue()) {
                defaultStatus = status;
            }
        }

        List<Priority> priorities = new ArrayList<>();
        Priority defaultPriority = null;
        for (JsonNode priorityNode : schemaNode.path(PRIORITY).path(EMBEDDED).path(ALLOWED_VALUES)) {
            Priority priority = new Priority(priorityNode);
            priorities.add(priority);
            if (defaultPriority == null && priorityNode.path(IS_DEFAULT).booleanValue()) {
                defaultPriority = priority;
            }
        }

        optionsResponse.put(SUBJECT,
            createInputOptions(
                getRequiredOptionForField(schemaNode, SUBJECT),
                TEXT,
                getLabelOptionForField(schemaNode, SUBJECT),
                null,
                null
            )
        );
        optionsResponse.put(
            DESCRIPTION,
            createInputOptions(
                getRequiredOptionForField(schemaNode, DESCRIPTION),
                TEXT,
                getLabelOptionForField(schemaNode, DESCRIPTION),
                null,
                null
            )
        );
        optionsResponse.put(
            TYPE,
            createInputOptions(
                getRequiredOptionForField(schemaNode, TYPE),
                SELECT,
                getLabelOptionForField(schemaNode, TYPE),
                types,
                defaultType
            )
        );
        optionsResponse.put(
            PRIORITY,
            createInputOptions(
                getRequiredOptionForField(schemaNode, PRIORITY),
                SELECT,
                getLabelOptionForField(schemaNode, PRIORITY),
                priorities,
                defaultPriority
            )
        );
        optionsResponse.put(
            STATUS,
            createInputOptions(
                getRequiredOptionForField(schemaNode, STATUS),
                SELECT,
                getLabelOptionForField(schemaNode, STATUS),
                statuses,
                defaultStatus
            )
        );
        setAssigneeOptions(schemaNode, apiClient, optionsResponse);
        optionsResponse.put(
            START_DATE,
            createInputOptions(
                getRequiredOptionForField(schemaNode, START_DATE),
                DATE,
                getLabelOptionForField(schemaNode, START_DATE),
                null,
                null
            )
        );
        optionsResponse.put(
            DUE_DATE,
            createInputOptions(
                getRequiredOptionForField(schemaNode, DUE_DATE),
                DATE,
                getLabelOptionForField(schemaNode, DUE_DATE),
                null,
                null
            )
        );

        return optionsResponse;
    }

    private boolean getRequiredOptionForField(JsonNode schemaNode, String fieldName)
    {
        return schemaNode.path(fieldName).path(REQUIRED).booleanValue();
    }

    private String getLabelOptionForField(JsonNode schemaNode, String fieldName)
    {
        return schemaNode.path(fieldName).path(NAME).asText();
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

            optionsResponse.put(
                ASSIGNEE,
                createInputOptions(
                    getRequiredOptionForField(schemaNode, ASSIGNEE),
                    SELECT,
                    getLabelOptionForField(schemaNode, ASSIGNEE),
                    assignees,
                    null
                )
            );
        } catch (ProjectManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> createInputOptions(boolean required, String type, String label,
        List<? extends BaseOpenProjectObject> allowedValues, BaseOpenProjectObject defaultValue)
    {
        Map<String, Object> fieldOptions = new HashMap<>();
        fieldOptions.put(REQUIRED, required);
        fieldOptions.put(TYPE, type);
        fieldOptions.put("label", label);
        fieldOptions.put(ALLOWED_VALUES, allowedValues);
        fieldOptions.put(DEFAULT_VALUE, defaultValue);
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
