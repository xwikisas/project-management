package com.xwiki.projectmanagement.openproject.internal.rest.workPackages;

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
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
 * Resource that exposes REST endpoints useful for creating Work Items in OpenProject.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.workPackages.HandleWorkPackages")
@Singleton
@Path("/wikis/{wikiName}/openproject/instance/{instance}/workPackages")
public class HandleWorkPackages extends XWikiResource
{
    private static final String HREF = "href";

    private static final String LINKS = "_links";

    private static final String EMBEDDED = "_embedded";

    private static final String PROJECT = "project";

    private static final String SUBJECT = "subject";

    private static final String TYPE = "type";

    private static final String PRIORITY = "priority";

    private static final String ASSIGNEE = "assignee";

    private static final String PARENT = "parent";

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

    private static final String VALIDATION_ERRORS = "validationErrors";

    private static final String VALIDATION_MESSAGE = "validationMessage";

    private static final String MESSAGE = "message";

    private static final String COMMIT = "commit";

    private static final String PAYLOAD = "payload";

    private static final String RAW = "raw";

    private static final String TITLE = "title";

    private static final String VALUE = "value";

    private static final String LABEL = "label";

    private static final String EMPTY_JSON = "{}";

    private static final String LOCK_VERSION = "lockVersion";

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * This endpoint exposes the available projects for creating a work package.
     *
     * @param wiki the wiki that contains the configured client.
     * @param instance the OpenProject client where to search for work item suggestions.
     * @param offset the offset from which to start retrieving projects.
     * @param pageSize the maximum number of projects to return.
     * @return a list of projects that can be used for creating a work package.
     * @since 1.1
     */
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/availableProjects")
    public Response getAvailableProjects(@PathParam("wikiName") String wiki,
        @PathParam("instance") String instance,
        @QueryParam("offset") @DefaultValue("0") int offset,
        @QueryParam("pageSize") @DefaultValue("20") int pageSize) throws ProjectManagementException
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
            response = apiClient.getWorkPackagesFormResponse(EMPTY_JSON);

            validateResponseType(response);

            JsonNode schemaNode = getSchemaNode(response);
            List<Project> projects = getAvailableProjects(apiClient, schemaNode,
                offset, pageSize);
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
     * @param instance the OpenProject client where to search for work item suggestions.
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
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        Map<String, Object> formRequest = createRequestForOpenProjectFormRequest(workPackage);

