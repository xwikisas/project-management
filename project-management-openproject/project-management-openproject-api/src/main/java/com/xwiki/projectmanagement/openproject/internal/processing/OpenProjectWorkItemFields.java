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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * The work package fields of an OpenProject instance that have no equivalent in the generic {@link WorkItem} keys,
 * such as the target version, the category or the custom fields, exposed as additional work item properties.
 * <p>
 * Each field is identified by a work item property id that doesn't depend on the instance: the target version is
 * {@value #PROPERTY_TARGET_VERSION}, whatever the name of the field in the OpenProject version ({@code version} or
 * {@code targetVersions}), a custom field is identified by its name, since its key ({@code customFieldN}) differs
 * between instances, and the other fields by their key in the OpenProject API (e.g. {@code category}).
 *
 * @version $Id$
 * @since 1.3.1
 */
public final class OpenProjectWorkItemFields
{
    /**
     * The id of the work item property holding the target version(s) of the work package.
     */
    public static final String PROPERTY_TARGET_VERSION = "targetVersion";

    /**
     * The type of the values of a filter on versions.
     */
    public static final String VERSION_FILTER_VALUE_TYPE = "[]Version";

    /**
     * The field holding the target version of the work packages before OpenProject supported several target
     * versions.
     */
    private static final String FIELD_VERSION = "version";

    private static final String FIELD_TARGET_VERSIONS = "targetVersions";

    /**
     * The OpenProject fields that are already exposed through the generic work item properties.
     */
    private static final Set<String> MAPPED_FIELDS = Set.of("id", "project", "subject", "type", "status", "priority",
        "author", "assignee", "createdAt", "updatedAt", "startDate", "dueDate", "percentageDone");

    /**
     * The fields holding the target version of the work packages, depending on the OpenProject version.
     */
    private static final Set<String> TARGET_VERSION_FIELDS = Set.of(FIELD_VERSION, FIELD_TARGET_VERSIONS);

    /**
     * The property ids that a custom field can't use, since they identify other properties.
     */
    private static final Set<String> RESERVED_PROPERTIES = Set.of(WorkItem.KEY_TYPE, WorkItem.KEY_IDENTIFIER,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_DESCRIPTION, WorkItem.KEY_START_DATE, WorkItem.KEY_DUE_DATE,
        WorkItem.KEY_DATE_RANGE, WorkItem.KEY_PROGRESS, WorkItem.KEY_CREATION_DATE, WorkItem.KEY_UPDATE_DATE,
        WorkItem.KEY_CREATOR, WorkItem.KEY_ASSIGNEES, WorkItem.KEY_PRIORITY, WorkItem.KEY_PROJECT,
        WorkItem.KEY_STATUS, WorkItem.KEY_REPORTER, WorkItem.KEY_RESOLUTION, WorkItem.KEY_RESOLVED,
        WorkItem.KEY_LABELS, WorkItem.KEY_CLOSE_DATE, WorkItem.KEY_MILESTONES, WorkItem.KEY_CLOSED_BY, "_actions");

    /**
     * The fields referencing other resources that can't be filtered, at least in the global work package queries.
     */
    private static final Set<String> LINK_FIELDS = Set.of("category", "parent", "responsible", FIELD_VERSION,
        FIELD_TARGET_VERSIONS, "observedInVersions", "budget", "sharedWithUsers", "projectPhase");

    private static final String LIST_VALUE_TYPE_PREFIX = "[]";

    private static final String OPERATOR_CONTAINS = "contains";

    private static final String OPERATOR_EQUALS = "equals";

    private static final OpenProjectWorkItemFields EMPTY = new OpenProjectWorkItemFields(Collections.emptyList());

    /**
     * The fields, keyed by the id of the work item property exposing them.
     */
    private final Map<String, WorkPackageField> fields = new LinkedHashMap<>();

