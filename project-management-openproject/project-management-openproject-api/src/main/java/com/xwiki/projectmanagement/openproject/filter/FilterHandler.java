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
package com.xwiki.projectmanagement.openproject.filter;

import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.livedata.LiveDataQuery;

/**
 * Interface for handling filter conversion.
 *
 * @version $Id$
 */
@Role
public interface FilterHandler
{
    /**
     * Converts a list of {@link LiveDataQuery.Filter} objects into a string representation.
     *
     * @param filters the list of filters to convert
     * @return a string representation of the filters
     */
    String convertFilters(List<LiveDataQuery.Filter> filters);

    /**
     * Converts a JSON string representation of filters into a processed filter string.
     * @param filtersList the list of filters, where each filter is a map containing keys and values
     * @return a processed string representation of the filters
     */
    String convertFiltersFromQuery(List<Map<String, Object>> filtersList);
}
