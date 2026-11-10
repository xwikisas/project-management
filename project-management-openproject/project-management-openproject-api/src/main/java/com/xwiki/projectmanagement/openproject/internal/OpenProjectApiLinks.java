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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;

/**
 * Resolves the links ({@code href}) returned by the OpenProject API. They are absolute paths, which include the path
 * the instance is served under: e.g. {@code /openproject/api/v3/projects/1} for an instance served at
 * {@code https://example.org/openproject}. They must thus be resolved against the origin of the instance, not
 * appended to its URL.
 *
 * @version $Id$
 * @since 1.4.0
 */
public final class OpenProjectApiLinks
{
    private static final String SLASH = "/";

    private static final String API_PATH = "/api/v3";

    private OpenProjectApiLinks()
    {
    }

    /**
     * @param connectionUrl the URL of the OpenProject instance, e.g. {@code https://example.org/openproject}
     * @param href a link returned by the OpenProject API, e.g. {@code /openproject/api/v3/projects/1}
     * @return the path of the link relative to the URL of the instance, e.g. {@code /api/v3/projects/1}, or
     *     {@code null} when the link isn't under the path of the instance
     */
    public static String toInstanceRelativePath(String connectionUrl, String href)
    {
        if (href == null) {
            return null;
        }
        String instancePath = getInstancePath(connectionUrl);
        if (instancePath.isEmpty()) {
            return href;
        }
        return href.startsWith(instancePath + SLASH) ? href.substring(instancePath.length()) : null;
    }

    /**
     * @param connectionUrl the URL of the OpenProject instance, e.g. {@code https://example.org/openproject}
     * @param href the API link of a resource, e.g. {@code /openproject/api/v3/projects/1}
     * @return the URL of the page of the resource in OpenProject, e.g.
     *     {@code https://example.org/openproject/projects/1}
     */
    public static String toPageUrl(String connectionUrl, String href)
    {
        String path = StringUtils.defaultString(href);
        // A link that isn't under the path of the instance (e.g. a missing link) is kept as is.
        path = StringUtils.defaultIfEmpty(toInstanceRelativePath(connectionUrl, path), path);
        return StringUtils.defaultString(connectionUrl) + StringUtils.removeStart(path, API_PATH);
    }

    /**
     * @param connectionUrl the URL of the OpenProject instance
     * @return the path the instance is served under, without trailing slash, e.g. {@code /openproject}, or an empty
     *     string when it is served at the root of its host
     */
    private static String getInstancePath(String connectionUrl)
    {
        if (connectionUrl == null) {
            return "";
        }
        try {
            return StringUtils.removeEnd(StringUtils.defaultString(new URI(connectionUrl).getRawPath()), SLASH);
        } catch (URISyntaxException e) {
            // The requests to an invalid URL fail anyway.
            return "";
        }
    }
}
