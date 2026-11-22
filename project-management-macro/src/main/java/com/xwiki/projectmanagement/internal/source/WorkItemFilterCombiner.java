package com.xwiki.projectmanagement.internal.source;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;

/**
 * Combines the filters on the same property into a single filter, which matches the work items matching all of them.
 * The Live Data keeps a single filter by property, and the project management tools (for example OpenProject) a
 * single condition by field, so the conditions written on the same property, for example
 * {@code priority IN (High, Low) AND priority NOT IN (Low)}, have to be expressed by one filter. The filters are
 * combined as follows:
 * <ul>
 * <li>the filters excluding values ({@code !=}, {@code NOT IN}) exclude all their values;</li>
 * <li>the filters including values ({@code =}, {@code IN}) keep the values that each of them includes, minus the
 * excluded values;</li>
 * <li>the filters requiring values ({@code ALL IN}) require all their values;</li>
 * <li>a lower bound ({@code >=} or {@code >}) and an upper bound ({@code <=} or {@code <}) of a number make a range,
 * held by a filter with both constraints;</li>
 * <li>a filter repeated as it is counts once;</li>
 * <li>the other filters can't be combined: for example two conditions on a number, or on a text.</li>
 * </ul>
 * The values are compared as they are, so the names must have been resolved into ids beforehand.
 *
 * @version $Id$
 * @since 1.5.0
 */
final class WorkItemFilterCombiner
{
    private static final String INCLUDING_OPERATOR = "=";

    private static final Set<String> INCLUDING_OPERATORS = Set.of(INCLUDING_OPERATOR, "equals");

    private static final String EXCLUDING_OPERATOR = "!";

    private static final String REQUIRING_OPERATOR = "&=";

    private static final String LOWER_BOUND_OPERATOR = ">=";

    private static final String UPPER_BOUND_OPERATOR = "<=";

    private static final Set<String> LOWER_BOUND_OPERATORS = Set.of(LOWER_BOUND_OPERATOR, ">");

    private static final Set<String> UPPER_BOUND_OPERATORS = Set.of(UPPER_BOUND_OPERATOR, "<");

    private WorkItemFilterCombiner()
    {
    }

    /**
     * @param filters the filters, for example the ones of the conditions of a query
     * @return the filters, those on the same property being combined into one, at the place of the first one
     * @throws WorkItemSourceException if the filters on the same property can't be combined, or can't match any
     *     work item together
     */
    static List<LiveDataQuery.Filter> combine(List<LiveDataQuery.Filter> filters) throws WorkItemSourceException
    {
        Map<String, List<LiveDataQuery.Filter>> filtersByProperty = new LinkedHashMap<>();
        List<LiveDataQuery.Filter> result = new ArrayList<>();
        for (LiveDataQuery.Filter filter : filters) {
            List<LiveDataQuery.Filter> propertyFilters = filtersByProperty.get(filter.getProperty());
            if (propertyFilters == null) {
                propertyFilters = new ArrayList<>();
                filtersByProperty.put(filter.getProperty(), propertyFilters);
                // The place of the combined filter.
                result.add(null);
            }
            propertyFilters.add(filter);
        }
        int index = 0;
        for (List<LiveDataQuery.Filter> propertyFilters : filtersByProperty.values()) {
            result.set(index++, combineFiltersOfProperty(propertyFilters));
        }
        return result;
    }

    private static LiveDataQuery.Filter combineFiltersOfProperty(List<LiveDataQuery.Filter> filters)
        throws WorkItemSourceException
    {
        Map<List<String>, LiveDataQuery.Filter> distinctFilters = new LinkedHashMap<>();
        for (LiveDataQuery.Filter filter : filters) {
            distinctFilters.putIfAbsent(describe(filter), filter);
        }
        if (distinctFilters.size() == 1) {
            return filters.get(0);
        }
        String property = filters.get(0).getProperty();
        Set<String> operators = filters.stream().flatMap(filter -> filter.getConstraints().stream())
            .map(LiveDataQuery.Constraint::getOperator).collect(Collectors.toSet());
        if (Set.of(REQUIRING_OPERATOR).equals(operators)) {
            return newFilter(property, REQUIRING_OPERATOR, getValues(filters, REQUIRING_OPERATOR));
        } else if (operators.stream().allMatch(
            operator -> INCLUDING_OPERATORS.contains(operator) || EXCLUDING_OPERATOR.equals(operator)))
        {
            return combineListFilters(property, filters);
        } else if (operators.stream().allMatch(
            operator -> LOWER_BOUND_OPERATORS.contains(operator) || UPPER_BOUND_OPERATORS.contains(operator)))
        {
            return combineBounds(property, filters);
        } else if (Set.of("o", "c").equals(operators)) {
            // All the open statuses, and all the closed ones.
            throw noMatch(property);
        }
        throw notCombinable(property);
    }

