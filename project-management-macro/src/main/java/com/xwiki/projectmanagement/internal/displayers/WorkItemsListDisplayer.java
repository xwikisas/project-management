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
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.BulletedListBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.ListItemBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Displays a given work items collection in a list.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("workItemsList")
public class WorkItemsListDisplayer extends AbstractWorkItemsDisplayer<ProjectManagementMacroParameters>
{
    /**
     * Default constructor.
     */
    public WorkItemsListDisplayer()
    {
        super("Work items list displayer");
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    protected List<Block> internalExecute(PaginatedResult<WorkItem> workItemList,
        ProjectManagementMacroParameters parameters, MacroTransformationContext context)
    {
        Block result = new GroupBlock();

        List<Block> listItems = new ArrayList<>();
        for (WorkItem item : workItemList.getItems()) {
            listItems.add(new ListItemBlock(
                Collections.singletonList(
                    new ParagraphBlock(
                        Collections.singletonList(new WordBlock((String) item.getOrDefault("identifier.value",
                            "10")))))));
        }

        result.addChild(new BulletedListBlock(listItems));

        return Collections.singletonList(result);
    }
}
