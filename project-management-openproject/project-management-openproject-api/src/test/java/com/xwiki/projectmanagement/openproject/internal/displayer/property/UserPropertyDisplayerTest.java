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
package com.xwiki.projectmanagement.openproject.internal.displayer.property;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.environment.internal.ServletEnvironment;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.openproject.internal.displayer.OpenProjectDisplayerManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserPropertyDisplayer}.
 *
 * @version $Id$
 */
class UserPropertyDisplayerTest
{
    @Test
    void avatarUrlEncodesTheConnectionName()
    {
        WorkItemPropertyDisplayerManager displayerManager = mock(WorkItemPropertyDisplayerManager.class);
        String user = "OpenProject Admin";
        LinkBlock userLink = new LinkBlock(List.of(new WordBlock(user)),
            new ResourceReference("http://localhost:8082/api/v3/users/4", ResourceType.URL), false);
        when(displayerManager.displayProperty(eq(String.class.getName()), eq(user), any()))
            .thenReturn(List.of(userLink));
        ServletEnvironment environment = mock(ServletEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getServletContext().getContextPath()).thenReturn("/xwiki");

        // A connection name with characters that would end the path segment or break the URL if not encoded.
        List<Block> blocks = new UserPropertyDisplayer(displayerManager, environment).display(user,
            Map.of(OpenProjectDisplayerManager.KEY_INSTANCE, "Local <b>\"OP\"</b> & 'co' 1+1",
                OpenProjectDisplayerManager.KEY_WIKI, "xwiki"));

        ImageBlock avatar = new XDOM(blocks).getFirstBlock(new ClassBlockMatcher(ImageBlock.class),
            Block.Axes.DESCENDANT);
        assertEquals("/xwiki/rest/wikis/xwiki/openproject/instance/"
            + "Local%20%3Cb%3E%22OP%22%3C%2Fb%3E%20%26%20%27co%27%201%2B1/users/4/avatar",
            avatar.getReference().getReference());
    }
}
