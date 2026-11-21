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
package com.xwiki.projectmanagement.openproject.internal.processing;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.FilterBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;
import com.xwiki.projectmanagement.source.WorkItemName;

/**
 * Resolves the names written in the values of the filters on work items (the {@link WorkItemName} values read from a
 * macro content, for example the status {@code Closed} or the project {@code documentation-application}) into the ids
 * of the matching OpenProject resources, since the OpenProject API only takes ids in its filters. The rules are:
 * <ul>
 * <li>the names are matched exactly but ignoring the case: a project by its identifier, else by its name; a version by
 * its name, among the versions of the projects the filters are restricted to, or among all the versions when the
 * filters aren't restricted to some projects; a status, a type or a priority by its name; a user by its full name,
 * {@code me} (the current user) being kept as it is;</li>
 * <li>a name matching no resource, or several (except for the versions of several filtered projects), is an error,
 * naming the property and the value;</li>
 * <li>the names of the other properties (for example the text of a custom field) are kept as text, and the values
 * that aren't names (for example ids, dates or texts) are kept as they are.</li>
 * </ul>
 * The resources are retrieved at most once by resolver: a resolver is meant to be used for a single request, so that
 * a renamed resource, or a resource that another user can't see, is never matched from a stale lookup.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class OpenProjectFilterValueResolver
{
    private static final String ASCII_DIGITS = "0123456789";

    private static final String CURRENT_USER = "me";

    /**
     * The maximum number of projects or users whose name contains the searched name, among which the ones having
     * exactly this name are picked.
     */
    private static final int NAME_SEARCH_PAGE_SIZE = 1000;

    private static final String NAME_FILTER = "name";

    /**
     * The operators restricting the work items to the values of the constraint, whose project values restrict the
     * versions that a version name can match.
     */
    private static final Set<String> INCLUDING_OPERATORS = Set.of("=", "equals", "contains", "startsWith");

    private static final Map<String, Kind> PROPERTY_KINDS = Map.of(
        WorkItem.KEY_PROJECT, Kind.PROJECT,
        WorkItem.KEY_STATUS, Kind.STATUS,
        WorkItem.KEY_TYPE, Kind.TYPE,
        WorkItem.KEY_PRIORITY, Kind.PRIORITY,
        WorkItem.KEY_ASSIGNEES, Kind.USER,
        WorkItem.KEY_CREATOR, Kind.USER,
        WorkItem.KEY_REPORTER, Kind.USER);

    private static final Map<String, Kind> FILTER_VALUE_TYPE_KINDS = Map.of(
        OpenProjectWorkItemFields.VERSION_FILTER_VALUE_TYPE, Kind.VERSION,
        "[]User", Kind.USER);

    private final OpenProjectApiClient client;

    private final OpenProjectWorkItemFields fields;

    private final Map<Kind, List<? extends BaseOpenProjectObject>> resources = new HashMap<>();

    private final Map<String, List<Version>> projectVersions = new HashMap<>();

    private final Map<String, Project> projects = new HashMap<>();

    private final Map<String, String> resolvedUsers = new HashMap<>();

    /**
     * The kinds of OpenProject resources that a filter value can name.
     */
    private enum Kind
    {
        PROJECT("project", "projects"),
        VERSION("version", "versions"),
        STATUS("status", "statuses"),
        TYPE("type", "types"),
        PRIORITY("priority", "priorities"),
        USER("user", "users");

        private final String label;

        private final String pluralLabel;

        Kind(String label, String pluralLabel)
        {
            this.label = label;
            this.pluralLabel = pluralLabel;
        }
    }

    /**
     * @param client the client of the OpenProject instance holding the resources
     * @param fields the work package fields of the instance, telling which properties hold versions or users (for
     *     example a custom field of type version), or {@link OpenProjectWorkItemFields#empty()} to resolve only the
     *     values of the generic work item properties
     */
    public OpenProjectFilterValueResolver(OpenProjectApiClient client, OpenProjectWorkItemFields fields)
    {
        this.client = client;
        this.fields = fields;
    }

    /**
     * Resolves the names in the values of filters.
     *
     * @param filters the filters on work item properties, for example a filter on {@code status} with the constraint
     *     {@code = Closed}, {@code Closed} being a {@link WorkItemName}
     * @return the filters whose names are replaced by ids, for example a filter on {@code status} with the constraint
     *     {@code = 12}; a version name may be replaced by several ids, one for each filtered project having a version
     *     with this name, each in its own constraint; the names that aren't resolved are replaced by their text; the
     *     filters without name are returned as they are
     * @throws ProjectManagementException if a name matches no resource or several, or if the resources couldn't be
     *     retrieved
     */
    public List<LiveDataQuery.Filter> resolve(List<LiveDataQuery.Filter> filters) throws ProjectManagementException
    {
        // The project filters are resolved first, since they restrict the versions that a version name can match.
        Map<LiveDataQuery.Filter, LiveDataQuery.Filter> resolvedFilters = new HashMap<>();
        Set<String> projectIds = new LinkedHashSet<>();
        for (LiveDataQuery.Filter filter : filters) {
            if (getKind(filter.getProperty()) == Kind.PROJECT) {
                LiveDataQuery.Filter resolvedFilter = resolveFilter(filter, Kind.PROJECT, Collections.emptySet());
                resolvedFilters.put(filter, resolvedFilter);
                projectIds.addAll(getIncludedValues(resolvedFilter));
            }
        }
        List<LiveDataQuery.Filter> result = new ArrayList<>();
        for (LiveDataQuery.Filter filter : filters) {
            LiveDataQuery.Filter resolvedFilter = resolvedFilters.get(filter);
            if (resolvedFilter == null) {
                resolvedFilter = resolveFilter(filter, getKind(filter.getProperty()), projectIds);
            }
            result.add(resolvedFilter);
        }
        return result;
    }

    private Kind getKind(String property)
    {
        if (property == null) {
            return null;
        }
        Kind kind = PROPERTY_KINDS.get(property);
        if (kind == null) {
            WorkPackageField field = this.fields.getField(property);
            kind = field == null || field.getFilterValueType() == null ? null
                : FILTER_VALUE_TYPE_KINDS.get(field.getFilterValueType());
        }
        return kind;
    }

    private LiveDataQuery.Filter resolveFilter(LiveDataQuery.Filter filter, Kind kind, Set<String> projectIds)
        throws ProjectManagementException
    {
        if (filter.getConstraints().stream().noneMatch(constraint -> constraint.getValue() instanceof WorkItemName)) {
            return filter;
        }
        LiveDataQuery.Filter resolvedFilter = new LiveDataQuery.Filter();
        resolvedFilter.setProperty(filter.getProperty());
        resolvedFilter.setMatchAll(filter.isMatchAll());
        for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
            if (constraint.getValue() instanceof WorkItemName) {
                String name = ((WorkItemName) constraint.getValue()).getName();
                List<String> values = isKept(kind, name) ? Collections.singletonList(name)
                    : resolveValue(kind, filter.getProperty(), name, projectIds);
                for (String value : values) {
                    resolvedFilter.getConstraints().add(new LiveDataQuery.Constraint(value, constraint.getOperator()));
                }
            } else {
                resolvedFilter.getConstraints().add(constraint);
            }
        }
        return resolvedFilter;
    }

    /**
     * @param kind the kind of resources of the filter, or {@code null} if its values aren't resources
     * @return {@code true} if the name is kept as text: the names of the properties whose values aren't resources, and
     *     {@code me}, the current user
     */
    private static boolean isKept(Kind kind, String name)
    {
        return kind == null || (kind == Kind.USER && CURRENT_USER.equals(name.trim()));
    }

    private static boolean isId(String value)
    {
        return StringUtils.isNotEmpty(value) && StringUtils.containsOnly(value, ASCII_DIGITS);
    }

    private static Set<String> getIncludedValues(LiveDataQuery.Filter filter)
    {
        return filter.getConstraints().stream()
            .filter(constraint -> (constraint.getOperator() == null
                || INCLUDING_OPERATORS.contains(constraint.getOperator()))
                && constraint.getValue() instanceof String && isId((String) constraint.getValue()))
            .map(constraint -> (String) constraint.getValue())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @return the ids of the resources named by the value: a single one, except for a version name matching a
     *     version of several filtered projects
     */
    private List<String> resolveValue(Kind kind, String property, String value, Set<String> projectIds)
        throws ProjectManagementException
    {
        String name = StringUtils.trimToEmpty(value);
        switch (kind) {
            case PROJECT:
                return Collections.singletonList(String.valueOf(findProject(property, name).getId()));
            case USER:
                return Collections.singletonList(
                    getCached(this.resolvedUsers, name, () -> resolveUserName(property, name)));
            case VERSION:
                return resolveVersionName(property, name, projectIds);
            default:
                return Collections.singletonList(getSingleId(kind, property, name, getResources(kind)));
        }
    }

    /**
     * Finds a project by identifier, else by name. The identifiers being in lower case, the identifier is looked up as
     * written, then in lower case.
     */
    private Project findProject(String property, String name) throws ProjectManagementException
    {
        String key = name.toLowerCase(Locale.ROOT);
        Project project = this.projects.get(key);
        if (project == null) {
            WorkItemRetrievalException notFound = null;
            // OpenProject also finds a project by its id, which a name made of digits isn't: the project identifiers
            // can't be made of digits only.
            for (String identifier : isId(name) ? List.<String>of() : new LinkedHashSet<>(List.of(name, key))) {
                try {
                    project = this.client.getProject(identifier);
                    if (project != null) {
                        break;
                    }
                } catch (WorkItemRetrievalException e) {
                    // Not an identifier: the project may be named so.
                    if (!isNotFound(e)) {
                        throw e;
                    }
                    notFound = notFound == null ? e : notFound;
                }
            }
            if (project == null) {
                project = findProjectByName(property, name, notFound);
            }
            this.projects.put(key, project);
        }
        return project;
    }

    private Project findProjectByName(String property, String name, Throwable notFound)
        throws ProjectManagementException
    {
        List<Project> candidates = getItems(this.client.getProjects(1, NAME_SEARCH_PAGE_SIZE, getNameFilter(name)));
        List<Project> matches = candidates.stream().filter(candidate -> hasName(candidate, name))
            .collect(Collectors.toList());
        if (matches.isEmpty()) {
            throw notFound(Kind.PROJECT, property, name, notFound);
        }
        // Fails when several projects have this name.
        getSingleId(Kind.PROJECT, property, name, matches);
        return matches.get(0);
    }

    private static boolean isNotFound(WorkItemRetrievalException e)
    {
        return Integer.valueOf(HttpURLConnection.HTTP_NOT_FOUND).equals(e.getStatusCode());
    }

    private String resolveUserName(String property, String name) throws ProjectManagementException
    {
        return getSingleId(Kind.USER, property, name,
            getItems(this.client.getUsers(1, NAME_SEARCH_PAGE_SIZE, getNameFilter(name))));
    }

    private List<String> resolveVersionName(String property, String name, Set<String> projectIds)
        throws ProjectManagementException
    {
        if (projectIds.isEmpty()) {
            return Collections.singletonList(getSingleId(Kind.VERSION, property, name, getResources(Kind.VERSION)));
        }
        // Each project has its own versions, so the same name can designate a version of each filtered project. A
        // version shared with several of the filtered projects is matched once.
        Map<Integer, Version> versions = new LinkedHashMap<>();
        for (String projectId : projectIds) {
            for (Version version : getProjectVersions(projectId)) {
                if (hasName(version, name)) {
                    versions.putIfAbsent(version.getId(), version);
                }
            }
        }
        if (versions.isEmpty()) {
            if (!getMatches(name, getResources(Kind.VERSION)).isEmpty()) {
                throw new OpenProjectFilterValueException(String.format(
                    "The OpenProject version [%s] of the filter on [%s] isn't a version of the filtered projects %s.",
                    name, property, projectIds));
            }
            throw notFound(Kind.VERSION, property, name, null);
        }
        return versions.keySet().stream().map(String::valueOf).collect(Collectors.toList());
    }

    private List<Version> getProjectVersions(String projectId) throws ProjectManagementException
    {
        List<Version> versions = this.projectVersions.get(projectId);
        if (versions == null) {
            try {
                versions = getItems(this.client.getProjectVersions(Integer.parseInt(projectId)));
            } catch (NumberFormatException e) {
                // Too big to be the id of a project: the filtered project doesn't exist.
                versions = Collections.emptyList();
            }
            this.projectVersions.put(projectId, versions);
        }
        return versions;
    }

    private List<? extends BaseOpenProjectObject> getResources(Kind kind) throws ProjectManagementException
    {
        List<? extends BaseOpenProjectObject> kindResources = this.resources.get(kind);
        if (kindResources == null) {
            switch (kind) {
                case STATUS:
                    kindResources = getItems(this.client.getStatuses());
                    break;
                case TYPE:
                    kindResources = getItems(this.client.getTypes());
                    break;
                case PRIORITY:
                    kindResources = getItems(this.client.getPriorities());
                    break;
                default:
                    kindResources = getItems(this.client.getVersions());
                    break;
            }
            this.resources.put(kind, kindResources);
        }
        return kindResources;
    }

    private static String getSingleId(Kind kind, String property, String name,
        Collection<? extends BaseOpenProjectObject> candidates) throws OpenProjectFilterValueException
    {
        List<String> ids = getMatches(name, candidates);
        if (ids.isEmpty()) {
            throw notFound(kind, property, name, null);
        } else if (ids.size() > 1) {
            throw new OpenProjectFilterValueException(String.format(
                "The name [%s]%s matches several OpenProject %s, whose ids are %s: use one of these ids instead.",
                name, getFilterLabel(property), kind.pluralLabel, ids));
        }
        return ids.get(0);
    }

    private static List<String> getMatches(String name, Collection<? extends BaseOpenProjectObject> candidates)
    {
        return candidates.stream()
            .filter(candidate -> hasName(candidate, name))
            .map(candidate -> String.valueOf(candidate.getId()))
            .distinct()
            .collect(Collectors.toList());
    }

    private static boolean hasName(BaseOpenProjectObject resource, String name)
    {
        return resource.getId() != null && name.equalsIgnoreCase(StringUtils.trim(resource.getName()));
    }


    private static OpenProjectFilterValueException notFound(Kind kind, String property, String name, Throwable cause)
    {
        String message = String.format("The OpenProject %s [%s]%s doesn't exist or you are not allowed to view it.",
            kind.label, name, getFilterLabel(property));
        return cause == null ? new OpenProjectFilterValueException(message)
            : new OpenProjectFilterValueException(message, cause);
    }

    /**
     * @param property the property of the filter holding the value, or {@code null} when the value isn't in a filter
     *     (for example the project parameter of a macro)
     */
    private static String getFilterLabel(String property)
    {
        return property == null ? "" : String.format(" of the filter on [%s]", property);
    }

    private static String getNameFilter(String name)
    {
        return new FilterBuilder().addFilter(NAME_FILTER, FilterBuilder.Operator.CONTAINS, name).build();
    }

    private static <T> List<T> getItems(PaginatedResult<T> result)
    {
        return result == null || result.getItems() == null ? Collections.emptyList() : result.getItems();
    }

    private static String getCached(Map<String, String> cache, String name, Lookup lookup)
        throws ProjectManagementException
    {
        String key = name.toLowerCase(Locale.ROOT);
        String id = cache.get(key);
        if (id == null) {
            id = lookup.get();
            cache.put(key, id);
        }
        return id;
    }

    @FunctionalInterface
    private interface Lookup
    {
        String get() throws ProjectManagementException;
    }
}
