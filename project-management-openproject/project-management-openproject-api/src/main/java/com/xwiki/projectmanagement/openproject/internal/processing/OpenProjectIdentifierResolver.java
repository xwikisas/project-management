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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;

/**
 * Turns the identifier of an OpenProject macro into the filters and sorting it stands for, in the format used by the
 * OpenProject API. Resolving can require requests to OpenProject (i.e. reading a saved query), so it is meant to be
 * done once, before the work packages are requested, rather than for each request.
 *
 * @version $Id$
 * @since 1.3.0
 */
@Component(roles = OpenProjectIdentifierResolver.class)
@Singleton
public class OpenProjectIdentifierResolver
{
    /**
     * The source parameter holding the results link of the saved query the identifier points to, retrieved before the
     * macro execution so that the query doesn't have to be read again for each request.
     */
    public static final String SAVED_QUERY_RESULTS_URL = "savedQueryResultsUrl";

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    private static final Pattern URL_QUERY_ID_PATTERN = Pattern.compile("^query_id=(\\d+)$");

    private static final Pattern QUERY_FILTERS_PATTERN = Pattern.compile("[?&]filters=([^&]+)");

    private static final Pattern QUERY_SORT_BY_PATTERN = Pattern.compile("[?&]sortBy=([^&]+)");

    private static final Pattern QUERY_PROJECT_PATTERN = Pattern.compile("/api/v3/projects/([^/?]+)/work_packages");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param identifier the identifier of the macro.
     * @return the id of the saved query the identifier points to, or {@code null} if the identifier is not an
     *     OpenProject URL.
     */
    public String getStandaloneQueryId(String identifier)
    {
        if (identifier == null || !URL_PATTERN.matcher(identifier.trim()).matches()) {
            return null;
        }

        String query;

        try {
            query = new URL(identifier.trim()).getQuery();
        } catch (MalformedURLException e) {
            return null;
        }

        if (query == null) {
            return null;
        }

        Matcher matcher = URL_QUERY_ID_PATTERN.matcher(query.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * Retrieves the results link OpenProject generates for a saved query. It holds the filters and sorting of the
     * query, which can then be read with {@link #getSavedQueryFilters(String)} and
     * {@link #getSavedQuerySortBy(String)}.
     *
     * @param client the client used to retrieve the query.
     * @param queryId the id of the saved query.
     * @return the results link of the query.
     * @throws ProjectManagementException if the query couldn't be retrieved or has no results link.
     */
    public String getSavedQueryResultsUrl(OpenProjectApiClient client, String queryId)
        throws ProjectManagementException
    {
        String resultsUrl = client.getQueryResultsUrl(queryId);

        if (resultsUrl == null || resultsUrl.isEmpty()) {
            throw new WorkItemRetrievalException(
                String.format("The saved query [%s] has no results link.", queryId));
        }

        return resultsUrl;
    }

    /**
     * Reads the filters of a saved query from its results link, without any request to OpenProject.
     *
     * @param resultsUrl the results link of the query, as returned by
     *     {@link #getSavedQueryResultsUrl(OpenProjectApiClient, String)}.
     * @return the filters of the query, in the OpenProject API format, i.e.
     *     {@code [{"status":{"operator":"o","values":[]}}]}. An empty array when the query has none.
     * @throws WorkItemRetrievalException if the filters of the query can't be read.
     */
    public JsonNode getSavedQueryFilters(String resultsUrl) throws WorkItemRetrievalException
    {
        Matcher matcher = QUERY_FILTERS_PATTERN.matcher(resultsUrl);

        if (!matcher.find()) {
            return objectMapper.createArrayNode();
        }

        try {
            return objectMapper.readTree(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new WorkItemRetrievalException("Failed to read the filters of the saved query", e);
        }
    }

    /**
     * Reads the sorting of a saved query from its results link, without any request to OpenProject.
     *
     * @param resultsUrl the results link of the query, as returned by
     *     {@link #getSavedQueryResultsUrl(OpenProjectApiClient, String)}.
     * @return the sorting of the query, in the OpenProject API format, i.e. {@code [["id","asc"]]}. An empty string
     *     when the query has none.
     */
    public String getSavedQuerySortBy(String resultsUrl)
    {
        Matcher matcher = QUERY_SORT_BY_PATTERN.matcher(resultsUrl);

        return matcher.find() ? URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8) : "";
    }

    /**
     * Reads the project a saved query belongs to from its results link, without any request to OpenProject.
     *
     * @param resultsUrl the results link of the query, as returned by
     * @return the id of the project of the query, or {@code null} when the query isn't bound to a project.
     */
    public String getSavedQueryProject(String resultsUrl)
    {
        Matcher matcher = QUERY_PROJECT_PATTERN.matcher(resultsUrl);

        return matcher.find() ? matcher.group(1) : null;
    }
}
