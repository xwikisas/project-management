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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xwiki.projectmanagement.model.WorkItem;

/**
 * The OpenProject names of the generic work item properties, and the types of Live Data filters. The translation of
 * the filters themselves is done by {@link OpenProjectFilterTranslator}.
 *
 * @version $Id$
 */
public final class OpenProjectMapper
{
    /**
     * The Live Data filter of the properties whose values are picked in a list.
     */
    public static final String FILTER_LIST = "list";

    /**
     * The Live Data filter of the text properties.
     */
    public static final String FILTER_TEXT = "text";

    /**
     * The Live Data filter of the number properties.
     */
    public static final String FILTER_NUMBER = "number";

    /**
     * The Live Data filter of the boolean properties.
     */
    public static final String FILTER_BOOLEAN = "boolean";

    /**
     * The Live Data filter of the date properties.
     */
    public static final String FILTER_DATE = "date";

    private static final String AUTHOR = "author";

    /**
     * The OpenProject filters and columns of the generic work item properties whose name differs in OpenProject.
     */
    private static final Map<String, String> KEY_MAPPING = Map.of(
        FILTER_DATE, "start_date",
        WorkItem.KEY_IDENTIFIER, "id",
        WorkItem.KEY_SUMMARY, "subject",
        WorkItem.KEY_ASSIGNEES, "assigned_to",
        WorkItem.KEY_CREATOR, AUTHOR,
        WorkItem.KEY_CREATION_DATE, "createdAt",
        WorkItem.KEY_UPDATE_DATE, "updatedAt",
        WorkItem.KEY_REPORTER, AUTHOR,
        WorkItem.KEY_PROGRESS, "percentageDone",
        // The OpenProject filter matching the work packages whose date range overlaps an interval.
        WorkItem.KEY_DATE_RANGE, "datesInterval");

    /**
     * The generic work item properties of the OpenProject filters and columns whose name differs in OpenProject, as
     * named by the links of the OpenProject work package lists. The date range has no column in the Live Data, so its
     * filter ({@code datesInterval}) isn't listed.
     */
    private static final Map<String, String> OPENPROJECT_KEY_MAPPING = createOpenProjectKeyMapping();

    private OpenProjectMapper()
    {
    }

    private static Map<String, String> createOpenProjectKeyMapping()
    {
        Map<String, String> mapping = new HashMap<>();
        for (String property : List.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_SUMMARY, WorkItem.KEY_ASSIGNEES,
            WorkItem.KEY_CREATOR, WorkItem.KEY_CREATION_DATE, WorkItem.KEY_UPDATE_DATE, WorkItem.KEY_PROGRESS))
        {
            mapping.put(KEY_MAPPING.get(property), property);
        }
        // The links of the OpenProject work package lists name the assignee filter and column after the field.
        mapping.put("assignee", WorkItem.KEY_ASSIGNEES);
        return Map.copyOf(mapping);
    }

    /**
     * Gets the OpenProject filter or column of a work item property.
     *
     * @param key a work item property, for example {@code summary}
     * @return the matching OpenProject filter or column, for example {@code subject}, or the property itself when its
     *     name is the same in OpenProject (for example {@code status}) or when it isn't a generic work item property
     */
    public static String mapLivedataProperty(String key)
    {
        return KEY_MAPPING.getOrDefault(key, key);
    }

    /**
     * Gets the generic work item property of an OpenProject filter or column, the reverse of
     * {@link #mapLivedataProperty(String)}.
     *
     * @param name an OpenProject filter or column, for example {@code subject}
     * @return the matching work item property, for example {@code summary}, or the name itself when it's the same in
     *     OpenProject (for example {@code status}) or when it isn't the filter or column of a generic work item
     *     property
     * @since 1.5.0
     */
    public static String mapOpenProjectName(String name)
    {
        return OPENPROJECT_KEY_MAPPING.getOrDefault(name, name);
    }

    /**
     * Gets the OpenProject filters and columns named otherwise than a generic work item property, whose generic
     * property it is (see {@link #mapOpenProjectName(String)}).
     *
     * @param property a work item property, for example {@code assignees}
     * @return the OpenProject names of the property, sorted, for example {@code assigned_to} and {@code assignee}, or
     *     an empty list when the property has the same name in OpenProject or isn't a generic work item property
     * @since 1.5.0
     */
    public static List<String> getOpenProjectNames(String property)
    {
        return OPENPROJECT_KEY_MAPPING.entrySet().stream().filter(entry -> entry.getValue().equals(property))
            .map(Map.Entry::getKey).sorted().collect(Collectors.toList());
    }
}
