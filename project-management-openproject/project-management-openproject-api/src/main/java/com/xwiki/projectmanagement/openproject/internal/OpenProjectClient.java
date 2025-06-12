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

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.apiclient.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.filterconverter.internal.OpenProjectFilterHandler;

/**
 * Open project client.
 *
 * @version $Id$
 */
@Component
@Named("openproject")
@Singleton
public class OpenProjectClient implements ProjectManagementClient
{
    @Inject
    private OpenProjectApiClient openProjectApiClient;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

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
            int offset = (page / pageSize) + 1;
            String connectionName = (String) executionContext.get("instance");
            String connectionUrl = openProjectConfiguration.getConnectionUrl(connectionName);
            String token = openProjectConfiguration.getTokenForCurrentConfig(connectionName);

            String identifier = (String) executionContext.get("identifier");

            String filtersString;
            if (identifier != null) {
                String parameterName = "query_props=";
                String queryParam =
                    identifier.substring(identifier.indexOf(parameterName) + parameterName.length());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode queriesNode = mapper.readTree(queryParam);
                JsonNode filtersNode = queriesNode.path("f");
                List<Map<String, Object>> filtersList =
                    mapper.convertValue(filtersNode, new TypeReference<List<Map<String, Object>>>()
                    {
                    });
                filtersString = OpenProjectFilterHandler.convertFiltersFromQuery(filtersList);
            } else {
                filtersString = OpenProjectFilterHandler.convertFilters(filters);
            }
            return openProjectApiClient.getWorkItems(offset, pageSize, connectionUrl, token, filtersString);
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
}
