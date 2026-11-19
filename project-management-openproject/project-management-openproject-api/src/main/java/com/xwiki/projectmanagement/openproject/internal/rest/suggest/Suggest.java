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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import com.xwiki.projectmanagement.internal.rest.WikiRequestExecutor;
import com.xwiki.projectmanagement.openproject.FilterBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

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
                response =
                    getProjectsSuggestions(openProjectApiClient, lowerSearch, pageSize, selectedId, selectedItem);
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
        return getSuggestions(openProjectApiClient.getPriorities().getItems());
    }

    private List<Map<String, String>> getStatusesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        return getSuggestions(openProjectApiClient.getStatuses().getItems());
    }

    private List<Map<String, String>> getProjectsSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize, Integer selectedId, String selectedItem) throws ProjectManagementException
    {
        String filter = getSelectedIdFilter(selectedId, searchString);
        if (StringUtils.isNotEmpty(filter)) {
            return getSuggestions(openProjectApiClient.getProjects(1, pageSize, filter).getItems());
        }
        boolean selectedIdentifier = selectedItem != null && PROJECT_IDENTIFIER_PATTERN.matcher(selectedItem).matches();
        if (selectedIdentifier && StringUtils.isNotEmpty(searchString)) {
            return getProjectByIdentifier(openProjectApiClient, selectedItem);
        }
        // The value of the selected project is kept as it is given, so that the client can match it with the value
        // of its input, while the other projects are suggested by their id.
        return openProjectApiClient.getProjects(1, pageSize, buildFilter(NAME, searchString)).getItems().stream()
            .map(project -> createSuggestion(
                selectedIdentifier && selectedItem.equals(project.getIdentifier()) ? selectedItem
                    : String.valueOf(project.getId()),
                project.getName(),
                project.getSelf().getLocation()))
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getProjectByIdentifier(OpenProjectApiClient openProjectApiClient,
        String identifier)
    {
        try {
            Project project = openProjectApiClient.getProject(identifier);
            return List.of(createSuggestion(identifier, project.getName(), project.getSelf().getLocation()));
        } catch (ProjectManagementException e) {
            // The project may not exist anymore, or not be visible to the current user: the client keeps showing the
            // identifier.
            this.slf4Jlogger.debug("Failed to retrieve the project with identifier [{}]. Root cause: [{}]", identifier,
                ExceptionUtils.getRootCauseMessage(e));
            return Collections.emptyList();
        }
    }

    private List<Map<String, String>> getTypesSuggestions(OpenProjectApiClient openProjectApiClient)
        throws ProjectManagementException
    {
        return getSuggestions(openProjectApiClient.getTypes().getItems());
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
        if (StringUtils.isEmpty(filter)) {
            filter = buildFilter(NAME, searchString);
        }
        return getSuggestions(openProjectApiClient.getUsers(1, pageSize, filter).getItems());
    }

    private List<Map<String, String>> getVersionsSuggestions(OpenProjectApiClient openProjectApiClient,
        String searchString, int pageSize) throws ProjectManagementException
    {
        // The versions API can't be filtered on the name, but all the versions are retrieved at once and cached.
        return getSuggestions(openProjectApiClient.getVersions().getItems().stream()
            .filter(version -> version.getName() != null && version.getName().toLowerCase().contains(searchString))
            .limit(pageSize)
            .collect(Collectors.toList()));
    }

    private List<Map<String, String>> getSuggestions(
        List<? extends BaseOpenProjectObject> openProjectObjects)
    {
        return openProjectObjects
            .stream()
            .map(
                obj -> createSuggestion(
                    String.valueOf(obj.getId()),
                    obj.getName(),
                    obj.getSelf().getLocation()
                )
            )
            .collect(Collectors.toList());
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
