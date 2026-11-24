package com.xwiki.projectmanagement.openproject.internal.rest.suggest;

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

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.rest.WikiRequestExecutor;
import com.xwiki.projectmanagement.openproject.FilterBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE;

/**
 * Rest endpoint that suggests ids based on some query string.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.suggest.Suggest")
@Singleton
@Path("/wikis/{wikiName}/openproject/instance/{instance}/suggest/{suggest}")
public class Suggest extends XWikiResource
{
    private static final String ID = "id";

    private static final String STATUSES = "statuses";

    private static final String PROJECTS = "projects";

    private static final String PRIORITIES = "priorities";

    private static final String NAME = "name";

    private static final String TYPES = "types";

    private static final String PARENT = "parent";

    private static final String USERS = "users";

    private static final String VERSIONS = "versions";

    private static final String WORK_PACKAGE_TYPE_PROPERTY = "type";

    private static final String WORK_PACKAGE_SUBJECT_PROPERTY = "subject";

    private static final String WORK_PACKAGE_API_PATH_FORMAT = "/api/v3/work_packages/%s";

    private static final String VALUE = "value";

    private static final String LABEL = "label";

    private static final String URL = "url";

    private static final String HINT = "hint";

    /**
     * The text written for the suggested value in the query of a macro content, which OpenProject resolves back into
     * the value: the identifier of a project (which doesn't change when the project is renamed), the name of a status,
     * a type, a priority, and the name of a version or a user when no other one has it. The values without it are
     * written by id.
     */
    private static final String QUERY_VALUE = "queryValue";

    /**
     * The maximum number of users whose name contains the name of a selected user, among which the ones having
     * exactly this name are counted.
     */
    private static final int SAME_NAME_PAGE_SIZE = 100;

    private static final String ICON = "icon";

    /**
     * The ids of the OpenProject entities are integers, so longer numbers can't be ids.
     */
    private static final Pattern ID_PATTERN = Pattern.compile("\\d{1,10}");

    /**
     * The identifiers of the OpenProject projects start with a lower case letter, followed by lower case letters,
     * digits, dashes or underscores (for example {@code demo-project}).
     */
    private static final Pattern PROJECT_IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");

    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private WikiRequestExecutor wikiRequestExecutor;

    /**
     * @param wiki the wiki that contains the configured client.
     * @param instance the OpenProject client where to search for work item suggestions.
     * @param suggest the type of suggestions to retrieve
     * @param search the string that should match the work item summary (or maybe other props as well).
     * @param pageSize the number of elements that should be returned.
     * @param selectedItem the id of the item that is already selected (or, for the projects, its id or its identifier).
     *     The result will contain its information.
     * @return a list of objects with the following properties: value, label, icon, url, hint.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getSuggestions(
        @PathParam("wikiName") String wiki,
        @PathParam("instance") String instance,
        @PathParam("suggest") @DefaultValue("") String suggest,
        @QueryParam("search") @DefaultValue("") String search,
        @QueryParam("pageSize") @DefaultValue("25") int pageSize,
        @QueryParam("selectedItem") String selectedItem
    )
    {
        return this.wikiRequestExecutor.execute(wiki,
            () -> getSuggestions(instance, suggest, search, pageSize, selectedItem));
    }

    private Response getSuggestions(String instance, String suggest, String search, int pageSize,
        String selectedItem)
    {
        OpenProjectApiClient openProjectApiClient;

        String lowerSearch = search.toLowerCase();

        openProjectApiClient = openProjectAccess.resolve(instance).getClient();
        if (openProjectApiClient == null) {
            // No configuration was found.
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<Map<String, String>> response;

        try {
            response = getSuggestions(openProjectApiClient, suggest, lowerSearch, pageSize, selectedItem);
        } catch (ProjectManagementException e) {
            // The exception message can hold the OpenProject URL and the body of its error response, which are only
            // meant for the administrators: log them and send a generic message to the client.
            this.slf4Jlogger.warn("Failed to retrieve the [{}] suggestions from the OpenProject instance [{}]. "
                + "Root cause: [{}]", suggest, instance, ExceptionUtils.getRootCauseMessage(e));
            return Response.serverError().entity("Failed to retrieve the suggestions from OpenProject.").build();
        }
        return Response.ok(response).build();
    }

    private List<Map<String, String>> getSuggestions(OpenProjectApiClient openProjectApiClient, String suggest,
        String lowerSearch, int pageSize, String selectedItem) throws ProjectManagementException
    {
        // The selected item of the projects can be given by its identifier, all the other selected items are ids. A
        // selected item that isn't an id is ignored, instead of failing the request, so that the client still gets the
        // suggestions.
        Integer selectedId = getSelectedId(selectedItem);
        List<Map<String, String>> response;
        switch (suggest) {
            case ID:
                response = getIdentifiersSuggestions(openProjectApiClient, lowerSearch, pageSize, selectedId);
                break;
            case PRIORITIES:
                response = getPrioritiesSuggestions(openProjectApiClient);
                break;
            case STATUSES:
                response = getStatusesSuggestions(openProjectApiClient);
                break;
            case PROJECTS:
                response = getProjectsSuggestions(openProjectApiClient, lowerSearch, pageSize, selectedItem);
                break;
            case TYPES:
                response = getTypesSuggestions(openProjectApiClient);
                break;
            case PARENT:
                response = getParentsSuggestions(openProjectApiClient, lowerSearch, pageSize, selectedId);
                break;
            case USERS:
                response = getUsersSuggestions(openProjectApiClient, lowerSearch, pageSize, selectedId);
                break;
            case VERSIONS:
                response = getVersionsSuggestions(openProjectApiClient, lowerSearch, pageSize);
                break;
            default:
                response = Collections.emptyList();
                break;
        }
        return response;
    }

    private List<Map<String, String>> getIdentifiersSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString,
        int pageSize, Integer selectedItem) throws ProjectManagementException
    {
        String filter = getSelectedIdFilter(selectedItem, searchString);
        List<WorkPackage> workPackages;
        if (StringUtils.isEmpty(filter)) {
            workPackages = getWorkPackages(openProjectApiClient, FilterBuilder::new, searchString, pageSize);
        } else {
            workPackages = openProjectApiClient.getWorkPackages(1, pageSize, filter, "").getItems();
        }
        return workPackages.stream()
            .map(
                obj -> createSuggestion(
                    String.valueOf(obj.getId()),
                    String.valueOf(obj.getId()),
                    obj.getSelf().getLocation(),
                    obj.getName()
                )
            )
            .collect(Collectors.toList());
    }

    /**
     * @param baseFilter creates the filters that all the returned work packages must match
     * @return the work packages whose subject contains the search string, preceded by the work package whose id is
     *     the search string, if any
     */
    private List<WorkPackage> getWorkPackages(OpenProjectApiClient openProjectApiClient,
        Supplier<FilterBuilder> baseFilter, String searchString, int pageSize) throws ProjectManagementException
    {
        List<WorkPackage> subjectMatches = openProjectApiClient.getWorkPackages(1, pageSize,
            baseFilter.get().addFilter(WORK_PACKAGE_SUBJECT_PROPERTY, FilterBuilder.Operator.CONTAINS, searchString)
                .build(), "").getItems();
        // OpenProject combines the filters with AND, so the work package whose id is typed is fetched separately, and
        // suggested first.
        if (pageSize <= 0 || !ID_PATTERN.matcher(searchString).matches()) {
            return subjectMatches;
        }
        List<WorkPackage> workPackages = new ArrayList<>(getWorkPackageById(openProjectApiClient,
            baseFilter.get().addFilter(ID, FilterBuilder.Operator.EQUALS, searchString).build(), searchString));
        for (WorkPackage workPackage : subjectMatches) {
            if (workPackages.size() < pageSize
                && workPackages.stream().noneMatch(item -> Objects.equals(item.getId(), workPackage.getId())))
            {
                workPackages.add(workPackage);
            }
        }
        return workPackages;
    }

    private List<WorkPackage> getWorkPackageById(OpenProjectApiClient openProjectApiClient, String filter, String id)
    {
        try {
            return openProjectApiClient.getWorkPackages(1, 1, filter, "").getItems();
        } catch (ProjectManagementException e) {
            // The typed number isn't necessarily the id of an existing work package: still suggest the work packages
            // whose subject contains it.
            this.slf4Jlogger.debug("Failed to retrieve the work package with id [{}]. Root cause: [{}]", id,
                ExceptionUtils.getRootCauseMessage(e));
            return Collections.emptyList();
        }
    }

    private List<Map<String, String>> getPrioritiesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        // OpenProject requires unique names for the priorities, the statuses and the types.
        List<? extends BaseOpenProjectObject> priorities = openProjectApiClient.getPriorities().getItems();
        return getNamedSuggestions(priorities, getUniqueNames(priorities));
    }

    private List<Map<String, String>> getStatusesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        List<? extends BaseOpenProjectObject> statuses = openProjectApiClient.getStatuses().getItems();
        return getNamedSuggestions(statuses, getUniqueNames(statuses));
    }

    private List<Map<String, String>> getProjectsSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize, String selectedItem) throws ProjectManagementException
    {
        // The selected project is suggested first, whatever the search, so that the client always gets its label: the
        // pickers send their value along with the text typed by the user. It keeps the value it is given (its id or its
        // identifier) so that the client can match it with the value of its input, while the other projects are
        // suggested by their id.
        Project selectedProject = getSelectedProject(openProjectApiClient, selectedItem);
        List<Map<String, String>> suggestions = new ArrayList<>();
        if (selectedProject != null && pageSize > 0) {
            suggestions.add(withQueryValue(
                createSuggestion(selectedItem, selectedProject.getName(), selectedProject.getSelf().getLocation()),
                selectedProject.getIdentifier()));
        }
        for (Project project : openProjectApiClient.getProjects(1, pageSize, buildFilter(NAME, searchString))
            .getItems())
        {
            if (suggestions.size() < pageSize
                && (selectedProject == null || !Objects.equals(selectedProject.getId(), project.getId())))
            {
                suggestions.add(withQueryValue(createSuggestion(String.valueOf(project.getId()), project.getName(),
                    project.getSelf().getLocation()), project.getIdentifier()));
            }
        }
        return suggestions;
    }

    /**
     * @param selectedItem the id (for example {@code 3}) or the identifier (for example {@code demo-project}) of the
     *     selected project, or any other value (for example the value of the "use the dashboard-selected project"
     *     option), which is ignored
     * @return the selected project, or {@code null} when there's none, or when it doesn't exist or isn't visible to
     *     the current user
     * @throws ProjectManagementException if OpenProject couldn't be reached or failed to answer
     */
    private Project getSelectedProject(OpenProjectApiClient openProjectApiClient, String selectedItem)
        throws ProjectManagementException
    {
        if (getSelectedId(selectedItem) == null && (selectedItem == null
            || !PROJECT_IDENTIFIER_PATTERN.matcher(selectedItem).matches()
            || USE_SELECTED_DASHBOARD_PROJECT_VALUE.equals(selectedItem)))
        {
            return null;
        }
        try {
            return openProjectApiClient.getProject(selectedItem);
        } catch (WorkItemRetrievalException e) {
            // OpenProject answers 404 both for the projects that don't exist and for those the user can't see: the
            // client keeps showing the value it has.
            if (Integer.valueOf(HttpURLConnection.HTTP_NOT_FOUND).equals(e.getStatusCode())) {
                this.slf4Jlogger.debug("Failed to retrieve the selected project [{}]. Root cause: [{}]", selectedItem,
                    ExceptionUtils.getRootCauseMessage(e));
                return null;
            }
            throw e;
        }
    }

    private List<Map<String, String>> getTypesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        List<? extends BaseOpenProjectObject> types = openProjectApiClient.getTypes().getItems();
        return getNamedSuggestions(types, getUniqueNames(types));
    }

    private List<Map<String, String>> getParentsSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize, Integer selectedItem) throws ProjectManagementException
    {
        String filter = getSelectedIdFilter(selectedItem, searchString);
        List<WorkPackage> workPackages;
        if (StringUtils.isEmpty(filter)) {
            // A milestone work package can't be a parent of another work package, so we only suggest work packages
            // whose type is not a milestone.
            List<String> nonMilestoneTypeIds = openProjectApiClient.getTypes().getItems().stream()
                .filter(type -> !type.isMilestone())
                .map(type -> String.valueOf(type.getId()))
                .collect(Collectors.toList());

            workPackages = getWorkPackages(openProjectApiClient,
                () -> new FilterBuilder()
                    .addFilter(WORK_PACKAGE_TYPE_PROPERTY, FilterBuilder.Operator.EQUALS, nonMilestoneTypeIds),
                searchString, pageSize);
        } else {
            workPackages = openProjectApiClient.getWorkPackages(1, pageSize, filter, "").getItems();
        }

        return workPackages
            .stream()
            .map(
                workPackage -> createSuggestion(
                    String.format(WORK_PACKAGE_API_PATH_FORMAT, workPackage.getId()),
                    String.format("#%s: %s", workPackage.getId(), workPackage.getName()),
                    workPackage.getSelf().getLocation(),
                    workPackage.getName()
                )
            )
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getUsersSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize, Integer selectedItem) throws ProjectManagementException
    {
        String filter = getSelectedIdFilter(selectedItem, searchString);
        boolean selected = StringUtils.isNotEmpty(filter);
        if (!selected) {
            filter = buildFilter(NAME, searchString);
        }
        List<User> users = openProjectApiClient.getUsers(1, pageSize, filter).getItems();
        // A user is written by name only when no other user has the same name: the other users having it are among
        // the found ones (their name contains the searched text too), unless the search returned a full page.
        Set<String> uniqueNames;
        if (selected) {
            uniqueNames = users.size() == 1 && isOnlyUserNamed(openProjectApiClient, users.get(0).getName())
                ? getUniqueNames(users) : Collections.emptySet();
        } else {
            uniqueNames = users.size() < pageSize ? getUniqueNames(users) : Collections.emptySet();
        }
        // Users can share a name: their suggestions are told apart by the login of the users, when OpenProject gives
        // it to the current user (who can then see it in OpenProject too). Their e-mail address is never shown.
        List<Map<String, String>> suggestions = getNamedSuggestions(users, uniqueNames);
        return IntStream.range(0, users.size())
            .mapToObj(index -> withHint(suggestions.get(index), users.get(index).getLogin()))
            .collect(Collectors.toList());
    }

    private boolean isOnlyUserNamed(OpenProjectApiClient openProjectApiClient, String name)
        throws ProjectManagementException
    {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        List<? extends BaseOpenProjectObject> users = openProjectApiClient.getUsers(1, SAME_NAME_PAGE_SIZE,
            buildFilter(NAME, name.toLowerCase())).getItems();
        return users.size() < SAME_NAME_PAGE_SIZE && countSameName(users, name) == 1;
    }

    private static long countSameName(List<? extends BaseOpenProjectObject> objects, String name)
    {
        return objects.stream().filter(object -> name != null && name.equalsIgnoreCase(object.getName())).count();
    }

    /**
     * @return the names, in lower case, that only one of the given resources has
     */
    private static Set<String> getUniqueNames(List<? extends BaseOpenProjectObject> objects)
    {
        return objects.stream().map(BaseOpenProjectObject::getName)
            .filter(name -> StringUtils.isNotBlank(name) && countSameName(objects, name) == 1)
            .map(String::toLowerCase).collect(Collectors.toSet());
    }

    private List<Map<String, String>> getVersionsSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize) throws ProjectManagementException
    {
        // The versions API can't be filtered on the name, but all the versions are retrieved at once and cached. A
        // version is written by name only when no other version has it (e.g. "1.0" in several projects).
        List<? extends BaseOpenProjectObject> versions = openProjectApiClient.getVersions().getItems();
        return getNamedSuggestions(versions.stream()
            .filter(version -> version.getName() != null && version.getName().toLowerCase().contains(searchString))
            .limit(pageSize)
            .collect(Collectors.toList()), getUniqueNames(versions));
    }

    /**
     * @param uniqueNames the names, in lower case, that identify a resource: the resources having one of them are
     *     written by name in a query, the others by id
     * @return the suggestions of the given resources
     */
    private List<Map<String, String>> getNamedSuggestions(List<? extends BaseOpenProjectObject> openProjectObjects,
        Set<String> uniqueNames)
    {
        return openProjectObjects.stream().map(object -> {
            Map<String, String> suggestion = createSuggestion(String.valueOf(object.getId()), object.getName(),
                object.getSelf().getLocation());
            return object.getName() != null && uniqueNames.contains(object.getName().toLowerCase())
                ? withQueryValue(suggestion, object.getName()) : suggestion;
        }).collect(Collectors.toList());
    }

    private Integer getSelectedId(String selectedItem)
    {
        // A number larger than the largest integer can't be an id.
        if (selectedItem != null && ID_PATTERN.matcher(selectedItem).matches()
            && Long.parseLong(selectedItem) <= Integer.MAX_VALUE)
        {
            return Integer.valueOf(selectedItem);
        }
        return null;
    }

    private String getSelectedIdFilter(Integer selectedId, String searchString)
    {
        if (selectedId != null && StringUtils.isNotEmpty(searchString)) {
            return buildFilter(ID, FilterBuilder.Operator.EQUALS, String.valueOf(selectedId));
        }
        return null;
    }

    private String buildFilter(String fieldName, FilterBuilder.Operator operator, String searchValue)
    {
        return new FilterBuilder().addFilter(fieldName, operator, searchValue).build();
    }

    private String buildFilter(String fieldName, String searchValue)
    {
        return buildFilter(fieldName, FilterBuilder.Operator.CONTAINS, searchValue);
    }

    private Map<String, String> createSuggestion(String value, String label, String url)
    {
        return Map.of(
            VALUE, value,
            LABEL, label,
            URL, url
        );
    }

    private static Map<String, String> withQueryValue(Map<String, String> suggestion, String queryValue)
    {
        if (StringUtils.isEmpty(queryValue)) {
            return suggestion;
        }
        return Map.of(VALUE, suggestion.get(VALUE), LABEL, suggestion.get(LABEL), URL, suggestion.get(URL),
            QUERY_VALUE, queryValue);
    }

    private static Map<String, String> withHint(Map<String, String> suggestion, String hint)
    {
        if (StringUtils.isEmpty(hint)) {
            return suggestion;
        }
        Map<String, String> suggestionWithHint = new HashMap<>(suggestion);
        suggestionWithHint.put(HINT, hint);
        return suggestionWithHint;
    }

    private Map<String, String> createSuggestion(String value, String label, String url, String hint)
    {
        return Map.of(
            VALUE, value,
            LABEL, label,
            URL, url,
            HINT, hint
        );
    }
}
