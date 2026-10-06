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

import org.junit.jupiter.api.Test;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectIdentifierResolver}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectIdentifierResolverTest
{
    private static final String QUERY_ID = "27";

    private static final String ENCODED_FILTERS =
        "%5B%7B%22status%22%3A%7B%22operator%22%3A%22o%22%2C%22values%22%3A%5B%5D%7D%7D%5D";

    private static final String ENCODED_SORT_BY = "%5B%5B%22id%22%2C%22desc%22%5D%5D";

    private static final String FILTERS = "[{\"status\":{\"operator\":\"o\",\"values\":[]}}]";

    @InjectMockComponents
    private OpenProjectIdentifierResolver resolver;

    @MockComponent
    private OpenProjectApiClient apiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getStandaloneQueryId()
    {
        assertEquals(QUERY_ID, resolver.getStandaloneQueryId("https://op.example.org/work_packages?query_id=27"));
        assertEquals(QUERY_ID,
            resolver.getStandaloneQueryId("https://op.example.org/projects/demo/work_packages?query_id=27"));
        assertEquals(QUERY_ID, resolver.getStandaloneQueryId("  http://op.example.org/work_packages?query_id=27  "));
    }

    @Test
    void getStandaloneQueryIdWhenNotAStandaloneQuery()
    {
        assertNull(resolver.getStandaloneQueryId(null));
        assertNull(resolver.getStandaloneQueryId("1,2,3"));
        assertNull(resolver.getStandaloneQueryId("query_id=27"));
        assertNull(resolver.getStandaloneQueryId("https://op.example.org/work_packages"));
        assertNull(resolver.getStandaloneQueryId("https://op.example.org/work_packages?query_id=abc"));
        // A tweaked saved query has to be handled as a regular URL, so that the tweaks are applied.
        assertNull(resolver.getStandaloneQueryId(
            "https://op.example.org/work_packages?query_id=27&query_props=%7B%22t%22%3A%22id%3Aasc%22%7D"));
        assertNull(resolver.getStandaloneQueryId(
            "https://op.example.org/work_packages?query_props=%7B%22t%22%3A%22id%3Aasc%22%7D"));
    }

    @Test
    void getSavedQueryResultsUrl() throws ProjectManagementException
    {
        String resultsUrl = "/api/v3/projects/1/work_packages?filters=" + ENCODED_FILTERS;
        when(apiClient.getQueryResultsUrl(QUERY_ID)).thenReturn(resultsUrl);

        assertEquals(resultsUrl, resolver.getSavedQueryResultsUrl(apiClient, QUERY_ID));
    }

    @Test
    void getSavedQueryResultsUrlWhenTheQueryHasNoResultsLink() throws ProjectManagementException
    {
        // This is what the api client returns when the query has no results link.
        when(apiClient.getQueryResultsUrl(QUERY_ID)).thenReturn("");

        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> resolver.getSavedQueryResultsUrl(apiClient, QUERY_ID));
        assertEquals("The saved query [27] has no results link.", exception.getMessage());
    }

    @Test
    void getSavedQueryFiltersFromRelativeResultsUrl() throws Exception
    {
        String resultsUrl = "/api/v3/projects/1/work_packages?offset=1&filters=" + ENCODED_FILTERS
            + "&sortBy=" + ENCODED_SORT_BY + "&pageSize=20";

        assertEquals(objectMapper.readTree(FILTERS), resolver.getSavedQueryFilters(resultsUrl));
    }

    @Test
    void getSavedQueryFiltersFromAbsoluteResultsUrl() throws Exception
    {
        String resultsUrl = "https://op.example.org/api/v3/workspaces/1/work_packages?filters=" + ENCODED_FILTERS;

        assertEquals(objectMapper.readTree(FILTERS), resolver.getSavedQueryFilters(resultsUrl));
    }

    @Test
    void getSavedQueryFiltersWhenTheQueryHasNone() throws WorkItemRetrievalException
    {
        assertTrue(resolver.getSavedQueryFilters("/api/v3/work_packages?offset=1&pageSize=20").isEmpty());
    }

    @Test
    void getSavedQueryFiltersWhenTheyCannotBeRead()
    {
        WorkItemRetrievalException exception = assertThrows(WorkItemRetrievalException.class,
            () -> resolver.getSavedQueryFilters("/api/v3/work_packages?filters=%5B%7Bnot-json"));
        assertEquals("Failed to read the filters of the saved query", exception.getMessage());
    }

    @Test
    void getSavedQuerySortBy()
    {
        assertEquals("[[\"id\",\"desc\"]]",
            resolver.getSavedQuerySortBy("/api/v3/work_packages?sortBy=" + ENCODED_SORT_BY + "&pageSize=20"));
    }

    @Test
    void getSavedQuerySortByWhenTheQueryHasNone()
    {
        assertEquals("", resolver.getSavedQuerySortBy("/api/v3/work_packages?filters=" + ENCODED_FILTERS));
    }
}
