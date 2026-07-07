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

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.model.Linkable;

/**
 * Describes the user object of a work package.
 *
 * @version $Id$
 * @since 1.0
 */
public class User extends BaseOpenProjectObject
{
    private static final String KEY_ROLES = "roles";

    /**
     * Create a User object from a JsonNode.
     *
     * @param userJson the JsonNode containing the user information.
     */
    public User(JsonNode userJson)
    {
        super(userJson);
    }

    /**
     * Default constructor.
     */
    public User()
    {
    }

    /**
     * @return the roles of the user as a list of {@link Linkable}.
     * @since 1.2
     */
    public List<Linkable> getRoles()
    {
        return (List<Linkable>) get(KEY_ROLES);
    }

    /**
     * @param roles see {@link #getRoles()}.
     * @since 1.2
     */
    public void setRoles(List<Linkable> roles)
    {
        put(KEY_ROLES, roles);
    }
}
