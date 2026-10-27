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

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.api.ProjectsApi;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemCreationException;
import com.xwiki.projectmanagement.exception.WorkItemDeletionException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.exception.WorkItemUpdatingException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.Project;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.exception.WorkPackageRetrievalBadRequestException;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterHandler;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectSortingHandler;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

/**
 * OpenProject client.
 *
 * @version $Id$
 */
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
@Component
@Named("openproject")
@Singleton
public class OpenProjectClient implements ProjectManagementClient
{
    /**
     * Pretty name of the project management platform (in this case, OpenProject).
     */
    public static final String CLIENT_PRETTY_NAME = "OpenProject";

    private static final String EQUALS = "=";

    private static final String QUERY_PROPS_QUERY_PARAMETER = "query_props=";

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    /**
     * A work package id or, on instances using project-based semantic identifiers, a work package key (e.g.
     * {@code XWIKI-123}).
     */
    private static final String ID_OR_KEY = "(\\d+|[A-Za-z][A-Za-z0-9_]*-\\d+)";

    private static final Pattern IDS_PATTERN =
        Pattern.compile(String.format("^\\s*%1$s(\\s*,\\s*%1$s)*\\s*$", ID_OR_KEY));

    private static final Pattern IDS_SEPARATOR_PATTERN = Pattern.compile("\\s*,\\s*");

    private static final String NO_FILTERS = "[]";

    private static final Pattern PROJECTS_PATTERN = Pattern.compile("/projects/([^/]+)/");

    private static final Pattern QUERY_PROPS_PATTERN = Pattern.compile(QUERY_PROPS_QUERY_PARAMETER + "([^&]+)");

    private static final String INCORRECT_IDENTIFIER_FORMAT_MESSAGE =
        "The provided identifier value is not in a supported format.";

    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    @Inject
    private OpenProjectIdentifierResolver identifierResolver;

