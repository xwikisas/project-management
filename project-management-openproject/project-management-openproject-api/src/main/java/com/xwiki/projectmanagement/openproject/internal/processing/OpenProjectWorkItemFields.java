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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.WorkPackageFieldsResult;
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
     * The generic work item properties that OpenProject can sort on (see
     * {@link OpenProjectMapper#mapLivedataProperty(String)} for the matching OpenProject columns).
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
     * The generic work item properties that OpenProject can filter on (see
     * {@link OpenProjectMapper#mapLivedataProperty(String)} for the matching OpenProject filters).
     */
    private static final Set<String> FILTERABLE_PROPERTIES = Set.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_TYPE,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_DESCRIPTION, WorkItem.KEY_START_DATE, WorkItem.KEY_DUE_DATE,
        WorkItem.KEY_DATE_RANGE, WorkItem.KEY_PROGRESS, WorkItem.KEY_CREATION_DATE, WorkItem.KEY_UPDATE_DATE,
        WorkItem.KEY_CREATOR, WorkItem.KEY_REPORTER, WorkItem.KEY_ASSIGNEES, WorkItem.KEY_PRIORITY,
        WorkItem.KEY_PROJECT, WorkItem.KEY_STATUS);

    private static final String SUBPROJECT_FILTER = "subprojectId";

    private static final Pattern CUSTOM_FIELD_FILTER = Pattern.compile("customField\\d+");

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectWorkItemFields.class);

    private static final String LIST_VALUE_TYPE_PREFIX = "[]";

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
        // The filters are known when the client reads them along with the fields.
        List<String> filters = workPackageFields instanceof WorkPackageFieldsResult
            ? ((WorkPackageFieldsResult) workPackageFields).getFilterKeys() : null;
        return new OpenProjectWorkItemFields(workPackageFields.getItems(), filters);
    }

    private static Set<String> toFilterKeys(Collection<String> workPackageFilters)
    {
        return workPackageFilters == null || workPackageFilters.isEmpty() ? null : Set.copyOf(workPackageFilters);
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
     * @param property the id of a work item property, for example {@code targetVersion}
     * @return the field exposed by the property, or {@code null} if the property doesn't expose a field of this
     *     instance, for example a generic work item property
     */
    WorkPackageField getField(String property)
    {
        return this.fields.get(property);
    }

    /**
     * @param filterKey an OpenProject filter, for example {@code status}
     * @return the field of this instance already exposed through a generic work item property whose OpenProject
     *     filter is the given one, used to know which operators the filter supports, or {@code null} if unknown
     */
    WorkPackageField getMappedField(String filterKey)
    {
        return this.mappedFields.get(filterKey);
    }

    /**
     * @return whether the property is a generic work item property that OpenProject filters on, or an OpenProject
     *     filter of the instance (e.g. {@code subprojectId}, {@code updatedAt} or {@code customField3}), or whether
     *     this can't be known, since the filters of the instance couldn't be retrieved
     * @param property a work item property, or an OpenProject filter
     */
    boolean isFilter(String property)
    {
        if (this.filterKeys == null || isProjectFilter(property)) {
            return true;
        }
        return property != null && (FILTERABLE_PROPERTIES.contains(property) || this.filterKeys.contains(property)
            || this.filterKeys.contains(OpenProjectMapper.mapLivedataProperty(property)));
    }

    /**
     * @param property a work item property, or an OpenProject filter
     * @return whether the filters on the property are kept (see {@link OpenProjectFilterTranslator}): the property
     *     exposes a field of this instance, or is a filter (see {@link #isFilter(String)})
     */
    boolean isFilteredProperty(String property)
    {
        return getField(property) != null || isFilter(property);
    }

    /**
     * @param property a property that isn't filtered (see {@link #isFilteredProperty(String)}), for example
     *     {@code STATUS}
     * @return the filtered property having the same name ignoring the case, for example {@code status}, or
     *     {@code null} if there is none
     */
    String getFilteredPropertyIgnoringCase(String property)
    {
        return Stream.of(FILTERABLE_PROPERTIES, this.fields.keySet(),
                this.filterKeys == null ? Set.<String>of() : this.filterKeys)
            .flatMap(Set::stream).filter(candidate -> candidate.equalsIgnoreCase(property)).findFirst().orElse(null);
    }

    /**
     * @return whether the property may be an OpenProject filter that the queries of some projects have, but that the
     *     global queries, whose filters are known, don't: the subprojects filter, of the projects having subprojects,
     *     and the custom fields referencing users or versions, filtered by key (e.g. {@code customField3})
     */
    private static boolean isProjectFilter(String property)
    {
        return property != null && (SUBPROJECT_FILTER.equals(property)
            || CUSTOM_FIELD_FILTER.matcher(property).matches());
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
}
