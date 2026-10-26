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

/**
 * How the current user can reach an OpenProject connection.
 *
 * @version $Id$
 * @since 1.3.1
 */
public enum OpenProjectAccessStatus
{
    /**
     * The current user has an OAuth token for the connection: OpenProject is called with that token.
     */
    AUTHENTICATED,

    /**
     * The connection is configured but the current user can't call it: either the user is a guest or they haven't
     * connected their OpenProject account (no OAuth token, or a blank one).
     */
    NOT_CONNECTED,

    /**
     * No connection with the given name is configured.
     */
    NOT_CONFIGURED
}
