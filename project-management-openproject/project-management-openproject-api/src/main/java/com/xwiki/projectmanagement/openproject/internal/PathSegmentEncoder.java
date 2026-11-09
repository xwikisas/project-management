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
package com.xwiki.projectmanagement.openproject.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Encodes the values inserted in the path of a URL, such as the name of a connection, which can contain any character,
 * e.g. {@code /}, {@code #}, {@code &} or quotes, that must neither end the path segment nor break the URL.
 *
 * @version $Id$
 * @since 1.4.0
 */
public final class PathSegmentEncoder
{
    private PathSegmentEncoder()
    {
    }

    /**
     * @param value a value inserted in the path of a URL, e.g. an identifier or the name of a connection
     * @return the value encoded to be used as a single path segment
     */
    public static String encode(String value)
    {
        // URLEncoder encodes for HTML forms: a space becomes "+", which is a literal "+" in a URL path.
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
