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
package com.xwiki.projectmanagement.internal;

/**
 * Converts the 1-based page numbers received by the REST and script APIs to the offsets expected by the project
 * management clients.
 *
 * @version $Id$
 */
public final class Pagination
{
    private Pagination()
    {
    }

    /**
     * @param page the number, starting at 1, of the requested page; a number smaller than 1 is the first page
     * @param pageSize the number of items in a page
     * @return the offset of the first item of the requested page
     */
    public static int getOffset(int page, int pageSize)
    {
        return (Math.max(page, 1) - 1) * pageSize;
    }
}
