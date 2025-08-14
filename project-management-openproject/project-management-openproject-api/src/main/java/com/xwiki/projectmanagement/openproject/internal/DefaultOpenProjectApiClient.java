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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.client.utils.URIBuilder;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.exception.WorkPackageRetrievalBadRequestException;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

/**
 * Default Open project get items client helper.
 *
 * @version $Id$
 */
public class DefaultOpenProjectApiClient implements OpenProjectApiClient
{
    private static final String OP_RESPONSE_AUTHOR = "author";

    private static final String OP_RESPONSE_EMBEDDED = "_embedded";

    private static final String OP_RESPONSE_ELEMENTS = "elements";

    private static final String OP_FILTERS = "filters";

    private static final String OP_SORT_BY = "sortBy";

    private static final String OP_SELECT = "select";

    private static final String OP_RESPONSE_ID = "id";

    private static final String OP_RESPONSE_LINKS = "_links";

    private static final String OP_RESPONSE_SELF = "self";

    private static final String OP_RESPONSE_SUBJECT = "subject";

    private static final String OP_RESPONSE_STATUS = "status";

    private static final String PAGE_SIZE = "pageSize";

    private static final String OP_RESPONSE_PROJECT = "project";

    private static final String OP_RESPONSE_NAME = "name";

    private static final String OP_RESPONSE_PRIORITY = "priority";

    private static final String OP_RESPONSE_TITLE = "title";

    private static final String OP_DESCRIPTION = "description";

    private static final String OP_PROJECTS = "projects";

    private static final String OP_START_DATE = "startDate";

    private static final String OP_DUE_DATE = "dueDate";

    private static final String OP_CREATED_AT = "createdAt";

    private static final String OP_UPDATED_AT = "updatedAt";

    private static final String OP_DERIVED_START_DATE = "derivedStartDate";

    private static final String OP_DERIVED_DUE_DATE = "derivedDueDate";

    private static final String HREF = "href";

    private static final String OP_RESPONSE_ASSIGNEE = "assignee";

    private static final String OP_RESPONSE_TYPE = "type";

    private static final String OP_RESPONSE_COLOR = "color";

    private static final String OP_RESPONSE_PERCENTAGE_DONE = "percentageDone";

    private static final String API_URL_PART = "/api/v3";

    private static final String API_URL_WORK_PACKAGES = "/api/v3/work_packages";

    private static final String API_URL_TYPES = "/api/v3/types";

    private static final String API_URL_STATUSES = "/api/v3/statuses";

    private static final String API_URL_PRIORITIES = "/api/v3/priorities";

    private static final String API_URL_USERS = "/api/v3/users";

    private static final String API_URL_PROJECTS = "/api/v3/projects";

    private static final String API_URL_SELECT_ELEMENTS_PARAM = "elements/id,elements/name";

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static final String OP_OFFSET = "offset";

    private final HttpClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String token;

    private final String connectionUrl;

    /**
     * Constructs a new {@code OpenProjectApiClient} with the given authentication token and connection URL.
     *
     * @param token the API authentication token used to access the OpenProject API
     * @param connectionUrl the base URL of the OpenProject instance
     */
    public DefaultOpenProjectApiClient(String connectionUrl, String token, HttpClient client)
    {
        this.connectionUrl = connectionUrl;
        this.token = token;
        this.client = client;
    }

    @Override
    public PaginatedResult<WorkPackage> getWorkPackages(int offset, int pageSize, String filters, String sortBy) throws
        ProjectManagementException
    {
        JsonNode mainNode =
            getOpenProjectResponse(API_URL_WORK_PACKAGES, String.valueOf(offset), String.valueOf(pageSize), filters,
                sortBy, "");

        return getWorkPackagePaginatedResult(mainNode, offset, pageSize);
    }

    @Override
    public PaginatedResult<WorkPackage> getProjectWorkPackages(String project, int offset, int pageSize,
        String filters, String sortBy)
        throws ProjectManagementException
    {
        String projectWorkPackagesUrl = String.format("%s/projects/%s/work_packages", API_URL_PART,
            project);
        JsonNode mainNode = getOpenProjectResponse(projectWorkPackagesUrl, String.valueOf(offset),
            String.valueOf(pageSize), filters, sortBy, "");
        return getWorkPackagePaginatedResult(mainNode, offset, pageSize);
    }

