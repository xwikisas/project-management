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
package com.xwiki.projectmanagement.openproject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * Allows the creation of OpenProject filter that gets passed to the REST endpoints.
 *
 * @version $Id$
 * @since 1.2.0
 */
public class FilterBuilder
{
    private static final String FILTER_FORMAT = "{\"%s\":{\"operator\":\"%s\",\"values\":[%s]}}";

    private static final String VALUE_FORMAT = "\"%s\"";

    private static final String DELIMITER = ",";

    private final List<String> filters = new ArrayList<>();

    /**
     * Enumeration for storing the different types of OP operators.
     */
    public enum Operator
    {
        // TODO: Add more operators
        /**
         * Contains operator, used mostly for text type of properties. i.e. description.
         */
        CONTAINS("~"),
        /**
         * Equals operator. Used, generally, for matching ids.
         */
        EQUALS("="),
        /**
         * Not equals operator. Used, generally, for matching ids.
         */
        NOT_EQUAL("!="),
        /**
         * None of operator. Used, generally, for excluding a set of ids from a list type property. i.e. type.
         */
        NONE_OF("!");

        private final String value;

        Operator(String value)
        {
            this.value = value;
        }
    }

    /**
     * Default constructor.
     */
    public FilterBuilder()
    {
    }

    /**
     * @return a new instance of the class.
     */
    public static FilterBuilder get()
    {
        return new FilterBuilder();
    }

    /**
     * @param property the name of the property that we want to filter on.
     * @param operator the operator that will be applied to the property.
     * @param value the value that the property needs to match according to the operator.
     * @return the filter builder instance for further building.
     */
    public FilterBuilder addFilter(String property, String operator, Object value)
    {
        // TODO: Handle the different types of possible values. i.e. numbers, booleans.
        if (value instanceof List) {
            String joinedValues = ((List<?>) value).stream()
                .map(element -> String.format(VALUE_FORMAT, element))
                .collect(Collectors.joining(DELIMITER));
            if (joinedValues.isEmpty()) {
                return this;
            }
            filters.add(String.format(FILTER_FORMAT, property, operator, joinedValues));
            return this;
        }
        if (value == null || StringUtils.isEmpty(value.toString())) {
            return this;
        }
        filters.add(String.format(FILTER_FORMAT, property, operator, String.format(VALUE_FORMAT, value)));
        return this;
    }

    /**
     * @param property the name of the property that we want to filter on.
     * @param operator the operator that will be applied to the property.
     * @param value the value that the property needs to match according to the operator.
     * @return the filter builder instance for further building.
     */
    public FilterBuilder addFilter(String property, Operator operator, Object value)
    {
        return addFilter(property, operator.value, value);
    }

    /**
     * @return a valid OpenProject filter based on the previous calls.
     */
    public String build()
    {
        return "[" + String.join(DELIMITER, filters) + "]";
    }
}
