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
package com.xwiki.projectmanagement.openproject.exception;

import com.xwiki.projectmanagement.exception.ProjectManagementException;

/**
 * Exception thrown when the parameters used to retrieve WorkPackages from the OpenProject API
 * are invalid or improperly formatted, resulting in a 400 Bad Request response.
 * This exception typically indicates issues such as unsupported filters, incorrect sorting options,
 * or malformed query parameters being sent to the OpenProject API endpoint.
 *
 * @version $Id$
 * @since 1.0
 */
public class WorkPackageRetrievalBadRequestException extends ProjectManagementException
{
    /**
     * Class ID for serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * @param message the detail message for this exception.
     */
    public WorkPackageRetrievalBadRequestException(String message)
    {
        super(message);
    }

    /**
     * @param message the detail message for this exception.
     * @param throwable the cause for this exception or null if none exists.
     */
    public WorkPackageRetrievalBadRequestException(String message, Throwable throwable)
    {
        super(message, throwable);
    }
}
