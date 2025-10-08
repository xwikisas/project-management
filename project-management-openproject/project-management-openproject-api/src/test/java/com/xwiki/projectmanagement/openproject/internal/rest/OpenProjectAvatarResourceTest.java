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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.xwiki.bridge.SkinAccessBridge;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.UserAvatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class OpenProjectAvatarResourceTest
{
    @InjectMockComponents
    private OpenProjectAvatarResource avatarResource;

    @MockComponent
    private Logger logger;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private SkinAccessBridge skinAccessBridge;

    @Mock
    private OpenProjectApiClient openProjectApiClient;

    @BeforeEach
    void configure() {
        ReflectionUtils.setFieldValue(this.avatarResource, "slf4Jlogger", this.logger);
    }
    @Test
    public void getExistingAvatarTest() throws ProjectManagementException, URISyntaxException
    {
        when(openProjectConfiguration.getOpenProjectApiClient(any())).thenReturn(openProjectApiClient);
        UserAvatar userAvatar = new UserAvatar(mock(StreamingOutput.class), "test");
        when(openProjectApiClient.getUserAvatar("user")).thenReturn(userAvatar);

        Response response = avatarResource.getAvatar("wiki", "test", "user");

        assertEquals(200, response.getStatus());
        assertEquals(userAvatar.getStreamingOutput(), response.getEntity());
        assertEquals(String.format("[%s/*]", userAvatar.getContentType()),
            response.getMetadata().get("Content-Type").toString());
    }

    @Test
    public void getAvatarThatDoesNotExistOrFailToRetrieveTest() throws ProjectManagementException, URISyntaxException
    {
        when(openProjectConfiguration.getOpenProjectApiClient(any())).thenReturn(openProjectApiClient);
        UserAvatar userAvatar = new UserAvatar(mock(StreamingOutput.class), "test");
        when(openProjectApiClient.getUserAvatar("user")).thenThrow(new ProjectManagementException(""));
        String noavatarPath = "/get/icons/xwiki/noavatar.png";
        when(skinAccessBridge.getSkinFile(any())).thenReturn(noavatarPath);
        Response response = avatarResource.getAvatar("wiki", "test", "user");

        assertEquals(303, response.getStatus());
        verify(skinAccessBridge).getSkinFile("icons/xwiki/noavatar.png");
        Object uri = response.getMetadata().getFirst("Location");
        assertInstanceOf(URI.class, uri);
        assertEquals(noavatarPath, ((URI) uri).getPath());
        verify(this.logger).error(anyString(), anyString(), any(Throwable.class));
    }

    @Test
    public void getAvatarWithoutBeingAuthorized() throws URISyntaxException
    {
        when(openProjectConfiguration.getOpenProjectApiClient(any())).thenReturn(null);

        Response response = avatarResource.getAvatar("wiki", "test", "user");

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }
}