    /**
     * Creates the work item properties exposing the given fields.
     *
     * @param workPackageFields the fields of the work packages of an OpenProject instance
     */
    public OpenProjectWorkItemFields(List<WorkPackageField> workPackageFields)
    {
        List<WorkPackageField> customFields = new ArrayList<>();
        for (WorkPackageField field : workPackageFields) {
            if (field.isCustomField()) {
                customFields.add(field);
            } else if (TARGET_VERSION_FIELDS.contains(field.getKey())) {
                this.fields.putIfAbsent(PROPERTY_TARGET_VERSION, field);
            } else if (!MAPPED_FIELDS.contains(field.getKey())) {
                this.fields.putIfAbsent(field.getKey(), field);
            }
        }
        // The custom fields come last, so that a custom field named like another property can't hide it.
        for (WorkPackageField field : customFields) {
            String name = StringUtils.defaultIfBlank(field.getName(), field.getKey());
            String propertyId = RESERVED_PROPERTIES.contains(name) || this.fields.containsKey(name)
                ? field.getKey() : name;
            this.fields.putIfAbsent(propertyId, field);
        }
    }

    /**
     * Retrieves the work package fields of the instance used by the given client.
     *
     * @param client the client of an OpenProject instance
     * @return the work item properties exposing the fields of the instance
     * @throws ProjectManagementException if the fields couldn't be retrieved
     */
    public static OpenProjectWorkItemFields of(OpenProjectApiClient client) throws ProjectManagementException
    {
        PaginatedResult<WorkPackageField> workPackageFields = client.getWorkPackageFields();
        if (workPackageFields == null || workPackageFields.getItems() == null) {
            return EMPTY;
        }
        return new OpenProjectWorkItemFields(workPackageFields.getItems());
    }

    /**
     * @return work item properties exposing no field
     */
    public static OpenProjectWorkItemFields empty()
    {
        return EMPTY;
    }

    /**
     * @return the fields, keyed by the id of the work item property exposing them, in the order of the instance
     *     columns, the custom fields being last
     */
    public Map<String, WorkPackageField> getFields()
    {
        return Collections.unmodifiableMap(this.fields);
    }

    /**
     * Retrieves the values of the fields of a work package.
     *
     * @param workPackage the work package
     * @return the values of the fields that have one, keyed by work item property id: a list of {@link Linkable} for
     *     a field referencing other resources (see {@link #isLinkField(WorkPackageField)}), a {@link String} otherwise
     */
    public Map<String, Object> getValues(WorkPackage workPackage)
    {
        Map<String, Object> values = new HashMap<>();
        Map<String, Object> fieldValues = workPackage.getFieldValues();
        for (Map.Entry<String, WorkPackageField> field : this.fields.entrySet()) {
            Object value = fieldValues.get(field.getValue().getKey());
            if (value != null) {
                values.put(field.getKey(),
                    isLinkField(field.getValue()) ? toLinkables(value) : toText(value));
            }
        }
        return values;
    }

    /**
     * Tells whether a field references other resources, such as versions, users or the options of a list custom
     * field, in which case its values are displayed as a list of links (or of labels, for the resources that have
     * no page in OpenProject), instead of as text.
     *
     * @param field a work package field
     * @return {@code true} if the field references other resources
     */
    public static boolean isLinkField(WorkPackageField field)
    {
        return StringUtils.startsWith(field.getFilterValueType(), LIST_VALUE_TYPE_PREFIX)
            || LINK_FIELDS.contains(field.getKey());
    }

    /**
     * Rewrites the filters on the properties exposing the fields of this instance into filters on the matching
     * OpenProject filters, the other filters being kept as they are. The versions can be referenced by name, e.g.
     * {@code 1.2.3}: a name is replaced by the ids of all the versions with this name, which the other filters (e.g.
     * on the project) can narrow down.
     *
     * @param filters the filters on the work item properties
     * @param client the client used to resolve the names of the versions
     * @return the rewritten filters
     * @throws ProjectManagementException if the versions couldn't be retrieved
     */
    public List<LiveDataQuery.Filter> mapFilters(List<LiveDataQuery.Filter> filters, OpenProjectApiClient client)
        throws ProjectManagementException
    {
        List<LiveDataQuery.Filter> mappedFilters = new ArrayList<>();
        Map<String, List<String>> versionIdsByName = null;
        for (LiveDataQuery.Filter filter : filters) {
            WorkPackageField field = this.fields.get(filter.getProperty());
            if (field == null) {
                mappedFilters.add(filter);
            } else {
                boolean isVersion = VERSION_FILTER_VALUE_TYPE.equals(field.getFilterValueType());
                if (isVersion && versionIdsByName == null && hasVersionName(filter)) {
                    versionIdsByName = getVersionIdsByName(client);
                }
                mappedFilters.add(mapFilter(filter, field, isVersion ? versionIdsByName : null));
            }
        }
        return mappedFilters;
    }

