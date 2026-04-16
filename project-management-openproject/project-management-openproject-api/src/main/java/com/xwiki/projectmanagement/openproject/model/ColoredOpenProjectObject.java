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

/**
 * Describes the Open Project entities that can have custom colors associated to them such as Status and Type.
 *
 * @version $Id$
 * @since 1.0
 */
public class ColoredOpenProjectObject extends BaseOpenProjectObject
{
    /**
     * The key that retrieves the color attribute of this entity.
     */
    public static final String KEY_COLOR = "color";

    /**
     * @return the color associated to this entity. It is used by the client to display this property in a specific
     *     color.
     */
    public String getColor()
    {
        return (String) get(KEY_COLOR);
    }

    /**
     * @param color see {@link #getColor()}.
     */
    public void setColor(String color)
    {
        put(KEY_COLOR, color);
    }
}
