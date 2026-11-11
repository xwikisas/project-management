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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.client.utils.URIBuilder;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.auth.OpenProjectAuthenticator;
import com.xwiki.projectmanagement.openproject.model.News;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Sprint;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.TimeEntry;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.UserAvatar;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WikiPageLink;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

/**
 * Default OpenProject get items client helper.
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

    private static final String OP_GROUP_BY = "groupBy";

    private static final String OP_OPERATOR = "operator";

    private static final String OP_VALUES = "values";

    private static final String OP_RESPONSE_ID = "id";

    private static final String OP_RESPONSE_LINKS = "_links";

    private static final String OP_RESPONSE_SELF = "self";

    private static final String OP_RESPONSE_SUBJECT = "subject";

    private static final String OP_RESPONSE_STATUS = "status";

    private static final String PAGE_SIZE = "pageSize";

    private static final String OP_RESPONSE_PROJECT = "project";

    private static final String OP_RESPONSE_PRIORITY = "priority";

    private static final String OP_RESPONSE_TITLE = "title";

    private static final String OP_RESPONSE_TOTAL = "total";

    private static final String OP_DESCRIPTION = "description";

    private static final String OP_HTML = "html";

    private static final String OP_START_DATE = "startDate";

    private static final String OP_DUE_DATE = "dueDate";

    private static final String OP_DATE = "date";

    private static final String OP_CREATED_AT = "createdAt";

    private static final String OP_UPDATED_AT = "updatedAt";

    private static final String OP_DERIVED_START_DATE = "derivedStartDate";

    private static final String OP_DERIVED_DUE_DATE = "derivedDueDate";

    private static final String HREF = "href";

    private static final String OP_RESPONSE_ASSIGNEE = "assignee";

    private static final String OP_RESPONSE_TYPE = "type";

    private static final String OP_RESPONSE_PERCENTAGE_DONE = "percentageDone";

    private static final String API_URL_PART = "/api/v3";

    private static final String API_URL_WORK_PACKAGES = "/api/v3/work_packages";

    private static final String API_URL_FORM_WORK_PACKAGES = "/api/v3/work_packages/form";

    private static final String API_URL_FORM_QUERIES = "/api/v3/queries/form";

    private static final String API_URL_FORM_EDIT_WORK_PACKAGE = "/api/v3/work_packages/%s/form";

    private static final String PATCH = "PATCH";

    private static final String DOT = ".";

    private static final String SLASH = "/";

    private static final String SEMICOLON = ";";

    private static final String API_URL_TYPES = "/api/v3/types";

    private static final String API_URL_WORK_PACKAGE_SCHEMAS = "/api/v3/work_packages/schemas";

    /**
     * The number of projects whose work package schemas (one per type of work package) are requested at once, to
     * find out which fields are multi-value.
     */
    private static final int SCHEMA_PROJECTS_PER_REQUEST = 10;

    /**
     * The maximum number of requests for work package schemas: the fields not found in the schemas of the first
     * projects are considered single-value.
     */
    private static final int SCHEMA_MAX_REQUESTS = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOpenProjectApiClient.class);

    private static final String API_URL_STATUSES = "/api/v3/statuses";

    private static final String API_URL_PRIORITIES = "/api/v3/priorities";

    // The users API is reserved to the administrators (and the users allowed to create or manage users): the users
    // are listed from the principals API, which lists those visible to the current user (e.g. the members of the
    // projects it can see), including for the anonymous user.
    private static final String API_URL_PRINCIPALS = "/api/v3/principals";

    private static final String INVALID_USER_FILTERS = "Invalid user filters [%s].";

    private static final String OP_EQUALS = "=";


    private static final String API_URL_PROJECTS = "/api/v3/projects";

    private static final String API_URL_VERSIONS = "/api/v3/versions";

    private static final String API_URL_PROJECT_VERSIONS = "/api/v3/workspaces/%d/versions";

    private static final String API_URL_VERSIONS_GET = "/versions/%s";

    private static final String API_URL_SPRINTS = "/api/v3/sprints";

    private static final String API_URL_PROJECT_SPRINTS = "/api/v3/projects/%d/sprints";

    private static final String API_URL_SPRINTS_GET = "/sprints/%s";

    private static final String URL_INSTANCE_METADATA = "/.well-known/openproject-metadata";

    private static final String OP_RESPONSE_INSTALLATION_UUID = "installation_uuid";

    private static final String API_URL_PROJECT = "/api/v3/projects/%s";

    private static final String API_PROJECT_PLACEHOLDER = "/projects/%s";

    private static final String API_URL_QUERY = "/api/v3/queries/%s";

    private static final String OP_RESPONSE_RESULTS = "results";

    private static final String API_URL_NEWS = "/api/v3/news";

    private static final String API_URL_MEMBERSHIPS = "/api/v3/memberships";

    private static final String API_URL_TIME_ENTRIES = "/api/v3/time_entries";

    private static final String OP_RESPONSE_PRINCIPAL = "principal";

    private static final String OP_RESPONSE_ROLES = "roles";

    private static final String API_URL_SELECT_ELEMENTS_PARAM = "elements/id,elements/name";

    private static final String COMMUNICATING_ISSUE_MESSAGE = "There was an issue in communicating with [%s].";

    private static final String OP_OFFSET = "offset";

    private static final String PROJECT_MANAGEMENT_EXCEPTION_MESSAGE = "Failed to build the OpenProject entity "
        + "retrieval url [%s].";

    /**
     * Maximum time to wait for the response of the OpenProject API, body included. Without it, an OpenProject instance
     * that stops responding blocks the thread rendering the page forever.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Maximum size of the JSON responses of the OpenProject API (a page of 1000 work packages is a few MB), so that an
     * unexpectedly large response doesn't exhaust the memory.
     */
    private static final int MAX_RESPONSE_SIZE = 16 * 1024 * 1024;

    /**
     * Maximum size of an avatar, matching the default maximum attachment size of OpenProject.
     */
    private static final int MAX_AVATAR_SIZE = 5 * 1024 * 1024;

    /**
     * The image types the avatars are served with. Other types, e.g. SVG or HTML, are not served from the wiki.
     */
    private static final Set<String> AVATAR_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif",
        "image/webp", "image/bmp", "image/avif", "image/x-icon", "image/vnd.microsoft.icon");

    /**
     * The maximum number of characters of the body of a failed request kept in the error message.
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private static final String AVATAR_REQUEST_ERROR_MESSAGE =
        "There was an issue in creating or sending the user avatar request.";

    /**
     * Builds the URI of a request to the OpenProject instance.
     */
    @FunctionalInterface
    private interface InstanceUriSupplier
    {
        URI get() throws URISyntaxException;
    }

    private final HttpClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenProjectAuthenticator authenticator;

    private final String connectionUrl;

    private final WorkPackageDescriptionCleaner descriptionCleaner;

    /**
     * Constructs a new {@code OpenProjectApiClient} with the given authentication method and connection URL.
     *
     * @param connectionUrl the base URL of the OpenProject instance
     * @param authenticator the authentication method used to authenticate the requests sent to the OpenProject API
     * @param client the {@link HttpClient} instance used to perform HTTP requests to the OpenProject API
     * @param descriptionCleaner cleans the HTML description of the work packages
     */
    public DefaultOpenProjectApiClient(String connectionUrl, OpenProjectAuthenticator authenticator, HttpClient client,
        WorkPackageDescriptionCleaner descriptionCleaner)
    {
        this.connectionUrl = connectionUrl;
        this.authenticator = authenticator;
        this.client = client;
        this.descriptionCleaner = descriptionCleaner;
    }

    @Override
    public PaginatedResult<WorkPackage> getWorkPackages(Integer offset, Integer pageSize, String filters,
        String sortBy) throws ProjectManagementException
    {
        JsonNode mainNode =
            getOpenProjectResponse(API_URL_WORK_PACKAGES, offset, pageSize, filters, sortBy, "");

        return getWorkPackagePaginatedResult(mainNode, offset, pageSize);
    }

    @Override
    public PaginatedResult<WorkPackage> getProjectWorkPackages(String project, Integer offset, Integer pageSize,
        String filters, String sortBy)
        throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(getWorkPackagesUrlPart(project), offset, pageSize, filters, sortBy,
            "");
        return getWorkPackagePaginatedResult(mainNode, offset, pageSize);
    }

    @Override
    public PaginatedResult<WorkPackage> getWorkPackages(String project, Integer offset, Integer pageSize,
        String filters, String sortBy, String select) throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(getWorkPackagesUrlPart(project), offset, pageSize,
            StringUtils.defaultString(filters), StringUtils.defaultString(sortBy), StringUtils.defaultString(select));
        return getWorkPackagePaginatedResult(mainNode, offset, pageSize);
    }

    @Override
    public Map<String, Long> countWorkPackages(String project, String filters, String groupBy)
        throws ProjectManagementException
    {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (StringUtils.isNotEmpty(filters)) {
            parameters.put(OP_FILTERS, filters);
        }
        parameters.put(OP_GROUP_BY, groupBy);
        // Only the number of work packages of each group is needed, not the work packages.
        parameters.put(PAGE_SIZE, "0");
        JsonNode groups = getOpenProjectResponse(getWorkPackagesUrlPart(project), parameters).path("groups");
        if (!groups.isArray()) {
            throw new ProjectManagementException(
                String.format("OpenProject didn't group the work packages by [%s].", groupBy));
        }
        Map<String, Long> counts = new HashMap<>();
        for (JsonNode group : groups) {
            JsonNode value = group.path("value");
            String key = value.isNull() || value.isMissingNode() ? null : value.asText();
            counts.merge(key, group.path("count").asLong(), Long::sum);
        }
        return counts;
    }

    @Override
    public String getWorkPackagesLocation(String project, String filters, String sortBy)
    {
        // The work package list of OpenProject takes its filters and sorting from the query_props parameter, whose
        // filters are written {"n": name, "o": operator, "v": values} and whose sorting is "name:direction,...".
        Map<String, Object> queryProps = new LinkedHashMap<>();
        List<Map<String, Object>> queryFilters = new ArrayList<>();
        try {
            for (JsonNode filter : readJsonArray(filters)) {
                filter.fields().forEachRemaining(entry -> {
                    Map<String, Object> queryFilter = new LinkedHashMap<>();
                    queryFilter.put("n", entry.getKey());
                    queryFilter.put("o", entry.getValue().path(OP_OPERATOR).asText());
                    queryFilter.put("v", entry.getValue().path(OP_VALUES));
                    queryFilters.add(queryFilter);
                });
            }
            queryProps.put("f", queryFilters);
            List<String> sortEntries = new ArrayList<>();
            for (JsonNode sortEntry : readJsonArray(sortBy)) {
                sortEntries.add(sortEntry.path(0).asText() + ':' + sortEntry.path(1).asText());
            }
            if (!sortEntries.isEmpty()) {
                queryProps.put("t", String.join(",", sortEntries));
            }
            String projectPath = project == null ? "" : "/projects/" + PathSegmentEncoder.encode(project);
            return String.format("%s%s/work_packages?query_props=%s", StringUtils.removeEnd(connectionUrl, SLASH),
                projectPath,
                PathSegmentEncoder.encode(objectMapper.writeValueAsString(queryProps)));
        } catch (JsonProcessingException e) {
            LOGGER.debug("Failed to build the URL of the work packages matching the filters [{}].", filters, e);
            return null;
        }
    }

    private JsonNode readJsonArray(String json) throws JsonProcessingException
    {
        return StringUtils.isEmpty(json) ? objectMapper.createArrayNode() : objectMapper.readTree(json);
    }

    private static String getWorkPackagesUrlPart(String project)
    {
        return project == null ? API_URL_WORK_PACKAGES
            : String.format("%s/projects/%s/work_packages", API_URL_PART, PathSegmentEncoder.encode(project));
    }

    @Override
    public PaginatedResult<WorkPackage> getQueryWorkPackages(String queryId, Integer offset, Integer pageSize,
        String sortBy) throws ProjectManagementException
    {
        // The filters are left out on purpose, sending them would replace the ones defined by the query.
        String queryUrl = String.format(API_URL_QUERY, PathSegmentEncoder.encode(queryId));
        JsonNode queryJson = getOpenProjectResponse(queryUrl, offset, pageSize, "", sortBy, "");

        return getWorkPackagePaginatedResult(queryJson.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_RESULTS), offset,
            pageSize);
    }

    @Override
    public WorkPackage getWorkPackage(String idOrKey) throws ProjectManagementException
    {
        String workPackageUrl = API_URL_WORK_PACKAGES + '/' + PathSegmentEncoder.encode(idOrKey);
        try {
            return createWorkPackageFromJson(getOpenProjectResponse(workPackageUrl, null, null, "", "", ""));
        } catch (WorkItemRetrievalException e) {
            // OpenProject answers 404 both for the work packages that don't exist and for those the user can't see.
            if (Integer.valueOf(HttpURLConnection.HTTP_NOT_FOUND).equals(e.getStatusCode())) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public String getQueryResultsUrl(String queryId) throws ProjectManagementException
    {
        String queryUrl = String.format(API_URL_QUERY, PathSegmentEncoder.encode(queryId));
        JsonNode queryJson = getOpenProjectResponse(queryUrl, null, null, "", "", "");
        return queryJson.path(OP_RESPONSE_LINKS).path(OP_RESPONSE_RESULTS).path(HREF).asText();
    }

    @Override
    public PaginatedResult<User> getUsers(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode =
            getOpenProjectResponse(API_URL_PRINCIPALS, offset, pageSize, getUserPrincipalsFilters(filters), "", "");
        JsonNode usersJson = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);

        List<User> users = new ArrayList<>();

        for (JsonNode element : usersJson) {
            User user = new User(element);
            user.initializeSelfWithPath(connectionUrl, String.format("/users/%s", user.getId()));
            users.add(user);
        }

        return new PaginatedResult<>(users, offset, pageSize, getTotalNumberOfEntities(mainNode));
    }

    /**
     * @param filters the filters of the users to retrieve, as a JSON array (possibly empty)
     * @return the given filters, restricted to the principals that are users (not groups or placeholder users)
     * @throws ProjectManagementException if the filters aren't a JSON array
     */
    private String getUserPrincipalsFilters(String filters) throws ProjectManagementException
    {
        try {
            JsonNode givenFilters = readJsonArray(StringUtils.trimToEmpty(filters));
            if (!givenFilters.isArray()) {
                throw new ProjectManagementException(String.format(INVALID_USER_FILTERS, filters));
            }
            ArrayNode principalsFilters = this.objectMapper.createArrayNode();
            ObjectNode typeFilter = principalsFilters.addObject().putObject(OP_RESPONSE_TYPE);
            typeFilter.put(OP_OPERATOR, OP_EQUALS);
            typeFilter.putArray(OP_VALUES).add("User");
            principalsFilters.addAll((ArrayNode) givenFilters);
            return this.objectMapper.writeValueAsString(principalsFilters);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(String.format(INVALID_USER_FILTERS, filters), e);
        }
    }

    @Override
    public PaginatedResult<User> getAvailableUsers(String url, Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode =
            getOpenProjectResponseFromHref(url, getParameters(offset, pageSize, filters, "", ""));
        JsonNode usersJson = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);

        List<User> users = new ArrayList<>();

        for (JsonNode element : usersJson) {
            users.add(new User(element));
        }

        return new PaginatedResult<>(users, offset, pageSize, getTotalNumberOfEntities(mainNode));
    }

    @Override
    public PaginatedResult<Project> getProjects(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(API_URL_PROJECTS, offset, pageSize, filters, "", "");
        JsonNode elements = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);

        List<Project> projects = new ArrayList<>();

        for (JsonNode element : elements) {
            Project project = new Project(element);
            project.initializeSelfWithPath(connectionUrl, String.format(API_PROJECT_PLACEHOLDER, project.getId()));
            projects.add(project);
        }

        return new PaginatedResult<>(projects, offset, pageSize, getTotalNumberOfEntities(mainNode));
    }

    @Override
    public PaginatedResult<Project> getAvailableProjects(String url, Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode =
            getOpenProjectResponseFromHref(url, getParameters(offset, pageSize, filters, "", ""));
        JsonNode elements = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);

        List<Project> projects = new ArrayList<>();

        for (JsonNode element : elements) {
            projects.add(new Project(element));
        }

        return new PaginatedResult<>(projects, offset, pageSize, getTotalNumberOfEntities(mainNode));
    }

    @Override
    public PaginatedResult<Type> getTypes() throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_TYPES, null, null, "", "", "");

        List<Type> types = new ArrayList<>();

        for (JsonNode element : elements) {
            Type type = new Type(element);
            type.initializeSelfWithPath(connectionUrl, String.format("/types/%s/edit/settings", type.getId()));
            types.add(type);
        }

        return new PaginatedResult<>(types, 0, types.size(), types.size());
    }

    @Override
    public PaginatedResult<Status> getStatuses() throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_STATUSES, null, null, "", "", "");
        List<Status> statuses = new ArrayList<>();

        for (JsonNode element : elements) {
            Status status = new Status(element);
            status.initializeSelfWithPath(connectionUrl, String.format("/statuses/%s/edit", status.getId()));
            statuses.add(status);
        }

        return new PaginatedResult<>(statuses, 0, statuses.size(), statuses.size());
    }

    @Override
    public JsonNode getWorkPackagesFormResponse(String jsonBody) throws ProjectManagementException
    {
        return getFormResponse(API_URL_FORM_WORK_PACKAGES, jsonBody);
    }

    @Override
    public PaginatedResult<WorkPackageField> getWorkPackageFields() throws ProjectManagementException
    {
        // The form of an empty query describes the columns and filters available to the global work package queries,
        // custom fields included.
        List<WorkPackageField> fields =
            WorkPackageFieldsParser.parseQueryForm(getFormResponse(API_URL_FORM_QUERIES, "{}"));
        return new PaginatedResult<>(fields, 0, fields.size(), fields.size());
    }

    /**
     * {@inheritDoc}
     * <p>
     * There is one schema per project and type of work package, and a field is in the schemas of the projects and
     * types it's enabled for, so the schemas are requested a few projects at a time, until all the fields are found.
     */
    @Override
    public Map<String, Boolean> getMultiValueWorkPackageFields(Set<String> fieldKeys)
        throws ProjectManagementException
    {
        Map<String, Boolean> multiValueFields = new HashMap<>();
        if (fieldKeys.isEmpty()) {
            return multiValueFields;
        }
        Set<String> unresolved = new HashSet<>(fieldKeys);
        List<String> typeIds = getIds(getOpenProjectResponseEntities(API_URL_TYPES, null, null, "", "", ""));
        boolean morePages = true;
        for (int page = 1; morePages && page <= SCHEMA_MAX_REQUESTS && !unresolved.isEmpty(); page++) {
            List<String> projectIds = getIds(
                getOpenProjectResponseEntities(API_URL_PROJECTS, page, SCHEMA_PROJECTS_PER_REQUEST, "", "", ""));
            Map<String, Boolean> found = getMultiValueFields(unresolved, projectIds, typeIds);
            multiValueFields.putAll(found);
            unresolved.removeAll(found.keySet());
            morePages = projectIds.size() == SCHEMA_PROJECTS_PER_REQUEST;
        }
        return multiValueFields;
    }

    /**
     * @return whether each of the given fields is multi-value according to the schemas of the given projects, for
     *     the fields found in these schemas
     */
    private Map<String, Boolean> getMultiValueFields(Set<String> fieldKeys, List<String> projectIds,
        List<String> typeIds) throws ProjectManagementException
    {
        List<String> schemaIds = new ArrayList<>();
        for (String projectId : projectIds) {
            for (String typeId : typeIds) {
                schemaIds.add(projectId + '-' + typeId);
            }
        }
        if (schemaIds.isEmpty()) {
            return Map.of();
        }
        String filters = toJson(List.of(Map.of(OP_RESPONSE_ID, Map.of(OP_OPERATOR, OP_EQUALS, OP_VALUES, schemaIds))));
        return WorkPackageFieldsParser.parseMultiValueFields(
            getOpenProjectResponseEntities(API_URL_WORK_PACKAGE_SCHEMAS, null, null, filters, "", ""), fieldKeys);
    }

    private String toJson(Object value) throws ProjectManagementException
    {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException("Failed to serialize the OpenProject request parameters.", e);
        }
    }

    private static List<String> getIds(JsonNode elements)
    {
        List<String> ids = new ArrayList<>();
        for (JsonNode element : elements) {
            ids.add(element.path(OP_RESPONSE_ID).asText());
        }
        return ids;
    }

    private JsonNode getFormResponse(String urlPart, String jsonBody) throws ProjectManagementException
    {
        try {
            URI uri = createInstanceUri(urlPart);
            HttpRequest request = createAuthorizedPostRequest(uri, jsonBody);
            HttpResponse<String> response = sendForJson(request);

            handleOpenProjectWorkPackagesRequestExceptions(response);

            String body = response.body();
            return objectMapper.readTree(body);
        } catch (URISyntaxException e) {
            throw new ProjectManagementException(
                String.format(PROJECT_MANAGEMENT_EXCEPTION_MESSAGE, connectionUrl + urlPart), e);
        } catch (IOException e) {
            throw new ProjectManagementException(
                String.format(COMMUNICATING_ISSUE_MESSAGE, connectionUrl + urlPart), e);
        }
    }

    @Override
    public PaginatedResult<Priority> getPriorities() throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_PRIORITIES, null, null, "", "", "");
        List<Priority> priorities = new ArrayList<>();

        for (JsonNode element : elements) {
            Priority priority = new Priority(element);
            priority.initializeSelfWithPath(connectionUrl, String.format("/priorities/%s/edit", priority.getId()));
            priorities.add(priority);
        }

        return new PaginatedResult<>(priorities, 0, priorities.size(), priorities.size());
    }

    @Override
    public PaginatedResult<WikiPageLink> getPageLinks(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse("/api/v3/wiki_page_links", offset, pageSize, filters, "", "");

        JsonNode elements = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);
        List<WikiPageLink> pageLinks = new ArrayList<>();

        for (JsonNode element : elements) {
            WikiPageLink pageLink = new WikiPageLink(element);
            pageLinks.add(pageLink);
        }
        return new PaginatedResult<>(pageLinks, offset, pageLinks.size(), getTotalNumberOfEntities(mainNode));
    }

    @Override
    public PaginatedResult<Version> getVersions() throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_VERSIONS, null, null, "", "", "");
        List<Version> versions = new ArrayList<>();

        for (JsonNode element : elements) {
            Version version = new Version(element);
            version.initializeSelfWithPath(connectionUrl, String.format(API_URL_VERSIONS_GET, version.getId()));
            versions.add(version);
        }

        return new PaginatedResult<>(versions, 0, versions.size(), versions.size());
    }

    @Override
    public PaginatedResult<Version> getProjectVersions(int projectId) throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(String.format(API_URL_PROJECT_VERSIONS, projectId), null,
            null, "", "", "");
        List<Version> versions = new ArrayList<>();

        for (JsonNode element : elements) {
            Version version = new Version(element);
            version.initializeSelfWithPath(connectionUrl, String.format(API_URL_VERSIONS_GET, version.getId()));
            versions.add(version);
        }

        return new PaginatedResult<>(versions, 0, versions.size(), versions.size());
    }

    @Override
    public PaginatedResult<Sprint> getSprints(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(API_URL_SPRINTS, offset, pageSize, filters, "", "");
        JsonNode elements = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);

        List<Sprint> sprints = new ArrayList<>();
        for (JsonNode element : elements) {
            Sprint sprint = new Sprint(element);
            sprint.initializeSelfWithPath(connectionUrl, String.format(API_URL_SPRINTS_GET, sprint.getId()));
            sprints.add(sprint);
        }

        return new PaginatedResult<>(sprints, offset, pageSize, getTotalNumberOfEntities(mainNode));
    }

    @Override
    public PaginatedResult<Sprint> getProjectSprints(Integer offset, Integer pageSize, String filters, int projectId)
        throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(String.format(API_URL_PROJECT_SPRINTS, projectId), offset,
            pageSize, filters, "", "");
        JsonNode elements = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);

        List<Sprint> sprints = new ArrayList<>();
        for (JsonNode element : elements) {
            Sprint sprint = new Sprint(element);
            sprint.initializeSelfWithPath(connectionUrl, String.format(API_URL_SPRINTS_GET, sprint.getId()));
            sprints.add(sprint);
        }

        return new PaginatedResult<>(sprints, offset, pageSize, getTotalNumberOfEntities(mainNode));
    }

    @Override
    public PaginatedResult<News> getNews(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_NEWS, offset, pageSize, filters, "", "");

        List<News> newsList = new ArrayList<>();
        for (JsonNode element : elements) {
            News news = new News(element);
            news.initializeSelfWithPath(connectionUrl, String.format("/news/%s", news.getId()));

            // The project is linked like the one of the work packages, from the API link given by OpenProject.
            JsonNode projectNode = element.path(OP_RESPONSE_LINKS).path(OP_RESPONSE_PROJECT);
            news.setProjectLink(new Linkable(projectNode.path(OP_RESPONSE_TITLE).asText(), getPageUrl(projectNode)));
            newsList.add(news);
        }

        return new PaginatedResult<>(newsList, offset, pageSize, newsList.size());
    }

    @Override
    public Project getProject(String idOrIdentifier) throws ProjectManagementException
    {
        // OpenProject accepts both the id and the identifier of the project in its URL.
        String urlPart = String.format(API_URL_PROJECT, PathSegmentEncoder.encode(idOrIdentifier));
        JsonNode projectJson = getOpenProjectResponse(urlPart, null, null, "", "", "");
        Project project = new Project(projectJson);
        project.initializeSelfWithPath(connectionUrl, String.format(API_PROJECT_PLACEHOLDER, project.getId()));
        // The HTML rendered by OpenProject is cleaned, as the work package descriptions, since it is displayed in the
        // wiki.
        project.setDescriptionHtml(this.descriptionCleaner.clean(
            projectJson.path(OP_DESCRIPTION).path(OP_HTML).asText(), this.connectionUrl));
        return project;
    }

    @Override
    public PaginatedResult<User> getMemberships(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode elements =
            getOpenProjectResponseEntities(API_URL_MEMBERSHIPS, offset, pageSize, filters, "", "");

        List<User> users = new ArrayList<>();
        for (JsonNode element : elements) {
            JsonNode linksNode = element.path(OP_RESPONSE_LINKS);
            JsonNode principalNode = linksNode.path(OP_RESPONSE_PRINCIPAL);

            String principalTitle = principalNode.path(OP_RESPONSE_TITLE).asText();
            String principalUrl = getPageUrl(principalNode);

            List<Linkable> roles = new ArrayList<>();
            for (JsonNode roleNode : linksNode.path(OP_RESPONSE_ROLES)) {
                roles.add(new Linkable(roleNode.path(OP_RESPONSE_TITLE).asText(), getPageUrl(roleNode)));
            }

            User user = new User();
            user.setName(principalTitle);
            user.setSelf(new Linkable(principalTitle, principalUrl));
            user.setRoles(roles);
            users.add(user);
        }

        return new PaginatedResult<>(users, offset, pageSize, users.size());
    }

    @Override
    public UserAvatar getUserAvatar(String userId) throws ProjectManagementException
    {
        String urlPart = String.format("/api/v3/users/%s/avatar", PathSegmentEncoder.encode(userId));
        String uriStr = connectionUrl + urlPart;
        try {
            URI uri = createInstanceUri(urlPart);
            HttpRequest request =
                createAuthorizedGetHttpRequest(uri);
            // The avatar is read entirely before being returned, so that its size and the duration of its download
            // are bounded like the other responses.
            HttpResponse<byte[]> response =
                send(request, LimitedBodyHandlers.ofByteArray(MAX_AVATAR_SIZE, REQUEST_TIMEOUT));

            if (response.statusCode() != 200) {
                if (response.statusCode() == 404) {
                    throw new WorkItemNotFoundException(
                        String.format("No avatar found for user [%s] at [%s].", userId, uriStr));
                }
                byte[] errorBody = response.body();
                throw new WorkItemRetrievalException(
                    String.format("Failed to get the user avatar with code [%s] and message [%s]",
                        response.statusCode(), new String(errorBody, 0,
                            Math.min(errorBody.length, MAX_ERROR_MESSAGE_LENGTH), StandardCharsets.UTF_8)));
            }
            String contentType = getAvatarContentType(response.headers().firstValue(CONTENT_TYPE).orElse(""));
            if (contentType == null) {
                throw new WorkItemRetrievalException(
                    String.format("The avatar of user [%s] has an unsupported content type [%s].", userId,
                        response.headers().firstValue(CONTENT_TYPE).orElse("")));
            }

            StreamingOutput streamingOutput = output -> output.write(response.body());

            return new UserAvatar(streamingOutput, contentType);
        } catch (HttpTimeoutException e) {
            // Not an error of the wiki: reported as a failed retrieval, so that it's not logged as an error on each
            // display of the avatar.
            throw new WorkItemRetrievalException(
                String.format("The avatar of user [%s] wasn't received in time.", userId), e);
        } catch (IOException e) {
            if (ExceptionUtils.indexOfType(e, LimitedBodyHandlers.ResponseTooLargeException.class) >= 0) {
                throw new WorkItemRetrievalException(String.format("The avatar of user [%s] is too large.", userId),
                    e);
            }
            throw new ProjectManagementException(AVATAR_REQUEST_ERROR_MESSAGE, e);
        } catch (URISyntaxException e) {
            throw new ProjectManagementException(AVATAR_REQUEST_ERROR_MESSAGE, e);
        }
    }

    /**
     * @param contentTypeHeader the content type returned by OpenProject for an avatar
     * @return the content type to serve the avatar with, or {@code null} when it isn't a supported raster image type
     */
    private static String getAvatarContentType(String contentTypeHeader)
    {
        String mediaType = StringUtils.substringBefore(contentTypeHeader, SEMICOLON).trim().toLowerCase(Locale.ROOT);
        return AVATAR_CONTENT_TYPES.contains(mediaType) ? mediaType : null;
    }

    @Override
    public PaginatedResult<TimeEntry> getTimeEntries(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode elements = getOpenProjectResponseEntities(API_URL_TIME_ENTRIES, offset, pageSize, filters, "", "");

        List<TimeEntry> entries = new ArrayList<>();
        for (JsonNode element : elements) {
            TimeEntry entry = new TimeEntry(element);
            // The API URL of the work package is replaced by the URL of its page.
            if (entry.getWorkPackage() != null) {
                entry.getWorkPackage().setLocation(
                    OpenProjectApiLinks.toPageUrl(connectionUrl, entry.getWorkPackage().getLocation()));
            }
            entries.add(entry);
        }

        return new PaginatedResult<>(entries, offset, pageSize, entries.size());
    }

    @Override
    public JsonNode createWorkPackage(String url, String jsonBody) throws ProjectManagementException
    {
        String uriStr = url;
        try {
            URI uri = createInstanceUriFromHref(url);
            uriStr = uri.toString();
            HttpRequest request = createAuthorizedPostRequest(uri, jsonBody);
            HttpResponse<String> response = sendForJson(request);

            handleOpenProjectWorkPackagesRequestExceptions(response);

            String body = response.body();
            return objectMapper.readTree(body);
        } catch (URISyntaxException | JsonProcessingException e) {
            throw new ProjectManagementException(
                String.format("Failed to build the OpenProject entity creation url [%s].", uriStr), e);
        } catch (IOException | SecurityException e) {
            throw new ProjectManagementException(
                String.format(COMMUNICATING_ISSUE_MESSAGE, uriStr), e);
        }
    }

    @Override
    public JsonNode getWorkPackageFormResponse(String workPackageId, String jsonBody) throws ProjectManagementException
    {
        String urlPart = String.format(API_URL_FORM_EDIT_WORK_PACKAGE, PathSegmentEncoder.encode(workPackageId));
        String urlString = connectionUrl + urlPart;
        try {
            URI uri = createInstanceUri(urlPart);
            HttpRequest request = createAuthorizedPostRequest(uri, jsonBody);
            HttpResponse<String> response = sendForJson(request);

            handleOpenProjectWorkPackagesRequestExceptions(response);

            String body = response.body();
            return objectMapper.readTree(body);
        } catch (URISyntaxException e) {
            throw new ProjectManagementException(
                String.format(PROJECT_MANAGEMENT_EXCEPTION_MESSAGE, urlString), e);
        } catch (IOException e) {
            throw new ProjectManagementException(
                String.format(COMMUNICATING_ISSUE_MESSAGE, urlString), e);
        }
    }

    @Override
    public JsonNode updateWorkPackage(String url, String jsonBody) throws ProjectManagementException
    {
        String uriStr = url;
        try {
            URI uri = createInstanceUriFromHref(url);
            uriStr = uri.toString();
            HttpRequest request = createAuthorizedPatchRequest(uri, jsonBody);
            HttpResponse<String> response = sendForJson(request);

            handleOpenProjectWorkPackagesRequestExceptions(response);

            String body = response.body();
            return objectMapper.readTree(body);
        } catch (URISyntaxException | JsonProcessingException e) {
            throw new ProjectManagementException(
                String.format("Failed to build the OpenProject entity update url [%s].", uriStr), e);
        } catch (IOException | SecurityException e) {
            throw new ProjectManagementException(
                String.format(COMMUNICATING_ISSUE_MESSAGE, uriStr), e);
        }
    }

    @Override
    public String getInstanceId() throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(URL_INSTANCE_METADATA, null, null, "", "", "");
        return mainNode.path(OP_RESPONSE_INSTALLATION_UUID).asText();
    }

    private JsonNode getOpenProjectResponse(String urlPart, Integer offset, Integer pageSize, String filtersString,
        String sortByString, String selectedElementsString) throws ProjectManagementException
    {
        return getOpenProjectResponse(urlPart, getParameters(offset, pageSize, filtersString, sortByString,
            selectedElementsString));
    }

    private static Map<String, String> getParameters(Integer offset, Integer pageSize, String filtersString,
        String sortByString, String selectedElementsString)
    {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (offset != null) {
            parameters.put(OP_OFFSET, String.valueOf(offset));
        }
        if (!filtersString.isEmpty()) {
            parameters.put(OP_FILTERS, filtersString);
        }
        if (!sortByString.isEmpty()) {
            parameters.put(OP_SORT_BY, sortByString);
        }
        if (!selectedElementsString.isEmpty()) {
            parameters.put(OP_SELECT, selectedElementsString);
        }
        if (pageSize != null) {
            parameters.put(PAGE_SIZE, String.valueOf(pageSize));
        }
        return parameters;
    }

    private JsonNode getOpenProjectResponse(String urlPart, Map<String, String> parameters)
        throws ProjectManagementException
    {
        return getOpenProjectResponse(connectionUrl + urlPart, () -> createInstanceUri(urlPart), parameters);
    }

    /**
     * @param href a link returned by the OpenProject API, see {@link #createInstanceUriFromHref(String)}
     */
    private JsonNode getOpenProjectResponseFromHref(String href, Map<String, String> parameters)
        throws ProjectManagementException
    {
        return getOpenProjectResponse(href, () -> createInstanceUriFromHref(href), parameters);
    }

    private JsonNode getOpenProjectResponse(String uri, InstanceUriSupplier uriSupplier,
        Map<String, String> parameters) throws ProjectManagementException
    {
        try {
            URIBuilder uriBuilder = new URIBuilder(uriSupplier.get());
            parameters.forEach(uriBuilder::setParameter);

            HttpRequest request =
                createAuthorizedGetHttpRequest(uriBuilder.build());
            HttpResponse<String> response = sendForJson(request);

            handleOpenProjectWorkPackagesRequestExceptions(response);

            String body = response.body();
            return objectMapper.readTree(body);
        } catch (URISyntaxException e) {
            throw new ProjectManagementException(
                String.format(PROJECT_MANAGEMENT_EXCEPTION_MESSAGE, uri), e);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(
                String.format("Error trying to read the OpenProject response from [%s].", uri), e);
        } catch (IOException | SecurityException e) {
            throw new ProjectManagementException(
                String.format(COMMUNICATING_ISSUE_MESSAGE, uri), e);
        }
    }

    private JsonNode getOpenProjectResponseEntities(String urlPart, Integer offset, Integer pageSize,
        String filtersString, String sortByString, String selectedElementsString) throws ProjectManagementException
    {
        return getOpenProjectResponse(urlPart, offset, pageSize, filtersString, sortByString,
            selectedElementsString).path(
            OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);
    }

    private int getTotalNumberOfEntities(JsonNode mainNode)
    {
        return mainNode.path(OP_RESPONSE_TOTAL).asInt();
    }

    private List<WorkPackage> getWorkPackagesFromResponse(JsonNode mainNode)
    {
        List<WorkPackage> workPackages = new ArrayList<>();

        JsonNode elementsNode = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);
        for (JsonNode element : elementsNode) {
            workPackages.add(createWorkPackageFromJson(element));
        }
        return workPackages;
    }

    private WorkPackage createWorkPackageFromJson(JsonNode element)
    {
        WorkPackage workPackage = new WorkPackage();

        workPackage.setDescription(
            this.descriptionCleaner.clean(element.path(OP_DESCRIPTION).path(OP_HTML).asText(), this.connectionUrl));
        // The progress isn't set (null) e.g. in the work-based progress mode when the work isn't estimated, which isn't
        // the same as no progress (0).
        JsonNode percentageDone = element.path(OP_RESPONSE_PERCENTAGE_DONE);
        workPackage.setPercentageDone(percentageDone.isNumber() ? percentageDone.asInt() : null);
        int id = element.path(OP_RESPONSE_ID).asInt();
        workPackage.setId(id);

        setDates(workPackage, element);
        setCreatedAndUpdatedDates(workPackage, element);
        setWorkPackageLinksNodeProperties(workPackage, element);

        workPackage.setType(element.path("_type").asText());

        workPackage.setSubject(element.path(OP_RESPONSE_SUBJECT).asText());

        JsonNode displayId = element.path("displayId");
        if (!displayId.isMissingNode() && !displayId.isNull()) {
            workPackage.setDisplayId(displayId.asText());
        }

        workPackage.setFieldValues(WorkPackageFieldsParser.parseFieldValues(element, connectionUrl));

        return workPackage;
    }

    private void setWorkPackageLinksNodeProperties(WorkPackage workPackage, JsonNode element)
    {
        String editUrlSuffix = "/edit";
        JsonNode linksNode = element.path(OP_RESPONSE_LINKS);
        int id = element.path(OP_RESPONSE_ID).asInt();

        String activityUrl = String.format("%s/work_packages/%s/activity", connectionUrl, id);

        String typeName = linksNode.path(OP_RESPONSE_TYPE).path(OP_RESPONSE_TITLE).asText();
        String typeUrl = getPageUrl(linksNode.path(OP_RESPONSE_TYPE)) + editUrlSuffix;
        workPackage.setTypeOfWorkPackage(new Linkable(typeName, typeUrl));

        JsonNode selfNode = linksNode.path(OP_RESPONSE_SELF);
        String issueName = selfNode.path(OP_RESPONSE_TITLE).asText();
        workPackage.setSelf(new Linkable(issueName, activityUrl));

        JsonNode statusNode = linksNode.path(OP_RESPONSE_STATUS);
        String statusName = statusNode.path(OP_RESPONSE_TITLE).asText();
        String statusUrl = getPageUrl(statusNode) + editUrlSuffix;
        workPackage.setStatus(new Linkable(statusName, statusUrl));

        JsonNode authorNode = linksNode.path(OP_RESPONSE_AUTHOR);
        String authorName = authorNode.path(OP_RESPONSE_TITLE).asText();
        String authorUrl = getPageUrl(authorNode);
        workPackage.setAuthor(new Linkable(authorName, authorUrl));

        JsonNode assigneeNode = linksNode.path(OP_RESPONSE_ASSIGNEE);
        String assigneeName = assigneeNode.path(OP_RESPONSE_TITLE).asText();
        String assigneeUrl = getPageUrl(assigneeNode);
        workPackage.setAssignee(new Linkable(assigneeName, assigneeUrl));

        JsonNode projectNode = linksNode.path(OP_RESPONSE_PROJECT);
        String projectName = projectNode.path(OP_RESPONSE_TITLE).asText();
        String projectUrl = getPageUrl(projectNode);
        workPackage.setProject(new Linkable(projectName, projectUrl));

        JsonNode priorityNode = linksNode.path(OP_RESPONSE_PRIORITY);
        String priorityName = priorityNode.path(OP_RESPONSE_TITLE).asText();
        workPackage.setPriority(new Linkable(priorityName, activityUrl));
    }

    /**
     * @param linkNode a link of a resource returned by the OpenProject API
     * @return the URL of the page of the resource in OpenProject
     */
    private String getPageUrl(JsonNode linkNode)
    {
        return OpenProjectApiLinks.toPageUrl(connectionUrl, linkNode.path(HREF).asText());
    }

    private void setDates(WorkPackage wp, JsonNode node)
    {
        if (node.has(OP_DATE)) {
            // A milestone has a single date instead of a start date and a due date: it starts and ends on that date.
            Date date = getDateFromNode(OP_DATE, node);
            wp.setStartDate(date);
            wp.setDueDate(date);
        } else {
            wp.setStartDate(getDateFromNode(OP_START_DATE, node));
            wp.setDueDate(getDateFromNode(OP_DUE_DATE, node));
        }
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

    private void setCreatedAndUpdatedDates(WorkPackage wp, JsonNode node)
    {
        wp.setCreatedAt(getDateTimeFromNode(OP_CREATED_AT, node));
        wp.setUpdatedAt(getDateTimeFromNode(OP_UPDATED_AT, node));
    }

    /**
     * Unlike the start and due dates, which are plain dates, the creation and update dates are ISO 8601 date-times
     * with an offset (e.g. {@code 2025-08-13T22:30:15.123Z}): they are parsed into the instant they denote, which is
     * then displayed in the time zone of the XWiki server.
     */
    private Date getDateTimeFromNode(String prop, JsonNode node)
    {
        JsonNode dateTime = node.path(prop);
        return dateTime.isNull() ? null : OpenProjectDateTimes.parse(dateTime.asText());
    }

    private PaginatedResult<WorkPackage> getWorkPackagePaginatedResult(JsonNode node, Integer offset, Integer pageSize)
    {
        PaginatedResult<WorkPackage> paginatedResult = new PaginatedResult<>();

        int totalNumberOfWorkPackages = getTotalNumberOfEntities(node);

        List<WorkPackage> workPackages = getWorkPackagesFromResponse(node);

        paginatedResult.setItems(workPackages);
        if (offset != null) {
            paginatedResult.setPage(offset);
        }
        if (pageSize != null) {
            paginatedResult.setPageSize(pageSize);
        }
        paginatedResult.setTotalItems(totalNumberOfWorkPackages);
        return paginatedResult;
    }

    /**
     * Builds the URI of a request to the OpenProject instance. The requests carry the credentials of the current user,
     * so they must only target the configured instance, whatever the given path holds (it can come from the response
     * of the instance or from the parameters of the request handled by XWiki).
     *
     * @param urlPart the path (and optional query) of the request, relative to the URL of the instance
     * @return the URI of the request
     * @throws URISyntaxException if the resulting URI is invalid or doesn't target the configured instance
     */
    private URI createInstanceUri(String urlPart) throws URISyntaxException
    {
        String uriString = connectionUrl + (urlPart == null ? "" : urlPart);
        URI uri = new URI(uriString);
        URI instanceUri = new URI(connectionUrl);
        if (!isSameOrigin(uri, instanceUri) || !isUnderInstancePath(uri, instanceUri)
            || hasDotSegment(uri.getRawPath()))
        {
            throw new URISyntaxException(uriString, "The URL doesn't target the configured OpenProject instance");
        }
        return uri;
    }

    /**
     * Builds the URI of a request from a link ({@code href}) returned by the OpenProject API. Such a link is an
     * absolute path, which already includes the path the instance is served under, so it's resolved against the
     * origin of the instance, with the same checks as {@link #createInstanceUri(String)}.
     *
     * @param href the link returned by the OpenProject API, e.g. {@code /openproject/api/v3/projects/1}
     * @return the URI of the request
     * @throws URISyntaxException if the resulting URI is invalid or doesn't target the configured instance
     */
    private URI createInstanceUriFromHref(String href) throws URISyntaxException
    {
        String path = OpenProjectApiLinks.toInstanceRelativePath(connectionUrl, href);
        if (path == null) {
            throw new URISyntaxException(String.valueOf(href),
                "The link doesn't target the configured OpenProject instance");
        }
        return createInstanceUri(path);
    }

    /**
     * The instance can be served under a path, e.g. {@code https://example.org/openproject}: the requests must stay
     * under this path.
     */
    private static boolean isUnderInstancePath(URI uri, URI instanceUri)
    {
        String instancePath = StringUtils.removeEnd(Objects.toString(instanceUri.getRawPath(), ""), SLASH);
        String path = Objects.toString(uri.getRawPath(), "");
        return instancePath.isEmpty() || path.equals(instancePath) || path.startsWith(instancePath + SLASH);
    }

    private static boolean isSameOrigin(URI uri, URI instanceUri)
    {
        boolean sameHost = uri.getHost() != null && uri.getHost().equalsIgnoreCase(instanceUri.getHost());
        boolean sameScheme = StringUtils.equalsIgnoreCase(uri.getScheme(), instanceUri.getScheme());
        return sameHost && sameScheme && uri.getPort() == instanceUri.getPort()
            && Objects.equals(uri.getRawUserInfo(), instanceUri.getRawUserInfo());
    }

    private static boolean hasDotSegment(String rawPath)
    {
        if (rawPath == null) {
            return false;
        }
        for (String segment : rawPath.split(SLASH)) {
            // Some servers ignore the parameters of a path segment, e.g. "..;x" is handled as "..".
            String decodedSegment =
                StringUtils.substringBefore(segment, SEMICOLON).replace("%2e", DOT).replace("%2E", DOT);
            if (DOT.equals(decodedSegment) || "..".equals(decodedSegment)) {
                return true;
            }
        }
        return false;
    }

    private HttpRequest createAuthorizedRequest(
        URI uri,
        String method,
        HttpRequest.BodyPublisher body,
        String accept
    )
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", accept);

        authenticator.authenticate(builder);

        if (POST.equals(method) || PUT.equals(method) || PATCH.equals(method)) {
            builder.header(CONTENT_TYPE, MediaType.APPLICATION_JSON);
        }

        switch (method.toUpperCase()) {
            case GET:
                builder.GET();
                break;
            case POST:
                builder.POST(body);
                break;
            case PUT:
                builder.PUT(body);
                break;
            case PATCH:
                builder.method(PATCH, body);
                break;
            case "DELETE":
                builder.DELETE();
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        return builder.build();
    }

    private HttpRequest createAuthorizedGetHttpRequest(URI uri)
    {
        return createAuthorizedRequest(
            uri,
            GET,
            HttpRequest.BodyPublishers.noBody(),
            MediaType.APPLICATION_JSON
        );
    }

    /**
     * Adds the path the instance is served under to the links of the given request body that were built by XWiki
     * without it (e.g. {@code /api/v3/projects/1} for a project chosen from its identifier), since OpenProject only
     * accepts the links that include it.
     *
     * @param jsonBody the JSON body of a request to the OpenProject API
     * @return the body, with its links including the path of the instance
     */
    private String normalizeLinks(String jsonBody)
    {
        if (OpenProjectApiLinks.getInstancePath(connectionUrl).isEmpty()) {
            // The instance is served at the root of its host: the links are complete.
            return jsonBody;
        }
        try {
            JsonNode body = objectMapper.readTree(jsonBody);
            if (body == null || !normalizeLinks(body, false)) {
                return jsonBody;
            }
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            // Sent as is: OpenProject reports the invalid body.
            return jsonBody;
        }
    }

    /**
     * @return whether a link was changed
     */
    private boolean normalizeLinks(JsonNode node, boolean inLinks)
    {
        boolean changed = false;
        if (node.isObject()) {
            JsonNode href = node.path(HREF);
            if (inLinks && href.isTextual()) {
                String normalizedHref = OpenProjectApiLinks.toInstanceHref(connectionUrl, href.asText());
                if (!normalizedHref.equals(href.asText())) {
                    ((ObjectNode) node).put(HREF, normalizedHref);
                    changed = true;
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                changed |= normalizeLinks(field.getValue(), inLinks || OP_RESPONSE_LINKS.equals(field.getKey()));
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                changed |= normalizeLinks(element, inLinks);
            }
        }
        return changed;
    }

    private HttpRequest createAuthorizedPostRequest(URI uri, String jsonBody)
    {
        return createAuthorizedRequest(
            uri,
            POST,
            HttpRequest.BodyPublishers.ofString(normalizeLinks(jsonBody)),
            MediaType.APPLICATION_JSON
        );
    }

    private HttpRequest createAuthorizedPatchRequest(URI uri, String jsonBody)
    {
        return createAuthorizedRequest(
            uri,
            PATCH,
            HttpRequest.BodyPublishers.ofString(normalizeLinks(jsonBody)),
            MediaType.APPLICATION_JSON
        );
    }

    private HttpResponse<String> sendForJson(HttpRequest request) throws IOException, ProjectManagementException
    {
        return send(request, LimitedBodyHandlers.ofString(MAX_RESPONSE_SIZE, REQUEST_TIMEOUT));
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
        throws IOException, ProjectManagementException
    {
        try {
            return client.send(request, bodyHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProjectManagementException(
                String.format("Interrupted while waiting for the response of [%s].", request.uri()), e);
        }
    }

    private void handleOpenProjectWorkPackagesRequestExceptions(HttpResponse<String> response)
        throws ProjectManagementException
    {
        int statusCode = response.statusCode();

        if (statusCode >= 400 && statusCode <= 499) {
            throw new WorkItemRetrievalException(
                String.format("The request to the OpenProject API was invalid. [%s]",
                    StringUtils.abbreviate(response.body(), MAX_ERROR_MESSAGE_LENGTH)),
                getOpenProjectError(response.body(), "OpenProject rejected the request: %s"), statusCode);
        } else if (statusCode >= 500) {
            throw new ProjectManagementException(
                String.format("Failed to retrieve the OpenProject entities. [%s].",
                    StringUtils.abbreviate(response.body(), MAX_ERROR_MESSAGE_LENGTH)),
                getOpenProjectError(response.body(), "OpenProject failed to answer the request: %s"));
        }
    }

    /**
     * The errors displayed to the users show the message of the root cause of the failure: the message of the error
     * returned by OpenProject is made the root cause, while the message of the failure keeps the whole response, for
     * the technical details.
     *
     * @param body the body of an error response of OpenProject
     * @param format the format of the message of the cause, holding the message of the OpenProject error
     * @return the message of the OpenProject error, as a cause, or {@code null} if the body is not an OpenProject
     *     error
     */
    private ProjectManagementException getOpenProjectError(String body, String format)
    {
        try {
            String message = objectMapper.readTree(StringUtils.defaultString(body)).path("message").asText("");
            return message.isBlank() ? null
                : new ProjectManagementException(String.format(format, message));
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
