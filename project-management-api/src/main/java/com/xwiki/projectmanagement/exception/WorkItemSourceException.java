package com.xwiki.projectmanagement.exception;

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
 * Thrown when the content of a work item macro can't be read by its source, e.g. because of a syntax error. The
 * message is displayed to the user, so it tells what is wrong in the content.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Unstable
public class WorkItemSourceException extends ProjectManagementException
{
    private static final long serialVersionUID = 1L;

    /**
     * @param message what is wrong in the content
     */
    public WorkItemSourceException(String message)
    {
        super(message);
    }

    /**
     * @param message what is wrong in the content
     * @param cause the cause of the failure
     */
    public WorkItemSourceException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
