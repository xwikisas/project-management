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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenProjectSortingHandlerTest
{
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertSortingToOpenProjectJson() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.SortEntry> sortEntries = new ArrayList<>();
        LiveDataQuery.SortEntry sortEntry1 = new LiveDataQuery.SortEntry("firstProperty", false);
        LiveDataQuery.SortEntry sortEntry2 = new LiveDataQuery.SortEntry("secondProperty", true);
        sortEntries.add(sortEntry1);
        sortEntries.add(sortEntry2);

        String expectedResult = "[[\"firstProperty\",\"asc\"],[\"secondProperty\",\"desc\"]]";

        String convertedSorting = OpenProjectSortingHandler.convertSorting(sortEntries);

        JsonNode expectedJson = mapper.readTree(expectedResult);
        JsonNode actualJson = mapper.readTree(convertedSorting);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void convertEmptySortEntries() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.SortEntry> sortEntries = new ArrayList<>();

        String expectedResult = "[]";

        String convertedSorting = OpenProjectSortingHandler.convertSorting(sortEntries);

        JsonNode expectedJson = mapper.readTree(expectedResult);
        JsonNode actualJson = mapper.readTree(convertedSorting);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void convertUsingOPProperties() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.SortEntry> sortEntries = new ArrayList<>();
        LiveDataQuery.SortEntry sortEntry1 = new LiveDataQuery.SortEntry("identifier", false);
        LiveDataQuery.SortEntry sortEntry2 = new LiveDataQuery.SortEntry("summary", true);
        sortEntries.add(sortEntry1);
        sortEntries.add(sortEntry2);

        String expectedResult = "[[\"id\",\"asc\"],[\"subject\",\"desc\"]]";

        String convertedSorting = OpenProjectSortingHandler.convertSorting(sortEntries);

        JsonNode expectedJson = mapper.readTree(expectedResult);
        JsonNode actualJson = mapper.readTree(convertedSorting);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void mergeSortEntries() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.SortEntry> sortEntries = new ArrayList<>();
        LiveDataQuery.SortEntry sortEntry1 = new LiveDataQuery.SortEntry("property1", false);
        LiveDataQuery.SortEntry sortEntry2 = new LiveDataQuery.SortEntry("property2", true);
        sortEntries.add(sortEntry1);
        sortEntries.add(sortEntry2);

        String stringSortEntries = "property3:asc,property4:desc";

        String expectedResult = "[[\"property1\",\"asc\"],[\"property2\",\"desc\"],[\"property3\","
            + "\"asc\"],[\"property4\",\"desc\"]]";

        String mergedSortingString = OpenProjectSortingHandler.mergeSortEntries(sortEntries, stringSortEntries);

        JsonNode expectedJson = mapper.readTree(expectedResult);
        JsonNode actualJson = mapper.readTree(mergedSortingString);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void mergeSortEntriesWithSameKeys() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.SortEntry> sortEntries = new ArrayList<>();
        LiveDataQuery.SortEntry sortEntry1 = new LiveDataQuery.SortEntry("property1", false);
        LiveDataQuery.SortEntry sortEntry2 = new LiveDataQuery.SortEntry("property2", false);
        sortEntries.add(sortEntry1);
        sortEntries.add(sortEntry2);

        String stringSortEntries = "property1:desc";

        String expectedResult = "[[\"property1\",\"asc\"],[\"property2\",\"asc\"]]";

        String mergedSortingString = OpenProjectSortingHandler.mergeSortEntries(sortEntries, stringSortEntries);

        JsonNode expectedJson = mapper.readTree(expectedResult);
        JsonNode actualJson = mapper.readTree(mergedSortingString);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void mergeSortEntriesWithSameMappedKeys() throws ProjectManagementException, JsonProcessingException
    {
        // The stored sort uses the OpenProject names, as in the query_props of an OpenProject URL, while the Live Data
        // sort uses the work item properties: the Live Data sort wins for the properties mapped to the same column.
        List<LiveDataQuery.SortEntry> sortEntries = List.of(new LiveDataQuery.SortEntry("identifier", true),
            new LiveDataQuery.SortEntry("summary", false));

        String mergedSortingString =
            OpenProjectSortingHandler.mergeSortEntries(sortEntries, "subject:desc,id:asc,identifier:asc,status:desc");

        assertEquals(mapper.readTree("[[\"id\",\"desc\"],[\"subject\",\"asc\"],[\"status\",\"desc\"]]"),
            mapper.readTree(mergedSortingString));
    }

    @Test
    void mergeSortEntriesWithoutDirection() throws ProjectManagementException, JsonProcessingException
    {
        String mergedSortingString =
            OpenProjectSortingHandler.mergeSortEntries(List.of(), "id,subject:,status:desc, type : desc ");

        // OpenProject sorts in ascending order when no direction is given.
        assertEquals(
            mapper.readTree("[[\"id\",\"asc\"],[\"subject\",\"asc\"],[\"status\",\"desc\"],[\"type\",\"desc\"]]"),
            mapper.readTree(mergedSortingString));
    }

    @Test
    void mergeSortEntriesWithBlankEntries() throws ProjectManagementException, JsonProcessingException
    {
        List<LiveDataQuery.SortEntry> sortEntries = List.of(new LiveDataQuery.SortEntry("summary", true));

        String mergedSortingString = OpenProjectSortingHandler.mergeSortEntries(sortEntries, ", ,id:desc,,:asc, ");

        assertEquals(mapper.readTree("[[\"subject\",\"desc\"],[\"id\",\"desc\"]]"),
            mapper.readTree(mergedSortingString));

        assertEquals(mapper.readTree("[[\"subject\",\"desc\"]]"),
            mapper.readTree(OpenProjectSortingHandler.mergeSortEntries(sortEntries, " ")));
    }
}
