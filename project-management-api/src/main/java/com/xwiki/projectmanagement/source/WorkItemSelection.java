package com.xwiki.projectmanagement.source;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.stability.Unstable;

/**
 * The work items selected by the content of a macro, as read by a {@link WorkItemSource}. The selected work items are
 * the ones matching all the parts of the selection: the filters, the listed work items, if any, and the client
 * specific values.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Unstable
public final class WorkItemSelection
{
    private final List<LiveDataQuery.Filter> filters;

    private final List<String> ids;

    private final Map<String, String> clientScope;

    /**
     * @param filters the filters on the work item properties, as written in the content (e.g. with the names of the
     *     values, which the client resolves), or an empty list
     * @param ids the ids or keys of the listed work items, in the order in which they are displayed, or an empty list
     *     if the work items aren't listed
     * @param clientScope the values that only the client understands (e.g. the link of a work item list of the
     *     project management tool), passed to it in its execution context, or an empty map
     */
    public WorkItemSelection(List<LiveDataQuery.Filter> filters, List<String> ids, Map<String, String> clientScope)
    {
        this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
        this.ids = Collections.unmodifiableList(new ArrayList<>(ids));
        this.clientScope = Collections.unmodifiableMap(new LinkedHashMap<>(clientScope));
    }

    /**
     * @param filters the filters on the work item properties
     * @return the selection of the work items matching the filters
     */
    public static WorkItemSelection ofFilters(List<LiveDataQuery.Filter> filters)
    {
        return new WorkItemSelection(filters, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * @param ids the ids or keys of the work items
     * @return the selection of the listed work items
     */
    public static WorkItemSelection ofIds(List<String> ids)
    {
        return new WorkItemSelection(Collections.emptyList(), ids, Collections.emptyMap());
    }

    /**
     * @param clientScope the values that only the client understands
     * @return the selection of the work items chosen by the client from these values
     */
    public static WorkItemSelection ofClientScope(Map<String, String> clientScope)
    {
        return new WorkItemSelection(Collections.emptyList(), Collections.emptyList(), clientScope);
    }

    /**
     * @return the filters on the work item properties, as written in the content, or an empty list
     */
    public List<LiveDataQuery.Filter> getFilters()
    {
        return this.filters;
    }

    /**
     * @return the ids or keys of the listed work items, in the order in which they are displayed, or an empty list if
     *     the work items aren't listed
     */
    public List<String> getIds()
    {
        return this.ids;
    }

    /**
     * @return the values that only the client understands, passed to it in its execution context, or an empty map
     */
    public Map<String, String> getClientScope()
    {
        return this.clientScope;
    }
}
