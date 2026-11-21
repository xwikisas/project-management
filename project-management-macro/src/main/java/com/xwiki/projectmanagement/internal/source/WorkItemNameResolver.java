package com.xwiki.projectmanagement.internal.source;

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

import java.util.List;

import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.xwiki.projectmanagement.source.WorkItemName;

/**
 * Replaces the names written in the filters read from the content of a macro (the {@link WorkItemName} values, for
 * example the project {@code xwiki}) by the ids of the resources of the project management tool, which its client
 * expects. It's called with the filters of a query even when they have no name, so that it can also check their
 * properties.
 *
 * @version $Id$
 * @since 1.5.0
 */
@FunctionalInterface
public interface WorkItemNameResolver
{
    /**
     * @param filters the filters read from the content, whose values are names ({@link WorkItemName}) or text (for
     *     example ids, dates or the text searched with a {@code ~} constraint)
     * @return the filters with the names of the resources known to the client replaced by their ids, the other values
     *     being kept as they are (the names left are then used as text)
     * @throws MacroExecutionException if a property doesn't exist, or if a name matches no resource or several, the
     *     message naming the property (and the value)
     */
    List<LiveDataQuery.Filter> resolve(List<LiveDataQuery.Filter> filters) throws MacroExecutionException;
}