    @Override
    public PaginatedResult<User> getUsers(int pageSize, String filters) throws ProjectManagementException
    {
        JsonNode elements =
            getOpenProjectResponseEntities(
                API_URL_USERS,
                "",
                String.valueOf(pageSize),
                filters,
                "",
                API_URL_SELECT_ELEMENTS_PARAM
            );

        List<User> users = new ArrayList<>();

        for (JsonNode element : elements) {
            User user = new User();
            int id = element.path(OP_RESPONSE_ID).asInt();

            String name = element.path(OP_RESPONSE_NAME).asText();
            user.setId(id);
            user.setName(name);
            user.setSelf(new Linkable("", String.format("%s/users/%s", connectionUrl,
                user.getId())));
            users.add(user);
        }

        return new PaginatedResult<>(users, 0, users.size(), users.size());
    }

    @Override
    public PaginatedResult<Project> getProjects(int pageSize, String filters) throws ProjectManagementException
    {
        JsonNode elements =
            getOpenProjectResponseEntities(
                API_URL_PROJECTS,
                "",
                String.valueOf(pageSize),
                filters,
                "",
                API_URL_SELECT_ELEMENTS_PARAM
            );

        List<Project> projects = new ArrayList<>();

        for (JsonNode element : elements) {
            Project project = new Project();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String name = element.path(OP_RESPONSE_NAME).asText();
            project.setId(id);
            project.setName(name);
            project.setSelf(new Linkable("", String.format("%s/projects/%s", connectionUrl, id)));
            projects.add(project);
        }

        return new PaginatedResult<>(projects, 0, projects.size(), projects.size());
    }

