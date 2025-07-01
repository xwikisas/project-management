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
package com.xwiki.projectmanagement.exception;

/**
 * Exception thrown when the authentication fails.
 *
 * @version $Id$
 * @since 1.0
 */
public class AuthenticationException extends Exception
{
    /**
     * Class ID for serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * @param message the detail message for this exception.
     */
    public AuthenticationException(String message)
    {
        super(message);
    }

    /**
     * @param message the detail message for this exception.
     * @param cause the cause for this exception or null if none exists.
     */
    public AuthenticationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
