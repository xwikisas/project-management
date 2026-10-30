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
package com.xwiki.projectmanagement.openproject.internal.access;

import org.xwiki.component.annotation.Role;

/**
 * Decides how the current user reaches a configured OpenProject connection, and provides the client to call it with.
 * This is the single place that looks up the connection and the current user's OAuth token: callers should not
 * read the token themselves.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Role
public interface OpenProjectAccess
{
    /**
     * @param connectionName the name of an OpenProject connection configured in the administration
     * @return how the current user reaches the connection, with the client to use when they can call it
     */
    OpenProjectAccessResult resolve(String connectionName);
}
