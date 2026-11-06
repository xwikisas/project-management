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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Class for converting keys and operators.
 *
 * @version $Id$
 */
public final class OpenProjectMapper
{
    /**
     * The mapping between original and converted key.
     */
    public static final Map<String, String> KEY_MAPPING = new HashMap<>();

    /**
     * The mapping between original and converted operators.
     */
    public static final Map<String, String> OPERATOR_MAPPING = new HashMap<>();

    /**
     * The Live Data filter of the properties whose values are picked in a list.
     */
    public static final String FILTER_LIST = "list";

    /**
     * The Live Data filter of the text properties.
     */
    public static final String FILTER_TEXT = "text";

    /**
     * The Live Data filter of the number properties.
     */
    public static final String FILTER_NUMBER = "number";

    /**
     * The Live Data filter of the boolean properties.
     */
    public static final String FILTER_BOOLEAN = "boolean";

    /**
     * The Live Data filter of the date properties.
     */
    public static final String FILTER_DATE = "date";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenProjectMapper.class);

    private static final String AUTHOR_STRING = "author";

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
     * The OpenProject operators replacing the generic operators that the Live Data offers on every filter of a type
     * (e.g. "starts with" on all the text filters), whatever the operators of the property, by type of filter. The
     * Live Data merges these generic operators with the ones of the property, and a source can't remove them (see
     * XWIKI-18274: The Advanced Filtering panel shows operators that are not supported by the live data source). A list
     * filter matches the values picked in a list, so whether it "contains" or "starts with" a value, it equals it.
     * OpenProject has no prefix match on texts, so "starts with" is the closest: "contains".
     */
    private static final Map<String, Map<String, String>> GENERIC_OPERATORS = Map.of(
        FILTER_LIST, Map.of(OPERATOR_EQUALS, OP_EQUALS, OPERATOR_CONTAINS, OP_EQUALS, OPERATOR_STARTS_WITH,
            OP_EQUALS, OPERATOR_EMPTY, OP_EMPTY),
        FILTER_TEXT, Map.of(OPERATOR_EQUALS, OP_EQUALS, OPERATOR_CONTAINS, OP_CONTAINS, OPERATOR_STARTS_WITH,
            OP_CONTAINS, OPERATOR_EMPTY, OP_EMPTY),
        FILTER_NUMBER, Map.of(OPERATOR_EQUALS, OP_EQUALS, OPERATOR_LESS, "<=", OPERATOR_GREATER, ">=",
            OPERATOR_EMPTY, OP_EMPTY),
        FILTER_BOOLEAN, Map.of(OPERATOR_EQUALS, OP_EQUALS),
        FILTER_DATE, Map.of(OPERATOR_BETWEEN, OP_BETWEEN, OPERATOR_BEFORE, OP_BETWEEN, OPERATOR_AFTER, OP_BETWEEN,
            OPERATOR_CONTAINS,
            OP_BETWEEN, OPERATOR_EMPTY, OP_EMPTY));

    static {
        KEY_MAPPING.put(FILTER_DATE, "start_date");
        KEY_MAPPING.put("identifier", "id");
        KEY_MAPPING.put("summary", "subject");
        KEY_MAPPING.put("assignees", "assigned_to");
        KEY_MAPPING.put("creator", AUTHOR_STRING);
        KEY_MAPPING.put("creationDate", "createdAt");
        KEY_MAPPING.put("updateDate", "updatedAt");
        KEY_MAPPING.put("reporter", AUTHOR_STRING);
        KEY_MAPPING.put("progress", "percentageDone");
        // The OpenProject filter matching the work packages whose date range overlaps an interval.
        KEY_MAPPING.put(WorkItem.KEY_DATE_RANGE, "datesInterval");
        OPERATOR_MAPPING.put(OPERATOR_EQUALS, OP_EQUALS);
        OPERATOR_MAPPING.put(OPERATOR_CONTAINS, OP_CONTAINS);
        OPERATOR_MAPPING.put(OPERATOR_BETWEEN, OP_BETWEEN);
        OPERATOR_MAPPING.put(OPERATOR_EMPTY, OP_EMPTY);
        OPERATOR_MAPPING.put(OPERATOR_BEFORE, OP_BETWEEN);
        OPERATOR_MAPPING.put(OPERATOR_AFTER, OP_BETWEEN);
    }

    private OpenProjectMapper()
    {

    }

    /**
     * A mapping of original filter keys to their corresponding converted keys.
     *
     * @param key original filter key
     * @return the converted key if the mapping exists. otherwise, returns the original key
     */
    public static String mapLivedataProperty(String key)
    {
        return KEY_MAPPING.getOrDefault(key, key);
    }

    /**
     * A mapping of original operator keys to their corresponding converted keys.
     *
     * @param operator original filter operator
     * @return the converted operator if the mapping exists. otherwise, returns the original operator
     */
    public static String mapLivedataOperator(String operator)
    {
        return OPERATOR_MAPPING.getOrDefault(operator, operator);
    }

    /**
     * Converts a constraint of a Live Data filter into a constraint using an OpenProject operator. The generic Live
     * Data operators (e.g. {@code startsWith}) are replaced by the closest OpenProject operator for the type of
     * filter, the OpenProject operators being kept as they are.
     *
     * @param filterType the Live Data filter of the property (e.g. {@value #FILTER_TEXT}), or {@code null} if
     *     unknown, in which case only the operators of {@link #OPERATOR_MAPPING} are converted
     * @param constraint the constraint to convert
     * @param supportedOperators the OpenProject operators supported by the filter of the property, or {@code null}
     *     (or an empty collection) if unknown
     * @param property the property being filtered, for the logs
     * @return the converted constraint, or {@code null} if the filter of the property supports no equivalent
     *     operator, in which case the constraint can't be applied
     * @since 1.4.0
     */
    public static LiveDataQuery.Constraint mapConstraint(String filterType, LiveDataQuery.Constraint constraint,
        Collection<String> supportedOperators, String property)
    {
        String operator = constraint.getOperator();
        Map<String, String> genericOperators = filterType == null ? null : GENERIC_OPERATORS.get(filterType);
        if (genericOperators == null || !genericOperators.containsKey(operator)) {
            return new LiveDataQuery.Constraint(constraint.getValue(), mapLivedataOperator(operator));
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
        // Some text filters (e.g. the subject) can't match a text exactly, nor its beginning, only contain it.
        boolean matchesText = OPERATOR_EQUALS.equals(operator) || OPERATOR_STARTS_WITH.equals(operator);
        return FILTER_TEXT.equals(filterType) && matchesText && supportedOperators.contains(OP_CONTAINS)
            ? OP_CONTAINS : null;
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
