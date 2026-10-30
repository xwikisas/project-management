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
package com.xwiki.projectmanagement.openproject.internal.mentions;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectMentionsResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultOpenProjectMentionsResourceTest
{
    @InjectMockComponents
    private DefaultOpenProjectMentionsResource resource;

    @MockComponent
    private URLShortenerManager urlShortenerManager;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @Test
    void getMentionsWithIdWhenFailing() throws Exception
    {
        when(this.urlShortenerManager.getDocumentReference(null, "id"))
            .thenThrow(new URLShortenerException("Internal details"));

        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.getMentionsWithId("id", null, 10, 0, false));

        assertEquals(500, exception.getResponse().getStatus());
        assertEquals("Failed to resolve the page id.", exception.getResponse().getEntity());
        assertEquals("Failed to resolve the page id.", this.logCapture.getMessage(0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getMentionsWithIdWhenThePageIdIsMissing(String pageId)
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.getMentionsWithId(pageId, null, 10, 0, false));

        assertEquals(400, exception.getResponse().getStatus());
        assertEquals("Missing page id.", exception.getResponse().getEntity());
    }

    @Test
    void getMentionsWithIdWhenThePageIsUnknown()
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.getMentionsWithId("unknown", null, 10, 0, false));

        assertEquals(404, exception.getResponse().getStatus());
        assertNull(exception.getResponse().getEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc", "1.5", "1 OR 1", "*" })
    void getPagesMentioningWorkPackageWhenTheIdIsNotAnInteger(String workPackageId)
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.getPagesMentioningWorkPackage(workPackageId, null, 10, 0, null, null, false));

        assertEquals(400, exception.getResponse().getStatus());
        assertEquals("Work package id should be an integer.", exception.getResponse().getEntity());
    }
}
