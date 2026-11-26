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
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.livedata.LiveDataPropertyDescriptor;

import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterValueResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectMapper.FILTER_BOOLEAN;
import static com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectMapper.FILTER_LIST;
import static com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectMapper.FILTER_NUMBER;
import static com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectMapper.FILTER_TEXT;

/**
 * Creates the Live Data property descriptors of the work package fields exposed by
 * {@link OpenProjectWorkItemFields}.
 *
 * @version $Id$
 * @since 1.4.0
 */
public final class WorkPackageFieldDescriptors
{
    private static final String OPERATOR_EQUALS = "=";

    private static final String OPERATOR_NOT_EQUALS = "!";

    private static final String OPERATOR_NOT_EMPTY = "*";

    private static final String OPERATOR_ALL_OF = "&=";

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
        FILTER_LIST, orderedMap(OPERATOR_EQUALS, NAME_EQUALS, OPERATOR_ALL_OF, "All of", OPERATOR_NOT_EQUALS,
            NAME_NOT_EQUALS, OPERATOR_NOT_EMPTY, NAME_NOT_EMPTY, OPERATOR_EMPTY, NAME_NOT_SET),
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
     * @param multiValueFields the keys of the fields holding several values in a work package
     * @return the descriptors of the work item properties exposing the fields, named after the fields
     */
    public static List<LiveDataPropertyDescriptor> getDescriptors(OpenProjectWorkItemFields fields,
        Set<String> multiValueFields)
    {
        List<LiveDataPropertyDescriptor> descriptors = new ArrayList<>();
        for (Map.Entry<String, WorkPackageField> field : fields.getFields().entrySet()) {
            descriptors.add(getDescriptor(field.getKey(), field.getValue(),
                multiValueFields.contains(field.getValue().getKey())));
        }
        return descriptors;
    }

    private static LiveDataPropertyDescriptor getDescriptor(String id, WorkPackageField field, boolean multiValue)
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

        LiveDataPropertyDescriptor.FilterDescriptor filter = getFilter(id, field, multiValue);
        descriptor.setFilterable(filter != null);
        descriptor.setFilter(filter);
        return descriptor;
    }

    private static LiveDataPropertyDescriptor.FilterDescriptor getFilter(String id, WorkPackageField field,
        boolean multiValue)
    {
        String valueType = field.getFilterValueType();
        if (field.getFilterKey() == null || valueType == null) {
            return null;
        }
        String filterId = OpenProjectWorkItemFields.getFilterType(field);
        if (filterId == null) {
            return null;
        }

        LiveDataPropertyDescriptor.FilterDescriptor filter = new LiveDataPropertyDescriptor.FilterDescriptor(filterId);
        addOperators(filter, field, multiValue);
        if (filter.getOperators() == null || filter.getOperators().isEmpty()) {
            return null;
        }
        filter.setDefaultOperator(getDefaultOperator(field, multiValue));
        setParameters(filter, id, field, multiValue);
        return filter;
    }

    /**
     * Sets the parameters of the filter of a field that the filter builder of the macros reads.
     */
    private static void setParameters(LiveDataPropertyDescriptor.FilterDescriptor filter, String id,
        WorkPackageField field, boolean multiValue)
    {
        String valueType = field.getFilterValueType();
        if (multiValue) {
            // Tells the filter builder that the field can be filtered on with several operators at once, e.g. the
            // work packages having one value and not another one.
            filter.getParameters().put("multiValue", true);
        }
        if (OpenProjectFilterValueResolver.resolvesNames(id, valueType)) {
            // Tells the filter builder to write the names of the values (e.g. the name of a project) in the query of
            // the macro content, which is resolved into ids when the work items are retrieved.
            filter.getParameters().put("valueNames", true);
        }
        String suggestion = SUGGESTIONS.get(valueType);
        if (suggestion != null) {
            filter.getParameters().put("searchURL", String.format(SUGGEST_URL_FORMAT, suggestion));
        }
        // The query of a macro can also filter on the field by its OpenProject filter, e.g. customField5 for the custom
        // field named Labels.
        if (!id.equals(field.getFilterKey())) {
            setAliases(filter, List.of(field.getFilterKey()));
        }
    }

    /**
     * Tells the filter builder the other names by which the query of a macro can filter on a property, e.g. its
     * OpenProject filter, so that the filters read from a query under these names are shown on its rows.
     *
     * @param filter the filter of the property
     * @param aliases the other names of the property, none being ignored
     */
    static void setAliases(LiveDataPropertyDescriptor.FilterDescriptor filter, List<String> aliases)
    {
        if (!aliases.isEmpty()) {
            filter.getParameters().put("aliases", aliases);
        }
    }

    private static void addOperators(LiveDataPropertyDescriptor.FilterDescriptor filter, WorkPackageField field,
        boolean multiValue)
    {
        for (Map.Entry<String, String> operator : OPERATORS.get(filter.getId()).entrySet()) {
            if (isOffered(operator.getKey(), field, multiValue)) {
                filter.addOperator(operator.getKey(), operator.getValue());
            }
        }
    }

    /**
     * @param field a work package field, for example a custom field holding a text
     * @param multiValue whether a work package can have several values for the field; when unknown, {@code true}
     *     keeps the "All of" operator ({@code &=}), which matches the same work packages as {@code =} for a single
     *     value
     * @return the default operator of the Live Data filter of the field: the first of the operators offered by the
     *     filter, in the order they are listed (for example {@code ~} for a text field supporting it, else {@code =}),
     *     or {@code null} if the field can't be filtered on
     */
    public static String getDefaultOperator(WorkPackageField field, boolean multiValue)
    {
        Map<String, String> operators = OPERATORS.get(StringUtils.defaultString(
            OpenProjectWorkItemFields.getFilterType(field)));
        if (operators == null) {
            return null;
        }
        return operators.keySet().stream().filter(operator -> isOffered(operator, field, multiValue)).findFirst()
            .orElse(null);
    }

    private static boolean isOffered(String operator, WorkPackageField field, boolean multiValue)
    {
        // OpenProject proposes "All of" on every list filter, but only a work package having several values for the
        // field can match more than one value.
        return field.getFilterOperators().contains(operator) && (multiValue || !OPERATOR_ALL_OF.equals(operator));
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
