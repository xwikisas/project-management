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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private static final Pattern QUERY_FILTERS_PATTERN = Pattern.compile("[?&]filters=([^&]+)");

    private static final Pattern QUERY_SORT_BY_PATTERN = Pattern.compile("[?&]sortBy=([^&]+)");

    /**
     * The path of the work packages of a project, in the results link of a saved query bound to a project. Recent
     * OpenProject versions name the projects workspaces in their API (e.g. {@code /api/v3/workspaces/9/work_packages}).
     */
    private static final Pattern QUERY_PROJECT_PATTERN =
        Pattern.compile("/api/v3/(?:projects|workspaces)/([^/?]+)/work_packages");

    /**
     * The column of the manual sorting of a saved query, which is stored with the query and can't be applied to a work
     * package list.
     */
    private static final String MANUAL_SORTING = "manualSorting";

    /**
     * The parameter of the results link of a saved query that excludes the work packages of the subprojects, which a
     * work package list can't express.
     */
    private static final Pattern QUERY_WITHOUT_SUBPROJECTS_PATTERN =
        Pattern.compile("[?&]includeSubprojects=false(?:&|$)");

    private static final Pattern QUERY_TIMESTAMPS_PATTERN = Pattern.compile("[?&]timestamps=([^&]*)");

    /**
     * The timestamps of a saved query showing the current state of its work packages, without baseline comparison.
     */
    private static final String CURRENT_TIMESTAMP = "PT0S";

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /**
     * Expresses a saved query as the link of a work package list carrying its filters and its sorting in its
     * {@code query_props} parameter, as OpenProject writes them in the links of the work package lists, without any
     * request to OpenProject. The list keeps the project of the query, but it can't express what is stored with the
     * query only: its manual sorting, the exclusion of the work packages of the subprojects, and the comparison with a
     * baseline (whose timestamps change the selected work packages). The display settings of the query, such as its
     * grouping, are ignored.
     *
     * @param link the link of the saved query, e.g. {@code https://op.example.com/work_packages?query_id=5}
     * @param resultsUrl the results link of the query, as returned by
     *     {@link #getSavedQueryResultsUrl(OpenProjectApiClient, String)}
     * @return the link of the work package list selecting the work packages of the query, in the order of the query,
     *     e.g. {@code https://op.example.com/projects/9/work_packages?query_props=...}; {@code null} when the query is
     *     sorted manually, excludes the subprojects or compares with a baseline, or when the link has no path of a
     *     work package list
     * @throws WorkItemRetrievalException if the filters or the sorting of the query can't be read
     * @since 1.5.0
     */
    public String getSavedQueryLink(OpenProjectLink link, String resultsUrl) throws WorkItemRetrievalException
    {
        List<String> sortEntries = getSavedQuerySortEntries(resultsUrl);
        if (sortEntries == null || !isExpressibleAsList(resultsUrl)) {
            return null;
        }
        ObjectNode queryProperties = this.objectMapper.createObjectNode();
        ArrayNode filters = queryProperties.putArray("f");
        for (JsonNode filter : getSavedQueryFilters(resultsUrl)) {
            Iterator<Map.Entry<String, JsonNode>> fields = filter.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                ObjectNode linkFilter = filters.addObject();
                linkFilter.put("n", field.getKey());
                linkFilter.set("o", field.getValue().path("operator"));
                linkFilter.set("v", field.getValue().path("values"));
            }
        }
        if (!sortEntries.isEmpty()) {
            queryProperties.put("t", String.join(",", sortEntries));
        }
        return link.toWorkPackageList(getSavedQueryProject(resultsUrl), queryProperties.toString());
    }

    /**
     * @return {@code true} if the saved query selects the work packages of the subprojects (or doesn't say) and shows
     *     their current state, as a work package list does
     */
    private static boolean isExpressibleAsList(String resultsUrl)
    {
        Matcher timestamps = QUERY_TIMESTAMPS_PATTERN.matcher(resultsUrl);
        return !QUERY_WITHOUT_SUBPROJECTS_PATTERN.matcher(resultsUrl).find() && (!timestamps.find()
            || CURRENT_TIMESTAMP.equals(URLDecoder.decode(timestamps.group(1), StandardCharsets.UTF_8)));
    }

    /**
     * @return the sorting of the saved query, as written in the {@code query_props} parameter of the links of the work
     *     package lists (e.g. {@code id:desc}), or {@code null} when the query is sorted manually
     */
    private List<String> getSavedQuerySortEntries(String resultsUrl) throws WorkItemRetrievalException
    {
        String sortBy = getSavedQuerySortBy(resultsUrl);
        List<String> sortEntries = new ArrayList<>();
        if (sortBy.isEmpty()) {
            return sortEntries;
        }
        JsonNode entries;
        try {
            entries = this.objectMapper.readTree(sortBy);
        } catch (JsonProcessingException e) {
            throw new WorkItemRetrievalException("Failed to read the sorting of the saved query", e);
        }
        for (JsonNode entry : entries) {
            String column = entry.path(0).asText();
            if (MANUAL_SORTING.equals(column)) {
                return null;
            }
            sortEntries.add(column + ':' + entry.path(1).asText());
        }
        return sortEntries;
    }
}
