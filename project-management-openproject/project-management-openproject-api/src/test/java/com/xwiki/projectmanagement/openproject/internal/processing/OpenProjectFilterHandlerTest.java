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

        JsonNode expectedNode = mapper.readTree(expected);
        JsonNode actualNode = mapper.readTree(convertedFilters);

        assertEquals(expectedNode, actualNode);
    }

    @Test
    void convertFiltersSameOperatorTest() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>();
        LiveDataQuery.Filter filter1 = new LiveDataQuery.Filter();
        filter1.setProperty("property1");
        LiveDataQuery.Constraint firstConstraint = new LiveDataQuery.Constraint("value1", "operator1");
        LiveDataQuery.Constraint secondConstraint = new LiveDataQuery.Constraint("value2", "operator1");
        LiveDataQuery.Constraint thirdConstraint = new LiveDataQuery.Constraint("value3", "operator2");
        filter1.getConstraints().add(firstConstraint);
        filter1.getConstraints().add(secondConstraint);

        LiveDataQuery.Filter filter2 = new LiveDataQuery.Filter();
        filter2.setProperty("property2");
        filter2.getConstraints().add(thirdConstraint);

        filters.add(filter1);
        filters.add(filter2);

        String expected = "["
            + "{"
            + "\"property2\":{\"operator\":\"operator2\",\"values\":[\"value3\"]}"
            + "},"
            + "{"
            + "\"property1\":{\"operator\":\"operator1\",\"values\":[\"value1\", \"value2\"]}}"
            + "]";


        String convertedFilters = OpenProjectFilterHandler.mergeFilters(filters, null);

        JsonNode expectedNode = mapper.readTree(expected);
        JsonNode actualNode = mapper.readTree(convertedFilters);

        jsonArrayEqualsIgnoreOrder(expectedNode, actualNode);
    }

    private void jsonArrayEqualsIgnoreOrder(JsonNode array1, JsonNode array2) {
        Set<JsonNode> set1 = new HashSet<>();
        array1.forEach(set1::add);

        Set<JsonNode> set2 = new HashSet<>();
        array2.forEach(set2::add);

        assertEquals(set1, set2, "Arrays contain different elements");
    }
}
