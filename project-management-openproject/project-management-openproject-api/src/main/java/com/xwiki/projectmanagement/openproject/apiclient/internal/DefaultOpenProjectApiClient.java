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
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.http.client.utils.URIBuilder;
import org.joda.time.LocalDate;
import org.xwiki.component.annotation.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.apiclient.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;

/**
 * Default Open project get items client helper.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultOpenProjectApiClient implements OpenProjectApiClient
{
    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    private final HttpClient client = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int offset, int pageSize, String filters)
    {
        try {
            String connectionName = (String) executionContext.get("instance");
            String connectionUrl = openProjectConfiguration.getConnectionUrl(connectionName);
            String token = openProjectConfiguration.getTokenForCurrentConfig(connectionName);
            URIBuilder uriBuilder = new URIBuilder(connectionUrl + "/api/v3/work_packages");
            uriBuilder.addParameter("offset", String.valueOf(offset));
            uriBuilder.addParameter("pageSize", String.valueOf(pageSize));
            uriBuilder.addParameter("filters", filters);

            PaginatedResult<WorkItem> paginatedResult = new PaginatedResult<>();
            HttpRequest request =
                createGetHttpRequest(token, uriBuilder.build());

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            int totalNumberOfWorkItems = getTotalNumberOfWorkItems(body);

            List<WorkItem> workItems = getWorkItemsFromResponse(connectionUrl, token, body);

            paginatedResult.setItems(workItems);
            paginatedResult.setPage(offset);
            paginatedResult.setPageSize(pageSize);
            paginatedResult.setTotalItems(totalNumberOfWorkItems);
            return paginatedResult;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int getTotalNumberOfWorkItems(String body) throws JsonProcessingException
    {
        return objectMapper.readTree(body).path("total").asInt();
    }

    private List<WorkItem> getWorkItemsFromResponse(String connectionUrl, String token, String body)
        throws IOException, URISyntaxException, InterruptedException
    {
        List<WorkItem> workItems = new ArrayList<>();

        JsonNode elementsNode = objectMapper.readTree(body).path("_embedded").path("elements");
        for (JsonNode element : elementsNode) {
            workItems.add(createWorkItemFromJson(connectionUrl, token, element));
        }
        return workItems;
    }

    private WorkItem createWorkItemFromJson(String connectionUrl, String token, JsonNode element)
        throws URISyntaxException, InterruptedException, IOException
    {
        String title = "title";
        String href = "href";
        WorkItem workItem = new WorkItem();
        int id = element.path("id").asInt();
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
        setWorkItemIsResolved(token, new URI(statusUrl), workItem);

        JsonNode assigneeNode = linksNode.path("assignee");
        workItem.setAssignees(List.of(new Linkable<>(assigneeNode.path(title).asText(),
            connectionUrl + assigneeNode.path(href).asText().replaceFirst("/api/v3", ""))));
        return workItem;
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

    private void setWorkItemIsResolved(String token, URI statusUri, WorkItem workItem)
        throws InterruptedException, IOException
    {
        HttpRequest request = createGetHttpRequest(token, statusUri);

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String statusBody = response.body();

        JsonNode statusJson = objectMapper.readTree(statusBody);
        boolean isResolved = statusJson.get("isClosed").asBoolean();
        workItem.setResolved(isResolved);
    }

    private HttpRequest createGetHttpRequest(String token, URI uri)
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
