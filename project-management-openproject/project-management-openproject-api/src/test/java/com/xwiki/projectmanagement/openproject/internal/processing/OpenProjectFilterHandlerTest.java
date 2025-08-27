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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenProjectFilterHandlerTest
{
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertFiltersTest() throws ProjectManagementException, JsonProcessingException
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
    void sameOperatorInFilterTest() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();

        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();
        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter();

        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "contains");
        LiveDataQuery.Constraint secondConstraint = new LiveDataQuery.Constraint("value2", "contains");
        LiveDataQuery.Constraint thirdConstraint = new LiveDataQuery.Constraint("value3", "between");

        filter1.setProperty("identifier.value");
        filter1.getConstraints().add(firstConstraint);
        filter1.getConstraints().add(secondConstraint);

        filter2.setProperty("summary.value");
        filter2.getConstraints().add(thirdConstraint);

        filters.add(filter1);
        filters.add(filter2);

        String expected = "["
            + "{"
            + "\"subject\":{\"operator\":\"<>d\",\"values\":[\"value3\"]}"
            + "},"
            + "{"
            + "\"id\":{\"operator\":\"=\",\"values\":[\"value1\",\"value2\"]}}"
            + "]";
        String convertedFilters = OpenProjectFilterHandler.mergeFilters(filters, null);

        assertEqualsStringFilters(expected, convertedFilters);
    }

    @Test
    void sameFilterDifferentOperatorsInLivedataFiltersTest()
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
    void openProjectFiltersTest() throws JsonProcessingException, ProjectManagementException
    {
        String opFiltersString = "["
            + "{\"n\":\"identifier.value\",\"o\":\"contains\",\"v\":[\"value1\"]},"
            + "{\"n\":\"property2\",\"o\":\"operator2\",\"v\":[\"value2\"]}"
            + "]";

        JsonNode opFiltersJson = mapper.readTree(opFiltersString);

        String expected = "["
            + "{"
            + "\"identifier.value\":{\"operator\":\"contains\",\"values\":[\"value1\"]}},"
            + "{\"property2\":{\"operator\":\"operator2\",\"values\":[\"value2\"]}"
            + "}"
            + "]";
        String convertedValues = OpenProjectFilterHandler.mergeFilters(new ArrayList<>(), opFiltersJson);

        assertEqualsStringFilters(expected, convertedValues);
    }

    @Test
    void liveDataAndOpenProjectFiltersTest() throws JsonProcessingException, ProjectManagementException
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
    void liveDataAndOpenProjectSameFilterInBothSourcesTest()
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

    private void assertEqualsStringFilters(String firstFilter, String secondFilter) throws JsonProcessingException
    {
        JsonNode firstFilterJson = mapper.readTree(firstFilter);
        JsonNode secondFilterJson = mapper.readTree(secondFilter);

        Set<JsonNode> set1 = new HashSet<>();
        firstFilterJson.forEach(set1::add);

        Set<JsonNode> set2 = new HashSet<>();
        secondFilterJson.forEach(set2::add);

        assertEquals(set1, set2);
    }
}
