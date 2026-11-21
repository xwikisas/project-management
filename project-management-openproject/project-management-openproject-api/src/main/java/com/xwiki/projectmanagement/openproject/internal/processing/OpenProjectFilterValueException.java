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
package com.xwiki.projectmanagement.openproject.internal.processing;

import java.net.HttpURLConnection;

import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;

/**
 * Thrown when a name in the value of a filter matches no OpenProject resource, or several (see
 * {@link OpenProjectFilterValueResolver}). Its message names the property and the value, to be displayed to the user,
 * and its status code is 400, the filters being invalid.
 *
 * @version $Id$
 * @since 1.5.0
 */
public class OpenProjectFilterValueException extends WorkItemRetrievalException
{
    private static final long serialVersionUID = 1L;

    /**
     * @param message the message, naming the property and the value
     */
    public OpenProjectFilterValueException(String message)
    {
        super(message, HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * @param message the message, naming the property and the value
     * @param cause the failure of the lookup of the resource
     */
    public OpenProjectFilterValueException(String message, Throwable cause)
    {
        super(message, cause, HttpURLConnection.HTTP_BAD_REQUEST);
    }
}
