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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;

/**
 * The filters of a work package query, translated to OpenProject (see {@link OpenProjectFilterTranslator}): each
 * filter is an OpenProject filter, with the values of each of its OpenProject operators. They can be sent as they are,
 * or merged with the filters of an OpenProject URL or of a saved query, which they narrow down.
 * <p>
 * Two policies coexist when the same OpenProject filter appears more than once:
 * <ul>
 * <li>{@link #toJson()} keeps every filter, so that OpenProject applies all of them;</li>
 * <li>{@link #mergeWithUrlFilters(JsonNode)} and {@link #mergeWithApiFilters(JsonNode)} keep only the last of the
 * translated filters on an OpenProject filter, then merge the values of the filters of both sides having the same
 * operator, the filters with different operators being both kept.</li>
 * </ul>
 * Instances are immutable.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class OpenProjectFilters
{
    private static final String OPERATOR = "operator";

    private static final String VALUES = "values";

    private static final String FAILURE_MESSAGE = "Failed to convert Livedata filters";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The OpenProject filters, in the order of the filters they were translated from: the key of each entry is the
     * OpenProject filter, and its value the values of each of its operators.
     */
    private final List<Map.Entry<String, Map<String, List<String>>>> filters;

    OpenProjectFilters(List<Map.Entry<String, Map<String, List<String>>>> filters)
    {
        this.filters = List.copyOf(filters);
    }

    /**
     * @return {@code true} if no filter is left, for example when all the constraints were empty
     */
    public boolean isEmpty()
    {
        return this.filters.stream().allMatch(filter -> filter.getValue().isEmpty());
    }

    /**
     * @return the filters, in the format of the OpenProject API, for example
     *     {@code [{"status":{"operator":"=","values":["1","2"]}}]}
     * @throws ProjectManagementException if the filters couldn't be serialized
     */
    public String toJson() throws ProjectManagementException
    {
        List<Map<String, Object>> convertedFilters = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> filter : this.filters) {
            filter.getValue().forEach((operator, values) -> convertedFilters.add(toJson(filter.getKey(), operator,
                values)));
        }
        return serialize(convertedFilters);
    }

    /**
     * Merges these filters with the filters of an OpenProject work package list URL (its {@code query_props}
     * parameter). The values of the URL filters come after the values of these filters.
     *
     * @param urlFilters the filters of the URL, that is {@code [{"n":"status","o":"=","v":["1"]}]}, or {@code null}
     * @return the merged filters, in the format of the OpenProject API
     * @throws ProjectManagementException if the filters couldn't be serialized
     */
    public String mergeWithUrlFilters(JsonNode urlFilters) throws ProjectManagementException
    {
        return merge(readUrlFilters(urlFilters), false);
    }

    /**
     * Merges these filters with filters in the format of the OpenProject API, for example the filters of a saved
     * query. The values of the given filters come before the values of these filters, since some operators, namely
     * contains, take only the first value of a filter into account.
     *
     * @param apiFilters filters in the format of the OpenProject API, that is
     *     {@code [{"subject":{"operator":"~","values":["a"]}}]}, or {@code null}
     * @return the merged filters, in the format of the OpenProject API
     * @throws ProjectManagementException if the filters couldn't be serialized
     */
    public String mergeWithApiFilters(JsonNode apiFilters) throws ProjectManagementException
    {
        return merge(readApiFilters(apiFilters), true);
    }

    private String merge(Map<String, Map<String, List<String>>> otherFilters, boolean otherValuesFirst)
        throws ProjectManagementException
    {
        Map<String, Map<String, List<String>>> mergedFilters = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> filter : this.filters) {
            mergedFilters.put(filter.getKey(), copy(filter.getValue()));
        }

        for (Map.Entry<String, Map<String, List<String>>> otherFilter : otherFilters.entrySet()) {
            Map<String, List<String>> filter = mergedFilters.get(otherFilter.getKey());
            if (filter == null) {
                mergedFilters.put(otherFilter.getKey(), otherFilter.getValue());
            } else {
                otherFilter.getValue().forEach((operator, values) -> filter.merge(operator, values,
                    (currentValues, newValues) -> mergeValues(currentValues, newValues, otherValuesFirst)));
            }
        }

        List<Map<String, Object>> convertedFilters = new ArrayList<>();
        mergedFilters.forEach((name, operators) -> operators.forEach(
            (operator, values) -> convertedFilters.add(toJson(name, operator, values))));
        return serialize(convertedFilters);
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> operators)
    {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        operators.forEach((operator, values) -> copy.put(operator, new ArrayList<>(values)));
        return copy;
    }

    private static List<String> mergeValues(List<String> values, List<String> otherValues, boolean otherValuesFirst)
    {
        List<String> mergedValues = new ArrayList<>(otherValuesFirst ? otherValues : values);
        for (String value : otherValuesFirst ? values : otherValues) {
            if (!mergedValues.contains(value)) {
                mergedValues.add(value);
            }
        }
        return mergedValues;
    }

    private static Map<String, Map<String, List<String>>> readUrlFilters(JsonNode urlFilters)
    {
        if (urlFilters == null || urlFilters.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> filtersList = MAPPER.convertValue(urlFilters,
            new TypeReference<List<Map<String, Object>>>()
            {
            });
        Map<String, Map<String, List<String>>> filters = new LinkedHashMap<>();
        for (Map<String, Object> filter : filtersList) {
            String name = (String) filter.get("n");
            String operator = (String) filter.get("o");
            List<String> values = (List<String>) filter.get("v");
            Map<String, List<String>> operators = new LinkedHashMap<>();
            operators.put(operator, values);
            filters.put(name, operators);
        }
        return filters;
    }

    private static Map<String, Map<String, List<String>>> readApiFilters(JsonNode apiFilters)
    {
        Map<String, Map<String, List<String>>> filters = new LinkedHashMap<>();
        if (apiFilters == null || apiFilters.isEmpty()) {
            return filters;
        }

        for (JsonNode filterNode : apiFilters) {
            Iterator<Map.Entry<String, JsonNode>> properties = filterNode.fields();
            while (properties.hasNext()) {
                Map.Entry<String, JsonNode> property = properties.next();
                List<String> values = new ArrayList<>();
                for (JsonNode value : property.getValue().path(VALUES)) {
                    values.add(value.asText());
                }
                Map<String, List<String>> operators = new LinkedHashMap<>();
                operators.put(property.getValue().path(OPERATOR).asText(), values);
                filters.put(property.getKey(), operators);
            }
        }
        return filters;
    }

    private static Map<String, Object> toJson(String filter, String operator, List<String> values)
    {
        return Map.of(filter, Map.of(OPERATOR, operator, VALUES, values));
    }

    private static String serialize(List<Map<String, Object>> filters) throws ProjectManagementException
    {
        try {
            return MAPPER.writeValueAsString(filters);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(FAILURE_MESSAGE, e);
        }
    }
}
