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
package com.xwiki.projectmanagement.openproject.apiclient.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.utils.URIBuilder;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
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
public class OpenProjectApiClient
{
    private static final String OP_RESPONSE_AUTHOR = "author";

    private static final String OP_RESPONSE_EMBEDDED = "_embedded";

    private static final String OP_RESPONSE_ELEMENTS = "elements";

    private static final String OP_FILTERS = "filters";

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

    private static final String OP_START_DATE = "startDate";

    private static final String OP_DUE_DATE = "dueDate";

    private static final String OP_CREATED_AT = "createdAt";

    private static final String OP_UPDATED_AT = "updatedAt";

    private static final String OP_DERIVED_START_DATE = "derivedStartDate";

    private static final String OP_DERIVED_DUE_DATE = "derivedDueDate";

    private static final String HREF = "href";

    private static final String OP_RESPONSE_ASSIGNEE = "assignee";

    private static final String OP_RESPONSE_TYPE = "type";

    private static final String API_URL_PART = "/api/v3";

    private static final String API_URL_WORK_PACKAGES = "/api/v3/work_packages";

    private static final String API_URL_TYPES = "/api/v3/types";

    private static final String API_URL_STATUSES = "/api/v3/statuses";

    private static final String API_URL_PRIORITIES = "/api/v3/priorities";

    private static final String API_URL_USERS = "/api/v3/users";

    private static final String API_URL_PROJECTS = "/api/v3/projects";

    private static final String API_URL_SELECT_ELEMENTS_PARAM = "elements/id,elements/name";

    private final HttpClient client = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String token;

    private final String connectionUrl;

    /**
     * Constructs a new {@code OpenProjectApiClient} with the given authentication token and connection URL.
     *
     * @param token the API authentication token used to access the OpenProject API
     * @param connectionUrl the base URL of the OpenProject instance
     */
    public OpenProjectApiClient(String connectionUrl, String token)
    {
        this.connectionUrl = connectionUrl;
        this.token = token;
    }