    /**
     * Rewrites the sort entries on the properties exposing the fields of this instance into sort entries on the
     * matching OpenProject columns. The sort entries on fields that can't be sorted are dropped.
     *
     * @param sortEntries the sort entries on the work item properties
     * @return the rewritten sort entries
     */
    public List<LiveDataQuery.SortEntry> mapSortEntries(List<LiveDataQuery.SortEntry> sortEntries)
    {
        List<LiveDataQuery.SortEntry> mappedSortEntries = new ArrayList<>();
        for (LiveDataQuery.SortEntry sortEntry : sortEntries) {
            WorkPackageField field = this.fields.get(sortEntry.getProperty());
            if (field == null) {
                mappedSortEntries.add(sortEntry);
            } else if (field.isSortable()) {
                mappedSortEntries.add(new LiveDataQuery.SortEntry(field.getKey(), sortEntry.isDescending()));
            }
        }
        return mappedSortEntries;
    }

    private static List<Linkable> toLinkables(Object value)
    {
        if (value instanceof List) {
            return ((List<?>) value).stream()
                .map(element -> element instanceof Linkable ? (Linkable) element : new Linkable(toText(element), null))
                .collect(Collectors.toList());
        } else if (value instanceof Linkable) {
            return List.of((Linkable) value);
        }
        return List.of(new Linkable(String.valueOf(value), null));
    }

    private static String toText(Object value)
    {
        if (value instanceof List) {
            return ((List<?>) value).stream().map(OpenProjectWorkItemFields::toText)
                .collect(Collectors.joining(", "));
        } else if (value instanceof Linkable) {
            return ((Linkable) value).getValue();
        }
        return String.valueOf(value);
    }

    private static LiveDataQuery.Filter mapFilter(LiveDataQuery.Filter filter, WorkPackageField field,
        Map<String, List<String>> versionIdsByName)
    {
        boolean isList = StringUtils.startsWith(field.getFilterValueType(), LIST_VALUE_TYPE_PREFIX);
        LiveDataQuery.Filter mappedFilter = new LiveDataQuery.Filter();
        mappedFilter.setProperty(StringUtils.defaultIfEmpty(field.getFilterKey(), field.getKey()));
        mappedFilter.setMatchAll(filter.isMatchAll());
        for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
            // A list filter matches any of its values.
            String operator = isList && OPERATOR_CONTAINS.equals(constraint.getOperator())
                ? OPERATOR_EQUALS : constraint.getOperator();
            for (Object value : getValues(constraint.getValue(), versionIdsByName)) {
                mappedFilter.getConstraints().add(new LiveDataQuery.Constraint(value, operator));
            }
        }
        return mappedFilter;
    }

    private static boolean hasVersionName(LiveDataQuery.Filter filter)
    {
        return filter.getConstraints().stream().anyMatch(constraint -> constraint.getValue() instanceof String
            && !((String) constraint.getValue()).isEmpty() && !StringUtils.isNumeric((String) constraint.getValue()));
    }

    private static List<Object> getValues(Object value, Map<String, List<String>> idsByName)
    {
        if (idsByName != null && value instanceof String && !StringUtils.isNumeric((String) value)) {
            List<String> ids = idsByName.get(value);
            if (ids != null) {
                return new ArrayList<>(ids);
            }
        }
        return Collections.singletonList(value);
    }

    private static Map<String, List<String>> getVersionIdsByName(OpenProjectApiClient client)
        throws ProjectManagementException
    {
        PaginatedResult<Version> versions = client.getVersions();
        if (versions == null || versions.getItems() == null) {
            return Collections.emptyMap();
        }
        Set<String> seen = new HashSet<>();
        return versions.getItems().stream()
            .filter(version -> version.getId() != null && version.getName() != null
                && seen.add(String.valueOf(version.getId())))
            .collect(Collectors.groupingBy(Version::getName, LinkedHashMap::new,
                Collectors.mapping(version -> String.valueOf(version.getId()), Collectors.toList())));
    }
}
