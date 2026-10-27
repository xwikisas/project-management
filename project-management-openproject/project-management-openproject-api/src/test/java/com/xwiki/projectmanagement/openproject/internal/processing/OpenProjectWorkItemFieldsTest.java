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

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.Version;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectWorkItemFields}.
 *
 * @version $Id$
 */
class OpenProjectWorkItemFieldsTest
{
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
    void mapFilters() throws Exception
    {
        Version version1 = version(10, "1.2.3");
        Version version2 = version(11, "1.2.3");
        when(this.client.getVersions()).thenReturn(
            new PaginatedResult<>(List.of(version1, version2, version(12, "2.0"), version1), 0, 4, 4));

        LiveDataQuery.Filter versionFilter = new LiveDataQuery.Filter("targetVersion", "contains", "1.2.3");
        versionFilter.getConstraints().add(new LiveDataQuery.Constraint("12", "contains"));
        versionFilter.getConstraints().add(new LiveDataQuery.Constraint("unknown", "contains"));
        LiveDataQuery.Filter statusFilter = new LiveDataQuery.Filter("status", "o", "");

        List<LiveDataQuery.Filter> mappedFilters = this.fields.mapFilters(List.of(versionFilter, statusFilter,
            new LiveDataQuery.Filter("customField6", "contains", "x"),
            new LiveDataQuery.Filter("category", "equals", "24")), this.client);

        LiveDataQuery.Filter mappedVersionFilter = mappedFilters.get(0);
        assertEquals("targetVersion", mappedVersionFilter.getProperty());
        // The name of a version is replaced by the ids of all the versions with this name, an unknown name is kept so
        // that OpenProject reports it.
        assertEquals(List.of("10", "11", "12", "unknown"), getValues(mappedVersionFilter));
        assertTrue(mappedVersionFilter.getConstraints().stream()
            .allMatch(constraint -> "equals".equals(constraint.getOperator())));

        assertSame(statusFilter, mappedFilters.get(1));

        // A text filter keeps its operator.
        assertEquals("customField6", mappedFilters.get(2).getProperty());
        assertEquals("contains", mappedFilters.get(2).getConstraints().get(0).getOperator());

        // A field without filter is sent as it is, so that OpenProject reports it.
        assertEquals("category", mappedFilters.get(3).getProperty());
    }

    @Test
    void mapFiltersWithVersionIds() throws Exception
    {
        List<LiveDataQuery.Filter> mappedFilters =
            this.fields.mapFilters(List.of(new LiveDataQuery.Filter("Observed in", "=", "10")), this.client);

        assertEquals("customField3", mappedFilters.get(0).getProperty());
        assertEquals(List.of("10"), getValues(mappedFilters.get(0)));
        verify(this.client, never()).getVersions();
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

    private static List<Object> getValues(LiveDataQuery.Filter filter)
    {
        return filter.getConstraints().stream().map(LiveDataQuery.Constraint::getValue).collect(Collectors.toList());
    }

    private static Version version(int id, String name)
    {
        Version version = new Version();
        version.setId(id);
        version.setName(name);
        return version;
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
