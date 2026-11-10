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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link OpenProjectApiLinks}.
 *
 * @version $Id$
 */
class OpenProjectApiLinksTest
{
    private static final String ROOT_URL = "https://example.org";

    private static final String PATH_URL = "https://example.org/openproject";

    @Test
    void toInstanceRelativePathAtTheRootOfTheHost()
    {
        assertEquals("/api/v3/projects/1", OpenProjectApiLinks.toInstanceRelativePath(ROOT_URL, "/api/v3/projects/1"));
        assertEquals("/api/v3/projects/1",
            OpenProjectApiLinks.toInstanceRelativePath(ROOT_URL + '/', "/api/v3/projects/1"));
        assertNull(OpenProjectApiLinks.toInstanceRelativePath(ROOT_URL, null));
    }

    @Test
    void toInstanceRelativePathUnderAPath()
    {
        assertEquals("/api/v3/projects/1",
            OpenProjectApiLinks.toInstanceRelativePath(PATH_URL, "/openproject/api/v3/projects/1"));
        assertEquals("/api/v3/projects/1",
            OpenProjectApiLinks.toInstanceRelativePath(PATH_URL + '/', "/openproject/api/v3/projects/1"));
        // The links outside the path of the instance are rejected.
        assertNull(OpenProjectApiLinks.toInstanceRelativePath(PATH_URL, "/api/v3/projects/1"));
        assertNull(OpenProjectApiLinks.toInstanceRelativePath(PATH_URL, "/openprojectx/api/v3/projects/1"));
        assertNull(OpenProjectApiLinks.toInstanceRelativePath(PATH_URL, "/openproject"));
    }

    @Test
    void toPageUrl()
    {
        assertEquals(ROOT_URL + "/projects/1", OpenProjectApiLinks.toPageUrl(ROOT_URL, "/api/v3/projects/1"));
        assertEquals(PATH_URL + "/projects/1",
            OpenProjectApiLinks.toPageUrl(PATH_URL, "/openproject/api/v3/projects/1"));
        // A missing link gives the URL of the instance, as before.
        assertEquals(PATH_URL, OpenProjectApiLinks.toPageUrl(PATH_URL, ""));
        assertEquals(PATH_URL, OpenProjectApiLinks.toPageUrl(PATH_URL, null));
        // An invalid URL of the instance is considered served at the root of its host.
        assertEquals("http://a b/projects/1", OpenProjectApiLinks.toPageUrl("http://a b", "/api/v3/projects/1"));
    }
}
