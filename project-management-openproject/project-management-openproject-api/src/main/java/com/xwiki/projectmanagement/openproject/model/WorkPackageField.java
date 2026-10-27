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
package com.xwiki.projectmanagement.openproject.model;

import java.util.List;

/**
 * Describes a field of the OpenProject work packages, as listed by the work package queries of an instance: a built-in
 * field (e.g. {@code category}) or a custom field (e.g. {@code customField3}).
 *
 * @version $Id$
 * @since 1.3.1
 */
public class WorkPackageField extends BaseOpenProjectObject
{
    private static final String KEY = "key";

    private static final String FILTER_KEY = "filterKey";

    private static final String FILTER_VALUE_TYPE = "filterValueType";

    private static final String FILTER_OPERATORS = "filterOperators";

    private static final String SORTABLE = "sortable";

    private static final String CUSTOM_FIELD_PREFIX = "customField";

    /**
     * @return the key of the field in the work packages returned by the OpenProject API, which is also the id of the
     *     query column and of the sort criterion, e.g. {@code category}, {@code targetVersions} or
     *     {@code customField3}
     */
    public String getKey()
    {
        return (String) get(KEY);
    }

    /**
     * @param key see {@link #getKey()}
     */
    public void setKey(String key)
    {
        put(KEY, key);
    }

    /**
     * @return the id of the OpenProject filter matching this field, e.g. {@code targetVersion} for the
     *     {@code targetVersions} field, or {@code null} if the work packages can't be filtered on this field
     */
    public String getFilterKey()
    {
        return (String) get(FILTER_KEY);
    }

    /**
     * @param filterKey see {@link #getFilterKey()}
     */
    public void setFilterKey(String filterKey)
    {
        put(FILTER_KEY, filterKey);
    }

    /**
     * @return the type of the values of the filter, as named by OpenProject, e.g. {@code []Version} for a list of
     *     versions or {@code [1]String} for a single string, or {@code null} if the field can't be filtered
     */
    public String getFilterValueType()
    {
        return (String) get(FILTER_VALUE_TYPE);
    }

    /**
     * @param filterValueType see {@link #getFilterValueType()}
     */
    public void setFilterValueType(String filterValueType)
    {
        put(FILTER_VALUE_TYPE, filterValueType);
    }

    /**
     * @return the ids of the operators supported by the filter of this field, e.g. {@code =} or {@code !*}
     */
    @SuppressWarnings("unchecked")
    public List<String> getFilterOperators()
    {
        List<String> operators = (List<String>) get(FILTER_OPERATORS);
        return operators == null ? List.of() : operators;
    }

    /**
     * @param filterOperators see {@link #getFilterOperators()}
     */
    public void setFilterOperators(List<String> filterOperators)
    {
        put(FILTER_OPERATORS, filterOperators);
    }

    /**
     * @return {@code true} if the work packages can be sorted on this field
     */
    public boolean isSortable()
    {
        return Boolean.TRUE.equals(get(SORTABLE));
    }

    /**
     * @param sortable see {@link #isSortable()}
     */
    public void setSortable(boolean sortable)
    {
        put(SORTABLE, sortable);
    }

    /**
     * @return {@code true} if this field is a custom field defined by the administrators of the instance
     */
    public boolean isCustomField()
    {
        String key = getKey();
        return key != null && key.startsWith(CUSTOM_FIELD_PREFIX);
    }
}
