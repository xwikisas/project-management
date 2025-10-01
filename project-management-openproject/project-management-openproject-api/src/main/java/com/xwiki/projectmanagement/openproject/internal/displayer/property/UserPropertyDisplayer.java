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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.openproject.internal.displayer.OpenProjectDisplayerManager;

import static org.xwiki.xml.html.HTMLConstants.ATTRIBUTE_CLASS;

/**
 * Displays a user property as a structure consisting of the avatar of the user and a link to their profile.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public class UserPropertyDisplayer implements WorkItemPropertyDisplayer
{
    private WorkItemPropertyDisplayerManager displayerManager;

    /**
     * @param displayerManager the displayer manager that handles this displayer.
     */
    public UserPropertyDisplayer(WorkItemPropertyDisplayerManager displayerManager)
    {
        this.displayerManager = displayerManager;
    }

    /**
     * @param property the property value that will be displayed.
     * @param params any additional params that the displayer might need (i.e. translation prefix).
     * @return the structure of blocks in the form of an avatar and a link.
     */
    @Override
    public List<Block> display(Object property, Map<String, String> params)
    {
        List<Block> blocks;
        if (property instanceof Collection<?>) {
            blocks = displayerManager.displayProperty("list", property, params);
        } else {
            blocks = displayerManager.displayProperty(property.getClass().getName(), property, params);
        }

        if (params.isEmpty() || !params.containsKey(OpenProjectDisplayerManager.KEY_INSTANCE) || !params.containsKey(
            OpenProjectDisplayerManager.KEY_WIKI))
        {
            return blocks;
        }
        XDOM xdom = new XDOM(blocks);

        List<Block> userLinkBlocks = xdom.getBlocks(new ClassBlockMatcher(LinkBlock.class),
            Block.Axes.DESCENDANT_OR_SELF);

        if (userLinkBlocks.isEmpty()) {
            return blocks;
        }

        List<Block> linksWithAvatars = new ArrayList<>();
        for (Block userLinkBlock : userLinkBlocks) {
            GroupBlock group = new GroupBlock();
            String userAvatarLink = ((LinkBlock) userLinkBlock).getReference().getReference();
            String userAvatarPath = userAvatarLink.substring(userAvatarLink.indexOf("/users/"));
            String xwikiUserAvatarLink = String.format("/xwiki/rest/wikis/%s/openproject/instance/%s%s/avatar",
                params.get(OpenProjectDisplayerManager.KEY_WIKI),
                params.get(OpenProjectDisplayerManager.KEY_INSTANCE),
                userAvatarPath);
            ImageBlock imageBlock = new ImageBlock(new ResourceReference(xwikiUserAvatarLink, ResourceType.URL), true,
                Map.of("alt", "User avatar", ATTRIBUTE_CLASS, "user-avatar"));

            group.addChild(new FormatBlock(Collections.singletonList(imageBlock), Format.NONE,
                Collections.singletonMap(ATTRIBUTE_CLASS, "open-project-user-avatar user")));
            group.addChild(new FormatBlock(Collections.singletonList(userLinkBlock), Format.NONE,
                Collections.singletonMap(ATTRIBUTE_CLASS, "open-project-user-link")));

            linksWithAvatars.add(group);
        }
        return linksWithAvatars;
    }
}
