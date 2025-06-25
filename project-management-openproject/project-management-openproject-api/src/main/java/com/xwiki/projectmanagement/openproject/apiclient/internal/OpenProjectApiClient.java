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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.utils.URIBuilder;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Default Open project get items client helper.
 *
 * @version $Id$
 */
public class OpenProjectApiClient
{
    private static final String AUTHOR = "author";

    private static final String EMBEDDED = "_embedded";

    private static final String ELEMENTS = "elements";

    private static final String FILTERS = "filters";

    private static final String SELECT = "select";

    private static final String ID = "id";

    private static final String LINKS = "_links";

    private static final String SELF = "self";

    private static final String SUBJECT = "subject";

    private static final String STATUS = "status";

    private static final String PAGE_SIZE = "pageSize";

    private static final String PROJECT = "project";

    private static final String VALUE = "value";

    private static final String LABEL = "label";

    private static final String URL = "url";

    private static final String NAME = "name";

    private static final String PRIORITY = "priority";

    private static final String TITLE = "title";

    private static final String HREF = "href";

    private static final String ASSIGNEE = "assignee";

    private static final String TYPE = "type";

    private static final String WORK_PACKAGES = "work_packages";

    private static final String ACTIVITY = "activity";

    private static final String API_URL_PART = "/api/v3";

    private static final String WORK_PACKAGES_API_URL = "/api/v3/work_packages";

    private static final String TYPES_API_URL = "/api/v3/types";

    private static final String STATUSES_API_URL = "/api/v3/statuses";

    private static final String PRIORITIES_API_URL = "/api/v3/priorities";

    private static final String USERS_API_URL = "/api/v3/users";

    private static final String PROJECTS_API_URL = "/api/v3/projects";

