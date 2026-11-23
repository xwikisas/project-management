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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * The filters and the sorting of the link of an OpenProject work package list (its {@code query_props} parameter,
 * e.g. {@code {"f":[{"n":"status","o":"o","v":[]}],"t":"id:desc"}}), expressed as Live Data filters and sort entries,
 * so that the Live Data of a macro displaying the work packages of the link shows them, and its viewers can change
 * them. A filter is moved to the Live Data only when the column filter of its property offers its operator (see
 * {@link Columns}): the other filters (e.g. the {@code search} filter, or a relative date such as {@code >t-}, which the
 * Live Data date filter can't show) are left in the link, which keeps selecting the same work packages once the other
 * filters and the sorting are removed from it: each filter is applied once, either from the Live Data or from the link.
 * <p>
 * The filters keep their OpenProject operators (e.g. {@code o} for the open statuses), which the translation of the
 * Live Data filters (see {@link OpenProjectFilterTranslator}) keeps as they are, except the ranges of dates on the
 * generic work item properties, which become the {@code between} operator of the Live Data date filters. The operators
 * taking no value (e.g. {@code o}) get an empty value, as the same conditions written in the query of the macro
 * content: the Live Data shows them in its filter panel, not in the filter of the column, which only shows values.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class OpenProjectLinkQuery
{
    private static final String OP_BETWEEN = "<>d";

    private static final String OPERATOR_BETWEEN = "between";

    /**
     * The filters of the {@code query_props} parameter, which is also the false value of the boolean filters.
     */
    private static final String FILTERS = "f";

    /**
     * The sorting of the {@code query_props} parameter, which is also the true value of the boolean filters.
     */
    private static final String SORT = "t";

    private static final String SORT_SEPARATOR = ":";

    private static final String DESCENDING = "desc";

    /**
     * The characters that the {@code sort} parameter of the macros uses to separate the sort entries, and a property
     * from its direction, which a property can't contain.
     */
    private static final Pattern SORT_PARAMETER_SEPARATORS = Pattern.compile("[,:]");

    /**
     * The values of the boolean OpenProject filters, as written in the Live Data boolean filters.
     */
    private static final Map<String, String> BOOLEAN_VALUES = Map.of(SORT, String.valueOf(true), FILTERS,
        String.valueOf(false));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<LiveDataQuery.Filter> filters;

    private final List<LiveDataQuery.SortEntry> sortEntries;

    private final String link;

    private OpenProjectLinkQuery(List<LiveDataQuery.Filter> filters, List<LiveDataQuery.SortEntry> sortEntries,
        String link)
    {
        this.filters = Collections.unmodifiableList(filters);
        this.sortEntries = Collections.unmodifiableList(sortEntries);
        this.link = link;
    }

    /**
     * @param link a link given to an OpenProject macro
     * @param columnsSupplier supplies the columns of the Live Data of the macro; it is called only when the link has
     *     filters or sorting, since retrieving them may need requests to OpenProject
     * @return the filters and the sorting of the link, or {@code null} if it has none: the link of a single work
     *     package, of a saved query (whose filters are on OpenProject) or of a work package list without filter nor
     *     sorting in its {@code query_props} parameter
     * @throws IllegalArgumentException if the {@code query_props} parameter of the link can't be read
     */
    public static OpenProjectLinkQuery read(OpenProjectLink link, Supplier<Columns> columnsSupplier)
    {
        JsonNode properties = readQueryProperties(link);
        if (properties == null
            || (properties.path(FILTERS).isEmpty() && StringUtils.isBlank(properties.path(SORT).asText())))
        {
            return null;
        }
        Columns columns = columnsSupplier.get();
        List<LiveDataQuery.Filter> filters = new ArrayList<>();
        ArrayNode linkFilters = MAPPER.createArrayNode();
        for (JsonNode filter : properties.path(FILTERS)) {
            String property = columns.fields.getFilterProperty(filter.path("n").asText());
            String operator = filter.path("o").asText();
            if (columns.hasFilter(property, getLiveDataOperator(property, operator))) {
                filters.add(toLiveDataFilter(property, operator, filter.path("v"), columns.fields));
            } else {
                linkFilters.add(filter);
            }
        }
        List<LiveDataQuery.SortEntry> sortEntries = toSortEntries(properties.path(SORT).asText(), columns.fields);

        String linkQueryProperties = null;
        if (!linkFilters.isEmpty()) {
            ObjectNode linkProperties = MAPPER.createObjectNode();
            linkProperties.set(FILTERS, linkFilters);
            linkQueryProperties = linkProperties.toString();
        }
        return new OpenProjectLinkQuery(filters, sortEntries, link.withQueryProperties(linkQueryProperties));
    }

    /**
     * @return the operator of the Live Data filter expressing the OpenProject operator
     */
    private static String getLiveDataOperator(String property, String operator)
    {
        return OP_BETWEEN.equals(operator) && OpenProjectFilterTranslator.PROPERTIES_DATE.contains(property)
            ? OPERATOR_BETWEEN : operator;
    }

    /**
     * @return the {@code query_props} parameter of the link of a work package list, or {@code null} if the link has
     *     none or isn't the link of a work package list
     */
    private static JsonNode readQueryProperties(OpenProjectLink link)
    {
        String queryProperties = link.getWorkPackageId() != null || link.getSavedQueryId() != null ? null
            : link.getQueryProperties();
        if (queryProperties == null) {
            return null;
        }
        JsonNode properties;
        try {
            properties = MAPPER.readTree(queryProperties);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("The query_props parameter of the link isn't valid JSON.", e);
        }
        if (properties == null || !properties.isObject()) {
            throw new IllegalArgumentException("The query_props parameter of the link isn't a JSON object.");
        }
        return properties;
    }

    private static LiveDataQuery.Filter toLiveDataFilter(String property, String operator, JsonNode values,
        OpenProjectWorkItemFields fields)
    {
        LiveDataQuery.Filter filter = new LiveDataQuery.Filter();
        filter.setProperty(property);
        List<String> textValues = new ArrayList<>();
        values.forEach(value -> textValues.add(value.asText()));
        if (OPERATOR_BETWEEN.equals(getLiveDataOperator(property, operator))) {
            // The Live Data date filters hold a range of dates as an ISO 8601 time interval, whose bounds may be empty.
            String from = textValues.isEmpty() ? "" : textValues.get(0);
            String to = textValues.size() < 2 ? "" : textValues.get(1);
            filter.getConstraints().add(new LiveDataQuery.Constraint(from + '/' + to, OPERATOR_BETWEEN));
        } else if (textValues.isEmpty()) {
            // An operator taking no value, e.g. the open statuses.
            filter.getConstraints().add(new LiveDataQuery.Constraint("", operator));
        } else {
            boolean isBoolean = isBoolean(fields.getField(property));
            for (String value : textValues) {
                filter.getConstraints().add(new LiveDataQuery.Constraint(
                    isBoolean ? BOOLEAN_VALUES.getOrDefault(value, value) : value, operator));
            }
        }
        return filter;
    }

    private static boolean isBoolean(WorkPackageField field)
    {
        return field != null && OpenProjectMapper.FILTER_BOOLEAN.equals(OpenProjectWorkItemFields.getFilterType(field));
    }

    /**
     * @param sort the sorting of the link, e.g. {@code id:desc,subject:asc}
     */
    private static List<LiveDataQuery.SortEntry> toSortEntries(String sort, OpenProjectWorkItemFields fields)
    {
        List<LiveDataQuery.SortEntry> sortEntries = new ArrayList<>();
        for (String entry : StringUtils.split(StringUtils.defaultString(sort), ',')) {
            String column = StringUtils.trim(StringUtils.substringBefore(entry, SORT_SEPARATOR));
            if (!column.isEmpty()) {
                String direction = StringUtils.trim(StringUtils.substringAfter(entry, SORT_SEPARATOR));
                String property = fields.getColumnProperty(column);
                // The OpenProject column names the property when its name can't be written in the sort parameter.
                if (SORT_PARAMETER_SEPARATORS.matcher(property).find()) {
                    property = column;
                }
                sortEntries.add(new LiveDataQuery.SortEntry(property,
                    DESCENDING.equals(direction.toLowerCase(Locale.ROOT))));
            }
        }
        return sortEntries;
    }

    /**
     * @return the filters of the link that the Live Data has a column filter for, on the work item properties, with
     *     the OpenProject operators
     */
    public List<LiveDataQuery.Filter> getFilters()
    {
        return this.filters;
    }

    /**
     * @return the sorting of the link, on the work item properties, or on the OpenProject columns that aren't
     *     work item properties
     */
    public List<LiveDataQuery.SortEntry> getSortEntries()
    {
        return this.sortEntries;
    }

    /**
     * @return the link without the filters returned by {@link #getFilters()} and without sorting: the link of the work
     *     package list (of a project, or of all the projects), with the filters that the Live Data has no column filter
     *     for, if any
     */
    public String getLink()
    {
        return this.link;
    }

    /**
     * The columns of the Live Data of a macro: the work package fields of its instance, to name the filters and the
     * columns of the fields after the properties exposing them (e.g. a custom field after its name), and the operators
     * offered by the filter of each column, which can show the filters using them.
     */
    public static final class Columns
    {
        private final OpenProjectWorkItemFields fields;

        private final Map<String, Set<String>> filterOperators;

        /**
         * @param fields the work package fields of the instance, or {@link OpenProjectWorkItemFields#empty()} to only
         *     name the filters and the columns of the generic work item properties
         * @param filterOperators the operators offered by the filter of each property that the link may filter (e.g.
         *     {@code status} with {@code =}, {@code !} and {@code o}); a filter on another property stays in the link
         */
        public Columns(OpenProjectWorkItemFields fields, Map<String, Set<String>> filterOperators)
        {
            this.fields = fields;
            this.filterOperators = Map.copyOf(filterOperators);
        }

        private boolean hasFilter(String property, String operator)
        {
            return this.filterOperators.getOrDefault(property, Set.of()).contains(operator);
        }
    }
}
