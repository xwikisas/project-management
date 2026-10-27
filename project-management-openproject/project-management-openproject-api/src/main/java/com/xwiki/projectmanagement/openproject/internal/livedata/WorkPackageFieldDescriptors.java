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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.livedata.LiveDataPropertyDescriptor;

import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * Creates the Live Data property descriptors of the work package fields exposed by
 * {@link OpenProjectWorkItemFields}.
 *
 * @version $Id$
 * @since 1.3.1
 */
public final class WorkPackageFieldDescriptors
{
    private static final String FILTER_LIST = "list";

    private static final String FILTER_TEXT = "text";

    private static final String FILTER_NUMBER = "number";

    private static final String FILTER_BOOLEAN = "boolean";

    private static final String OPERATOR_EQUALS = "=";

    private static final String OPERATOR_NOT_EQUALS = "!";

    private static final String OPERATOR_NOT_EMPTY = "*";

    private static final String OPERATOR_EMPTY = "!*";

    private static final String NAME_EQUALS = "Equals";

    private static final String NAME_NOT_EQUALS = "Not equals";

    private static final String NAME_NOT_EMPTY = "Not empty";

    private static final String NAME_NOT_SET = "Not set";

    private static final String SUGGEST_URL_FORMAT =
        "/rest/wikis/{wikiName}/openproject/instance/{instance}/suggest/%s?search={encodedQuery}";

    /**
     * The suggestions of the values of the list filters, by type of value.
     */
    private static final Map<String, String> SUGGESTIONS = Map.of(
        OpenProjectWorkItemFields.VERSION_FILTER_VALUE_TYPE, "versions",
        "[]User", "users",
        "[]Project", "projects",
        "[]WorkPackage", "id");

    /**
     * The names of the supported operators, by filter.
     */
    private static final Map<String, Map<String, String>> OPERATORS = Map.of(
        FILTER_LIST, orderedMap(OPERATOR_EQUALS, NAME_EQUALS, "&=", "All of", OPERATOR_NOT_EQUALS, NAME_NOT_EQUALS,
            OPERATOR_NOT_EMPTY, NAME_NOT_EMPTY, OPERATOR_EMPTY, NAME_NOT_SET),
        FILTER_TEXT, orderedMap("~", "Contains", "!~", "Does not contain", OPERATOR_EQUALS, NAME_EQUALS,
            OPERATOR_NOT_EQUALS, NAME_NOT_EQUALS, OPERATOR_NOT_EMPTY, NAME_NOT_EMPTY, OPERATOR_EMPTY, NAME_NOT_SET),
        FILTER_NUMBER, orderedMap(OPERATOR_EQUALS, NAME_EQUALS, OPERATOR_NOT_EQUALS, NAME_NOT_EQUALS,
            ">=", "Greater or equal", "<=", "Lower or equal", OPERATOR_NOT_EMPTY, NAME_NOT_EMPTY, OPERATOR_EMPTY,
            NAME_NOT_SET),
        FILTER_BOOLEAN, orderedMap(OPERATOR_EQUALS, NAME_EQUALS, OPERATOR_NOT_EQUALS, NAME_NOT_EQUALS));

    private WorkPackageFieldDescriptors()
    {
    }

    /**
     * @param fields the work package fields of an OpenProject instance
     * @return the descriptors of the work item properties exposing the fields, named after the fields
     */
    public static List<LiveDataPropertyDescriptor> getDescriptors(OpenProjectWorkItemFields fields)
    {
        List<LiveDataPropertyDescriptor> descriptors = new ArrayList<>();
        for (Map.Entry<String, WorkPackageField> field : fields.getFields().entrySet()) {
            descriptors.add(getDescriptor(field.getKey(), field.getValue()));
        }
        return descriptors;
    }

    private static LiveDataPropertyDescriptor getDescriptor(String id, WorkPackageField field)
    {
        LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();
        descriptor.setId(id);
        descriptor.setName(field.getName());
        descriptor.setSortable(field.isSortable());
        // The links are sent as HTML, rendered from the values by the work item property displayers. The other
        // values are sent as text, displayed (and escaped) by the default displayer.
        if (OpenProjectWorkItemFields.isLinkField(field)) {
            descriptor.setDisplayer(new LiveDataPropertyDescriptor.DisplayerDescriptor("html"));
        }

        LiveDataPropertyDescriptor.FilterDescriptor filter = getFilter(field);
        descriptor.setFilterable(filter != null);
        descriptor.setFilter(filter);
        return descriptor;
    }

    private static LiveDataPropertyDescriptor.FilterDescriptor getFilter(WorkPackageField field)
    {
        String valueType = field.getFilterValueType();
        if (field.getFilterKey() == null || valueType == null) {
            return null;
        }
        String filterId = getFilterId(valueType);
        if (filterId == null) {
            return null;
        }

        LiveDataPropertyDescriptor.FilterDescriptor filter = new LiveDataPropertyDescriptor.FilterDescriptor(filterId);
        for (Map.Entry<String, String> operator : OPERATORS.get(filterId).entrySet()) {
            if (field.getFilterOperators().contains(operator.getKey())) {
                filter.addOperator(operator.getKey(), operator.getValue());
            }
        }
        if (filter.getOperators() == null || filter.getOperators().isEmpty()) {
            return null;
        }
        filter.setDefaultOperator(filter.getOperators().get(0).getId());
        String suggestion = SUGGESTIONS.get(valueType);
        if (suggestion != null) {
            filter.getParameters().put("searchURL", String.format(SUGGEST_URL_FORMAT, suggestion));
        }
        return filter;
    }

    private static String getFilterId(String valueType)
    {
        String filterId = null;
        if (valueType.startsWith("[]")) {
            filterId = FILTER_LIST;
        } else if (valueType.endsWith("String")) {
            filterId = FILTER_TEXT;
        } else if (valueType.endsWith("Integer") || valueType.endsWith("Float")) {
            filterId = FILTER_NUMBER;
        } else if (valueType.endsWith("Boolean")) {
            filterId = FILTER_BOOLEAN;
        }
        // The dates and the other types of values would need to be converted to the format of OpenProject.
        return filterId;
    }

    private static Map<String, String> orderedMap(String... keysAndValues)
    {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