    private static final String SELECT_ELEMENTS_FROM_API_URL_PARAM = "elements/id,elements/name";

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
     * Retrieves a paginated list of {@link WorkItem} objects from the OpenProject API.
     *
     * @param offset the offset index from which to start retrieving work items
     * @param pageSize the maximum number of work items to return
     * @param filters optional filters to apply (e.g. query parameters encoded as a string)
     * @return a {@link PaginatedResult} containing the list of work items and pagination metadata
     */
    public PaginatedResult<WorkItem> getWorkItems(int offset, int pageSize, String filters)
    {
        try {
            URIBuilder uriBuilder = new URIBuilder(connectionUrl + WORK_PACKAGES_API_URL);
            uriBuilder.addParameter("offset", String.valueOf(offset));
            uriBuilder.addParameter(PAGE_SIZE, String.valueOf(pageSize));
            uriBuilder.addParameter(FILTERS, filters);

            PaginatedResult<WorkItem> paginatedResult = new PaginatedResult<>();
            HttpRequest request =
                createGetHttpRequest(uriBuilder.build());

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            int totalNumberOfWorkItems = getTotalNumberOfWorkItems(body);

            List<WorkItem> workItems = getWorkItemsFromResponse(body);

            paginatedResult.setItems(workItems);
            paginatedResult.setPage(offset);
            paginatedResult.setPageSize(pageSize);
            paginatedResult.setTotalItems(totalNumberOfWorkItems);
            return paginatedResult;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves a paginated list of work package identifiers based on the specified page size and filter criteria.
     *
     * @param pageSize the number of work packages id's to retrieve per page
     * @param filters a JSON-formatted string representing filter criteria to apply to the request
     * @return a list of maps
     */
    public List<Map<String, String>> getIdentifiers(int pageSize, String filters)
    {
        JsonNode elements = getSuggestionsMainNode(WORK_PACKAGES_API_URL, String.valueOf(pageSize),
            filters, "elements/id,elements/subject");
        List<Map<String, String>> identifiers = new ArrayList<>();
        for (JsonNode element : elements) {
            Map<String, String> identifier = new HashMap<>();
            String id = element.path(ID).asText();
            String label = element.path(SUBJECT).asText();
            String url = String.format("%s/%s/%s/%s", connectionUrl, WORK_PACKAGES, id, ACTIVITY);
            identifier.put(VALUE, id);
            identifier.put(LABEL, label);
            identifier.put(URL, url);
            identifiers.add(identifier);
        }
        return identifiers;
    }

    /**
     * Retrieves a paginated list of users based on the specified page size and filter criteria.
     *
     * @param pageSize the number of users to retrieve per page
     * @param filters a JSON-formatted string representing filter criteria to apply to the request
     * @return a list of maps
     */
    public List<Map<String, String>> getUsers(int pageSize, String filters)
    {
        JsonNode elements =
            getSuggestionsMainNode(
                USERS_API_URL,
                String.valueOf(pageSize),
                filters,
                SELECT_ELEMENTS_FROM_API_URL_PARAM
            );
        List<Map<String, String>> users = new ArrayList<>();
        for (JsonNode element : elements) {
            Map<String, String> user = new HashMap<>();
            String id = element.path(ID).asText();
            String label = element.path(NAME).asText();
            String url = String.format("%s/users/%s", connectionUrl, id);
            user.put(VALUE, id);
            user.put(LABEL, label);
            user.put(URL, url);
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
    public List<Map<String, String>> getProjects(int pageSize, String filters)
    {
        JsonNode elements =
            getSuggestionsMainNode(
                PROJECTS_API_URL,
                String.valueOf(pageSize),
                filters,
                SELECT_ELEMENTS_FROM_API_URL_PARAM
            );
        List<Map<String, String>> projects = new ArrayList<>();
        for (JsonNode element : elements) {
            Map<String, String> project = new HashMap<>();
            String id = element.path(ID).asText();
            String label = element.path(NAME).asText();
            String url = String.format("%s/projects/%s", connectionUrl, id);
            project.put(VALUE, id);
            project.put(LABEL, label);
            project.put(URL, url);
            projects.add(project);
        }
        return projects;
    }

    /**
     * Retrieves all available work-package types from the current OpenProject configuration.
     *
     * @return a List of Maps
     */
    public List<Map<String, String>> getTypes()
    {
        JsonNode elements = getSuggestionsMainNode(TYPES_API_URL, "", "", "");
        List<Map<String, String>> types = new ArrayList<>();
        for (JsonNode element : elements) {
            Map<String, String> type = new HashMap<>();
            String id = element.path(ID).asText();
            String name = element.path(NAME).asText();
            String url = String.format("%s/types/%s/edit/settings", connectionUrl, id);
            type.put(VALUE, id);
            type.put(LABEL, name);
            type.put(URL, url);
            types.add(type);
        }
        return types;
    }

    /**
     * Retrieves all available work-package statuses from the current OpenProject configuration.
     *
     * @return a List of Maps
     */
    public List<Map<String, String>> getStatuses()
    {
        JsonNode elements = getSuggestionsMainNode(STATUSES_API_URL, "", "", "");
        List<Map<String, String>> statuses = new ArrayList<>();
        for (JsonNode element : elements) {
            Map<String, String> status = new HashMap<>();
            String id = element.path(ID).asText();
            String labelName = element.path(NAME).asText();
            String url = String.format("%s/statuses/%s/edit", connectionUrl, id);
            status.put(VALUE, id);
            status.put(LABEL, labelName);
            status.put(URL, url);
            statuses.add(status);
        }
        return statuses;
    }

    /**
     * Retrieves all available work-package priorities from the current OpenProject configuration.
     *
     * @return a List of Maps
     */
    public List<Map<String, String>> getPriorities()
    {
        JsonNode elements = getSuggestionsMainNode(PRIORITIES_API_URL, "", "", "");
        List<Map<String, String>> priorities = new ArrayList<>();
        for (JsonNode element : elements) {
            Map<String, String> priority = new HashMap<>();
            String id = element.path(ID).asText();
            String labelName = element.path(NAME).asText();
            String url = String.format("%s/priorities/%s/edit", connectionUrl, id);
            priority.put(VALUE, id);
            priority.put(LABEL, labelName);
            priority.put(URL, url);
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
                uriBuilder.addParameter(FILTERS, filtersString);
            }
            if (!selectedElementsString.isEmpty()) {
                uriBuilder.addParameter(SELECT, selectedElementsString);
            }
            if (!pageSize.isEmpty()) {
                uriBuilder.addParameter(PAGE_SIZE, pageSize);
            }
            HttpRequest request =
                createGetHttpRequest(uriBuilder.build());
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            return objectMapper.readTree(body).path(EMBEDDED).path(ELEMENTS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int getTotalNumberOfWorkItems(String body) throws JsonProcessingException
    {
        return objectMapper.readTree(body).path("total").asInt();
    }

    private List<WorkItem> getWorkItemsFromResponse(String body)
        throws IOException, URISyntaxException, InterruptedException
    {
        List<WorkItem> workItems = new ArrayList<>();

        JsonNode elementsNode = objectMapper.readTree(body).path(EMBEDDED).path(ELEMENTS);
        for (JsonNode element : elementsNode) {
            workItems.add(createWorkItemFromJson(element));
        }
        return workItems;
    }

    private WorkItem createWorkItemFromJson(JsonNode element)
        throws URISyntaxException, InterruptedException, IOException
    {

        WorkItem workItem = new WorkItem();
        int id = element.path(ID).asInt();
        workItem.setDescription(element.path("description").path("raw").asText());

        JsonNode startDate = element.get("startDate");
        JsonNode dueDate = element.get("dueDate");
        JsonNode createdAtDate = element.get("createdAt");
        JsonNode updatedAtDate = element.get("updatedAt");
        setDates(workItem, startDate, dueDate, createdAtDate, updatedAtDate);

        JsonNode linksNode = element.path(LINKS);
        workItem.setType(linksNode.path(TYPE).path(TITLE).asText());
        JsonNode selfNode = linksNode.path(SELF);
        String issueName = selfNode.get(TITLE).asText();
        String issueUrl = String.format("%s/work_packages/%s/activity", connectionUrl, id);
        workItem.setIdentifier(new Linkable<>(issueName, issueUrl));
        workItem.setSummary(new Linkable<>(element.path(SUBJECT).asText(), issueUrl));

        JsonNode statusNode = linksNode.path(STATUS);
        workItem.setStatus(statusNode.path(TITLE).asText());
        String statusUrl = connectionUrl + linksNode.path(STATUS).get(HREF).asText();
        setWorkItemIsResolved(new URI(statusUrl), workItem);

        JsonNode authorNode = linksNode.path(AUTHOR);
        workItem.setCreator(new Linkable<>(authorNode.path(TITLE).asText(),
            connectionUrl + authorNode.path(HREF).asText().replaceFirst(API_URL_PART, "")));

        JsonNode assigneeNode = linksNode.path(ASSIGNEE);
        workItem.setAssignees(List.of(new Linkable<>(assigneeNode.path(TITLE).asText(),
            connectionUrl + assigneeNode.path(HREF).asText().replaceFirst(API_URL_PART, ""))));

        JsonNode projectNode = linksNode.path(PROJECT);
        workItem.setProject(new Linkable<>(projectNode.path(TITLE).asText(),
            connectionUrl + projectNode.path(HREF).asText().replaceFirst(API_URL_PART, "")));

        JsonNode priorityNode = linksNode.path(PRIORITY);
        workItem.setPriority(priorityNode.path(TITLE).asText());

        return workItem;
    }

    private void setDates(WorkItem workItem, JsonNode startDate, JsonNode dueDate, JsonNode createdAtDate,
        JsonNode updatedAtDate)
    {
        if (startDate != null && !startDate.isNull()) {
            workItem.setStartDate(LocalDate.parse(startDate.asText()).toDate());
        }
        if (dueDate != null && !dueDate.isNull()) {
            workItem.setDueDate(LocalDate.parse(dueDate.asText()).toDate());
        }

        if (createdAtDate != null && !createdAtDate.isNull()) {
            String iso = createdAtDate.asText();
            String dateOnly = iso.substring(0, 10);
            workItem.setCreationDate(LocalDate.parse(dateOnly).toDate());
        }

        if (updatedAtDate != null && !updatedAtDate.isNull()) {
            String iso = updatedAtDate.asText();
            String dateOnly = iso.substring(0, 10);
            workItem.setUpdateDate(LocalDate.parse(dateOnly).toDate());
        }
    }

    private void setWorkItemIsResolved(URI statusUri, WorkItem workItem)
        throws InterruptedException, IOException
    {
        HttpRequest request = createGetHttpRequest(statusUri);

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String statusBody = response.body();

        JsonNode statusJson = objectMapper.readTree(statusBody);
        boolean isResolved = statusJson.get("isClosed").asBoolean();
        workItem.setResolved(isResolved);
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
