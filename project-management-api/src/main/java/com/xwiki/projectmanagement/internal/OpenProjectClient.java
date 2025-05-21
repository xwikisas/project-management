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
package com.xwiki.projectmanagement.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.dto.OpenProjectWorkItem;
import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

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
    private Logger logger;

    @Override
    public WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException
    {
        return null;
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int page, int pageSize, List<LiveDataQuery.Filter> filters)
        throws WorkItemRetrievalException
    {
        PaginatedResult<WorkItem> paginatedResult = new PaginatedResult<>();

        List<WorkItem> workItems = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest
                .newBuilder()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + "")
                .uri(new URI("http://localhost:8080/api/v3/work_packages"))
                .GET()
                .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JsonNode rootNode = objectMapper.readTree(body);

            JsonNode embeddedNode = rootNode.path("_embedded");

            JsonNode elementsNode = embeddedNode.path("elements");

            List<OpenProjectWorkItem> workItemList = objectMapper
                .convertValue(elementsNode, new TypeReference<List<OpenProjectWorkItem>>()
                {
                });
            for (OpenProjectWorkItem workItem : workItemList) {
                WorkItem workItemEntity = new WorkItem();
                workItemEntity.setProject(workItem.getProject());
                workItemEntity.setStartDate(workItem.getStartDate());
                workItemEntity.setDueDate(workItem.getDueDate());
                workItem.setSubject(workItem.getSubject());
                workItems.add(workItemEntity);
            }
            paginatedResult.setItems(workItems);
            return paginatedResult;
        } catch (Exception e) {
            throw new RuntimeException(e);
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
}
