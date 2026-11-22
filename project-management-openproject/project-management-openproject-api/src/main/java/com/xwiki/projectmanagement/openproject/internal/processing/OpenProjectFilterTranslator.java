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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * Translates the generic Live Data filters on work item properties into OpenProject filters, for the work package
 * fields of an OpenProject instance. This is the only place where the work item properties, the Live Data operators
 * and the filter values are converted to OpenProject:
 * <ol>
 * <li>the filters on the properties exposing the fields of the instance (for example the target version or a custom
 * field) become filters on the matching OpenProject filters, their generic operators being replaced by the
 * OpenProject operators that the field supports, and the version names by the ids of the versions with this name;</li>
 * <li>a search of the work packages whose summary contains a work package key (for example {@code PROJ-42}) becomes a
 * search with the filter of the OpenProject work package pickers, which also matches the keys;</li>
 * <li>when the filters of the instance are known, the filters on properties that are neither generic work item
 * properties nor OpenProject filters (for example a typo in the filters parameter of a macro) are dropped, since
 * OpenProject rejects them, failing the whole request;</li>
 * <li>the generic operators left (for example on the filters of the generic properties, whose field is unknown) are
 * replaced by the closest OpenProject operator for the type of the property, the constraints without value are
 * dropped (unless their operator takes none), the dates are converted to OpenProject intervals and the generic
 * properties renamed after their OpenProject filter (for example {@code summary} to {@code subject}).</li>
 * </ol>
 * The second operator mapping only affects the operators left generic by the first one, the OpenProject operators
 * being kept as they are.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class OpenProjectFilterTranslator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectFilterTranslator.class);

    /**
     * The OpenProject filter used by its own work package pickers, which matches the work packages whose subject, or
     * the name of whose project, type or status, contains the text, and, on the instances using project-based
     * semantic identifiers, the work packages whose key starts with the text.
     */
    private static final String TYPEAHEAD_FILTER = "typeahead";

    private static final String TYPEAHEAD_OPERATOR = "**";

    /**
     * The values of the boolean OpenProject filters, by Live Data value.
     */
    private static final Map<String, String> BOOLEAN_VALUES = Map.of("true", String.valueOf('t'), "false", "f");

    /**
     * A work package key (for example {@code PROJ-42}), or the beginning of one up to its dash (for example
     * {@code PROJ-}), as typed when searching a work package.
     */
    private static final Pattern WORK_PACKAGE_KEY_SEARCH = Pattern.compile("[A-Za-z][A-Za-z0-9_]*-\\d*");

    private static final String OP_BETWEEN = "<>d";

    private static final String OP_EQUALS = "=";

    private static final String OP_CONTAINS = "~";

    private static final String OP_EMPTY = "!*";

    private static final String OPERATOR_EQUALS = "equals";

    private static final String OPERATOR_CONTAINS = "contains";

    private static final String OPERATOR_STARTS_WITH = "startsWith";

    private static final String OPERATOR_EMPTY = "empty";

    private static final String OPERATOR_LESS = "less";

    private static final String OPERATOR_GREATER = "greater";

    private static final String OPERATOR_BETWEEN = "between";

    private static final String OPERATOR_BEFORE = "before";

    private static final String OPERATOR_AFTER = "after";

    /**
     * The OpenProject operators replacing the generic Live Data operators whatever the type of the property.
     */
    private static final Map<String, String> OPERATOR_MAPPING = Map.of(OPERATOR_EQUALS, OP_EQUALS, OPERATOR_CONTAINS,
        OP_CONTAINS, OPERATOR_BETWEEN, OP_BETWEEN, OPERATOR_EMPTY, OP_EMPTY, OPERATOR_BEFORE, OP_BETWEEN,
        OPERATOR_AFTER, OP_BETWEEN);

    /**
     * The OpenProject operators replacing the generic operators that the Live Data offers on every filter of a type
     * (for example "starts with" on all the text filters), whatever the operators of the property, by type of filter.
     * The Live Data merges these generic operators with the ones of the property, and a source can't remove them (see
     * XWIKI-18274: The Advanced Filtering panel shows operators that are not supported by the live data source). A list
     * filter matches the values picked in a list, so whether it "contains" or "starts with" a value, it equals it.
     * OpenProject has no prefix match on texts, so "starts with" is the closest: "contains".
     */
    private static final Map<String, Map<String, String>> GENERIC_OPERATORS = Map.of(
        OpenProjectMapper.FILTER_LIST, Map.of(OPERATOR_EQUALS, OP_EQUALS, OPERATOR_CONTAINS, OP_EQUALS,
            OPERATOR_STARTS_WITH, OP_EQUALS, OPERATOR_EMPTY, OP_EMPTY),
        OpenProjectMapper.FILTER_TEXT, Map.of(OPERATOR_EQUALS, OP_EQUALS, OPERATOR_CONTAINS, OP_CONTAINS,
            OPERATOR_STARTS_WITH, OP_CONTAINS, OPERATOR_EMPTY, OP_EMPTY),
        OpenProjectMapper.FILTER_NUMBER, Map.of(OPERATOR_EQUALS, OP_EQUALS, OPERATOR_LESS, "<=", OPERATOR_GREATER,
            ">=", OPERATOR_EMPTY, OP_EMPTY),
        OpenProjectMapper.FILTER_BOOLEAN, Map.of(OPERATOR_EQUALS, OP_EQUALS),
        OpenProjectMapper.FILTER_DATE, Map.of(OPERATOR_BETWEEN, OP_BETWEEN, OPERATOR_BEFORE, OP_BETWEEN,
            OPERATOR_AFTER, OP_BETWEEN, OPERATOR_CONTAINS, OP_BETWEEN, OPERATOR_EMPTY, OP_EMPTY));

    private static final Set<String> PROPERTIES_DATE = Set.of(WorkItem.KEY_START_DATE, WorkItem.KEY_DUE_DATE,
        WorkItem.KEY_CREATION_DATE, WorkItem.KEY_UPDATE_DATE, WorkItem.KEY_DATE_RANGE);

    /**
     * The operators that take no value, whose constraints are kept even without value.
     */
    private static final Set<String> OPERATORS_EMPTY_VALS =
        Set.of("t", "w", "*", OP_EMPTY, String.valueOf('o'), "c", OPERATOR_EMPTY);

    private static final Set<String> LIST_FILTERS =
        Set.of(WorkItem.KEY_STATUS, WorkItem.KEY_IDENTIFIER, WorkItem.KEY_ASSIGNEES, WorkItem.KEY_CREATOR,
            WorkItem.KEY_PRIORITY, WorkItem.KEY_TYPE, WorkItem.KEY_REPORTER, WorkItem.KEY_PROJECT);

    private static final Set<String> TEXT_FILTERS = Set.of(WorkItem.KEY_SUMMARY, WorkItem.KEY_DESCRIPTION);

    /**
     * The OpenProject operators of the filters on the subject and on the description, which can't match a text
     * exactly.
     */
    private static final Set<String> TEXT_FILTER_OPERATORS = Set.of(OP_CONTAINS, "!~");

    private final OpenProjectWorkItemFields fields;

    private final OpenProjectApiClient client;

    /**
     * @param fields the work package fields of the instance, or {@link OpenProjectWorkItemFields#empty()} to translate
     *     only the filters on the generic work item properties
     * @param client the client of the instance, used to resolve the names of the versions, which is called only when
     *     a filter on a version field has a version name; may be {@code null} when {@code fields} has no version field
     */
    public OpenProjectFilterTranslator(OpenProjectWorkItemFields fields, OpenProjectApiClient client)
    {
        this.fields = fields;
        this.client = client;
    }

    /**
     * Translates filters into OpenProject filters.
     *
     * @param filters the filters on the work item properties, for example a filter on {@code status} with the
     *     constraint {@code equals 1}
     * @return the OpenProject filters, for example {@code [{"status":{"operator":"=","values":["1"]}}]} once
     *     serialized
     * @throws ProjectManagementException if a filter names a version and the versions of the instance couldn't be
     *     retrieved
     */
    public OpenProjectFilters translate(List<LiveDataQuery.Filter> filters) throws ProjectManagementException
    {
        return toOpenProjectFilters(mapFilters(withTextValues(filters)));
    }

    /**
     * Translates filters that aren't on work packages, for example the filters on the projects, which only need their
     * generic operators and their properties to be converted to OpenProject: the work package fields, the work package
     * key searches and the unknown properties are left aside.
     *
     * @param filters the filters, for example a filter on {@code name} with the constraint {@code contains xwiki}
     * @return the OpenProject filters, for example {@code [{"name":{"operator":"~","values":["xwiki"]}}]} once
     *     serialized
     */
    public static OpenProjectFilters translateOtherFilters(List<LiveDataQuery.Filter> filters)
    {
        return toOpenProjectFilters(withTextValues(filters));
    }

    /**
     * Converts a value written as a JSON number or boolean into text, which is how OpenProject reads the values of its
     * filters: the filters written by hand often hold numbers, OpenProject ids being numbers (for example
     * {@code "value":14} instead of {@code "value":"14"}).
     *
     * @param value the value of a filter constraint
     * @return the value as text if it's a number or a boolean, else the value itself
     */
    static Object toTextValue(Object value)
    {
        return value instanceof Number || value instanceof Boolean ? String.valueOf(value) : value;
    }

    private static List<LiveDataQuery.Filter> withTextValues(List<LiveDataQuery.Filter> filters)
    {
        boolean hasNonTextValue = filters.stream().flatMap(filter -> filter.getConstraints().stream())
            .anyMatch(constraint -> toTextValue(constraint.getValue()) != constraint.getValue());
        if (!hasNonTextValue) {
            return filters;
        }
        List<LiveDataQuery.Filter> result = new ArrayList<>();
        for (LiveDataQuery.Filter filter : filters) {
            LiveDataQuery.Filter textFilter = new LiveDataQuery.Filter();
            textFilter.setProperty(filter.getProperty());
            textFilter.setMatchAll(filter.isMatchAll());
            for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
                textFilter.getConstraints().add(
                    new LiveDataQuery.Constraint(toTextValue(constraint.getValue()), constraint.getOperator()));
            }
            result.add(textFilter);
        }
        return result;
    }

    private static OpenProjectFilters toOpenProjectFilters(List<LiveDataQuery.Filter> filters)
    {
        List<Map.Entry<String, Map<String, List<String>>>> openProjectFilters = new ArrayList<>();
        for (LiveDataQuery.Filter filter : filters) {
            List<LiveDataQuery.Constraint> validConstraints = getValidConstraints(filter);
            if (!validConstraints.isEmpty()) {
                openProjectFilters.add(new AbstractMap.SimpleImmutableEntry<>(
                    OpenProjectMapper.mapLivedataProperty(filter.getProperty()),
                    Collections.unmodifiableMap(toOpenProjectConstraints(filter.getProperty(), validConstraints))));
            }
        }
        return new OpenProjectFilters(openProjectFilters);
    }

    /**
     * Rewrites the filters on the properties exposing the fields of the instance into filters on the matching
     * OpenProject filters, the other filters being kept as they are, unless they are unknown.
     */
    private List<LiveDataQuery.Filter> mapFilters(List<LiveDataQuery.Filter> filters)
        throws ProjectManagementException
    {
        List<LiveDataQuery.Filter> mappedFilters = new ArrayList<>();
        Map<String, List<String>> versionIdsByName = null;
        for (LiveDataQuery.Filter filter : filters) {
            WorkPackageField field = this.fields.getField(filter.getProperty());
            if (isWorkPackageKeySearch(filter)) {
                mappedFilters.add(new LiveDataQuery.Filter(TYPEAHEAD_FILTER, TYPEAHEAD_OPERATOR,
                    ((String) filter.getConstraints().get(0).getValue()).trim()));
            } else if (field == null) {
                maybeAddGenericPropertyFilter(filter, mappedFilters);
            } else {
                boolean isVersion = OpenProjectWorkItemFields.VERSION_FILTER_VALUE_TYPE.equals(
                    field.getFilterValueType());
                if (isVersion && versionIdsByName == null && hasVersionName(filter)) {
                    versionIdsByName = getVersionIdsByName();
                }
                mappedFilters.add(mapFilter(filter, field, isVersion ? versionIdsByName : null));
            }
        }
        return mappedFilters;
    }

    private void maybeAddGenericPropertyFilter(LiveDataQuery.Filter filter, List<LiveDataQuery.Filter> mappedFilters)
    {
        if (this.fields.isFilter(filter.getProperty())) {
            mappedFilters.add(mapGenericPropertyFilter(filter));
        } else {
            LOGGER.debug("Ignoring the filter on the unknown work item property [{}].", filter.getProperty());
        }
    }

    /**
     * A search of the work packages whose summary contains a work package key (for example {@code PROJ-42}) is meant
     * to find the work package with this key: the subject filter of OpenProject only matches the subject, so the
     * search is done with the filter of the OpenProject work package pickers, which also matches the keys (by prefix,
     * for example {@code PROJ-4} matches {@code PROJ-42}). Besides the work packages whose subject contains the text,
     * this filter also matches those whose project, type or status name contains it, which is unlikely for a text
     * shaped like a key.
     */
    private static boolean isWorkPackageKeySearch(LiveDataQuery.Filter filter)
    {
        if (!WorkItem.KEY_SUMMARY.equals(filter.getProperty()) || filter.getConstraints().size() != 1) {
            return false;
        }
        LiveDataQuery.Constraint constraint = filter.getConstraints().get(0);
        return OPERATOR_CONTAINS.equals(constraint.getOperator()) && constraint.getValue() instanceof String
            && WORK_PACKAGE_KEY_SEARCH.matcher(((String) constraint.getValue()).trim()).matches();
    }

    /**
     * Replaces the generic Live Data operators of a filter on a generic work item property (for example the status) by
     * the OpenProject operators that the matching OpenProject filter supports. The filters on dates are converted
     * later on, their values depending on the generic operator, as the filters on the properties without matching
     * field.
     */
    private LiveDataQuery.Filter mapGenericPropertyFilter(LiveDataQuery.Filter filter)
    {
        WorkPackageField field =
            this.fields.getMappedField(OpenProjectMapper.mapLivedataProperty(filter.getProperty()));
        String filterType = field == null ? null : OpenProjectWorkItemFields.getFilterType(field);
        if (filterType == null) {
            return filter;
        }
        LiveDataQuery.Filter mappedFilter = new LiveDataQuery.Filter();
        mappedFilter.setProperty(filter.getProperty());
        mappedFilter.setMatchAll(filter.isMatchAll());
        for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
            LiveDataQuery.Constraint mappedConstraint =
                mapConstraint(filterType, constraint, field.getFilterOperators(), filter.getProperty());
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
        String filterType = OpenProjectWorkItemFields.getFilterType(field);
        for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
            LiveDataQuery.Constraint mappedConstraint =
                mapConstraint(filterType, constraint, field.getFilterOperators(), filter.getProperty());
            if (mappedConstraint == null) {
                continue;
            }
            if (OpenProjectMapper.FILTER_BOOLEAN.equals(filterType) && mappedConstraint.getValue() instanceof String) {
                // OpenProject takes t and f, the Live Data (and the query) true and false.
                String value = (String) mappedConstraint.getValue();
                mappedConstraint = new LiveDataQuery.Constraint(
                    BOOLEAN_VALUES.getOrDefault(value.trim().toLowerCase(Locale.ROOT), value),
                    mappedConstraint.getOperator());
            }
            for (Object value : getVersionValues(mappedConstraint.getValue(), versionIdsByName)) {
                mappedFilter.getConstraints().add(new LiveDataQuery.Constraint(value, mappedConstraint.getOperator()));
            }
        }
        return mappedFilter;
    }

    private static boolean hasVersionName(LiveDataQuery.Filter filter)
    {
        return filter.getConstraints().stream().anyMatch(constraint -> constraint.getValue() instanceof String
            && !((String) constraint.getValue()).isEmpty() && !StringUtils.isNumeric((String) constraint.getValue()));
    }

    /**
     * @return the ids of the versions named by the value, or the value itself if it isn't a version name
     */
    private static List<Object> getVersionValues(Object value, Map<String, List<String>> idsByName)
    {
        if (idsByName != null && value instanceof String && !StringUtils.isNumeric((String) value)) {
            List<String> ids = idsByName.get(value);
            if (ids != null) {
                return new ArrayList<>(ids);
            }
        }
        return Collections.singletonList(value);
    }

    private Map<String, List<String>> getVersionIdsByName() throws ProjectManagementException
    {
        PaginatedResult<Version> versions = this.client.getVersions();
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

    private static List<LiveDataQuery.Constraint> getValidConstraints(LiveDataQuery.Filter filter)
    {
        return filter.getConstraints().stream()
            .filter(constraint -> {
                String constraintValue = (String) constraint.getValue();
                // Some operators ignore values: their constraints are valid whether the value is present or not.
                if (OPERATORS_EMPTY_VALS.contains(constraint.getOperator())) {
                    return true;
                }
                return constraintValue != null && !constraintValue.isEmpty();
            })
            .collect(Collectors.toList());
    }

    /**
     * @return the values of each OpenProject operator of the constraints, after replacing the generic operators left
     */
    private static Map<String, List<String>> toOpenProjectConstraints(String propertyName,
        List<LiveDataQuery.Constraint> constraints)
    {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String filterType = getFilterType(propertyName);
        Set<String> supportedOperators = TEXT_FILTERS.contains(propertyName) ? TEXT_FILTER_OPERATORS : null;
        for (LiveDataQuery.Constraint constraint : constraints) {
            LiveDataQuery.Constraint mappedConstraint =
                mapConstraint(filterType, constraint, supportedOperators, propertyName);
            if (mappedConstraint == null) {
                continue;
            }
            String operator = mappedConstraint.getOperator();
            List<String> values = toOpenProjectValues(propertyName, (String) mappedConstraint.getValue(),
                constraint.getOperator(), operator);
            result.computeIfAbsent(operator, key -> new ArrayList<>()).addAll(values);
        }
        return result;
    }

    /**
     * @return the Live Data filter of a generic work item property, as configured in the OpenProject Live Data
     *     configuration, or {@code null} for the other properties, for example the OpenProject filters on the work
     *     package fields, whose constraints already use OpenProject operators
     */
    private static String getFilterType(String propertyName)
    {
        String filterType = null;
        if (LIST_FILTERS.contains(propertyName)) {
            filterType = OpenProjectMapper.FILTER_LIST;
        } else if (PROPERTIES_DATE.contains(propertyName)) {
            filterType = OpenProjectMapper.FILTER_DATE;
        } else if (TEXT_FILTERS.contains(propertyName)) {
            filterType = OpenProjectMapper.FILTER_TEXT;
        } else if (WorkItem.KEY_PROGRESS.equals(propertyName)) {
            filterType = OpenProjectMapper.FILTER_NUMBER;
        }
        return filterType;
    }

    private static List<String> toOpenProjectValues(String propertyName, String values, String initialOperator,
        String operator)
    {
        if (!PROPERTIES_DATE.contains(propertyName)) {
            return Collections.singletonList(values);
        }
        // A range of dates is an ISO 8601 time interval. The other operators take a single date, which isn't split even
        // when it contains a slash (for example a date formatted as 2026/11/10).
        if (OP_BETWEEN.equals(operator)) {
            String[] parts = values.split("/");
            if (parts.length > 1) {
                return Arrays.asList(parts[0], parts[1]);
            }
        }
        if (initialOperator.equals(OPERATOR_BEFORE)) {
            return Arrays.asList("1970-01-01", values);
        } else if (initialOperator.equals(OPERATOR_AFTER)) {
            return Arrays.asList(values, "3000-01-01");
        }
        return Collections.singletonList(values);
    }

    /**
     * Converts a constraint of a Live Data filter into a constraint using an OpenProject operator. The generic Live
     * Data operators (for example {@code startsWith}) are replaced by the closest OpenProject operator for the type of
     * filter, the OpenProject operators being kept as they are.
     *
     * @param filterType the Live Data filter of the property (for example {@value OpenProjectMapper#FILTER_TEXT}), or
     *     {@code null} if unknown, in which case only the operators of {@link #OPERATOR_MAPPING} are converted
     * @param supportedOperators the OpenProject operators supported by the filter of the property, or {@code null}
     *     (or an empty collection) if unknown
     * @return the converted constraint, or {@code null} if the filter of the property supports no equivalent
     *     operator, in which case the constraint can't be applied
     */
    private static LiveDataQuery.Constraint mapConstraint(String filterType, LiveDataQuery.Constraint constraint,
        Collection<String> supportedOperators, String property)
    {
        String operator = constraint.getOperator();
        Map<String, String> genericOperators = filterType == null ? null : GENERIC_OPERATORS.get(filterType);
        if (genericOperators == null || !genericOperators.containsKey(operator)) {
            return new LiveDataQuery.Constraint(constraint.getValue(), OPERATOR_MAPPING.getOrDefault(operator,
                operator));
        }

        String mappedOperator =
            getSupportedOperator(filterType, operator, genericOperators.get(operator), supportedOperators);
        if (mappedOperator == null) {
            LOGGER.warn("Ignoring the [{}] constraint of the filter on [{}], since OpenProject supports no "
                + "equivalent operator for this property.", operator, property);
            return null;
        }
        return new LiveDataQuery.Constraint(mapValue(operator, constraint.getValue()), mappedOperator);
    }

    private static String getSupportedOperator(String filterType, String operator, String mappedOperator,
        Collection<String> supportedOperators)
    {
        if (supportedOperators == null || supportedOperators.isEmpty() || supportedOperators.contains(mappedOperator)) {
            return mappedOperator;
        }
        // Some text filters (for example the subject) can't match a text exactly, nor its beginning, only contain it.
        boolean matchesText = OPERATOR_EQUALS.equals(operator) || OPERATOR_STARTS_WITH.equals(operator);
        return OpenProjectMapper.FILTER_TEXT.equals(filterType) && matchesText
            && supportedOperators.contains(OP_CONTAINS) ? OP_CONTAINS : null;
    }

    /**
     * OpenProject compares the numbers inclusively only: a whole number lower than N is lower or equal to N - 1.
     */
    private static Object mapValue(String operator, Object value)
    {
        boolean less = OPERATOR_LESS.equals(operator);
        if ((less || OPERATOR_GREATER.equals(operator)) && value instanceof String) {
            String number = StringUtils.trim((String) value);
            try {
                return String.valueOf(Long.parseLong(number) + (less ? -1 : 1));
            } catch (NumberFormatException e) {
                // A decimal number is compared inclusively.
                return number;
            }
        }
        return value;
    }
}
