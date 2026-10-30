package com.xwiki.projectmanagement.internal.displayers;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.BulletedListBlock;
import org.xwiki.rendering.block.ListItemBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Displays the work items as a bulleted list, one work item per line with its linked identifier and its summary.
 *
 * @version $Id$
 * @since 1.4.0
 */
@Component
@Singleton
@Named("workItemsList")
public class WorkItemsListDisplayer extends AbstractWorkItemsListDisplayer
{
    /**
     * Default constructor.
     */
    public WorkItemsListDisplayer()
    {
        super("List work items displayer.",
            "Display the work items as a bulleted list of their linked identifiers and summaries.");
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    protected List<Block> display(List<WorkItem> workItems, MacroTransformationContext context)
    {
        List<Block> items = new ArrayList<>();
        for (WorkItem workItem : workItems) {
            List<Block> line = getIdentifierBlocks(workItem);
            List<Block> summary = getSummaryBlocks(workItem);
            if (!line.isEmpty() && !summary.isEmpty()) {
                line.add(new SpaceBlock());
            }
            line.addAll(summary);
            items.add(new ListItemBlock(strikeThroughIfClosed(line, workItem)));
        }
        return Collections.singletonList(new BulletedListBlock(items));
    }
}
