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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String OP_START_DATE = "startDate";

    private static final String OP_DUE_DATE = "dueDate";

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

    private static final String API_URL_STATUSES = "/api/v3/statuses";

    private static final String API_URL_PRIORITIES = "/api/v3/priorities";

    private static final String API_URL_USERS = "/api/v3/users";

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
     * The maximum number of bytes of the body of a failed avatar request kept in the error message.
     */
    private static final int MAX_AVATAR_ERROR_MESSAGE_LENGTH = 1024;

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
        String projectWorkPackagesUrl =
            String.format("%s/projects/%s/work_packages", API_URL_PART, encodePathSegment(project));
        JsonNode mainNode = getOpenProjectResponse(projectWorkPackagesUrl, offset, pageSize, filters, sortBy, "");
        return getWorkPackagePaginatedResult(mainNode, offset, pageSize);
    }

    @Override
    public PaginatedResult<WorkPackage> getQueryWorkPackages(String queryId, Integer offset, Integer pageSize,
        String sortBy) throws ProjectManagementException
    {
        // The filters are left out on purpose, sending them would replace the ones defined by the query.
        String queryUrl = String.format(API_URL_QUERY, encodePathSegment(queryId));
        JsonNode queryJson = getOpenProjectResponse(queryUrl, offset, pageSize, "", sortBy, "");

        return getWorkPackagePaginatedResult(queryJson.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_RESULTS), offset,
            pageSize);
    }

    @Override
    public WorkPackage getWorkPackage(String idOrKey) throws ProjectManagementException
    {
        String workPackageUrl = API_URL_WORK_PACKAGES + '/' + encodePathSegment(idOrKey);
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
        String queryUrl = String.format(API_URL_QUERY, encodePathSegment(queryId));
        JsonNode queryJson = getOpenProjectResponse(queryUrl, null, null, "", "", "");
        return queryJson.path(OP_RESPONSE_LINKS).path(OP_RESPONSE_RESULTS).path(HREF).asText();
    }

    @Override
    public PaginatedResult<User> getUsers(Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(API_URL_USERS, offset, pageSize, filters, "", "");
        JsonNode usersJson = mainNode.path(OP_RESPONSE_EMBEDDED).path(OP_RESPONSE_ELEMENTS);

        List<User> users = new ArrayList<>();

        for (JsonNode element : usersJson) {
            User user = new User(element);
            user.initializeSelfWithPath(connectionUrl, String.format("/users/%s", user.getId()));
            users.add(user);
        }

        return new PaginatedResult<>(users, offset, pageSize, getTotalNumberOfEntities(mainNode));
    }

    @Override
    public PaginatedResult<User> getAvailableUsers(String url, Integer offset, Integer pageSize, String filters)
        throws ProjectManagementException
    {
        JsonNode mainNode = getOpenProjectResponse(url, offset, pageSize, filters, "", "");
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
        JsonNode mainNode = getOpenProjectResponse(url, offset, pageSize, filters, "", "");
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
            news.setProjectLink(new Linkable(projectNode.path(OP_RESPONSE_TITLE).asText(),
                connectionUrl + projectNode.path(HREF).asText().replaceFirst(API_URL_PART, "")));
            newsList.add(news);
        }

        return new PaginatedResult<>(newsList, offset, pageSize, newsList.size());
    }

    @Override
    public Project getProject(Integer projectId) throws ProjectManagementException
    {
        String urlPart = String.format(API_URL_PROJECT, projectId);
        JsonNode projectJson = getOpenProjectResponse(urlPart, null, null, "", "", "");
        Project project = new Project(projectJson);
        project.initializeSelfWithPath(connectionUrl, String.format(API_PROJECT_PLACEHOLDER, project.getId()));
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
            String principalHref = principalNode.path(HREF).asText();
            String principalUrl = connectionUrl + principalHref.replaceFirst(API_URL_PART, "");

            List<Linkable> roles = new ArrayList<>();
            for (JsonNode roleNode : linksNode.path(OP_RESPONSE_ROLES)) {
                roles.add(new Linkable(
                    roleNode.path(OP_RESPONSE_TITLE).asText(),
                    connectionUrl + roleNode.path(HREF).asText().replaceFirst(API_URL_PART, "")
                ));
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
        String urlPart = String.format("/api/v3/users/%s/avatar", encodePathSegment(userId));
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
                            Math.min(errorBody.length, MAX_AVATAR_ERROR_MESSAGE_LENGTH), StandardCharsets.UTF_8)));
            }
            String contentType = getAvatarContentType(response.headers().firstValue(CONTENT_TYPE).orElse(""));
            if (contentType == null) {
                throw new WorkItemRetrievalException(
                    String.format("The avatar of user [%s] has an unsupported content type [%s].", userId,
                        response.headers().firstValue(CONTENT_TYPE).orElse("")));
            }

            StreamingOutput streamingOutput = output -> output.write(response.body());

            return new UserAvatar(streamingOutput, contentType);
        } catch (URISyntaxException | IOException e) {
            throw new ProjectManagementException("There was an issue in creating or sending the user avatar request.",
                e);
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
                    connectionUrl + entry.getWorkPackage().getLocation().replaceFirst(API_URL_PART, ""));
            }
            entries.add(entry);
        }

        return new PaginatedResult<>(entries, offset, pageSize, entries.size());
    }

    @Override
    public JsonNode createWorkPackage(String url, String jsonBody) throws ProjectManagementException
    {
        String uriStr = connectionUrl + url;
        try {
            URI uri = createInstanceUri(url);
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
        String urlPart = String.format(API_URL_FORM_EDIT_WORK_PACKAGE, encodePathSegment(workPackageId));
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
        String uriStr = connectionUrl + url;
        try {
            URI uri = createInstanceUri(url);
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
        String uri = connectionUrl + urlPart;
        try {
            URIBuilder uriBuilder = new URIBuilder(createInstanceUri(urlPart));
            if (offset != null) {
                uriBuilder.setParameter(OP_OFFSET, String.valueOf(offset));
            }
            if (!filtersString.isEmpty()) {
                uriBuilder.setParameter(OP_FILTERS, filtersString);
            }
            if (!sortByString.isEmpty()) {
                uriBuilder.setParameter(OP_SORT_BY, sortByString);
            }
            if (!selectedElementsString.isEmpty()) {
                uriBuilder.setParameter(OP_SELECT, selectedElementsString);
            }
            if (pageSize != null) {
                uriBuilder.setParameter(PAGE_SIZE, String.valueOf(pageSize));
            }

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
            this.descriptionCleaner.clean(element.path(OP_DESCRIPTION).path("html").asText(), this.connectionUrl));
        workPackage.setPercentageDone(element.path(OP_RESPONSE_PERCENTAGE_DONE).asInt());
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
        String editCreateUrlString = "%s%s/edit";
        JsonNode linksNode = element.path(OP_RESPONSE_LINKS);
        int id = element.path(OP_RESPONSE_ID).asInt();

        String activityUrl = String.format("%s/work_packages/%s/activity", connectionUrl, id);

        String typeName = linksNode.path(OP_RESPONSE_TYPE).path(OP_RESPONSE_TITLE).asText();
        String typeUrl = String.format(editCreateUrlString, connectionUrl,
            linksNode.path(OP_RESPONSE_TYPE).path(HREF).asText()).replaceFirst(API_URL_PART, "");
        workPackage.setTypeOfWorkPackage(new Linkable(typeName, typeUrl));

        JsonNode selfNode = linksNode.path(OP_RESPONSE_SELF);
        String issueName = selfNode.path(OP_RESPONSE_TITLE).asText();
        workPackage.setSelf(new Linkable(issueName, activityUrl));

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
        workPackage.setPriority(new Linkable(priorityName, activityUrl));
    }

    private void setDates(WorkPackage wp, JsonNode node)
    {
        wp.setStartDate(getDateFromNode(OP_START_DATE, node));
        wp.setDueDate(getDateFromNode(OP_DUE_DATE, node));
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

    /**
     * @param value a value inserted in the path of a request, e.g. an identifier
     * @return the value encoded to be used as a single path segment
     */
    private static String encodePathSegment(String value)
    {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
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

    private HttpRequest createAuthorizedPostRequest(URI uri, String jsonBody)
    {
        return createAuthorizedRequest(
            uri,
            POST,
            HttpRequest.BodyPublishers.ofString(jsonBody),
            MediaType.APPLICATION_JSON
        );
    }

    private HttpRequest createAuthorizedPatchRequest(URI uri, String jsonBody)
    {
        return createAuthorizedRequest(
            uri,
            PATCH,
            HttpRequest.BodyPublishers.ofString(jsonBody),
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
                String.format("The request to the OpenProject API was invalid. [%s]", response.body()), statusCode
            );
        } else if (statusCode >= 500) {
            throw new ProjectManagementException(
                String.format("Failed to retrieve the OpenProject entities. [%s].", response.body())
            );
        }
    }
}
