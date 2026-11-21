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

import org.xwiki.stability.Unstable;

/**
 * The context in which a {@link WorkItemSource} reads the content of a macro.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Unstable
public class WorkItemSourceContext
{
    private final String client;

    /**
     * @param client the id of the client retrieving the work items (e.g. {@code openproject}), or {@code null} if the
     *     macro isn't specific to a client
     */
    public WorkItemSourceContext(String client)
    {
        this.client = client;
    }

    /**
     * @return the id of the client retrieving the work items (e.g. {@code openproject}), or {@code null} if the macro
     *     isn't specific to a client
     */
    public String getClient()
    {
        return this.client;
    }
}
