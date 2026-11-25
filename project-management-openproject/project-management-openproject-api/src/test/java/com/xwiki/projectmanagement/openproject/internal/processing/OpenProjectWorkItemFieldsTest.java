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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectWorkItemFields}.
 *
 * @version $Id$
 */
class OpenProjectWorkItemFieldsTest
{
    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private static final String TARGET_VERSIONS = "targetVersions";

    private static final String LIST_OF_VERSIONS = "[]Version";

    private final OpenProjectApiClient client = mock(OpenProjectApiClient.class);

    private final OpenProjectWorkItemFields fields = new OpenProjectWorkItemFields(List.of(
        field("id", "ID", "id", "[]WorkPackage", true),
        field("status", "Status", "status", "[]Status", true),
        field(TARGET_VERSIONS, "Target versions", "targetVersion", LIST_OF_VERSIONS, true),
        field("category", "Category", null, null, true),
        field("estimatedTime", "Work", "estimatedTime", "[1]Integer", true),
        field("spentTime", "Spent time", null, null, false),
        field("customField3", "Observed in", "customField3", LIST_OF_VERSIONS, true),
        field("customField5", "Labels", "customField5", "[]CustomOption", true),
        field("customField6", "category", "customField6", "[1]String", true),
        field("customField7", "status", "customField7", "[1]String", true),
        field("customField8", "", "customField8", "[1]String", true)));

    @Test
    void getFields()
    {
        // The fields covered by the generic properties are left out, the target version has a stable id, the custom
        // fields are identified by their name unless it is already taken.
        assertEquals(List.of("targetVersion", "category", "estimatedTime", "spentTime", "Observed in", "Labels",
            "customField6", "customField7", "customField8"), new ArrayList<>(this.fields.getFields().keySet()));
        assertEquals(TARGET_VERSIONS, this.fields.getFields().get("targetVersion").getKey());
    }

    @Test
    void ofClient() throws Exception
    {
        assertSame(OpenProjectWorkItemFields.empty(), OpenProjectWorkItemFields.of(this.client));

        when(this.client.getWorkPackageFields()).thenReturn(
            new PaginatedResult<>(List.of(field("category", "Category", null, null, true)), 0, 1, 1));
        assertEquals(List.of("category"),
            new ArrayList<>(OpenProjectWorkItemFields.of(this.client).getFields().keySet()));
    }

    @Test
    void getValues()
    {
        WorkPackage workPackage = new WorkPackage();
        Linkable version = new Linkable("1.2", "http://op/versions/2");
        workPackage.setFieldValues(Map.of(
            TARGET_VERSIONS, List.of(version),
            "category", new Linkable("Generic", null),
            "estimatedTime", "8h",
            "customField5", "unexpected text",
            "customField6", List.of(new Linkable("a", null), new Linkable("b", null)),
            "customField7", new Linkable("c", null),
            "unknown", "ignored"));

        Map<String, Object> values = this.fields.getValues(workPackage);

        assertEquals(List.of(version), values.get("targetVersion"));
        // The links are always lists, the other values always text.
        assertEquals(List.of(new Linkable("Generic", null)), values.get("category"));
        assertEquals(List.of(new Linkable("unexpected text", null)), values.get("Labels"));
        assertEquals("8h", values.get("estimatedTime"));
        assertEquals("a, b", values.get("customField6"));
        assertEquals("c", values.get("customField7"));
        assertEquals(6, values.size());
    }

    @Test
    void mapSortEntries()
    {
        LiveDataQuery.SortEntry summary = new LiveDataQuery.SortEntry("summary", false);

        List<LiveDataQuery.SortEntry> mappedSortEntries = this.fields.mapSortEntries(List.of(summary,
            new LiveDataQuery.SortEntry("targetVersion", true), new LiveDataQuery.SortEntry("spentTime", false)));

        // The fields that can't be sorted are dropped.
        assertEquals(2, mappedSortEntries.size());
        assertSame(summary, mappedSortEntries.get(0));
        assertEquals(TARGET_VERSIONS, mappedSortEntries.get(1).getProperty());
        assertTrue(mappedSortEntries.get(1).isDescending());
    }