    @Inject
    private Logger logger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WorkItem getWorkItem(String workItemId) throws WorkItemNotFoundException, WorkItemRetrievalException
    {
        LiveDataQuery.Filter filter = new LiveDataQuery.Filter();

        filter.setProperty(WorkItem.KEY_IDENTIFIER);
        filter.getConstraints().add(new LiveDataQuery.Constraint(workItemId, EQUALS));

        PaginatedResult<WorkItem> result =
            getWorkItems(0, 1, Collections.singletonList(filter), Collections.emptyList());

        if (result.getItems().isEmpty()) {
            throw new WorkItemNotFoundException("Work Item with ID [%s] was not found.");
        }

        return result.getItems().get(0);
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int page, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemRetrievalException
    {
        try {
            int offset = (page / pageSize) + 1;
            String identifier = (String) executionContext.get("identifier");
            OpenProjectApiClient openProjectApiClient = getOpenProjectApiClient();
            OpenProjectWorkItemFields fields = getWorkItemFields(openProjectApiClient);
            List<LiveDataQuery.Filter> mappedFilters = fields.mapFilters(filters, openProjectApiClient);
            List<LiveDataQuery.SortEntry> mappedSortEntries = fields.mapSortEntries(sortEntries);

            PaginatedResult<WorkItem> workItems;
            if (identifier != null && IDS_PATTERN.matcher(identifier).matches()) {
                workItems = handleIds(openProjectApiClient, fields, identifier, page, pageSize, mappedFilters,
                    mappedSortEntries);
            } else if (identifier != null && !identifier.trim().isEmpty()) {
                workItems = handleIdentifier(openProjectApiClient, fields, identifier.trim(), offset, pageSize,
                    mappedFilters, mappedSortEntries);
            } else {
                String filtersString = OpenProjectFilterHandler.convertFilters(mappedFilters);
                String sortByString = OpenProjectSortingHandler.convertSorting(mappedSortEntries);
                PaginatedResult<WorkPackage> workPackagesPaginatedResult =
                    openProjectApiClient.getWorkPackages(offset, pageSize, filtersString, sortByString);
                workItems = OpenProjectConverters.convertPaginatedResult(workPackagesPaginatedResult,
                    workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
            }
            markClosedWorkItems(openProjectApiClient, workItems);
            return workItems;
        } catch (WorkItemRetrievalException e) {
            // Report authentication and rights failures, so that the caller can tell the user what to do.
            if (isAuthenticationOrRightsFailure(e)) {
                throw e;
            }
            return handleWorkPackageRetrievalException(e);
        } catch (ProjectManagementException e) {
            throw new WorkItemRetrievalException("An error occurred while trying to get the work items", e);
        }
    }

    private static boolean isAuthenticationOrRightsFailure(WorkItemRetrievalException e)
    {
        Integer statusCode = e.getStatusCode();
        return statusCode != null
            && (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED || statusCode == HttpURLConnection.HTTP_FORBIDDEN);
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

    @Override
    public ProjectsApi projects()
    {
        return new ProjectsApi()
        {
            @Override
            public Project get(String id) throws WorkItemNotFoundException, WorkItemRetrievalException
            {
                LiveDataQuery.Filter filter = new LiveDataQuery.Filter();

                filter.setProperty(Project.KEY_IDENTIFIER);
                filter.getConstraints().add(new LiveDataQuery.Constraint(id, EQUALS));
                PaginatedResult<Project> result = get(0, 1, Collections.singletonList(filter));
                if (result.getItems().isEmpty()) {
                    throw new WorkItemNotFoundException("Project with ID [%s] was not found.");
                }

                return result.getItems().get(0);
            }

            @Override
            public PaginatedResult<Project> get(int page, int pageSize, List<LiveDataQuery.Filter> filters)
                throws WorkItemRetrievalException
            {
                try {
                    String filtersString = OpenProjectFilterHandler.convertFilters(filters);

                    PaginatedResult<com.xwiki.projectmanagement.openproject.model.Project> projects =
                        getOpenProjectApiClient().getProjects(page, pageSize, filtersString);
                    return OpenProjectConverters.convertPaginatedResult(projects,
                        OpenProjectConverters::convertProject);
                } catch (Exception e) {
                    throw new WorkItemRetrievalException("Failed to retrieve the OpenProject projects.", e);
                }
            }
        };
    }

    private OpenProjectApiClient getOpenProjectApiClient() throws WorkItemRetrievalException
    {
        String connectionName = (String) executionContext.get("instance");
        OpenProjectAccessResult access = openProjectAccess.resolve(connectionName);
        OpenProjectApiClient openProjectApiClient = access.getClient();

        if (access.getStatus() == OpenProjectAccessStatus.NOT_CONNECTED) {
            throw new WorkItemRetrievalException(String.format(
                "The current user isn't connected to the OpenProject instance [%s].", connectionName),
                HttpURLConnection.HTTP_UNAUTHORIZED);
        } else if (openProjectApiClient == null) {
            throw new WorkItemRetrievalException(
                String.format("No configuration for instance [%s] was found.", connectionName), 407);
        }
        return openProjectApiClient;
    }

    private PaginatedResult<WorkItem> handleIdentifier(OpenProjectApiClient openProjectApiClient,
        OpenProjectWorkItemFields fields, String identifier, int offset, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        if (!URL_PATTERN.matcher(identifier).matches()) {
            throw new WorkPackageRetrievalBadRequestException(INCORRECT_IDENTIFIER_FORMAT_MESSAGE);
        }

        URL url = parseUrl(identifier);
        String urlQueryId = identifierResolver.getStandaloneQueryId(identifier);

        if (urlQueryId != null) {
            return handleSavedQuery(openProjectApiClient, fields, urlQueryId, offset, pageSize, filtersEntries,
                sortEntries);
        }

        JsonNode parametersNode = extractJsonNodeFromQuery(url.getQuery());
        String project = extractProjectFromPath(url.getPath());

        JsonNode filtersNode = null;
        JsonNode sortNode = null;

        if (parametersNode != null) {
            filtersNode = parametersNode.path("f");
            sortNode = parametersNode.path("t");
        }

        String filters = extractFiltersFromQuery(filtersEntries, filtersNode);
        String sortBy = extractSortByString(sortEntries, sortNode);

        try {
            if (project != null) {
                return OpenProjectConverters.convertPaginatedResult(
                    openProjectApiClient.getProjectWorkPackages(project, offset, pageSize, filters, sortBy),
                    workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
            }

            return OpenProjectConverters.convertPaginatedResult(
                openProjectApiClient.getWorkPackages(offset, pageSize, filters, sortBy),
                workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
        } catch (WorkPackageRetrievalBadRequestException e) {
            return handleWorkPackageRetrievalException(e);
        }
    }

    /**
     * Retrieves the work packages listed by the identifier, given by their ids or their keys. OpenProject can't filter
     * work packages by key, so the keys are first resolved to ids. Unless the livedata sorts them, the work packages
     * are returned in the order of the identifier: since there can't be more results than listed work packages, they
     * are all retrieved at once, then ordered and paginated here.
     */
    private PaginatedResult<WorkItem> handleIds(OpenProjectApiClient openProjectApiClient,
        OpenProjectWorkItemFields fields, String identifier, int page, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        List<String> ids = resolveIds(openProjectApiClient, IDS_SEPARATOR_PATTERN.split(identifier.trim()));
        if (ids.isEmpty()) {
            return new PaginatedResult<>(Collections.emptyList(), page, pageSize, 0);
        }

        Map<String, Object> idFiltersAsJson = Map.of("n", "id", "o", EQUALS, "v", ids);
        String filters =
            OpenProjectFilterHandler.mergeFilters(filtersEntries, objectMapper.valueToTree(List.of(idFiltersAsJson)));

        try {
            if (!sortEntries.isEmpty()) {
                return OpenProjectConverters.convertPaginatedResult(
                    openProjectApiClient.getWorkPackages((page / pageSize) + 1, pageSize, filters,
                        OpenProjectSortingHandler.convertSorting(sortEntries)),
                    workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
            }

            List<WorkPackage> workPackages =
                new ArrayList<>(openProjectApiClient.getWorkPackages(1, ids.size(), filters, "").getItems());
            workPackages.sort(Comparator.comparingInt(workPackage -> ids.indexOf(String.valueOf(workPackage.getId()))));
            List<WorkPackage> pageItems = workPackages.subList(Math.min(page, workPackages.size()),
                Math.min(page + pageSize, workPackages.size()));

            return OpenProjectConverters.convertPaginatedResult(
                new PaginatedResult<>(pageItems, page, pageSize, workPackages.size()),
                workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
        } catch (WorkPackageRetrievalBadRequestException e) {
            return handleWorkPackageRetrievalException(e);
        }
    }

    /**
     * Turns the work package keys into ids, dropping the duplicates and the keys of the work packages that don't exist
     * or that the user can't see, so that they don't prevent the other work packages from being displayed. Each key
     * requires a request to OpenProject, so they are resolved in parallel (and the client caches them).
     * <p>
     * TODO: send the keys directly in the id filter, without resolving them, once OpenProject accepts them there:
     * https://community.openproject.org/wp/82068 (API v3: allow filtering work packages by their semantic identifier
     * (e.g. PROJ-42) in the id filter).
     */
    private List<String> resolveIds(OpenProjectApiClient openProjectApiClient, String[] idsOrKeys)
        throws ProjectManagementException
    {
        Map<String, CompletableFuture<WorkPackage>> resolutions = new HashMap<>();
        for (String idOrKey : idsOrKeys) {
            if (!StringUtils.isNumeric(idOrKey)) {
                resolutions.computeIfAbsent(idOrKey,
                    key -> CompletableFuture.supplyAsync(() -> getWorkPackage(openProjectApiClient, key)));
            }
        }

        Set<String> ids = new LinkedHashSet<>();
        for (String idOrKey : idsOrKeys) {
            if (StringUtils.isNumeric(idOrKey)) {
                ids.add(idOrKey);
            } else {
                WorkPackage workPackage = joinResolution(idOrKey, resolutions.get(idOrKey));
                if (workPackage != null) {
                    ids.add(String.valueOf(workPackage.getId()));
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static WorkPackage getWorkPackage(OpenProjectApiClient openProjectApiClient, String key)
    {
        try {
            return openProjectApiClient.getWorkPackage(key);
        } catch (ProjectManagementException e) {
            throw new CompletionException(e);
        }
    }

    private WorkPackage joinResolution(String key, CompletableFuture<WorkPackage> resolution)
        throws ProjectManagementException
    {
        try {
            return resolution.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WorkItemRetrievalException
                && !isAuthenticationOrRightsFailure((WorkItemRetrievalException) cause))
            {
                // OpenProject rejected the key, which is then skipped like a work package that doesn't exist.
                logger.warn("Failed to resolve the work package key [{}]: {}", key, cause.getMessage());
                return null;
            } else if (cause instanceof ProjectManagementException) {
                throw (ProjectManagementException) cause;
            }
            throw new WorkItemRetrievalException(String.format("Failed to resolve the work package key [%s].", key),
                cause);
        }
    }

    /**
     * Marks the work items whose status is closed as resolved. The work package collections only link the status of
     * each work package, so whether it is closed comes from the statuses of the instance (which the client caches).
     */
    private void markClosedWorkItems(OpenProjectApiClient openProjectApiClient, PaginatedResult<WorkItem> workItems)
    {
        if (workItems.getItems() == null || workItems.getItems().isEmpty()) {
            return;
        }
        Set<String> closedStatuses = new HashSet<>();
        try {
            PaginatedResult<Status> statuses = openProjectApiClient.getStatuses();
            if (statuses == null || statuses.getItems() == null) {
                return;
            }
            for (Status status : statuses.getItems()) {
                if (status.isClosed()) {
                    closedStatuses.add(status.getName());
                }
            }
        } catch (ProjectManagementException | UnsupportedOperationException e) {
            // The work items can still be displayed, without telling which ones are closed.
            logger.warn("Failed to retrieve the work package statuses: {}", ExceptionUtils.getRootCauseMessage(e));
            return;
        }
        for (WorkItem workItem : workItems.getItems()) {
            workItem.setResolved(closedStatuses.contains(workItem.getStatus()));
        }
    }

    private OpenProjectWorkItemFields getWorkItemFields(OpenProjectApiClient openProjectApiClient)
    {
        try {
            return OpenProjectWorkItemFields.of(openProjectApiClient);
        } catch (ProjectManagementException | UnsupportedOperationException e) {
            // The work items can still be displayed, without the fields that the generic properties don't cover.
            logger.warn("Failed to retrieve the work package fields: {}", ExceptionUtils.getRootCauseMessage(e));
            return OpenProjectWorkItemFields.empty();
        }
    }

    private PaginatedResult<WorkItem> handleWorkPackageRetrievalException(ProjectManagementException e)
    {
        logger.warn("Failed to retrieve work packages: {}", e.getMessage());
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        result.setTotalItems(0);
        return result;
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
        Matcher matcher = PROJECTS_PATTERN.matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractFiltersFromQuery(List<LiveDataQuery.Filter> filtersList, JsonNode filtersNode)
        throws ProjectManagementException
    {
        return OpenProjectFilterHandler.mergeFilters(filtersList, filtersNode);
    }

    /**
     * Retrieves the work packages matching a saved query. As long as the livedata doesn't filter anything, the results
     * are read straight from the query, which already embeds them. Once the livedata filters, the queries endpoint
     * can't be used anymore since the filters it receives replace the ones of the query instead of restricting them, so
     * the two sets are merged and sent to the work packages endpoint. A query bound to a project keeps its scope:
     * OpenProject doesn't add the project to the filters of the query, so the project is read from the path of the
     * results link and the project work packages endpoint is used instead.
     * The filters of the query are read from its results link. The link is normally retrieved once, before the macro
     * got executed, and passed through the source parameters; it is retrieved here only when it is missing.
     */
    private PaginatedResult<WorkItem> handleSavedQuery(OpenProjectApiClient openProjectApiClient,
        OpenProjectWorkItemFields fields, String queryId, int offset, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        String sortBy = sortEntries.isEmpty() ? "" : OpenProjectSortingHandler.convertSorting(sortEntries);

        try {
            if (NO_FILTERS.equals(OpenProjectFilterHandler.convertFilters(filtersEntries))) {
                return OpenProjectConverters.convertPaginatedResult(
                    openProjectApiClient.getQueryWorkPackages(queryId, offset, pageSize, sortBy),
                    workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields)
                );
            }

            String resultsUrl = (String) executionContext.get(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL);
            if (StringUtils.isEmpty(resultsUrl)) {
                resultsUrl = identifierResolver.getSavedQueryResultsUrl(openProjectApiClient, queryId);
            }
            String filters = OpenProjectFilterHandler.mergeApiFilters(filtersEntries,
                identifierResolver.getSavedQueryFilters(resultsUrl));
            String sorting = sortBy.isEmpty() ? identifierResolver.getSavedQuerySortBy(resultsUrl) : sortBy;
            String project = identifierResolver.getSavedQueryProject(resultsUrl);

            if (project != null) {
                return OpenProjectConverters.convertPaginatedResult(
                    openProjectApiClient.getProjectWorkPackages(project, offset, pageSize, filters, sorting),
                    workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields)
                );
            }

            return OpenProjectConverters.convertPaginatedResult(
                openProjectApiClient.getWorkPackages(offset, pageSize, filters, sorting),
                workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields)
            );
        } catch (WorkPackageRetrievalBadRequestException e) {
            return handleWorkPackageRetrievalException(e);
        }
    }

    private String extractSortByString(List<LiveDataQuery.SortEntry> sortEntries, JsonNode sortByNode)
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

        Matcher matcher = QUERY_PROPS_PATTERN.matcher(queryParameters);

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
