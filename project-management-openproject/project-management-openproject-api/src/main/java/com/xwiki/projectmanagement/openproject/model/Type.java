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
public class Type extends ColoredOpenProjectObject
{
    /**
     * The key that retrieves the isMilestone attribute of this type.
     */
    public static final String KEY_IS_MILESTONE = "isMilestone";

    /**
     * Create a Type object from a JsonNode.
     *
     * @param typeNode the JsonNode containing the type information.
     */
    public Type(JsonNode typeNode)
    {
        super(typeNode);
        this.setMilestone(typeNode.path(KEY_IS_MILESTONE).asBoolean());
    }

    /**
     * Default constructor.
     */
    public Type()
    {
    }

    /**
     * @return {@code true} if this type represents a milestone, {@code false} otherwise.
     * @since 1.2
     */
    public boolean isMilestone()
    {
        return Boolean.TRUE.equals(get(KEY_IS_MILESTONE));
    }

    /**
     * @param milestone see {@link #isMilestone()}.
     * @since 1.2
     */
    public void setMilestone(boolean milestone)
    {
        put(KEY_IS_MILESTONE, milestone);
    }
}
