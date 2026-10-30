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
package com.xwiki.projectmanagement.openproject.internal.service;

import com.xwiki.projectmanagement.exception.ProjectManagementException;

/**
 * Thrown when an OpenProject connection can't be saved, either because the data sent is not valid (e.g. a missing
 * client secret or an invalid server URL) or because it conflicts with the current state of the wiki (e.g. its name is
 * already used by another connection).
 *
 * @version $Id$
 * @since 1.3.1
 */
public final class ConnectionSaveException extends ProjectManagementException
{
    private static final long serialVersionUID = 1L;

    private final boolean conflict;

    private ConnectionSaveException(String message, boolean conflict)
    {
        super(message);
        this.conflict = conflict;
    }

    /**
     * @param message the message explaining why the connection data is not valid
     * @return the exception to throw when the data sent is not valid
     */
    public static ConnectionSaveException invalid(String message)
    {
        return new ConnectionSaveException(message, false);
    }

    /**
     * @param message the message explaining the conflict
     * @return the exception to throw when the connection conflicts with the current state of the wiki
     */
    public static ConnectionSaveException conflict(String message)
    {
        return new ConnectionSaveException(message, true);
    }

    /**
     * @return {@code true} if the connection conflicts with the current state of the wiki, {@code false} if the data
     *     sent is not valid
     */
    public boolean isConflict()
    {
        return this.conflict;
    }
}