    /**
     * Retrieves a list of {@link WorkPackage} objects from the OpenProject API.
     *
     * @param offset the offset index from which to start retrieving work items
     * @param pageSize the maximum number of work items to return
     * @param filters optional filters to apply (e.g. query parameters encoded as a string)
     * @return a {@link PaginatedResult} containing the list of work packages and pagination metadata
     */
    public PaginatedResult<WorkPackage> getWorkPackages(int offset, int pageSize, String filters)
    {
        try {
            URIBuilder uriBuilder = new URIBuilder(connectionUrl + API_URL_WORK_PACKAGES);
            uriBuilder.addParameter("offset", String.valueOf(offset));
            uriBuilder.addParameter(PAGE_SIZE, String.valueOf(pageSize));
            uriBuilder.addParameter(OP_FILTERS, filters);

            PaginatedResult<WorkPackage> paginatedResult = new PaginatedResult<>();
            HttpRequest request =
                createGetHttpRequest(uriBuilder.build());

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            JsonNode mainNode = objectMapper.readTree(body);

            int totalNumberOfWorkItems = getTotalNumberOfWorkItems(mainNode);

            List<WorkPackage> workPackages = getWorkPackagesFromResponse(mainNode);

            paginatedResult.setItems(workPackages);
            paginatedResult.setPage(offset);
            paginatedResult.setPageSize(pageSize);
            paginatedResult.setTotalItems(totalNumberOfWorkItems);
            return paginatedResult;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves a paginated list of users based on the specified page size and filter criteria.
     *
     * @param pageSize the number of users to retrieve per page
     * @param filters a JSON-formatted string representing filter criteria to apply to the request
     * @return a list of maps
     */
    public List<User> getUsers(int pageSize, String filters)
    {
        JsonNode elements =
            getSuggestionsMainNode(
                API_URL_USERS,
                String.valueOf(pageSize),
                filters,
                API_URL_SELECT_ELEMENTS_PARAM
            );
        List<User> users = new ArrayList<>();
        for (JsonNode element : elements) {
            User user = new User();
            int id = element.path(OP_RESPONSE_ID).asInt();

            String name = element.path(OP_RESPONSE_NAME).asText();
            user.setId(id);
            user.setName(name);
            users.add(user);
        }
        return users;
    }

    /**
     * Retrieves a paginated list of projects based on the specified page size and filter criteria.
     *
     * @param pageSize the number of users to retrieve per page
     * @param filters a JSON-formatted string representing filter criteria to apply to the request
     * @return a list of maps
     */
    public List<Project> getProjects(int pageSize, String filters)
    {
        JsonNode elements =
            getSuggestionsMainNode(
                API_URL_PROJECTS,
                String.valueOf(pageSize),
                filters,
                API_URL_SELECT_ELEMENTS_PARAM
            );
        List<Project> projects = new ArrayList<>();
        for (JsonNode element : elements) {
            Project project = new Project();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String name = element.path(OP_RESPONSE_NAME).asText();
            project.setId(id);
            project.setName(name);
            projects.add(project);
        }
        return projects;
    }

    /**
     * Retrieves all available work-package types from the current OpenProject configuration.
     *
     * @return a List of Maps
     */
    public List<Type> getTypes()
    {
        JsonNode elements = getSuggestionsMainNode(API_URL_TYPES, "", "", "");
        List<Type> types = new ArrayList<>();
        for (JsonNode element : elements) {
            Type type = new Type();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String name = element.path(OP_RESPONSE_NAME).asText();
            type.setName(name);
            type.setId(id);
            types.add(type);
        }
        return types;
    }

    /**
     * Retrieves all available work-package statuses from the current OpenProject configuration.
     *
     * @return a List of Maps
     */
    public List<Status> getStatuses()
    {
        JsonNode elements = getSuggestionsMainNode(API_URL_STATUSES, "", "", "");
        List<Status> statuses = new ArrayList<>();
        for (JsonNode element : elements) {
            Status status = new Status();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String labelName = element.path(OP_RESPONSE_NAME).asText();
            status.setId(id);
            status.setName(labelName);
            statuses.add(status);
        }
        return statuses;
    }

    /**
     * Retrieves all available work-package priorities from the current OpenProject configuration.
     *
     * @return a List of Maps
     */
    public List<Priority> getPriorities()
    {
        JsonNode elements = getSuggestionsMainNode(API_URL_PRIORITIES, "", "", "");
        List<Priority> priorities = new ArrayList<>();
        for (JsonNode element : elements) {
            Priority priority = new Priority();
            int id = element.path(OP_RESPONSE_ID).asInt();
            String name = element.path(OP_RESPONSE_NAME).asText();
            priority.setId(id);
            priority.setName(name);
            priorities.add(priority);
        }
        return priorities;
    }

    private JsonNode getSuggestionsMainNode(String urlPart, String pageSize, String filtersString,
        String selectedElementsString)
    {
        try {
            URIBuilder uriBuilder = new URIBuilder(connectionUrl + urlPart);
            if (!filtersString.isEmpty()) {
                uriBuilder.addParameter(OP_FILTERS, filtersString);
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
            String body = response.body();
            return objectMapper.readTree(body).path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

        workPackage.setDescription(element.path(OP_DESCRIPTION).path("raw").asText());
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
        String editCreateUrlString = "%s/%s/edit";
        JsonNode linksNode = element.path(OP_RESPONSE_LINKS);
        int id = element.path(OP_RESPONSE_ID).asInt();

        String typeName = linksNode.path(OP_RESPONSE_TYPE).path(OP_RESPONSE_TITLE).asText();
        String typeUrl = String.format(editCreateUrlString, connectionUrl,
            linksNode.path(OP_RESPONSE_TYPE).path(HREF).asText()).replaceFirst(API_URL_PART, "");
        workPackage.setTypeOfWorkPackage(new Linkable(typeName, typeUrl));

        JsonNode selfNode = linksNode.path(OP_RESPONSE_SELF);
        String issueName = selfNode.path(OP_RESPONSE_TITLE).asText();
        String issueUrl = String.format("%s/work_packages/%s/activity", connectionUrl, id);
        workPackage.setSelf(new Linkable(issueUrl, issueName));

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
        String priorityUrl = String.format("%s/%s/activity", connectionUrl,
            priorityNode.path(HREF).asText().replaceFirst(API_URL_PART, ""));
        workPackage.setPriority(new Linkable(priorityName, priorityUrl));
    }

    private void setDates(WorkPackage wp, JsonNode node)
    {
        if (!node.path(OP_START_DATE).isNull() && !node.path(OP_START_DATE).asText().isBlank()) {
            wp.setStartDate(LocalDate.parse(node.path(OP_START_DATE).asText()).toDate());
        }

        if (!node.path(OP_DUE_DATE).isNull() && !node.path(OP_DUE_DATE).asText().isBlank()) {
            wp.setDueDate(LocalDate.parse(node.path(OP_DUE_DATE).asText()).toDate());
        }

        if (!node.path(OP_DERIVED_START_DATE).isNull() && !node.path(OP_DERIVED_START_DATE).asText().isBlank()) {
            wp.setDerivedStartDate(LocalDate.parse(node.path(OP_DERIVED_START_DATE).asText()).toDate());
        }

        if (!node.path(OP_DERIVED_DUE_DATE).isNull() && !node.path(OP_DERIVED_DUE_DATE).asText().isBlank()) {
            wp.setDerivedDueDate(LocalDate.parse(node.path(OP_DERIVED_DUE_DATE).asText()).toDate());
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
}