        JsonNode response;
        try {
            response = apiClient.getWorkPackagesFormResponse(objectMapper.writeValueAsString(formRequest));

            validateResponseType(response);

            JsonNode validationErrors = response.path(EMBEDDED).path(VALIDATION_ERRORS);

            if (!validationErrors.isEmpty()) {
                JsonNode schemaNode = getSchemaNode(response);
                Map<String, Object> optionsResponse = convertFormResponseToOptionsResponse(schemaNode,
                    objectMapper.createObjectNode(), apiClient);
                return Response.status(Response.Status.OK).entity(optionsResponse).build();
            }

            String commitLink = response.path(LINKS).path(COMMIT).path(HREF).asText();
            JsonNode payload = response.path(EMBEDDED).path(PAYLOAD);

            JsonNode createWorkPackageResponse = apiClient.createWorkPackage(commitLink,
                objectMapper.writeValueAsString(payload));
            return Response.ok(createWorkPackageResponse).build();
        } catch (JsonProcessingException | ProjectManagementException e) {
            throw new ProjectManagementException("The Work Package creation failed", e);
        }
    }

    /**
     * Exposes the editable options of an existing work package, pre-filled with its current values.
     *
     * @param wiki the wiki that contains the configured client.
     * @param instance the OpenProject client where the work package lives.
     * @param workPackageId the id of the work package to edit.
     * @return the options response describing the editable fields and their current values.
     * @since 1.2
     */
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/{workPackageId}/editForm")
    public Response getEditForm(@PathParam("wikiName") String wiki,
        @PathParam("instance") String instance,
        @PathParam("workPackageId") String workPackageId) throws ProjectManagementException
    {
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instance);

        if (apiClient == null) {
            return Response
                .status(Response.Status.CONFLICT)
                .entity("You must authenticate to the OpenProject instance from XWiki before being able to edit a"
                    + " work package.")
                .build();
        }

        try {
            JsonNode response = apiClient.getWorkPackageFormResponse(workPackageId, "");

            validateResponseType(response);

            JsonNode schemaNode = getSchemaNode(response);
            JsonNode payloadNode = response.path(EMBEDDED).path(PAYLOAD);
            return Response.ok(convertFormResponseToOptionsResponse(schemaNode, payloadNode, apiClient)).build();
        } catch (ProjectManagementException e) {
            throw new ProjectManagementException(
                String.format("Failed to retrieve the edit form for work package [%s]", workPackageId), e);
        }
    }

    /**
     * Validates and, if valid, commits the changes to an existing work package.
     *
     * @param wiki the wiki that contains the configured client.
     * @param instance the OpenProject client where the work package lives.
     * @param workPackageId the id of the work package to update.
     * @param workPackage the changes to apply to the work package.
     * @return the options response containing the validation errors if the update failed or the updated work package if
     *     the update succeeded.
     * @since 1.2
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/{workPackageId}/update")
    public Response updateWorkPackage(@PathParam("wikiName") String wiki,
        @PathParam("instance") String instance,
        @PathParam("workPackageId") String workPackageId, CreateWorkPackage workPackage)
        throws ProjectManagementException
    {
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instance);
        Map<String, Object> formRequest = createRequestForOpenProjectFormRequest(workPackage);

        try {
            JsonNode response =
                apiClient.getWorkPackageFormResponse(workPackageId, objectMapper.writeValueAsString(formRequest));

            validateResponseType(response);

            JsonNode validationErrors = response.path(EMBEDDED).path(VALIDATION_ERRORS);

            if (!validationErrors.isEmpty()) {
                JsonNode schemaNode = getSchemaNode(response);
                JsonNode payloadNode = response.path(EMBEDDED).path(PAYLOAD);

                Map<String, Object> optionsResponse =
                    convertFormResponseToOptionsResponse(schemaNode, payloadNode, apiClient);
                optionsResponse.put(VALIDATION_MESSAGE, extractFirstValidationMessage(validationErrors));
                return Response.status(UNPROCESSABLE_ENTITY).entity(optionsResponse).build();
            }

            String commitLink = response.path(LINKS).path(COMMIT).path(HREF).asText();
            JsonNode payload = response.path(EMBEDDED).path(PAYLOAD);

            JsonNode updateWorkPackageResponse = apiClient.updateWorkPackage(commitLink,
                objectMapper.writeValueAsString(payload));
            return Response.ok(updateWorkPackageResponse).build();
        } catch (JsonProcessingException | ProjectManagementException e) {
            throw new ProjectManagementException("The Work Package update failed", e);
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

    private String extractFirstValidationMessage(JsonNode validationErrors)
    {
        for (JsonNode errorNode : validationErrors) {
            String message = errorNode.path(MESSAGE).asText();
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        return null;
    }

    private Map<String, Object> convertFormResponseToOptionsResponse(JsonNode schemaNode, JsonNode payloadNode,
        OpenProjectApiClient apiClient)
    {
        Map<String, Object> optionsResponse = new LinkedHashMap<>();

        putTextField(optionsResponse, schemaNode, SUBJECT, getTextDefault(payloadNode, SUBJECT));
        putTextField(optionsResponse, schemaNode, DESCRIPTION, getDescriptionDefault(payloadNode));
        putSelectField(optionsResponse, schemaNode, payloadNode, TYPE, buildTypes(schemaNode));
        putSelectField(optionsResponse, schemaNode, payloadNode, PRIORITY, buildPriorities(schemaNode));
        putSelectField(optionsResponse, schemaNode, payloadNode, STATUS, buildStatuses(schemaNode));
        setAssigneeOptions(schemaNode, payloadNode, apiClient, optionsResponse);
        putDateFields(optionsResponse, schemaNode, payloadNode);

        addCurrentValueMetadata(payloadNode, optionsResponse);

        return optionsResponse;
    }

    private void putTextField(Map<String, Object> optionsResponse, JsonNode schemaNode, String fieldName,
        String defaultValue)
    {
        optionsResponse.put(
            fieldName,
            createInputOptions(
                getRequiredOptionForField(schemaNode, fieldName),
                TEXT,
                getLabelOptionForField(schemaNode, fieldName),
                null,
                defaultValue
            )
        );
    }

    private void putSelectField(Map<String, Object> optionsResponse, JsonNode schemaNode, JsonNode payloadNode,
        String fieldName, List<? extends BaseOpenProjectObject> allowedValues)
    {
        optionsResponse.put(
            fieldName,
            createInputOptions(
                getRequiredOptionForField(schemaNode, fieldName),
                SELECT,
                getLabelOptionForField(schemaNode, fieldName),
                allowedValues,
                getSelectDefault(payloadNode, fieldName, allowedValues,
                    findSchemaDefault(schemaNode, fieldName, allowedValues)
                )
            )
        );
    }

    private List<Type> buildTypes(JsonNode schemaNode)
    {
        List<Type> types = new ArrayList<>();
        for (JsonNode typeNode : schemaNode.path(TYPE).path(EMBEDDED).path(ALLOWED_VALUES)) {
            types.add(new Type(typeNode));
        }
        return types;
    }

    private List<Status> buildStatuses(JsonNode schemaNode)
    {
        List<Status> statuses = new ArrayList<>();
        for (JsonNode statusNode : schemaNode.path(STATUS).path(EMBEDDED).path(ALLOWED_VALUES)) {
            statuses.add(new Status(statusNode));
        }
        return statuses;
    }

    private List<Priority> buildPriorities(JsonNode schemaNode)
    {
        List<Priority> priorities = new ArrayList<>();
        for (JsonNode priorityNode : schemaNode.path(PRIORITY).path(EMBEDDED).path(ALLOWED_VALUES)) {
            priorities.add(new Priority(priorityNode));
        }
        return priorities;
    }

    private BaseOpenProjectObject findSchemaDefault(JsonNode schemaNode, String fieldName,
        List<? extends BaseOpenProjectObject> allowedValues)
    {
        int index = 0;
        for (JsonNode valueNode : schemaNode.path(fieldName).path(EMBEDDED).path(ALLOWED_VALUES)) {
            if (valueNode.path(IS_DEFAULT).booleanValue()) {
                return allowedValues.get(index);
            }
            index++;
        }
        return null;
    }

    private void putDateFields(Map<String, Object> optionsResponse, JsonNode schemaNode, JsonNode payloadNode)
    {
        // A Milestone exposes a single "date" field in its schema, while other types expose
        // "startDate" / "dueDate". Render only the date inputs that apply to this work package type.
        if (schemaNode.path(DATE).isObject()) {
            putDateField(optionsResponse, schemaNode, payloadNode, DATE);
        } else {
            putDateField(optionsResponse, schemaNode, payloadNode, START_DATE);
            putDateField(optionsResponse, schemaNode, payloadNode, DUE_DATE);
        }
    }

    private void putDateField(Map<String, Object> optionsResponse, JsonNode schemaNode, JsonNode payloadNode,
        String fieldName)
    {
        optionsResponse.put(
            fieldName,
            createInputOptions(
                getRequiredOptionForField(schemaNode, fieldName),
                DATE,
                getLabelOptionForField(schemaNode, fieldName),
                null,
                getTextDefault(payloadNode, fieldName)
            )
        );
    }

    private void addCurrentValueMetadata(JsonNode payloadNode, Map<String, Object> optionsResponse)
    {
        addParentOption(payloadNode, optionsResponse);
        addLockVersion(payloadNode, optionsResponse);
    }

    private boolean getRequiredOptionForField(JsonNode schemaNode, String fieldName)
    {
        return schemaNode.path(fieldName).path(REQUIRED).booleanValue();
    }

    private String getLabelOptionForField(JsonNode schemaNode, String fieldName)
    {
        return schemaNode.path(fieldName).path(NAME).asText();
    }

    private void setAssigneeOptions(JsonNode schemaNode, JsonNode payloadNode, OpenProjectApiClient apiClient,
        Map<String, Object> optionsResponse)
    {
        String assigneeUrl = schemaNode.path(ASSIGNEE).path(LINKS).path(ALLOWED_VALUES).path(
            HREF).asText();

        try {
            List<User> assignees;

            if (assigneeUrl != null && !assigneeUrl.isEmpty()) {
                PaginatedResult<User> usersPaginatedResult = apiClient.getAvailableUsers(assigneeUrl, null, null, "");
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
                    getSelectDefault(payloadNode, ASSIGNEE, assignees, null)
                )
            );
        } catch (ProjectManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTextDefault(JsonNode payloadNode, String fieldName)
    {
        JsonNode fieldNode = payloadNode.path(fieldName);

        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }

        return fieldNode.asText();
    }

    private String getDescriptionDefault(JsonNode payloadNode)
    {
        JsonNode descriptionNode = payloadNode.path(DESCRIPTION).path(RAW);
        if (descriptionNode.isMissingNode() || descriptionNode.isNull()) {
            return null;
        }
        return descriptionNode.asText();
    }

    private String getSelectDefault(JsonNode payloadNode, String fieldName,
        List<? extends BaseOpenProjectObject> allowedValues, BaseOpenProjectObject fallback)
    {
        String href = payloadNode.path(LINKS).path(fieldName).path(HREF).asText();
        if (href != null && !href.isEmpty() && findAllowedValueByHref(allowedValues, href) != null) {
            return href;
        }
        return fallback != null && fallback.getSelf() != null ? fallback.getSelf().getLocation() : null;
    }

    private BaseOpenProjectObject findAllowedValueByHref(List<? extends BaseOpenProjectObject> allowedValues,
        String href)
    {
        for (BaseOpenProjectObject allowedValue : allowedValues) {
            if (allowedValue.getSelf() != null && href.equals(allowedValue.getSelf().getLocation())) {
                return allowedValue;
            }
        }
        return null;
    }

    private void addParentOption(JsonNode payloadNode, Map<String, Object> optionsResponse)
    {
        JsonNode parentLink = payloadNode.path(LINKS).path(PARENT);
        String href = parentLink.path(HREF).asText();
        if (href == null || href.isEmpty()) {
            return;
        }
        String title = parentLink.path(TITLE).asText();
        String parentId = href.substring(href.lastIndexOf('/') + 1);
        Map<String, String> parentSeed = new HashMap<>();
        parentSeed.put(VALUE, href);
        parentSeed.put(LABEL, String.format("#%s: %s", parentId, title));
        optionsResponse.put(PARENT, parentSeed);
    }

    private void addLockVersion(JsonNode payloadNode, Map<String, Object> optionsResponse)
    {
        JsonNode lockVersionNode = payloadNode.path(LOCK_VERSION);
        if (lockVersionNode.isNumber()) {
            optionsResponse.put(LOCK_VERSION, lockVersionNode.intValue());
        }
    }

    private Map<String, Object> createInputOptions(boolean required, String type, String label,
        List<? extends BaseOpenProjectObject> allowedValues, String defaultValue)
    {
        Map<String, Object> fieldOptions = new HashMap<>();
        fieldOptions.put(REQUIRED, required);
        fieldOptions.put(TYPE, type);
        fieldOptions.put(LABEL, label);
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
        linkMappings.put(PARENT, workPackage.getParent());

        for (Map.Entry<String, String> entry : linkMappings.entrySet()) {
            if (entry.getValue() != null) {
                links.put(entry.getKey(), Map.of(HREF, entry.getValue()));
            }
        }

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put(START_DATE, workPackage.getStartDate());
        fieldMappings.put(DUE_DATE, workPackage.getDueDate());
        fieldMappings.put(DATE, workPackage.getDate());

        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            if (entry.getValue() != null) {
                formRequest.put(entry.getKey(), entry.getValue());
            }
        }

        if (workPackage.getDescription() != null) {
            formRequest.put(DESCRIPTION, Map.of(RAW, workPackage.getDescription()));
        }

        if (workPackage.getLockVersion() != null) {
            formRequest.put(LOCK_VERSION, workPackage.getLockVersion());
        }

        formRequest.put(LINKS, links);
        formRequest.put(SUBJECT, workPackage.getSubject());

        return formRequest;
    }

    private List<Project> getAvailableProjects(OpenProjectApiClient apiClient, JsonNode schemaNode,
        Integer offset, Integer pageSize)
        throws ProjectManagementException
    {
        String projectsUrl = schemaNode.path(PROJECT).path(LINKS).path(ALLOWED_VALUES).path(
            HREF).asText();

        if (projectsUrl != null && !projectsUrl.isEmpty()) {
            PaginatedResult<Project> projectsPaginatedResult =
                apiClient.getAvailableProjects(projectsUrl, offset, pageSize, "");
            return projectsPaginatedResult.getItems();
        } else {
            return new ArrayList<>();
        }
    }
}
