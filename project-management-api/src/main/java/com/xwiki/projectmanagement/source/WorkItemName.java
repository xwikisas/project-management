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

import java.util.Objects;

import org.xwiki.stability.Unstable;

/**
 * The value of a filter constraint written as the name of a resource of the project management tool (for example the
 * project {@code xwiki} or the version {@code "17"}) rather than as its id, in a {@link WorkItemSelection} read from
 * the macro content. The macro replaces the names of the resources known to its client by their ids before retrieving
 * the work items (the other names being kept as text), so that a name made of digits is never taken for an id.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Unstable
public final class WorkItemName
{
    private final String name;

    /**
     * @param name the name, as written in the content
     */
    public WorkItemName(String name)
    {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * @return the name, as written in the content
     */
    public String getName()
    {
        return this.name;
    }

    @Override
    public boolean equals(Object object)
    {
        return object instanceof WorkItemName && this.name.equals(((WorkItemName) object).name);
    }

    @Override
    public int hashCode()
    {
        return this.name.hashCode();
    }

    @Override
    public String toString()
    {
        return this.name;
    }
}
