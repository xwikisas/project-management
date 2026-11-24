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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link OpenProjectLinkQuery}.
 *
 * @version $Id$
 */
class OpenProjectLinkQueryTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROJECT_LIST = "https://op.example.com/projects/demo/work_packages";

    private static final OpenProjectWorkItemFields FIELDS = new OpenProjectWorkItemFields(List.of(
        field("version", "Version", "[]Version"),
        field("customField3", "Approved", "Boolean"),
        field("customField4", "Deadline", "Date"),
        field("customField5", "Labels", "[]CustomOption"),
        field("customField6", "Review: final", "Date")));

    private static final Set<String> LIST = Set.of("=", "!");

    private static final Set<String> DATE = Set.of("t", "w", "=d", "between");

    /**
     * The operators of the column filters, as configured by the OpenProject client.
     */
    private static final Map<String, Set<String>> OPERATORS = Map.ofEntries(Map.entry("identifier", LIST),
        Map.entry("type", LIST), Map.entry("summary", Set.of("~", "!~")), Map.entry("startDate", DATE),
        Map.entry("dueDate", DATE), Map.entry("creationDate", DATE), Map.entry("updateDate", DATE),
        Map.entry("progress", Set.of("=", "!", ">=", "<=", "*", "!*")), Map.entry("creator", LIST),
        Map.entry("assignees", Set.of("=", "!", "*", "!*")), Map.entry("status", Set.of("=", "!", "o", "c", "*")),
        Map.entry("targetVersion", Set.of("=", "!", "*", "!*")), Map.entry("Approved", Set.of("=")),
        Map.entry("Labels", Set.of("=", "&=", "!")));

    @Test
    void readsTheFiltersAndTheSortingOfAWorkPackageList()
    {
        OpenProjectLinkQuery query = read(PROJECT_LIST + "?query_props=", "{\"c\":[\"id\",\"subject\"],\"hi\":false,"
            + "\"t\":\"id:desc,subject\",\"f\":[{\"n\":\"type\",\"o\":\"=\",\"v\":[\"1\",\"2\"]},"
            + "{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"subject\",\"o\":\"~\",\"v\":[\"page\"]},"
            + "{\"n\":\"assignee\",\"o\":\"=\",\"v\":[\"me\"]},{\"n\":\"author\",\"o\":\"!\",\"v\":[\"4\"]},"
            + "{\"n\":\"percentageDone\",\"o\":\">=\",\"v\":[\"20\"]},"
            + "{\"n\":\"createdAt\",\"o\":\"<>d\",\"v\":[\"2026-11-01\",\"\"]},"
            + "{\"n\":\"version\",\"o\":\"=\",\"v\":[\"3\"]},{\"n\":\"customField3\",\"o\":\"=\",\"v\":[\"t\"]},"
            + "{\"n\":\"customField5\",\"o\":\"&=\",\"v\":[\"8\",\"9\"]}],\"pp\":20}", FIELDS);

        // The filters keep their OpenProject operators, except the ranges of dates.
        assertEquals(List.of("type = [1, 2]", "status o []", "summary ~ [page]", "assignees = [me]",
            "creator ! [4]", "progress >= [20]", "creationDate between [2026-11-01/]",
            "targetVersion = [3]", "Approved = [true]", "Labels &= [8, 9]"), describe(query.getFilters()));
        assertEquals(List.of("identifier:desc", "summary:asc"), describeSort(query.getSortEntries()));
        // Every filter is shown by the Live Data, so the link has none left: none is applied twice.
        assertEquals(PROJECT_LIST, query.getLink());
    }

    @Test
    void keepsInTheLinkTheFiltersThatTheLiveDataCantShow()
    {
        // Filters without column, a date custom field without column filter, and operators that the column filter
        // doesn't offer, such as the relative dates.
        String linkFilters = "[{\"n\":\"search\",\"o\":\"**\",\"v\":[\"wiki\"]},"
            + "{\"n\":\"datesInterval\",\"o\":\"<>d\",\"v\":[\"2026-11-01\",\"2026-11-30\"]},"
            + "{\"n\":\"customField4\",\"o\":\"=d\",\"v\":[\"2026-11-20\"]},"
            + "{\"n\":\"dueDate\",\"o\":\"<t+\",\"v\":[\"7\"]},{\"n\":\"updatedAt\",\"o\":\">t-\",\"v\":[\"7\"]},"
            + "{\"n\":\"createdAt\",\"o\":\"!*\",\"v\":[]}]";
        OpenProjectLinkQuery query = read(PROJECT_LIST + "?query_id=5&query_props=",
            "{\"f\":[{\"n\":\"status\",\"o\":\"c\",\"v\":[]}," + linkFilters.substring(1) + ",\"t\":\"customField3:asc,"
                + "position:desc,customField6:desc\"}", FIELDS);

        assertEquals(List.of("status c []"), describe(query.getFilters()));
        // The columns of the instance fields are named after their property, unless its name can't be written in the
        // sort parameter; the other OpenProject columns are kept.
        assertEquals(List.of("Approved:asc", "position:desc", "customField6:desc"),
            describeSort(query.getSortEntries()));
        assertEquals(PROJECT_LIST + "?query_props=" + encode("{\"f\":" + linkFilters + "}"), query.getLink());
    }

    @Test
    void readsTheInstanceFieldsByKeyWhenTheirFieldsAreUnknown()
    {
        OpenProjectLinkQuery query = read("https://op.example.com/work_packages?query_props=",
            "{\"f\":[{\"n\":\"assigned_to\",\"o\":\"!*\",\"v\":[]},{\"n\":\"customField3\",\"o\":\"=\",\"v\":[\"t\"]}],"
                + "\"t\":\"customField3:desc\"}", OpenProjectWorkItemFields.empty());

        // Without the fields of the instance, the custom field filter stays in the link, applied by OpenProject.
        assertEquals(List.of("assignees !* []"), describe(query.getFilters()));
        assertEquals(List.of("customField3:desc"), describeSort(query.getSortEntries()));
        assertEquals("https://op.example.com/work_packages?query_props="
            + encode("{\"f\":[{\"n\":\"customField3\",\"o\":\"=\",\"v\":[\"t\"]}]}"), query.getLink());
    }

    @Test
    void theLiveDataFiltersSelectTheSameWorkPackagesAsTheLink() throws Exception
    {
        OpenProjectLinkQuery query = read(PROJECT_LIST + "?query_props=", "{\"f\":[{\"n\":\"type\",\"o\":\"=\","
            + "\"v\":[\"1\",\"2\"]},{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"subject\",\"o\":\"~\","
            + "\"v\":[\"page\"]},{\"n\":\"startDate\",\"o\":\"<>d\",\"v\":[\"\",\"2026-11-30\"]},"
            + "{\"n\":\"customField3\",\"o\":\"=\",\"v\":[\"f\"]}]}", FIELDS);

        // The filters requested from OpenProject once the Live Data filters are translated are the ones of the link.
        assertEquals(MAPPER.readTree("[{\"type\":{\"operator\":\"=\",\"values\":[\"1\",\"2\"]}},"
                + "{\"status\":{\"operator\":\"o\",\"values\":[\"\"]}},"
                + "{\"subject\":{\"operator\":\"~\",\"values\":[\"page\"]}},"
                + "{\"startDate\":{\"operator\":\"<>d\",\"values\":[\"\",\"2026-11-30\"]}},"
                + "{\"customField3\":{\"operator\":\"=\",\"values\":[\"f\"]}}]"),
            MAPPER.readTree(new OpenProjectFilterTranslator(FIELDS, null).translate(query.getFilters()).toJson()));
    }

    @Test
    void readsTheFiltersAndTheSortingOfASavedQuery() throws Exception
    {
        // The results link of a saved query of a project, as returned by OpenProject 17.9.
        String resultsUrl = "/api/v3/workspaces/3/work_packages?filters=" + encode("[{\"status\":{\"operator\":\"o\","
            + "\"values\":[]}},{\"type\":{\"operator\":\"=\",\"values\":[\"1\"]}},{\"search\":{\"operator\":\"**\","
            + "\"values\":[\"wiki\"]}}]") + "&includeSubprojects=true&offset=1&pageSize=20&showHierarchies=true"
            + "&showSums=false&sortBy=" + encode("[[\"id\",\"asc\"]]") + "&timestamps=PT0S";
        String savedQueryLink = new OpenProjectIdentifierResolver().getSavedQueryLink(
            OpenProjectLink.parse("https://op.example.com/work_packages?query_id=5"), resultsUrl);

        OpenProjectLinkQuery query =
            OpenProjectLinkQuery.read(OpenProjectLink.parse(savedQueryLink), () -> columns(FIELDS));

        assertEquals(List.of("status o []", "type = [1]"), describe(query.getFilters()));
        assertEquals(List.of("identifier:asc"), describeSort(query.getSortEntries()));
        // The list of the project of the query keeps the filter that the Live Data can't show.
        assertEquals("https://op.example.com/projects/3/work_packages?query_props="
            + encode("{\"f\":[{\"n\":\"search\",\"o\":\"**\",\"v\":[\"wiki\"]}]}"), query.getLink());
    }

    @ParameterizedTest
    @ValueSource(strings = { PROJECT_LIST, PROJECT_LIST + "?query_id=5", "https://op.example.com/wp/5?query_props=%7B%7D",
        PROJECT_LIST + "/12/activity", PROJECT_LIST + "?query_props=%7B%22c%22%3A%5B%22id%22%5D%7D" })
    void readsNothingFromTheLinksWithoutFilters(String link)
    {
        // The work package lists without filter nor sorting, the saved queries and the single work packages.
        assertNull(OpenProjectLinkQuery.read(OpenProjectLink.parse(link), () -> columns(FIELDS)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "not json", "[1]", "%E0%A4%A" })
    void rejectsTheInvalidQueryProperties(String queryProperties)
    {
        OpenProjectLink link = OpenProjectLink.parse(PROJECT_LIST + "?query_props=" + queryProperties);

        assertThrows(IllegalArgumentException.class, () -> OpenProjectLinkQuery.read(link, () -> columns(FIELDS)));
    }

    private static OpenProjectLinkQuery read(String link, String queryProperties, OpenProjectWorkItemFields fields)
    {
        return OpenProjectLinkQuery.read(OpenProjectLink.parse(link + encode(queryProperties)),
            () -> columns(fields));
    }

    private static OpenProjectLinkQuery.Columns columns(OpenProjectWorkItemFields fields)
    {
        return new OpenProjectLinkQuery.Columns(fields, OPERATORS);
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void countFilters()
    {
        assertEquals(2, countFilters(PROJECT_LIST + "?query_props=" + encode("{\"t\":\"id:desc\",\"f\":["
            + "{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"type\",\"o\":\"=\",\"v\":[\"1\"]}]}")));
        assertEquals(0, countFilters(PROJECT_LIST + "?query_props=" + encode("{\"t\":\"id:desc\"}")));
        assertEquals(0, countFilters(PROJECT_LIST));
        // The filters of a saved query are on OpenProject, and a work package page has none.
        assertEquals(0, countFilters(PROJECT_LIST + "?query_id=5"));
        assertEquals(0, countFilters(PROJECT_LIST + "/5?query_props=" + encode("{\"f\":[{\"n\":\"status\"}]}")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "?query_props=%5B%5D", "?query_id=abc", "?query_id=5&page=2", "?per_page=20" })
    void countFiltersOfAnInvalidLink(String query)
    {
        // The client can't read the parameters of a work package list without query_props.
        assertThrows(IllegalArgumentException.class, () -> countFilters(PROJECT_LIST + query));
    }

    private static int countFilters(String link)
    {
        return OpenProjectLinkQuery.countFilters(OpenProjectLink.parse(link));
    }

    private static List<String> describe(List<LiveDataQuery.Filter> filters)
    {
        return filters.stream().map(filter -> String.format("%s %s %s", filter.getProperty(),
            filter.getConstraints().get(0).getOperator(), filter.getConstraints().stream()
                .map(constraint -> String.valueOf(constraint.getValue())).filter(value -> !value.isEmpty())
                .collect(Collectors.toList()))).collect(Collectors.toList());
    }

    private static List<String> describeSort(List<LiveDataQuery.SortEntry> sortEntries)
    {
        return sortEntries.stream()
            .map(sortEntry -> sortEntry.getProperty() + ':' + (sortEntry.isDescending() ? "desc" : "asc"))
            .collect(Collectors.toList());
    }

    private static WorkPackageField field(String key, String name, String filterValueType)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(name);
        field.setFilterKey(key);
        field.setFilterValueType(filterValueType);
        field.setSortable(true);
        return field;
    }
}
