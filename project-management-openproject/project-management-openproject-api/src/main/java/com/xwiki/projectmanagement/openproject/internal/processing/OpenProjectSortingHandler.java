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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;

/**
 * Sorting converter handler.
 *
 * @version $Id$
 */
public final class OpenProjectSortingHandler
{
    private static final String ASC = "asc";

    private static final String DESC = "desc";

    private static final String FAILURE_MESSAGE = "Failed to convert Livedata sorting";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenProjectSortingHandler()
    {

    }

    /**
     * Converts a list of {@link LiveDataQuery.SortEntry} objects into a string representation compatible with
     * OpenProject's query sorting format.
     *
     * @param sortEntries the list of LiveData sort entries to be converted
     * @return a string representing the sort criteria in the format expected by the OpenProject API
     * @throws ProjectManagementException if the sortEntries cannot be mapped to a valid OpenProject sort string
     */
    public static String convertSorting(List<LiveDataQuery.SortEntry> sortEntries) throws ProjectManagementException
    {
        List<List<String>> convertedSortEntries = sortEntries
            .stream()
            .map(
                sortEntry -> List.of(
                    OpenProjectMapper.mapLivedataProperty(sortEntry.getProperty()),
                    sortEntry.isDescending() ? DESC : ASC
                )
            )
            .collect(Collectors.toList());

        try {
            return MAPPER.writeValueAsString(convertedSortEntries);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(
                FAILURE_MESSAGE, e);
        }
    }

    /**
     * Combines sorting criteria from two sources: a list of {@link LiveDataQuery.SortEntry} objects and a string
     * representing OpenProject's query sorting format.
     * <p>
     * If the same key exists in both, the value from the SortEntry list takes priority. Returns a string formatted
     * according to OpenProject's sorting format.
     *
     * @param sortEntries a list of {@link LiveDataQuery.SortEntry} objects
     * @param sortByEntries a comma-separated string  representing sort criteria
     * @return a string representing the merged sorting options in OpenProject format
     * @throws ProjectManagementException if the sorting entries cannot be converted properly
     */
    public static String mergeSortEntries(List<LiveDataQuery.SortEntry> sortEntries, String sortByEntries)
        throws ProjectManagementException
    {
        Map<String, String> sortMap = new LinkedHashMap<>();

        sortEntries.forEach(
            entry -> sortMap.put(
                OpenProjectMapper.mapLivedataProperty(entry.getProperty()),
                entry.isDescending() ? DESC : ASC
            )
        );

        if (sortByEntries != null && !sortByEntries.isEmpty()) {
            Arrays.stream(sortByEntries.split(",")).forEach(
                (entry) -> {
                    String[] splitEntry = entry.split(":");
                    if (!sortMap.containsKey(splitEntry[0])) {
                        sortMap.put(
                            OpenProjectMapper.mapLivedataProperty(splitEntry[0]),
                            splitEntry[1]
                        );
                    }
                }
            );
        }

        List<List<String>> listOfEntries = sortMap.entrySet().stream().map(
            entry -> List.of(
                entry.getKey(), entry.getValue()
            )
        ).collect(Collectors.toList());

        try {
            return MAPPER.writeValueAsString(listOfEntries);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(
                FAILURE_MESSAGE, e);
        }
    }
}