    @Test
    void mapSortEntriesDropsTheUnknownProperties()
    {
        List<LiveDataQuery.SortEntry> mappedSortEntries = this.fields.mapSortEntries(List.of(
            new LiveDataQuery.SortEntry("identifier.value", false), new LiveDataQuery.SortEntry("status", true),
            new LiveDataQuery.SortEntry("updatedAt", true), new LiveDataQuery.SortEntry("assignee", false),
            new LiveDataQuery.SortEntry("subject", false), new LiveDataQuery.SortEntry("customField3", false),
            new LiveDataQuery.SortEntry("unknown", true), new LiveDataQuery.SortEntry("spentTime", false)));

        // OpenProject rejects a sort on an unknown column, failing the whole request. The generic properties, and the
        // OpenProject columns of the generic properties and of the sortable fields, are kept.
        assertEquals(List.of("status", "updatedAt", "assignee", "subject", "customField3"),
            getProperties(mappedSortEntries));
    }

    @Test
    void getFilterAliases()
    {
        // The filters of an OpenProject 17 instance name the assignee filter "assignee", not "assigned_to".
        OpenProjectWorkItemFields withFilters =
            new OpenProjectWorkItemFields(List.of(), List.of("subject", "assignee", "id", "status", "createdAt"));

        assertEquals(List.of("subject"), withFilters.getFilterAliases("summary"));
        assertEquals(List.of("assignee"), withFilters.getFilterAliases("assignees"));
        assertEquals(List.of("createdAt"), withFilters.getFilterAliases("creationDate"));
        // Named the same in OpenProject, or not a generic property.
        assertEquals(List.of(), withFilters.getFilterAliases("status"));
        assertEquals(List.of(), withFilters.getFilterAliases("Labels"));
        // The filters of the instance couldn't be retrieved: all the OpenProject names may be filters.
        assertEquals(List.of("assigned_to", "assignee"), this.fields.getFilterAliases("assignees"));
    }

    @Test
    void isSortable()
    {
        // The generic properties and OpenProject columns that can be sorted, as kept by mapSortEntries.
        assertEquals(List.of(true, true, true, true, false, false, false, false),
            Stream.of("summary", "updatedAt", "targetVersion", "customField3", "spentTime", "identifier.value",
                "unknown", null).map(this.fields::isSortable).collect(Collectors.toList()));
        // The fields of the instance couldn't be retrieved: an unknown property may be a column of the instance.
        assertTrue(OpenProjectWorkItemFields.empty().isSortable("identifier.value"));
    }

    @Test
    void mapSortEntriesWithUnknownFields()
    {
        List<LiveDataQuery.SortEntry> sortEntries = List.of(new LiveDataQuery.SortEntry("identifier.value", false),
            new LiveDataQuery.SortEntry("version", true), new LiveDataQuery.SortEntry("summary", false));

        // The fields of the instance couldn't be retrieved: the unknown properties may be columns of the instance.
        assertEquals(List.of("identifier.value", "version", "summary"),
            getProperties(OpenProjectWorkItemFields.empty().mapSortEntries(sortEntries)));
    }

    private static List<String> getProperties(List<LiveDataQuery.SortEntry> sortEntries)
    {
        return sortEntries.stream().map(LiveDataQuery.SortEntry::getProperty).collect(Collectors.toList());
    }

    private static WorkPackageField field(String key, String name, String filterKey, String filterValueType,
        boolean sortable)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(name);
        field.setFilterKey(filterKey);
        field.setFilterValueType(filterValueType);
        field.setFilterOperators(filterKey == null ? List.of() : List.of("=", "!", "~", "*", "!*"));
        field.setSortable(sortable);
        return field;
    }
}
