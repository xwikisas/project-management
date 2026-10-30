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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Describes the type object of a work package.
 *
 * @version $Id$
 * @since 1.0
 */
public class Status extends ColoredOpenProjectObject
{
    private static final String KEY_IS_CLOSED = "isClosed";

    /**
     * Kept out of the map entries, so that the JSON representation of the status, which some REST resources return,
     * doesn't change.
     */
    private boolean closed;

    /**
     * Create a Status object from a JsonNode.
     *
     * @param statusNode the JsonNode containing the status information.
     */
    public Status(JsonNode statusNode)
    {
        super(statusNode);
        setClosed(statusNode.path(KEY_IS_CLOSED).asBoolean());
    }

    /**
     * Default constructor.
     */
    public Status()
    {
    }

    /**
     * @return {@code true} if the work packages having this status are closed
     * @since 1.4.0
     */
    public boolean isClosed()
    {
        return this.closed;
    }

    /**
     * @param closed see {@link #isClosed()}
     * @since 1.4.0
     */
    public void setClosed(boolean closed)
    {
        this.closed = closed;
    }
}