    @Override
    public PaginatedResult<Type> getTypes() throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_TYPES, "", "", "", "", "");

        List<Type> types = new ArrayList<>();

        for (JsonNode element : elements) {
            Type type = new Type();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String name = element.path(OP_RESPONSE_NAME).asText();
            String color = element.path(OP_RESPONSE_COLOR).asText();
            type.setName(name);
            type.setId(id);
            type.setColor(color);
            type.setSelf(new Linkable("", String.format("%s/types/%s/edit/settings", connectionUrl, type.getId())));
            types.add(type);
        }

        return new PaginatedResult<>(types, 0, types.size(), types.size());
    }

    @Override
    public PaginatedResult<Status> getStatuses() throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_STATUSES, "", "", "", "", "");
        List<Status> statuses = new ArrayList<>();

        for (JsonNode element : elements) {
            Status status = new Status();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String labelName = element.path(OP_RESPONSE_NAME).asText();
            String color = element.path(OP_RESPONSE_COLOR).asText();
            status.setId(id);
            status.setName(labelName);
            status.setColor(color);
            status.setSelf(new Linkable("", buildEditUrl(connectionUrl, "statuses", id)));
            statuses.add(status);
        }

        return new PaginatedResult<>(statuses, 0, statuses.size(), statuses.size());
    }

    @Override
    public PaginatedResult<Priority> getPriorities() throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_PRIORITIES, "", "", "", "", "");
        List<Priority> priorities = new ArrayList<>();

        for (JsonNode element : elements) {
            Priority priority = new Priority();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String name = element.path(OP_RESPONSE_NAME).asText();
            String color = element.path(OP_RESPONSE_COLOR).asText();
            priority.setId(id);
            priority.setName(name);
            priority.setSelf(new Linkable("", buildEditUrl(connectionUrl, "priorities", id)));
            priority.setColor(color);
            priorities.add(priority);
        }

        return new PaginatedResult<>(priorities, 0, priorities.size(), priorities.size());
    }

    private JsonNode getOpenProjectResponse(String urlPart, String offset, String pageSize, String filtersString,
        String sortByString, String selectedElementsString) throws ProjectManagementException
    {
        String uri = connectionUrl + urlPart;
        try {
            URIBuilder uriBuilder = new URIBuilder(uri);
            if (!offset.isEmpty()) {
                uriBuilder.addParameter(OP_OFFSET, offset);
            }
            if (!filtersString.isEmpty()) {
                uriBuilder.addParameter(OP_FILTERS, filtersString);
            }
            if (!sortByString.isEmpty()) {
                uriBuilder.addParameter(OP_SORT_BY, sortByString);
            }
            if (!selectedElementsString.isEmpty()) {
                uriBuilder.addParameter(OP_SELECT, selectedElementsString);
            }
            if (!pageSize.isEmpty()) {
                uriBuilder.addParameter(PAGE_SIZE, pageSize);
            }
            HttpRequest request =
                createGetHttpRequest(uriBuilder.build());
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            handleOpenProjectWorkPackagesRequestExceptions(response);

            String body = response.body();
            return objectMapper.readTree(body);
        } catch (URISyntaxException e) {
            throw new ProjectManagementException(
                String.format("Failed to build the open project entity retrieval url [%s].", uri), e);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(
                String.format("Error trying to read the open project response from [%s].", uri), e);
        } catch (IOException | InterruptedException | SecurityException e) {
            throw new ProjectManagementException(
                String.format("There was an issue in communicating with [%s].", uri), e);
        }
    }

    private JsonNode getOpenProjectResponseEntities(String urlPart, String offset, String pageSize,
        String filtersString, String sortByString, String selectedElementsString) throws ProjectManagementException
    {
        return getOpenProjectResponse(urlPart, offset, pageSize, filtersString, sortByString,
            selectedElementsString).path(
            OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);
    }

    private int getTotalNumberOfWorkItems(JsonNode mainNode)
    {
        return mainNode.path("total").asInt();
    }

    private List<WorkPackage> getWorkPackagesFromResponse(JsonNode mainNode)
    {
        List<WorkPackage> workItems = new ArrayList<>();

        JsonNode elementsNode = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);
        for (JsonNode element : elementsNode) {
            workItems.add(createWorkPackageFromJson(element));
        }
        return workItems;
    }

    private WorkPackage createWorkPackageFromJson(JsonNode element)
    {
        WorkPackage workPackage = new WorkPackage();

        workPackage.setDescription(element.path(OP_DESCRIPTION).path("html").asText());
        workPackage.setPercentageDone(element.path(OP_RESPONSE_PERCENTAGE_DONE).asInt());
        int id = element.path(OP_RESPONSE_ID).asInt();
        workPackage.setId(id);

        setDates(workPackage, element);
        setCreatedAndUpdatedDates(workPackage, element);
        setWorkPackageLinksNodeProperties(workPackage, element);

        workPackage.setType(element.path("_type").asText());

        workPackage.setSubject(element.path(OP_RESPONSE_SUBJECT).asText());

        return workPackage;
    }

    private void setWorkPackageLinksNodeProperties(WorkPackage workPackage, JsonNode element)
    {
        String editCreateUrlString = "%s%s/edit";
        JsonNode linksNode = element.path(OP_RESPONSE_LINKS);
        int id = element.path(OP_RESPONSE_ID).asInt();

        String typeName = linksNode.path(OP_RESPONSE_TYPE).path(OP_RESPONSE_TITLE).asText();
        String typeUrl = String.format(editCreateUrlString, connectionUrl,
            linksNode.path(OP_RESPONSE_TYPE).path(HREF).asText()).replaceFirst(API_URL_PART, "");
        workPackage.setTypeOfWorkPackage(new Linkable(typeName, typeUrl));

        JsonNode selfNode = linksNode.path(OP_RESPONSE_SELF);
        String issueName = selfNode.path(OP_RESPONSE_TITLE).asText();
        String issueUrl = String.format("%s/work_packages/%s/activity", connectionUrl, id);
        workPackage.setSelf(new Linkable(issueName, issueUrl));

        JsonNode statusNode = linksNode.path(OP_RESPONSE_STATUS);
        String statusName = statusNode.path(OP_RESPONSE_TITLE).asText();
        String statusUrl =
            String.format(editCreateUrlString, connectionUrl,
                statusNode.path(HREF).asText().replaceFirst(API_URL_PART, ""));
        workPackage.setStatus(new Linkable(statusName, statusUrl));

        JsonNode authorNode = linksNode.path(OP_RESPONSE_AUTHOR);
        String authorName = authorNode.path(OP_RESPONSE_TITLE).asText();
        String authorUrl = connectionUrl + authorNode.path(HREF).asText().replaceFirst(API_URL_PART, "");
        workPackage.setAuthor(new Linkable(authorName, authorUrl));

        JsonNode assigneeNode = linksNode.path(OP_RESPONSE_ASSIGNEE);
        String assigneeName = assigneeNode.path(OP_RESPONSE_TITLE).asText();
        String assigneeUrl = connectionUrl + assigneeNode.path(HREF).asText().replaceFirst(API_URL_PART, "");
        workPackage.setAssignee(new Linkable(assigneeName, assigneeUrl));

        JsonNode projectNode = linksNode.path(OP_RESPONSE_PROJECT);
        String projectName = projectNode.path(OP_RESPONSE_TITLE).asText();
        String projectUrl = connectionUrl + projectNode.path(HREF).asText().replaceFirst(API_URL_PART, "");
        workPackage.setProject(new Linkable(projectName, projectUrl));

        JsonNode priorityNode = linksNode.path(OP_RESPONSE_PRIORITY);
        String priorityName = priorityNode.path(OP_RESPONSE_TITLE).asText();
        String priorityUrl = String.format("%s/work_packages/%s/activity", connectionUrl, id);
        workPackage.setPriority(new Linkable(priorityName, priorityUrl));
    }

    private void setDates(WorkPackage wp, JsonNode node)
    {
        wp.setStartDate(getDateFromNode(OP_START_DATE, node));
        wp.setDueDate(getDateFromNode(OP_DUE_DATE, node));
        wp.setDerivedStartDate(getDateFromNode(OP_DERIVED_START_DATE, node));
        wp.setDerivedDueDate(getDateFromNode(OP_DERIVED_DUE_DATE, node));
    }

    private Date getDateFromNode(String prop, JsonNode node)
    {
        JsonNode date = node.path(prop);
        if (date.isNull() || date.asText().isBlank()) {
            return null;
        }
        return LocalDate.parse(node.path(prop).asText()).toDate();
    }

    private Date getIsoDateFromNode(String prop, JsonNode node)
    {
        JsonNode date = node.path(prop);

        if (date.isNull() || date.asText().isBlank()) {
            return null;
        }

        try {
            return DATE_FORMAT.parse(date.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private void setCreatedAndUpdatedDates(WorkPackage wp, JsonNode node)
    {
        if (!node.path(OP_CREATED_AT).isNull()) {
            String createdAtText = node.path(OP_CREATED_AT).asText();
            if (!createdAtText.isBlank() && createdAtText.length() >= 10) {
                String dateOnly = createdAtText.substring(0, 10);
                wp.setCreatedAt(LocalDate.parse(dateOnly).toDate());
            }
        }

        if (!node.path(OP_UPDATED_AT).isNull()) {
            String updatedAtText = node.path(OP_UPDATED_AT).asText();
            if (!updatedAtText.isBlank() && updatedAtText.length() >= 10) {
                String dateOnly = updatedAtText.substring(0, 10);
                wp.setUpdatedAt(LocalDate.parse(dateOnly).toDate());
            }
        }
    }

    private PaginatedResult<WorkPackage> getWorkPackagePaginatedResult(JsonNode node, int offset, int pageSize)
    {
        PaginatedResult<WorkPackage> paginatedResult = new PaginatedResult<>();

        int totalNumberOfWorkItems = getTotalNumberOfWorkItems(node);

        List<WorkPackage> workPackages = getWorkPackagesFromResponse(node);

        paginatedResult.setItems(workPackages);
        paginatedResult.setPage(offset);
        paginatedResult.setPageSize(pageSize);
        paginatedResult.setTotalItems(totalNumberOfWorkItems);
        return paginatedResult;
    }

    private String buildEditUrl(String connectionUrl, String entity, Object id)
    {
        return String.format("%s/%s/%s/edit", connectionUrl, entity, id);
    }

    private HttpRequest createGetHttpRequest(URI uri)
    {
        return HttpRequest
            .newBuilder()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .uri(uri)
            .GET()
            .build();
    }

    private void handleOpenProjectWorkPackagesRequestExceptions(HttpResponse<String> response)
        throws ProjectManagementException
    {
        int statusCode = response.statusCode();

        if (statusCode >= 400 && statusCode <= 499) {
            throw new WorkPackageRetrievalBadRequestException(
                String.format("The request to the OpenProject API was invalid. [%s]", response.body())
            );
        } else if (statusCode >= 500) {
            throw new ProjectManagementException(
                String.format("Failed to retrieve the open project entities. [%s].", response.body())
            );
        }
    }
}
