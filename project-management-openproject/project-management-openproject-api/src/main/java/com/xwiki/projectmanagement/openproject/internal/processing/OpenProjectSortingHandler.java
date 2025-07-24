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

import java.util.List;
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

    private OpenProjectSortingHandler()
    {

    }

    /**
     * Converts a list of {@link LiveDataQuery.SortEntry} objects into a string representation compatible with
     * OpenProject's query sorting format.
     *
     * @param sortEntries the list of LiveData sort entries to be converted
     * @return a string representing the sort criteria in the format expected by the OpenProject API
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

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(convertedSortEntries);
        } catch (JsonProcessingException e) {
            throw new ProjectManagementException(
                "Failed to convert the project management sort entries into Open Project sortBy entry.", e);
        }
    }
}
