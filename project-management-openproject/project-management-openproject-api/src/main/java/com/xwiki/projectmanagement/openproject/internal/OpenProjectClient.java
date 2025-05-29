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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.joda.time.LocalDate;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;

/**
 * Open project client.
 *
 * @version $Id$
 */
@Component
@Named("open-project-client")
@Singleton
public class OpenProjectClient implements ProjectManagementClient
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    private final HttpClient client = HttpClient.newHttpClient();

    private String connectionUrl;

    private String token;

    @Override
    public WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException
    {
        return null;
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int page, int pageSize, List<LiveDataQuery.Filter> filters)
        throws WorkItemRetrievalException
    {
        try {
            String filtersString = URLEncoder.encode(
                "[{\"status\":{\"operator\":\"*\",\"values\":[]}}]", StandardCharsets.UTF_8);
            String connectionName = (String) executionContext.get("instance");
            this.connectionUrl = openProjectConfiguration.getConnectionUrl(connectionName);
            this.token = openProjectConfiguration.getTokenForCurrentConfig(connectionName);
            PaginatedResult<WorkItem> paginatedResult = new PaginatedResult<>();
            HttpRequest request =
                createGetHttpRequest(token,
                    connectionUrl + "/api/v3/work_packages?filters=" + filtersString);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            List<WorkItem> workItems = getWorkItemsFromResponse(body);

            paginatedResult.setItems(workItems);
            return paginatedResult;
        } catch (Exception e) {
            throw new WorkItemRetrievalException("An error occurred while trying to get the work items");
        }
    }

    @Override
    public WorkItem createWorkItem(WorkItem workItem) throws WorkItemCreationException
    {
        return null;
    }

    @Override
    public WorkItem updateWorkItem(WorkItem workItem) throws WorkItemUpdatingException
    {
        return null;
    }

    @Override
    public boolean deleteWorkItem(String workItemId) throws WorkItemDeletionException
    {
        return false;
    }

    private void setDates(WorkItem workItem, JsonNode startDate, JsonNode dueDate)
    {
        if (startDate != null && !startDate.isNull()) {
            workItem.setStartDate(LocalDate.parse(startDate.asText()).toDate());
        }
        if (dueDate != null && !dueDate.isNull()) {
            workItem.setDueDate(LocalDate.parse(dueDate.asText()).toDate());
        }
    }

    private HttpRequest createGetHttpRequest(String token, String url) throws URISyntaxException
    {
        return HttpRequest
            .newBuilder()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .uri(new URI(url))
            .GET()
            .build();
    }

    private WorkItem createWorkItemFromJson(JsonNode element)
        throws URISyntaxException, InterruptedException, IOException
    {
        String title = "title";
        String href = "href";
        WorkItem workItem = new WorkItem();
        int id = element.path("id").asInt();
        ObjectMapper objectMapper = new ObjectMapper();
        workItem.setDescription(element.path("description").path("raw").asText());

        JsonNode startDate = element.get("startDate");
        JsonNode dueDate = element.get("dueDate");
        setDates(workItem, startDate, dueDate);

        JsonNode linksNode = element.path("_links");
        workItem.setType(linksNode.path("type").path(title).asText());
        JsonNode selfNode = linksNode.path("self");
        String issueName = selfNode.get(title).asText();
        String issueUrl = connectionUrl + "/work_packages/" + id + "/activity";
        workItem.setIdentifier(new Linkable<>(issueName, issueUrl));
        workItem.setSummary(new Linkable<>(element.path("subject").asText(), issueUrl));

        String statusUrl = connectionUrl + linksNode.path("status").get(href).asText();

        HttpRequest request = createGetHttpRequest(token, statusUrl);

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String statusBody = response.body();

        JsonNode statusJson = objectMapper.readTree(statusBody);
        boolean isResolved = statusJson.get("isClosed").asBoolean();
        workItem.setResolved(isResolved);

        JsonNode assigneeNode = linksNode.path("assignee");

        workItem.setAssignees(List.of(new Linkable<>(assigneeNode.path(title).asText(),
            connectionUrl + assigneeNode.path(href).asText().replaceFirst("/api/v3", ""))));
        return workItem;
    }

    private List<WorkItem> getWorkItemsFromResponse(String body)
        throws IOException, URISyntaxException, InterruptedException
    {
        List<WorkItem> workItems = new ArrayList<>();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode elementsNode = objectMapper.readTree(body).path("_embedded").path("elements");
        for (JsonNode element : elementsNode) {
            workItems.add(createWorkItemFromJson(element));
        }
        return workItems;
    }
}
