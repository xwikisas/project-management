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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @since 1.4.0
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
        WorkItem.KEY_LABELS, WorkItem.KEY_CLOSE_DATE, WorkItem.KEY_MILESTONES, WorkItem.KEY_CLOSED_BY, WorkItem.KEY_KEY,
        "_actions");

    /**
     * The fields referencing other resources that can't be filtered, at least in the global work package queries.
     */
    private static final Set<String> LINK_FIELDS = Set.of("category", "parent", "responsible", FIELD_VERSION,
        FIELD_TARGET_VERSIONS, "observedInVersions", "budget", "sharedWithUsers", "projectPhase");

    /**
     * The generic work item properties that OpenProject can sort on (see {@link OpenProjectMapper#KEY_MAPPING} for the
     * matching OpenProject columns).
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_TYPE,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_START_DATE, WorkItem.KEY_DUE_DATE, WorkItem.KEY_PROGRESS,
        WorkItem.KEY_CREATION_DATE, WorkItem.KEY_UPDATE_DATE, WorkItem.KEY_CREATOR, WorkItem.KEY_REPORTER,
        WorkItem.KEY_ASSIGNEES, WorkItem.KEY_PRIORITY, WorkItem.KEY_PROJECT, WorkItem.KEY_STATUS);

    /**
     * The generic work item properties that OpenProject can sort on, their OpenProject columns, and the OpenProject
     * fields already exposed through the generic properties (e.g. {@code assignee}).
     */
    private static final Set<String> SORTABLE_COLUMNS = Stream.of(SORTABLE_PROPERTIES.stream(),
        SORTABLE_PROPERTIES.stream().map(OpenProjectMapper::mapLivedataProperty), MAPPED_FIELDS.stream())
        .flatMap(Function.identity()).collect(Collectors.toSet());

    /**
     * The generic work item properties that OpenProject can filter on (see {@link OpenProjectMapper#KEY_MAPPING} for
     * the matching OpenProject filters).
     */
    private static final Set<String> FILTERABLE_PROPERTIES = Set.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_TYPE,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_DESCRIPTION, WorkItem.KEY_START_DATE, WorkItem.KEY_DUE_DATE,
        WorkItem.KEY_DATE_RANGE, WorkItem.KEY_PROGRESS, WorkItem.KEY_CREATION_DATE, WorkItem.KEY_UPDATE_DATE,
        WorkItem.KEY_CREATOR, WorkItem.KEY_REPORTER, WorkItem.KEY_ASSIGNEES, WorkItem.KEY_PRIORITY,
        WorkItem.KEY_PROJECT, WorkItem.KEY_STATUS);

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectWorkItemFields.class);

    private static final String LIST_VALUE_TYPE_PREFIX = "[]";

    /**
     * The OpenProject filter used by its own work package pickers, which matches the work packages whose subject, or
     * the name of whose project, type or status, contains the text, and, on the instances using project-based
     * semantic identifiers, the work packages whose key starts with the text.
     */
    private static final String TYPEAHEAD_FILTER = "typeahead";

    private static final String TYPEAHEAD_OPERATOR = "**";

    private static final String CONTAINS_OPERATOR = "contains";

    /**
     * A work package key (e.g. {@code PROJ-42}), or the beginning of one up to its dash (e.g. {@code PROJ-}), as typed
     * when searching a work package.
     */
    private static final Pattern WORK_PACKAGE_KEY_SEARCH = Pattern.compile("[A-Za-z][A-Za-z0-9_]*-\\d*");

    private static final OpenProjectWorkItemFields EMPTY = new OpenProjectWorkItemFields(Collections.emptyList());

    /**
     * The fields, keyed by the id of the work item property exposing them.
     */
    private final Map<String, WorkPackageField> fields = new LinkedHashMap<>();

    /**
     * The fields already exposed through the generic work item properties, keyed by OpenProject filter, used to know
     * which operators their filters support.
     */
    private final Map<String, WorkPackageField> mappedFields = new HashMap<>();

    /**
     * The keys of the fields of the instance that can be sorted.
     */
    private final Set<String> sortableFieldKeys = new HashSet<>();

    /**
     * Whether the fields of the instance are known, i.e. whether they could be retrieved.
     */
    private final boolean known;

    /**
     * The keys of the filters of the instance, or {@code null} if they are unknown, since they couldn't be retrieved.
     */
    private final Set<String> filterKeys;

    /**
     * Creates the work item properties exposing the given fields.
     *
     * @param workPackageFields the fields of the work packages of an OpenProject instance
     */
    public OpenProjectWorkItemFields(List<WorkPackageField> workPackageFields)
    {
        this(workPackageFields, null);
    }

    /**
     * Creates the work item properties exposing the given fields.
     *
     * @param workPackageFields the fields of the work packages of an OpenProject instance
     * @param workPackageFilters the keys of the filters available to the work package queries of the instance, or
     *     {@code null} if they are unknown
     * @since 1.4.1
     */
    public OpenProjectWorkItemFields(List<WorkPackageField> workPackageFields, Collection<String> workPackageFilters)
    {
        this.known = !workPackageFields.isEmpty();
        this.filterKeys = toFilterKeys(workPackageFilters);
        List<WorkPackageField> customFields = new ArrayList<>();
        for (WorkPackageField field : workPackageFields) {
            if (field.isSortable()) {
                this.sortableFieldKeys.add(field.getKey());
            }
            if (field.isCustomField()) {
                customFields.add(field);
            } else if (TARGET_VERSION_FIELDS.contains(field.getKey())) {
                this.fields.putIfAbsent(PROPERTY_TARGET_VERSION, field);
            } else if (!MAPPED_FIELDS.contains(field.getKey())) {
                this.fields.putIfAbsent(field.getKey(), field);
            } else if (field.getFilterKey() != null) {
                this.mappedFields.put(field.getFilterKey(), field);
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
        return new OpenProjectWorkItemFields(workPackageFields.getItems(), getFilters(client));
    }

    private static Set<String> toFilterKeys(Collection<String> workPackageFilters)
    {
        return workPackageFilters == null || workPackageFilters.isEmpty() ? null : Set.copyOf(workPackageFilters);
    }

    private static List<String> getFilters(OpenProjectApiClient client)
    {
        try {
            return client.getWorkPackageFilters();
        } catch (ProjectManagementException | UnsupportedOperationException e) {
            // The filters are then all sent to OpenProject, as they are.
            LOGGER.debug("Failed to retrieve the work package filters: {}", ExceptionUtils.getRootCauseMessage(e));
            return null;
        }
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
     * @param field a work package field
     * @return the Live Data filter matching the values of the field (e.g. {@link OpenProjectMapper#FILTER_TEXT}), or
     *     {@code null} if the field values can't be filtered on, e.g. the dates, which would need to be converted to
     *     the format of OpenProject
     */
    public static String getFilterType(WorkPackageField field)
    {
        String valueType = StringUtils.defaultString(field.getFilterValueType());
        String filterType = null;
        if (valueType.startsWith(LIST_VALUE_TYPE_PREFIX)) {
            filterType = OpenProjectMapper.FILTER_LIST;
        } else if (valueType.endsWith("String")) {
            filterType = OpenProjectMapper.FILTER_TEXT;
        } else if (valueType.endsWith("Integer") || valueType.endsWith("Float")) {
            filterType = OpenProjectMapper.FILTER_NUMBER;
        } else if (valueType.endsWith("Boolean")) {
            filterType = OpenProjectMapper.FILTER_BOOLEAN;
        }
        return filterType;
    }

    /**
     * Rewrites the filters on the properties exposing the fields of this instance into filters on the matching
     * OpenProject filters, the other filters being kept as they are. When the filters of the instance are known, the
     * filters on properties that are neither generic work item properties nor OpenProject filters (e.g. a typo in the
     * filters parameter of a macro) are dropped, since OpenProject rejects them, failing the whole request. The
     * versions can be referenced by name, e.g. {@code 1.2.3}: a name is replaced by the ids of all the versions with
     * this name, which the other filters (e.g. on the project) can narrow down.
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
            if (isWorkPackageKeySearch(filter)) {
                mappedFilters.add(new LiveDataQuery.Filter(TYPEAHEAD_FILTER, TYPEAHEAD_OPERATOR,
                    ((String) filter.getConstraints().get(0).getValue()).trim()));
            } else if (field == null) {
                maybeAddGenericPropertyFilter(filter, mappedFilters);
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
     * @return whether the property is a generic work item property that OpenProject filters on, or an OpenProject
     *     filter of the instance (e.g. {@code subprojectId}, {@code updatedAt} or {@code customField3}), or whether
     *     this can't be known, since the filters of the instance couldn't be retrieved
     */
    private boolean isFilter(String property)
    {
        if (this.filterKeys == null) {
            return true;
        }
        return property != null && (FILTERABLE_PROPERTIES.contains(property) || this.filterKeys.contains(property)
            || this.filterKeys.contains(OpenProjectMapper.mapLivedataProperty(property)));
    }

    private void maybeAddGenericPropertyFilter(LiveDataQuery.Filter filter, List<LiveDataQuery.Filter> mappedFilters)
    {
        if (isFilter(filter.getProperty())) {
            mappedFilters.add(mapGenericPropertyFilter(filter));
        } else {
            LOGGER.debug("Ignoring the filter on the unknown work item property [{}].", filter.getProperty());
        }
    }

    /**
     * A search of the work packages whose summary contains a work package key (e.g. {@code PROJ-42}) is meant to find
     * the work package with this key: the subject filter of OpenProject only matches the subject, so the search is
     * done with the filter of the OpenProject work package pickers, which also matches the keys (by prefix, e.g.
     * {@code PROJ-4} matches {@code PROJ-42}). Besides the work packages whose subject contains the text, this filter
     * also matches those whose project, type or status name contains it, which is unlikely for a text shaped like a
     * key.
     */
    private static boolean isWorkPackageKeySearch(LiveDataQuery.Filter filter)
    {
        if (!WorkItem.KEY_SUMMARY.equals(filter.getProperty()) || filter.getConstraints().size() != 1) {
            return false;
        }
        LiveDataQuery.Constraint constraint = filter.getConstraints().get(0);
        return CONTAINS_OPERATOR.equals(constraint.getOperator()) && constraint.getValue() instanceof String
            && WORK_PACKAGE_KEY_SEARCH.matcher(((String) constraint.getValue()).trim()).matches();
    }

    /**
     * Rewrites the sort entries on the properties exposing the fields of this instance into sort entries on the
     * matching OpenProject columns. The sort entries on fields that can't be sorted are dropped. When the fields of the
     * instance are known, the sort entries on unknown properties (e.g. a typo in the sort parameter of a macro) are
     * dropped too, since OpenProject rejects them, failing the whole request. A sort entry can also name an
     * OpenProject column (e.g. {@code updatedAt} or {@code customField3}).
     *
     * @param sortEntries the sort entries on the work item properties
     * @return the rewritten sort entries
     */
    public List<LiveDataQuery.SortEntry> mapSortEntries(List<LiveDataQuery.SortEntry> sortEntries)
    {
        List<LiveDataQuery.SortEntry> mappedSortEntries = new ArrayList<>();
        for (LiveDataQuery.SortEntry sortEntry : sortEntries) {
            WorkPackageField field = this.fields.get(sortEntry.getProperty());
            if (!isSortable(sortEntry.getProperty())) {
                LOGGER.debug("Ignoring the sort on the work item property [{}], which can't be sorted.",
                    sortEntry.getProperty());
            } else if (field == null) {
                mappedSortEntries.add(sortEntry);
            } else {
                mappedSortEntries.add(new LiveDataQuery.SortEntry(field.getKey(), sortEntry.isDescending()));
            }
        }
        return mappedSortEntries;
    }

    /**
     * Tells whether the work items can be sorted on a property, i.e. whether {@link #mapSortEntries(List)} keeps the
     * sort entries on it.
     *
     * @param property a work item property, or an OpenProject column
     * @return {@code true} if OpenProject can sort on the property, or if this can't be known, since the fields of the
     *     instance couldn't be retrieved
     * @since 1.4.1
     */
    public boolean isSortable(String property)
    {
        if (property == null) {
            return false;
        }
        WorkPackageField field = this.fields.get(property);
        return field == null ? isSortableColumn(property) : field.isSortable();
    }

    /**
     * @return whether the property is a generic work item property that OpenProject sorts on, or an OpenProject
     *     column (of a generic property or of a field of this instance), or whether this can't be known, since the
     *     fields of the instance couldn't be retrieved
     */
    private boolean isSortableColumn(String property)
    {
        return !this.known || SORTABLE_COLUMNS.contains(property) || this.sortableFieldKeys.contains(property);
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

    /**
     * Replaces the generic Live Data operators of a filter on a generic work item property (e.g. the status) by the
     * OpenProject operators that the matching OpenProject filter supports. The filters on dates are converted later
     * on, their values depending on the generic operator, as the filters on the properties without matching field.
     */
    private LiveDataQuery.Filter mapGenericPropertyFilter(LiveDataQuery.Filter filter)
    {
        WorkPackageField field = this.mappedFields.get(OpenProjectMapper.mapLivedataProperty(filter.getProperty()));
        String filterType = field == null ? null : getFilterType(field);
        if (filterType == null) {
            return filter;
        }
        LiveDataQuery.Filter mappedFilter = new LiveDataQuery.Filter();
        mappedFilter.setProperty(filter.getProperty());
        mappedFilter.setMatchAll(filter.isMatchAll());
        for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
            LiveDataQuery.Constraint mappedConstraint = OpenProjectMapper.mapConstraint(filterType, constraint,
                field.getFilterOperators(), filter.getProperty());
            if (mappedConstraint != null) {
                mappedFilter.getConstraints().add(mappedConstraint);
            }
        }
        return mappedFilter;
    }

    private static LiveDataQuery.Filter mapFilter(LiveDataQuery.Filter filter, WorkPackageField field,
        Map<String, List<String>> versionIdsByName)
    {
        LiveDataQuery.Filter mappedFilter = new LiveDataQuery.Filter();
        mappedFilter.setProperty(StringUtils.defaultIfEmpty(field.getFilterKey(), field.getKey()));
        mappedFilter.setMatchAll(filter.isMatchAll());
        String filterType = getFilterType(field);
        for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
            LiveDataQuery.Constraint mappedConstraint = OpenProjectMapper.mapConstraint(filterType, constraint,
                field.getFilterOperators(), filter.getProperty());
            if (mappedConstraint == null) {
                continue;
            }
            for (Object value : getValues(mappedConstraint.getValue(), versionIdsByName)) {
                mappedFilter.getConstraints()
                    .add(new LiveDataQuery.Constraint(value, mappedConstraint.getOperator()));
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
