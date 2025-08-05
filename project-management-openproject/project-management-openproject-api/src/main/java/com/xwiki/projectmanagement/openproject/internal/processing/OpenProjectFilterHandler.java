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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;

/**
 * Filter converter handler.
 *
 * @version $Id$
 */
public final class OpenProjectFilterHandler
{
    private static final String OPERATOR = "operator";

    private static final String VALUES = "values";

    private static final String FAILURE_MESSAGE = "Failed to convert Livedata filters";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> PROPERTIES_DATE = Set.of("startDate", "dueDate", "creationDate", "updateDate");

    private static final Set<String> OPERATORS_EMPTY_VALS =
        Set.of("t", "w", "*", "!*", String.valueOf('o'), "c");

    private OpenProjectFilterHandler()
    {
    }

    /**
     * Converts a list of {@link LiveDataQuery.Filter} objects into a JSON string representation compatible with
     * OpenProject's query filter format.
     *
     * @param filters the list of {@link  LiveDataQuery.Filter} to be converted
     * @return a JSON string representing the filters in the format expected by the OpenProject API
     */
    public static String convertFilters(List<LiveDataQuery.Filter> filters) throws ProjectManagementException
    {
        List<Map<String, Object>> convertedFilters = getConvertedFilters(filters);

        try {
            return MAPPER.writeValueAsString(convertedFilters);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(FAILURE_MESSAGE, e);
        }
    }

    /**
     * Merges a list of {@link LiveDataQuery.Filter} with filters represented as a JSON string. The resulting filter
     * string is compatible with the OpenProject API's expected filter format.
     *
     * @param filters the list of {@link LiveDataQuery.Filter} objects representing the base filters
     * @param filtersNode a JsonNode representing OpenProject URL encoded filters
     * @return a string is compatible with the OpenProject API's expected filter format.
     * @throws ProjectManagementException if an error occurs during parsing or serialization
     */
    public static String mergeFilters(List<LiveDataQuery.Filter> filters, JsonNode filtersNode)
        throws ProjectManagementException
    {
        Map<String, Object> mergedFilters = new HashMap<>();

        for (LiveDataQuery.Filter filter : filters) {

            List<LiveDataQuery.Constraint> validConstraints = getValidConstraints(filter);

            if (validConstraints.isEmpty()) {
                continue;
            }

            Map<String, List<String>> filterConstraints =
                handleFilterConstraints(filter.getProperty(), validConstraints);
            String filterName = OpenProjectMapper.mapLivedataProperty(filter.getProperty());
            mergedFilters.put(filterName, filterConstraints);
        }

        Map<String, Object> convertedFiltersFromNode = handleFilterNode(filtersNode);

        mergeFiltersMaps(mergedFilters, convertedFiltersFromNode);

        return convertMapOfFiltersToString(mergedFilters);
    }

    private static Map<String, Object> handleFilterNode(JsonNode filterNode)
    {
        if (filterNode == null || filterNode.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> filtersList;
        filtersList = MAPPER.convertValue(filterNode, new TypeReference<List<Map<String, Object>>>()
        {
        });

        Map<String, Object> convertedFilters = new HashMap<>();

        for (Map<String, Object> filter : filtersList) {
            String name = (String) filter.get("n");
            String operator = (String) filter.get("o");
            List<String> values = (List<String>) filter.get("v");
            convertedFilters.put(name, Map.of(operator, values));
        }
        return convertedFilters;
    }

    private static List<Map<String, Object>> getConvertedFilters(
        List<LiveDataQuery.Filter> filters)
    {
        List<Map<String, Object>> convertedFilters = new ArrayList<>();

        for (LiveDataQuery.Filter filter : filters) {

            List<LiveDataQuery.Constraint> validConstraints = getValidConstraints(filter);

            if (validConstraints.isEmpty()) {
                continue;
            }

            Map<String, List<String>> filterConstraints =
                handleFilterConstraints(filter.getProperty(), validConstraints);

            filterConstraints.forEach(
                (key, value) -> {
                    String filterName = OpenProjectMapper.mapLivedataProperty(filter.getProperty());
                    convertedFilters.add(
                        Map.of(
                            filterName,
                            Map.of(
                                OPERATOR, key,
                                VALUES, value
                            )
                        )
                    );
                }
            );
        }
        return convertedFilters;
    }

    private static List<String> convertToOpenProjectValue(String propertyName, String values)
    {
        if (!PROPERTIES_DATE.contains(propertyName)) {
            return Collections.singletonList(values);
        }
        // We can either receive a date range or a simple date with the format specified in the livedata config json.
        String[] parts = values.split("/");
        if (parts.length > 1) {
            return Arrays.asList(parts[0], parts[1]);
        } else {
            return Collections.singletonList(values);
        }
    }

    private static List<LiveDataQuery.Constraint> getValidConstraints(LiveDataQuery.Filter filter)
    {
        return filter
            .getConstraints()
            .stream()
            .filter(
                constraint -> {
                    String constraintValue = (String) constraint.getValue();
                    // Some operators ignore values. If thats the case, we know its a valid constraint whether the
                    // value is present or not.
                    if (OPERATORS_EMPTY_VALS.contains(constraint.getOperator())) {
                        return true;
                    }
                    return constraintValue != null && !constraintValue.isEmpty();
                }
            )
            .collect(Collectors.toList());
    }

    private static Map<String, List<String>> handleFilterConstraints(String propertyName,
        List<LiveDataQuery.Constraint> filterConstraints)
    {
        Map<String, List<String>> result = new HashMap<>();
        for (LiveDataQuery.Constraint constraint : filterConstraints) {
            String operatorValue = OpenProjectMapper.mapLivedataOperator(constraint.getOperator());
            List<String> values = convertToOpenProjectValue(propertyName, (String) constraint.getValue());
            if (result.containsKey(operatorValue)) {
                List<String> currentValues = result.get(operatorValue);
                currentValues.addAll(values);
            } else {
                result.put(operatorValue, new ArrayList<>(values));
            }
        }
        return result;
    }

    private static String convertMapOfFiltersToString(Map<String, Object> filters) throws ProjectManagementException
    {
        List<Map<String, Object>> convertedFilters = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Map<String, Object> constraints = (Map<String, Object>) entry.getValue();
            constraints.forEach(
                (key, value) -> {
                    convertedFilters.add(
                        Map.of(
                            entry.getKey(),
                            Map.of(
                                OPERATOR, key,
                                VALUES, value
                            )
                        )
                    );
                }
            );
        }

        try {
            return MAPPER.writeValueAsString(convertedFilters);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(FAILURE_MESSAGE, e);
        }
    }

    private static void mergeFiltersMaps(Map<String, Object> firstMap, Map<String, Object> secondMap)
    {
        for (Map.Entry<String, Object> entry : secondMap.entrySet()) {
            if (firstMap.containsKey(entry.getKey())) {
                Map<String, Object> firstFilter = (Map<String, Object>) firstMap.get(entry.getKey());
                Map<String, Object> secondFilter = (Map<String, Object>) entry.getValue();
                secondFilter.forEach((key, value) -> {
                    if (firstFilter.containsKey(key)) {
                        List<String> firstFilterValues = (List<String>) firstFilter.get(key);
                        List<String> secondFilterValues = (List<String>) value;
                        for (String filterValue : secondFilterValues) {
                            if (!firstFilterValues.contains(filterValue)) {
                                firstFilterValues.add(filterValue);
                            }
                        }
                    } else {
                        firstFilter.put(key, value);
                    }
                });
            } else {
                firstMap.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
