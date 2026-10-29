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

package com.xwiki.projectmanagement.openproject.internal.rest;

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.bridge.SkinAccessBridge;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.UserAvatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectAvatarResource}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectAvatarResourceTest
{
    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectAvatarResource avatarResource;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private SkinAccessBridge skinAccessBridge;

    @Mock
    private OpenProjectApiClient openProjectApiClient;

    @Test
    public void getExistingAvatarTest() throws ProjectManagementException, URISyntaxException
    {
        when(openProjectAccess.resolve(any())).thenReturn(OpenProjectAccessResult.authenticated(openProjectApiClient));
        UserAvatar userAvatar = new UserAvatar(mock(StreamingOutput.class), "test");
        when(openProjectApiClient.getUserAvatar("42")).thenReturn(userAvatar);

        Response response = avatarResource.getAvatar("wiki", "test", "42");

        assertEquals(200, response.getStatus());
        assertEquals(userAvatar.getStreamingOutput(), response.getEntity());
        assertEquals(String.format("[%s/*]", userAvatar.getContentType()),
            response.getMetadata().get("Content-Type").toString());
        assertEquals("nosniff", response.getMetadata().getFirst("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", response.getMetadata().getFirst("Content-Security-Policy"));
    }

    @Test
    void getAvatarWithInvalidUserId() throws Exception
    {
        when(openProjectAccess.resolve(any())).thenReturn(OpenProjectAccessResult.authenticated(openProjectApiClient));
        when(skinAccessBridge.getSkinFile(any())).thenReturn("/get/icons/xwiki/noavatar.png");

        Response response = avatarResource.getAvatar("wiki", "test", "../../attachments/1/content");

        assertEquals(303, response.getStatus());
        verify(openProjectApiClient, never()).getUserAvatar(any());
    }

    @Test
    public void getAvatarThatDoesNotExistOrFailToRetrieveTest() throws ProjectManagementException, URISyntaxException
    {
        when(openProjectAccess.resolve(any())).thenReturn(OpenProjectAccessResult.authenticated(openProjectApiClient));
        when(openProjectApiClient.getUserAvatar("42")).thenThrow(new ProjectManagementException(""));
        String noavatarPath = "/get/icons/xwiki/noavatar.png";
        when(skinAccessBridge.getSkinFile(any())).thenReturn(noavatarPath);
        Response response = avatarResource.getAvatar("wiki", "test", "42");

        assertEquals(303, response.getStatus());
        verify(skinAccessBridge).getSkinFile("icons/xwiki/noavatar.png");
        Object uri = response.getMetadata().getFirst("Location");
        assertInstanceOf(URI.class, uri);
        assertEquals(noavatarPath, ((URI) uri).getPath());
        assertEquals("Failed to retrive the avatar for user [42] due to network or server issues.",
            this.logCapture.getMessage(0));
    }

    @Test
    public void getAvatarWithoutBeingAuthorized() throws URISyntaxException
    {
        when(openProjectAccess.resolve(any())).thenReturn(OpenProjectAccessResult.notConnected());

        Response response = avatarResource.getAvatar("wiki", "test", "42");

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }
}
