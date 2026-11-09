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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.xwiki.projectmanagement.openproject.internal.processing.WorkPackageFieldsCache;
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

    /**
     * The path of the page of a single work package, e.g. {@code /work_packages/5},
     * {@code /projects/demo/work_packages/5/activity} or {@code /wp/5}.
     */
    private static final Pattern WORK_PACKAGE_PATH_PATTERN =
        Pattern.compile("/(?:work_packages|wp)/(\\d+)(?:/[^/]+)?/?$");

    private static final Pattern PROJECTS_PATTERN = Pattern.compile("/projects/([^/]+)/");

    private static final Pattern QUERY_PROPS_PATTERN = Pattern.compile(QUERY_PROPS_QUERY_PARAMETER + "([^&]+)");

    private static final String IDENTIFIER = "identifier";

    private static final String GROUP_BY_AUTHOR = "author";

    /**
     * The work item properties held by the main properties of the work packages (see
     * {@link OpenProjectApiClient#SELECT_MAIN_PROPERTIES}).
     */
    private static final Set<String> MAIN_PROPERTIES = Set.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_KEY,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_TYPE, WorkItem.KEY_STATUS, WorkItem.KEY_RESOLVED, WorkItem.KEY_PROJECT,
        WorkItem.KEY_CREATOR, WorkItem.KEY_REPORTER, WorkItem.KEY_ASSIGNEES, WorkItem.KEY_START_DATE,
        WorkItem.KEY_DUE_DATE);

    /**
     * The OpenProject names of the properties grouping the work packages when counting them, by work item property.
     * Only the properties whose group names are the values of the work items are listed (e.g. OpenProject names the
     * progress groups {@code 10%}), so that the counts match the work items, and OpenProject can't group by date.
     */
    private static final Map<String, String> GROUP_BY_PROPERTIES = Map.of(WorkItem.KEY_STATUS, "status",
        WorkItem.KEY_TYPE, "type", WorkItem.KEY_PRIORITY, "priority", WorkItem.KEY_PROJECT, "project",
        WorkItem.KEY_ASSIGNEES, "assignee", WorkItem.KEY_CREATOR, GROUP_BY_AUTHOR,
        WorkItem.KEY_REPORTER, GROUP_BY_AUTHOR, OpenProjectWorkItemFields.PROPERTY_TARGET_VERSION, "version");

    private static final String INCORRECT_IDENTIFIER_FORMAT_MESSAGE =
        "The provided identifier value is not in a supported format.";

    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private WorkPackageFieldsCache workPackageFieldsCache;

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
            throw new WorkItemNotFoundException(String.format("Work Item with ID [%s] was not found.", workItemId));
        }

        return result.getItems().get(0);
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(int offset, int pageSize, List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemRetrievalException
    {
        try {
            int openProjectPage = getOpenProjectPage(offset, pageSize);
            String identifier = getIdentifier();
            OpenProjectAccessResult access = resolveAccess();
            OpenProjectApiClient openProjectApiClient = access.getClient();
            OpenProjectWorkItemFields fields = getWorkItemFields(access);
            List<LiveDataQuery.Filter> mappedFilters = fields.mapFilters(filters, openProjectApiClient);
            List<LiveDataQuery.SortEntry> mappedSortEntries = fields.mapSortEntries(sortEntries);

            PaginatedResult<WorkItem> workItems;
            if (identifier != null && IDS_PATTERN.matcher(identifier).matches()) {
                workItems = handleIds(openProjectApiClient, fields, identifier, offset, pageSize, mappedFilters,
                    mappedSortEntries);
            } else if (identifier != null && !identifier.trim().isEmpty()) {
                workItems = handleIdentifier(openProjectApiClient, fields, identifier.trim(), openProjectPage,
                    pageSize, mappedFilters, mappedSortEntries);
            } else {
                String filtersString = OpenProjectFilterHandler.convertFilters(mappedFilters);
                String sortByString = OpenProjectSortingHandler.convertSorting(mappedSortEntries);
                PaginatedResult<WorkPackage> workPackagesPaginatedResult =
                    getWorkPackages(openProjectApiClient, null, openProjectPage, pageSize, filtersString,
                        sortByString);
                workItems = OpenProjectConverters.convertPaginatedResult(workPackagesPaginatedResult,
                    workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
            }
            markClosedWorkItems(openProjectApiClient, workItems);
            return workItems;
        } catch (WorkItemRetrievalException e) {
            // Report authentication and rights failures, so that the caller can tell the user what to do.
            if (!isAuthenticationOrRightsFailure(e) && isLiveDataRequest()) {
                return handleWorkPackageRetrievalException(e);
            }
            throw e;
        } catch (ProjectManagementException e) {
            if (isLiveDataRequest()) {
                return handleWorkPackageRetrievalException(e);
            }
            throw new WorkItemRetrievalException("An error occurred while trying to get the work items", e);
        }
    }

    /**
     * OpenProject paginates the work packages by page: the {@code offset} parameter of its API is the number, starting
     * at 1, of the page to return. The page holding the work item at the given offset is retrieved.
     */
    private static int getOpenProjectPage(int offset, int pageSize)
    {
        return offset / pageSize + 1;
    }

    /**
     * Live Data can't display the failure of a request (it only notifies that it failed) and the failures of the
     * requests that its viewers make by filtering and sorting it (e.g. an unsupported combination of filters, which no
     * work package matches) shouldn't be reported as errors: the request then returns no work package. The other
     * callers, e.g. the charts, display the failure, so that it isn't mistaken for an empty result.
     */
    private boolean isLiveDataRequest()
    {
        return Boolean.TRUE.equals(executionContext.get(ProjectManagementClientExecutionContext.LIVE_DATA));
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
                    throw new WorkItemNotFoundException(String.format("Project with ID [%s] was not found.", id));
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
        return resolveAccess().getClient();
    }

    /**
     * @return how the current user reaches the connection of the macro, when they can call it
     */
    private OpenProjectAccessResult resolveAccess() throws WorkItemRetrievalException
    {
        String connectionName = getConnectionName();
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
        return access;
    }

    private PaginatedResult<WorkItem> handleIdentifier(OpenProjectApiClient openProjectApiClient,
        OpenProjectWorkItemFields fields, String identifier, int openProjectPage, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        if (!URL_PATTERN.matcher(identifier).matches()) {
            throw new WorkPackageRetrievalBadRequestException(INCORRECT_IDENTIFIER_FORMAT_MESSAGE);
        }

        String urlQueryId = identifierResolver.getStandaloneQueryId(identifier);

        if (urlQueryId != null) {
            return handleSavedQuery(openProjectApiClient, fields, urlQueryId, openProjectPage, pageSize,
                filtersEntries, sortEntries);
        }

        WorkPackagesQuery query = getWorkPackagesQuery(identifier, filtersEntries, sortEntries);
        return OpenProjectConverters.convertPaginatedResult(
            getWorkPackages(openProjectApiClient, query.project, openProjectPage, pageSize, query.filters,
                query.sortBy),
            workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
    }

    /**
     * @return the work packages listed by an OpenProject URL (of a project, or of all the projects), matching the
     *     filters of the URL and the given ones
     */
    private WorkPackagesQuery getWorkPackagesQuery(String identifier, List<LiveDataQuery.Filter> filtersEntries,
        List<LiveDataQuery.SortEntry> sortEntries) throws ProjectManagementException
    {
        URL url = parseUrl(identifier);
        JsonNode parametersNode = extractJsonNodeFromQuery(url.getQuery());

        JsonNode filtersNode = null;
        JsonNode sortNode = null;

        if (parametersNode != null) {
            filtersNode = parametersNode.path("f");
            sortNode = parametersNode.path("t");
        }

        return new WorkPackagesQuery(extractProjectFromPath(url.getPath()),
            extractFiltersFromQuery(filtersEntries, filtersNode), extractSortByString(sortEntries, sortNode));
    }

    /**
     * Retrieves the work packages, with only their main properties when the caller doesn't need the others (see
     * {@link ProjectManagementClientExecutionContext#PROPERTIES}), since they are then returned much faster.
     */
    private PaginatedResult<WorkPackage> getWorkPackages(OpenProjectApiClient openProjectApiClient, String project,
        int openProjectPage, int pageSize, String filters, String sortBy) throws ProjectManagementException
    {
        if (isMainPropertiesRequest()) {
            return openProjectApiClient.getWorkPackages(project, openProjectPage, pageSize, filters, sortBy,
                OpenProjectApiClient.SELECT_MAIN_PROPERTIES);
        } else if (project != null) {
            return openProjectApiClient.getProjectWorkPackages(project, openProjectPage, pageSize, filters, sortBy);
        }
        return openProjectApiClient.getWorkPackages(openProjectPage, pageSize, filters, sortBy);
    }

    /**
     * @return the identifier of the macro, where the link of a single work package is replaced by the id of the work
     *     package, so that it's displayed like when given by its id
     */
    private String getIdentifier()
    {
        String identifier = StringUtils.trim((String) executionContext.get(IDENTIFIER));
        if (identifier != null && URL_PATTERN.matcher(identifier).matches()) {
            try {
                Matcher matcher = WORK_PACKAGE_PATH_PATTERN.matcher(new URL(identifier).getPath());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (MalformedURLException e) {
                // The identifier is then rejected like the other invalid links.
            }
        }
        return identifier;
    }

    private boolean isIdsOrSavedQuery(String identifier)
    {
        return StringUtils.isNotEmpty(identifier) && (IDS_PATTERN.matcher(identifier).matches()
            || identifierResolver.getStandaloneQueryId(identifier) != null);
    }

    private boolean isMainPropertiesRequest()
    {
        Object properties = executionContext.get(ProjectManagementClientExecutionContext.PROPERTIES);
        return properties instanceof Collection && !((Collection<?>) properties).isEmpty()
            && MAIN_PROPERTIES.containsAll((Collection<?>) properties);
    }

    @Override
    public Optional<Map<String, Long>> countWorkItems(List<LiveDataQuery.Filter> filters, String property)
        throws WorkItemRetrievalException
    {
        String groupBy = GROUP_BY_PROPERTIES.get(property);
        String identifier = getIdentifier();
        // OpenProject can't count the work packages of a saved query, and a list of work packages is short enough to
        // be retrieved.
        if (groupBy == null || isIdsOrSavedQuery(identifier)) {
            return Optional.empty();
        }
        try {
            OpenProjectAccessResult access = resolveAccess();
            OpenProjectApiClient openProjectApiClient = access.getClient();
            OpenProjectWorkItemFields fields = getWorkItemFields(access);
            List<LiveDataQuery.Filter> mappedFilters = fields.mapFilters(filters, openProjectApiClient);
            WorkPackagesQuery query = StringUtils.isEmpty(identifier)
                ? new WorkPackagesQuery(null, OpenProjectFilterHandler.convertFilters(mappedFilters), "")
                : getWorkPackagesQuery(identifier, mappedFilters, Collections.emptyList());
            return Optional.of(openProjectApiClient.countWorkPackages(query.project, query.filters, groupBy));
        } catch (ProjectManagementException | UnsupportedOperationException e) {
            if (e instanceof WorkItemRetrievalException
                && isAuthenticationOrRightsFailure((WorkItemRetrievalException) e))
            {
                throw (WorkItemRetrievalException) e;
            }
            // E.g. OpenProject can't group the work packages by this property: they are then retrieved and counted.
            logger.debug("Failed to count the work packages by [{}]: {}", groupBy,
                ExceptionUtils.getRootCauseMessage(e));
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getWorkItemsLocation(List<LiveDataQuery.Filter> filters,
        List<LiveDataQuery.SortEntry> sortEntries)
    {
        String identifier = getIdentifier();
        try {
            if (isIdsOrSavedQuery(identifier)) {
                // A list of work packages is never truncated, and the filters can't be added to a saved query.
                return Optional.empty();
            }
            OpenProjectAccessResult access = resolveAccess();
            OpenProjectApiClient openProjectApiClient = access.getClient();
            OpenProjectWorkItemFields fields = getWorkItemFields(access);
            List<LiveDataQuery.Filter> mappedFilters = fields.mapFilters(filters, openProjectApiClient);
            List<LiveDataQuery.SortEntry> mappedSortEntries = fields.mapSortEntries(sortEntries);
            WorkPackagesQuery query = StringUtils.isEmpty(identifier)
                ? new WorkPackagesQuery(null, OpenProjectFilterHandler.convertFilters(mappedFilters),
                    OpenProjectSortingHandler.convertSorting(mappedSortEntries))
                : getWorkPackagesQuery(identifier, mappedFilters, mappedSortEntries);
            return Optional.ofNullable(
                openProjectApiClient.getWorkPackagesLocation(query.project, query.filters, query.sortBy));
        } catch (ProjectManagementException | RuntimeException e) {
            logger.debug("Failed to build the URL of the work packages: {}", ExceptionUtils.getRootCauseMessage(e));
            return Optional.empty();
        }
    }

    /**
     * Retrieves the work packages listed by the identifier, given by their ids or their keys. OpenProject can't filter
     * work packages by key, so the keys are first resolved to ids. Unless the livedata sorts them, the work packages
     * are returned in the order of the identifier: since there can't be more results than listed work packages, they
     * are all retrieved at once, then ordered and paginated here.
     */
    private PaginatedResult<WorkItem> handleIds(OpenProjectApiClient openProjectApiClient,
        OpenProjectWorkItemFields fields, String identifier, int offset, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        List<String> ids = resolveIds(openProjectApiClient, IDS_SEPARATOR_PATTERN.split(identifier.trim()));
        try {
            return getWorkItemsByIds(openProjectApiClient, fields, ids, offset, pageSize, filtersEntries, sortEntries);
        } catch (WorkItemRetrievalException e) {
            // OpenProject rejects the whole id filter when one of the work packages doesn't exist, or can't be seen by
            // the user: the other work packages are then displayed without it.
            if (!Integer.valueOf(HttpURLConnection.HTTP_BAD_REQUEST).equals(e.getStatusCode())) {
                throw e;
            }
            List<String> existingIds = getExistingIds(openProjectApiClient, ids);
            if (existingIds.size() == ids.size()) {
                throw e;
            }
            return getWorkItemsByIds(openProjectApiClient, fields, existingIds, offset, pageSize, filtersEntries,
                sortEntries);
        }
    }

    private PaginatedResult<WorkItem> getWorkItemsByIds(OpenProjectApiClient openProjectApiClient,
        OpenProjectWorkItemFields fields, List<String> ids, int offset, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        int openProjectPage = getOpenProjectPage(offset, pageSize);
        if (ids.isEmpty()) {
            return new PaginatedResult<>(Collections.emptyList(), openProjectPage, pageSize, 0);
        }

        Map<String, Object> idFiltersAsJson = Map.of("n", "id", "o", EQUALS, "v", ids);
        String filters =
            OpenProjectFilterHandler.mergeFilters(filtersEntries, objectMapper.valueToTree(List.of(idFiltersAsJson)));

        if (!sortEntries.isEmpty()) {
            return OpenProjectConverters.convertPaginatedResult(
                openProjectApiClient.getWorkPackages(openProjectPage, pageSize, filters,
                    OpenProjectSortingHandler.convertSorting(sortEntries)),
                workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
        }

        List<WorkPackage> workPackages =
            new ArrayList<>(openProjectApiClient.getWorkPackages(1, ids.size(), filters, "").getItems());
        workPackages.sort(Comparator.comparingInt(workPackage -> ids.indexOf(String.valueOf(workPackage.getId()))));
        List<WorkPackage> pageItems = workPackages.subList(Math.min(offset, workPackages.size()),
            Math.min(offset + pageSize, workPackages.size()));

        return OpenProjectConverters.convertPaginatedResult(
            new PaginatedResult<>(pageItems, openProjectPage, pageSize, workPackages.size()),
            workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields));
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

    /**
     * @return the ids of the work packages that exist and that the user can see, in the given order. Each id requires
     *     a request to OpenProject, so they are checked in parallel (and the client caches them).
     */
    private List<String> getExistingIds(OpenProjectApiClient openProjectApiClient, List<String> ids)
        throws ProjectManagementException
    {
        Map<String, CompletableFuture<WorkPackage>> checks = new HashMap<>();
        for (String id : ids) {
            checks.put(id, CompletableFuture.supplyAsync(() -> getWorkPackage(openProjectApiClient, id)));
        }
        List<String> existingIds = new ArrayList<>();
        for (String id : ids) {
            if (joinResolution(id, checks.get(id)) != null) {
                existingIds.add(id);
            }
        }
        return existingIds;
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

    private String getConnectionName()
    {
        return (String) executionContext.get("instance");
    }

    private OpenProjectWorkItemFields getWorkItemFields(OpenProjectAccessResult access)
    {
        try {
            return workPackageFieldsCache.getFields(getConnectionName(), access);
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
        OpenProjectWorkItemFields fields, String queryId, int openProjectPage, int pageSize,
        List<LiveDataQuery.Filter> filtersEntries, List<LiveDataQuery.SortEntry> sortEntries)
        throws ProjectManagementException
    {
        String sortBy = sortEntries.isEmpty() ? "" : OpenProjectSortingHandler.convertSorting(sortEntries);

        if (NO_FILTERS.equals(OpenProjectFilterHandler.convertFilters(filtersEntries))) {
            return OpenProjectConverters.convertPaginatedResult(
                openProjectApiClient.getQueryWorkPackages(queryId, openProjectPage, pageSize, sortBy),
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
                openProjectApiClient.getProjectWorkPackages(project, openProjectPage, pageSize, filters, sorting),
                workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields)
            );
        }

        return OpenProjectConverters.convertPaginatedResult(
            openProjectApiClient.getWorkPackages(openProjectPage, pageSize, filters, sorting),
            workPackage -> OpenProjectConverters.convertWorkPackageToWorkItem(workPackage, fields)
        );
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

    /**
     * The work packages of a project, or of all the projects, matching filters, in the format of the OpenProject API.
     */
    private static final class WorkPackagesQuery
    {
        private final String project;

        private final String filters;

        private final String sortBy;

        WorkPackagesQuery(String project, String filters, String sortBy)
        {
            this.project = project;
            this.filters = filters;
            this.sortBy = sortBy;
        }
    }
}
