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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.openproject.exception.WorkPackageRetrievalBadRequestException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterHandler;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectSortingHandler;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

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
    private static final String QUERY_PROPS_QUERY_PARAMETER = "query_props=";

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    @Inject
    private Logger logger;

    private OpenProjectApiClient openProjectApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException
    {
        return null;
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int page, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries)
        throws WorkItemRetrievalException
    {
        try {
            int offset = (page / pageSize) + 1;
            String identifier = (String) executionContext.get("identifier");
            String connectionName = (String) executionContext.get("instance");
            openProjectApiClient = openProjectConfiguration.getOpenProjectApiClient(connectionName);

            if (openProjectApiClient == null) {
                throw new WorkItemRetrievalException(
                    String.format("No configuration for instance [%s] was found.", connectionName));
            }

            if (identifier != null) {
                return handleIdentifier(identifier, offset, pageSize, filters, sortEntries);
            }

            String filtersString = OpenProjectFilterHandler.convertFilters(filters);
            String sortByString = OpenProjectSortingHandler.convertSorting(sortEntries);
            PaginatedResult<WorkPackage> workPackagesPaginatedResult =
                openProjectApiClient.getWorkPackages(offset,
                    pageSize, filtersString, sortByString);

            return OpenProjectConverters.convertPaginatedResult(
                workPackagesPaginatedResult,
                OpenProjectConverters::convertWorkPackageToWorkItem
            );
        } catch (WorkPackageRetrievalBadRequestException e) {
            return handleWorkPackageRetrievalException(e);
        } catch (ProjectManagementException e) {
            throw new WorkItemRetrievalException("An error occurred while trying to get the work items", e);
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

    private PaginatedResult<WorkItem> handleIdentifier(String identifier, int offset, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries,
        List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        URL url = parseUrl(identifier);
        JsonNode parametersNode = extractJsonNodeFromQuery(url.getQuery());
        String project = extractProjectFromPath(url.getPath());

        JsonNode filtersValue = null;
        JsonNode sortByValue = null;

        if (parametersNode != null) {
            filtersValue = parametersNode.path("f");
            sortByValue = parametersNode.path("t");
        }

        String filters = extractFiltersFromQuery(filtersValue, filtersEntries);
        String sortBy = extractSortByString(sortByValue, sortEntries);

        try {
            PaginatedResult<WorkPackage> workPackagesPaginatedResult = (project != null)
                ? openProjectApiClient.getProjectWorkPackages(project, offset, pageSize, filters, sortBy)
                : openProjectApiClient.getWorkPackages(offset, pageSize, filters, sortBy);

            return OpenProjectConverters.convertPaginatedResult(
                workPackagesPaginatedResult,
                OpenProjectConverters::convertWorkPackageToWorkItem
            );
        } catch (WorkPackageRetrievalBadRequestException e) {
            return handleWorkPackageRetrievalException(e);
        }
    }

    private PaginatedResult<WorkItem> handleWorkPackageRetrievalException(ProjectManagementException e)
    {
        logger.warn("Failed to retrieve work packages: {}", e.getMessage());
        return new PaginatedResult<>();
    }

    private URL parseUrl(String url) throws WorkItemRetrievalException
    {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new WorkItemRetrievalException("The identifier format is not correct", e);
        }
    }

    private String extractProjectFromPath(String path)
    {
        Matcher matcher = Pattern.compile("/projects/([^/]+)/").matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractFiltersFromQuery(JsonNode filtersNode, List<LiveDataQuery.Filter> filtersList)
        throws ProjectManagementException
    {
        return OpenProjectFilterHandler.mergeFilters(filtersList, filtersNode);
    }

    private String extractSortByString(JsonNode sortByNode, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        String sortByString = "";

        if (sortByNode != null) {
            sortByString = sortByNode.asText();
        }

        return OpenProjectSortingHandler.mergeSortEntries(sortEntries, sortByString);
    }

    private JsonNode extractJsonNodeFromQuery(String queryParameters) throws WorkItemRetrievalException
    {
        if (queryParameters == null || queryParameters.isEmpty()) {
            return null;
        }

        Pattern pattern = Pattern.compile(QUERY_PROPS_QUERY_PARAMETER + "([^&]+)");
        Matcher matcher = pattern.matcher(queryParameters);

        if (!matcher.find()) {
            throw new WorkItemRetrievalException("The query parameters format is not correct");
        }

        String parameter = matcher.group(1);
        parameter = URLDecoder.decode(parameter, StandardCharsets.UTF_8);
        try {
            return objectMapper.readTree(parameter);
        } catch (JsonProcessingException e) {
            throw new WorkItemRetrievalException("An error occurred while trying to get the query parameters", e);
        }
    }
}
