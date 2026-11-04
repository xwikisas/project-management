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
package com.xwiki.projectmanagement.openproject;

import org.xwiki.stability.Unstable;

/**
 * The properties of a project that the OpenProject Project Details macro can display, in their display order.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Unstable
public enum OpenProjectProjectDetailsProperty
{
    /**
     * The numeric id of the project.
     */
    ID,

    /**
     * The name of the project, linked to the project.
     */
    NAME,

    /**
     * The textual identifier of the project, used in its URL.
     */
    IDENTIFIER,

    /**
     * The description of the project.
     */
    DESCRIPTION,

    /**
     * The status of the project.
     */
    STATUS,

    /**
     * The creation date of the project.
     */
    CREATED
}
