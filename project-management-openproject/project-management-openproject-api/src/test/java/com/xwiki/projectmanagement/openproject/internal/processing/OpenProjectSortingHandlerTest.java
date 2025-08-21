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

public class OpenProjectSortingHandlerTest
{
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertTest() throws ProjectManagementException, JsonProcessingException
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
    void convertEmptySortEntryTest() throws ProjectManagementException, JsonProcessingException
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
        LiveDataQuery.SortEntry sortEntry1 = new LiveDataQuery.SortEntry("identifier.value", false);
        LiveDataQuery.SortEntry sortEntry2 = new LiveDataQuery.SortEntry("summary.value", true);
        sortEntries.add(sortEntry1);
        sortEntries.add(sortEntry2);

        String expectedResult = "[[\"id\",\"asc\"],[\"subject\",\"desc\"]]";

        String convertedSorting = OpenProjectSortingHandler.convertSorting(sortEntries);

        JsonNode expectedJson = mapper.readTree(expectedResult);
        JsonNode actualJson = mapper.readTree(convertedSorting);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void mergeSortEntriesTest() throws ProjectManagementException, JsonProcessingException
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
    void mergeSortEntriesWithSameKeysTest() throws ProjectManagementException, JsonProcessingException
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
}
