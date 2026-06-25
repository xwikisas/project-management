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

/**
 * Exception thrown when the retrieval of a work item has failed due to various reasons.
 *
 * @version $Id$
 * @since 1.0
 */
public class WorkItemRetrievalException extends WorkItemException
{
    /**
     * Class ID for serialization.
     */
    private static final long serialVersionUID = 1L;

    private final Integer statusCode;

    /**
     * @param message the detail message for this exception.
     */
    public WorkItemRetrievalException(String message)
    {
        this(message, -1);
    }

    /**
     * @param message the detail message for this exception.
     * @param statusCode the status code received from the request to the project management platform.
     * @since 1.2.0
     */
    public WorkItemRetrievalException(String message, Integer statusCode)
    {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * @param message the detail message for this exception.
     * @param throwable the cause for this exception or null if none exists.
     */
    public WorkItemRetrievalException(String message, Throwable throwable)
    {
        this(message, throwable, -1);
    }

    /**
     * @param message the detail message for this exception.
     * @param throwable the cause for this exception or null if none exists.
     * @param statusCode the status code received from the request to the project management platform.
     * @since 1.2.0
     */
    public WorkItemRetrievalException(String message, Throwable throwable, Integer statusCode)
    {
        super(message, throwable);
        this.statusCode = statusCode;
    }

    /**
     * @return the status code received from the request to the project management platform.
     * @since 1.2.0
     */
    public Integer getStatusCode()
    {
        return statusCode;
    }
}
