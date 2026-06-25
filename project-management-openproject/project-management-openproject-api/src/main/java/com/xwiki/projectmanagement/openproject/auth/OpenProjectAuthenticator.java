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
package com.xwiki.projectmanagement.openproject.auth;

import java.net.http.HttpRequest;

/**
 * Interface used to authenticate requests sent to the OpenProject API. Implementations of this interface are used by
 * the {@link com.xwiki.projectmanagement.openproject.OpenProjectApiClient} to authenticate outgoing requests.
 *
 * @version $Id$
 * @since 1.2
 */
public interface OpenProjectAuthenticator
{
    /**
     * Decorates the given request builder with the credentials needed to authenticate the request.
     *
     *
     * @param builder the request builder to decorate
     */
    void authenticate(HttpRequest.Builder builder);
}