    private static WorkItemSourceException notCombinable(String property)
    {
        return new WorkItemSourceException(String.format("The conditions on [%s] can't be combined: only =, !=, IN "
            + "and NOT IN conditions, and a lower (>=, >) and an upper (<=, <) bound of a number, can be repeated on "
            + "a property.", property));
    }

    /**
     * Combines a lower and an upper bound of a number into a range.
     */
    private static LiveDataQuery.Filter combineBounds(String property, List<LiveDataQuery.Filter> filters)
        throws WorkItemSourceException
    {
        Set<List<Object>> lowerBounds = getBounds(filters, LOWER_BOUND_OPERATORS);
        Set<List<Object>> upperBounds = getBounds(filters, UPPER_BOUND_OPERATORS);
        if (lowerBounds.size() > 1 || upperBounds.size() > 1) {
            throw new WorkItemSourceException(String.format("The conditions on [%s] have several %s bounds.", property,
                lowerBounds.size() > 1 ? "lower" : "upper"));
        }
        LiveDataQuery.Filter filter = new LiveDataQuery.Filter();
        filter.setProperty(property);
        // A work item matches the range when it matches both bounds.
        filter.setMatchAll(true);
        for (List<Object> bound : lowerBounds) {
            filter.getConstraints().add(new LiveDataQuery.Constraint(bound.get(1), (String) bound.get(0)));
        }
        for (List<Object> bound : upperBounds) {
            filter.getConstraints().add(new LiveDataQuery.Constraint(bound.get(1), (String) bound.get(0)));
        }
        if (!lowerBounds.isEmpty() && !upperBounds.isEmpty()) {
            checkRange(property, lowerBounds.iterator().next(), upperBounds.iterator().next());
        }
        return filter;
    }

    /**
     * @param lowerBound the operator and the value of the lower bound
     * @param upperBound the operator and the value of the upper bound
     */
    private static void checkRange(String property, List<Object> lowerBound, List<Object> upperBound)
        throws WorkItemSourceException
    {
        int comparison;
        try {
            comparison = new BigDecimal(String.valueOf(lowerBound.get(1)).trim())
                .compareTo(new BigDecimal(String.valueOf(upperBound.get(1)).trim()));
        } catch (NumberFormatException e) {
            // Only numbers make a range.
            throw notCombinable(property);
        }
        boolean strict = !LOWER_BOUND_OPERATOR.equals(lowerBound.get(0))
            || !UPPER_BOUND_OPERATOR.equals(upperBound.get(0));
        if (comparison > 0 || comparison == 0 && strict) {
            throw noMatch(property);
        }
    }

    /**
     * @return the distinct bounds (operator and value) of the filters, among the given operators
     */
    private static Set<List<Object>> getBounds(List<LiveDataQuery.Filter> filters, Set<String> operators)
    {
        return filters.stream().flatMap(filter -> filter.getConstraints().stream())
            .filter(constraint -> operators.contains(constraint.getOperator()))
            .map(constraint -> List.of(constraint.getOperator(), constraint.getValue()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static LiveDataQuery.Filter combineListFilters(String property, List<LiveDataQuery.Filter> filters)
        throws WorkItemSourceException
    {
        Set<Object> excludedValues = getValues(filters, EXCLUDING_OPERATOR);
        Set<Object> includedValues = null;
        for (LiveDataQuery.Filter filter : filters) {
            Set<Object> values = filter.getConstraints().stream()
                .filter(constraint -> INCLUDING_OPERATORS.contains(constraint.getOperator()))
                .map(LiveDataQuery.Constraint::getValue).collect(Collectors.toCollection(LinkedHashSet::new));
            if (!values.isEmpty()) {
                if (includedValues == null) {
                    includedValues = values;
                } else {
                    includedValues.retainAll(values);
                }
            }
        }
        if (includedValues == null) {
            return newFilter(property, EXCLUDING_OPERATOR, excludedValues);
        }
        includedValues.removeAll(excludedValues);
        if (includedValues.isEmpty()) {
            throw noMatch(property);
        }
        return newFilter(property, INCLUDING_OPERATOR, includedValues);
    }

    private static WorkItemSourceException noMatch(String property)
    {
        return new WorkItemSourceException(
            String.format("The conditions on [%s] can't be true together: no work item matches them.", property));
    }

    private static Set<Object> getValues(List<LiveDataQuery.Filter> filters, String operator)
    {
        return filters.stream().flatMap(filter -> filter.getConstraints().stream())
            .filter(constraint -> operator.equals(constraint.getOperator()))
            .map(LiveDataQuery.Constraint::getValue).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static LiveDataQuery.Filter newFilter(String property, String operator, Set<Object> values)
    {
        LiveDataQuery.Filter filter = new LiveDataQuery.Filter();
        filter.setProperty(property);
        values.forEach(value -> filter.getConstraints().add(new LiveDataQuery.Constraint(value, operator)));
        return filter;
    }

    private static List<String> describe(LiveDataQuery.Filter filter)
    {
        return filter.getConstraints().stream()
            .map(constraint -> constraint.getOperator() + ':' + constraint.getValue()).collect(Collectors.toList());
    }
}
