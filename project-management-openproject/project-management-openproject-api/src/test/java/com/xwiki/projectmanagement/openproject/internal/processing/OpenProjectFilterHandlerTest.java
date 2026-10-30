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

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xwiki.projectmanagement.exception.ProjectManagementException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenProjectFilterHandlerTest
{
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertFiltersToOpenProjectJson() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter("property1", "operator1", "value1");
        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter("property2", "operator2", "value2");

        filters.add(filter1);
        filters.add(filter2);

        String expected = "["
            + "{"
            + "\"property1\":{\"operator\":\"operator1\",\"values\":[\"value1\"]}},"
            + "{\"property2\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}"
            + "}"
            + "]";
        String convertedFilters = OpenProjectFilterHandler.convertFilters(filters);

        assertEqualsStringFilters(expected, convertedFilters);
    }

    @Test
    void mergeFiltersGroupsConstraintsWithSameOperator() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();
        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "equals");
        LiveDataQuery.Constraint secondConstraint = new LiveDataQuery.Constraint("value2", "equals");
        // The Live Data offers the between operator only on the date properties, with a start/end value.
        LiveDataQuery.Constraint thirdConstraint = new LiveDataQuery.Constraint("2026-01-01/2026-02-01", "between");

        filter1.setProperty("identifier");
        filter1.getConstraints().add(firstConstraint);
        filter1.getConstraints().add(secondConstraint);

        filter2.setProperty("startDate");
        filter2.getConstraints().add(thirdConstraint);

        filters.add(filter1);
        filters.add(filter2);

        String expected = "["
            + "{"
            + "\"startDate\":{\"operator\":\"<>d\",\"values\":[\"2026-01-01\",\"2026-02-01\"]}"
            + "},"
            + "{"
            + "\"id\":{\"operator\":\"=\",\"values\":[\"value1\",\"value2\"]}}"
            + "]";
        String convertedFilters = OpenProjectFilterHandler.mergeFilters(filters, null);

        assertEqualsStringFilters(expected, convertedFilters);
    }

    @Test
    void sameFilterDifferentOperatorsInLivedataFilters()
        throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();
        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "operator1");
        LiveDataQuery.Constraint secondConstraint = new LiveDataQuery.Constraint("value2", "operator2");

        filter1.setProperty("property1");
        filter1.getConstraints().add(firstConstraint);

        filter2.setProperty("property1");
        filter2.getConstraints().add(secondConstraint);

        filters.add(filter1);
        filters.add(filter2);

        //we expect to have only the second filter converted
        String expected = "["
            + "{\"property1\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}}"
            + "]";
        String convertedFilters = OpenProjectFilterHandler.mergeFilters(filters, null);

        assertEqualsStringFilters(expected, convertedFilters);
    }

    @Test
    void mergeFiltersConvertsOpenProjectFilters() throws JsonProcessingException, ProjectManagementException
    {
        String opFiltersString = "["
            + "{\"n\":\"identifier\",\"o\":\"contains\",\"v\":[\"value1\"]},"
            + "{\"n\":\"property2\",\"o\":\"operator2\",\"v\":[\"value2\"]}"
            + "]";

        JsonNode opFiltersJson = mapper.readTree(opFiltersString);

        String expected = "["
            + "{"
            + "\"identifier\":{\"operator\":\"contains\",\"values\":[\"value1\"]}},"
            + "{\"property2\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}"
            + "}"
            + "]";
        String convertedValues = OpenProjectFilterHandler.mergeFilters(new ArrayList<>(), opFiltersJson);

        assertEqualsStringFilters(expected, convertedValues);
    }

    @Test
    void liveDataAndOpenProjectFilters() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "operator1");

        filter1.setProperty("property1");
        filter1.getConstraints().add(firstConstraint);

        filters.add(filter1);

        String opFiltersString = "["
            + "{\"n\":\"property2\",\"o\":\"operator2\",\"v\":[\"value2\"]}"
            + "]";

        JsonNode opFiltersJson = mapper.readTree(opFiltersString);

        String expected = "["
            + "{"
            + "\"property1\":{\"operator\":\"operator1\",\"values\":[\"value1\"]}},"
            + "{\"property2\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}"
            + "}"
            + "]";
        String convertedValues = OpenProjectFilterHandler.mergeFilters(filters, opFiltersJson);

        assertEqualsStringFilters(expected, convertedValues);
    }

    @Test
    void liveDataAndOpenProjectSameFilterInBothSources()
        throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "operator1");

        filter1.setProperty("property1");
        filter1.getConstraints().add(firstConstraint);

        filters.add(filter1);

        String opFiltersString = "["
            + "{\"n\":\"property1\",\"o\":\"operator1\",\"v\":[\"value2\"]}"
            + "]";

        JsonNode opFiltersJson = mapper.readTree(opFiltersString);

        String expected = "["
            + "{\"property1\":{\"operator\":\"operator1\",\"values\":[\"value1\",\"value2\"]}}"
            + "]";
        String convertedValues = OpenProjectFilterHandler.mergeFilters(filters, opFiltersJson);

        assertEqualsStringFilters(expected, convertedValues);
    }

    @Test
    void mergeApiFiltersOnDifferentProperties() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("summary", "contains", "bug"));
        JsonNode apiFilters = mapper.readTree("[{\"status\":{\"operator\":\"o\",\"values\":[]}}]");

        String expected = "["
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}},"
            + "{\"status\":{\"operator\":\"o\",\"values\":[]}}"
            + "]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.mergeApiFilters(filters, apiFilters));
    }

    @Test
    void mergeApiFiltersOnSamePropertyAndOperator() throws JsonProcessingException, ProjectManagementException
    {
        // The livedata property is mapped to the OpenProject one (identifier -> id) before merging.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("identifier", "equals", "5"),
            new LiveDataQuery.Filter("identifier", "equals", "6"));
        JsonNode apiFilters = mapper.readTree("[{\"id\":{\"operator\":\"=\",\"values\":[\"4\",\"5\"]}}]");

        // The values of the api filters come first, and the duplicated value is kept once.
        String expected = "[{\"id\":{\"operator\":\"=\",\"values\":[\"4\",\"5\",\"6\"]}}]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.mergeApiFilters(filters, apiFilters));
    }

    @Test
    void mergeApiFiltersOnSamePropertyWithDifferentOperators()
        throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "equals", "1"));
        JsonNode apiFilters = mapper.readTree("[{\"status\":{\"operator\":\"o\",\"values\":[]}}]");

        // Both operators are kept, as separate filters on the same property.
        String expected = "["
            + "{\"status\":{\"operator\":\"=\",\"values\":[\"1\"]}},"
            + "{\"status\":{\"operator\":\"o\",\"values\":[]}}"
            + "]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.mergeApiFilters(filters, apiFilters));
    }

    @Test
    void mergeApiFiltersWithoutApiFilters() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("summary", "contains", "bug"));
        String expected = "[{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}}]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.mergeApiFilters(filters, null));
        assertEqualsStringFilters(expected, OpenProjectFilterHandler.mergeApiFilters(filters, mapper.readTree("[]")));
    }

    @Test
    void convertDateRangeFilter() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("summary", "contains", "bug"),
            new LiveDataQuery.Filter("dateRange", "between", "2026-10-01T00:00:00Z/2026-11-01T00:00:00Z"));

        // The date range is mapped to the OpenProject datesInterval filter, which also matches the work packages
        // having only a start date or only a due date inside the interval.
        String expected = "["
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"bug\"]}},"
            + "{\"datesInterval\":{\"operator\":\"<>d\","
            + "\"values\":[\"2026-10-01T00:00:00Z\",\"2026-11-01T00:00:00Z\"]}}"
            + "]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.convertFilters(filters));
    }

    @Test
    void convertBeforeAndAfterDateFilters() throws JsonProcessingException, ProjectManagementException
    {
        // OpenProject has no before or after operators: they become intervals bounded by a distant date.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("startDate", "before", "2026-10-01"),
            new LiveDataQuery.Filter("dueDate", "after", "2026-11-01"));

        String expected = "["
            + "{\"startDate\":{\"operator\":\"<>d\",\"values\":[\"1970-01-01\",\"2026-10-01\"]}},"
            + "{\"dueDate\":{\"operator\":\"<>d\",\"values\":[\"2026-11-01\",\"3000-01-01\"]}}"
            + "]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.convertFilters(filters));
    }

    @Test
    void convertFiltersDropsTheConstraintsWithoutValue() throws JsonProcessingException, ProjectManagementException
    {
        LiveDataQuery.Filter summary = new LiveDataQuery.Filter("summary", "contains", "");
        summary.getConstraints().add(new LiveDataQuery.Constraint(null, "contains"));
        LiveDataQuery.Filter description = new LiveDataQuery.Filter("description", "contains", "");
        description.getConstraints().add(new LiveDataQuery.Constraint("bug", "contains"));

        // The filters left without any constraint are dropped too.
        String expected = "[{\"description\":{\"operator\":\"~\",\"values\":[\"bug\"]}}]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.convertFilters(List.of(summary, description)));
    }

    @Test
    void convertFiltersKeepsTheOperatorsThatTakeNoValue() throws JsonProcessingException, ProjectManagementException
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "o", ""),
            new LiveDataQuery.Filter("assignees", "!*", ""), new LiveDataQuery.Filter("startDate", "t", ""));

        // The empty value is sent as is, and ignored by OpenProject for these operators.
        String expected = "["
            + "{\"status\":{\"operator\":\"o\",\"values\":[\"\"]}},"
            + "{\"assigned_to\":{\"operator\":\"!*\",\"values\":[\"\"]}},"
            + "{\"startDate\":{\"operator\":\"t\",\"values\":[\"\"]}}"
            + "]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.convertFilters(filters));
    }

    @Test
    void convertContainsOnListFilters() throws JsonProcessingException, ProjectManagementException
    {
        // The list properties are matched on their exact values, the text ones on a substring.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("assignees", "contains", "5"),
            new LiveDataQuery.Filter("status", "contains", "1"), new LiveDataQuery.Filter("summary", "contains", "a"));

        String expected = "["
            + "{\"assigned_to\":{\"operator\":\"=\",\"values\":[\"5\"]}},"
            + "{\"status\":{\"operator\":\"=\",\"values\":[\"1\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"a\"]}}"
            + "]";

        assertEqualsStringFilters(expected, OpenProjectFilterHandler.convertFilters(filters));
    }

    /**
     * Compares the filters in any order (the merged filters are built from a map), but each one as many times as
     * expected.
     */
    private void assertEqualsStringFilters(String expected, String actual) throws JsonProcessingException
    {
        assertEquals(canonicalFilters(expected), canonicalFilters(actual));
    }

    private List<String> canonicalFilters(String filters) throws JsonProcessingException
    {
        ObjectMapper sortingMapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        List<String> canonicalFilters = new ArrayList<>();
        for (JsonNode filter : this.mapper.readTree(filters)) {
            canonicalFilters.add(sortingMapper.writeValueAsString(this.mapper.convertValue(filter, Object.class)));
        }
        Collections.sort(canonicalFilters);
        return canonicalFilters;
    }
}
