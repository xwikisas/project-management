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
package com.xwiki.projectmanagement.openproject.internal.livedata;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataPropertyDescriptor;

import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WorkPackageFieldDescriptors}.
 *
 * @version $Id$
 */
class WorkPackageFieldDescriptorsTest
{
    private static final List<String> ALL_OPERATORS = List.of("=", "&=", "!", "~", "!~", ">=", "<=", "*", "!*");

    @Test
    void getDescriptors()
    {
        List<LiveDataPropertyDescriptor> descriptors = WorkPackageFieldDescriptors.getDescriptors(
            new OpenProjectWorkItemFields(List.of(
                field("targetVersions", "Target versions", "[]Version", ALL_OPERATORS),
                field("category", "Category", null, List.of()),
                field("responsible", "Accountable", "[]User", List.of("=", "!")),
                field("customField5", "Labels", "[]CustomOption", ALL_OPERATORS),
                field("customField6", "Notes", "[1]String", ALL_OPERATORS),
                field("estimatedTime", "Work", "[1]Integer", ALL_OPERATORS),
                field("customField7", "Flag", "[1]Boolean", ALL_OPERATORS),
                field("customField8", "Deadline", "[1]Date", ALL_OPERATORS),
                field("customField9", "Odd", "[1]String", List.of("o")))));

        assertEquals(List.of("targetVersion", "category", "responsible", "estimatedTime", "Labels", "Notes", "Flag",
            "Deadline", "Odd"),
            descriptors.stream().map(LiveDataPropertyDescriptor::getId).collect(Collectors.toList()));

        LiveDataPropertyDescriptor version = descriptors.get(0);
        assertEquals("Target versions", version.getName());
        assertTrue(version.isSortable());
        assertTrue(version.isFilterable());
        assertEquals("html", version.getDisplayer().getId());
        assertEquals("list", version.getFilter().getId());
        assertEquals(List.of("=", "&=", "!", "*", "!*"), getOperators(version));
        assertEquals("=", version.getFilter().getDefaultOperator());
        assertEquals("/rest/wikis/{wikiName}/openproject/instance/{instance}/suggest/versions?search={encodedQuery}",
            version.getFilter().getParameters().get("searchURL"));

        LiveDataPropertyDescriptor category = descriptors.get(1);
        assertFalse(category.isFilterable());
        assertNull(category.getFilter());
        assertEquals("html", category.getDisplayer().getId());

        assertEquals(List.of("=", "!"), getOperators(descriptors.get(2)));
        assertTrue(((String) descriptors.get(2).getFilter().getParameters().get("searchURL")).contains("/users?"));

        LiveDataPropertyDescriptor labels = descriptors.get(4);
        assertEquals("list", labels.getFilter().getId());
        assertNull(labels.getFilter().getParameters().get("searchURL"));

        // The values that aren't links are displayed as text.
        LiveDataPropertyDescriptor notes = descriptors.get(5);
        assertNull(notes.getDisplayer());
        assertEquals("text", notes.getFilter().getId());
        assertEquals(List.of("~", "!~", "=", "!", "*", "!*"), getOperators(notes));

        assertEquals("number", descriptors.get(3).getFilter().getId());
        assertEquals("boolean", descriptors.get(6).getFilter().getId());

        // The dates and the filters without supported operators can't be filtered.
        assertFalse(descriptors.get(7).isFilterable());
        assertFalse(descriptors.get(8).isFilterable());
    }

    private static List<String> getOperators(LiveDataPropertyDescriptor descriptor)
    {
        return descriptor.getFilter().getOperators().stream().map(LiveDataPropertyDescriptor.OperatorDescriptor::getId)
            .collect(Collectors.toList());
    }

    private static WorkPackageField field(String key, String name, String filterValueType, List<String> operators)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(name);
        if (filterValueType != null) {
            field.setFilterKey(key);
            field.setFilterValueType(filterValueType);
        }
        field.setFilterOperators(operators);
        field.setSortable(true);
        return field;
    }
}
