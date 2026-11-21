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

import java.util.Map;

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

    private OpenProjectMapper()
    {
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
}
