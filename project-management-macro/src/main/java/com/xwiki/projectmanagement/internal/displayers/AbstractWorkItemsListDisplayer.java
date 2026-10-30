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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Base class of the displayers rendering several work items as plain content (no JavaScript), so that they also show
 * up in exports and in the text of the page used for search. The closed (resolved) work items are struck through.
 *
 * @version $Id$
 * @since 1.4.0
 */
public abstract class AbstractWorkItemsListDisplayer extends AbstractWorkItemsDisplayer
{
    /**
     * @param name the name of the displayer.
     * @param description the description of the displayer.
     */
    public AbstractWorkItemsListDisplayer(String name, String description)
    {
        super(name, description);
    }

    @Override
    protected List<Block> internalExecute(PaginatedResult<WorkItem> workItemList,
        ProjectManagementMacroParameters parameters, MacroTransformationContext context)
    {
        if (workItemList.getItems().isEmpty()) {
            return Collections.singletonList(
                new MacroBlock("warning", Collections.emptyMap(), getNoItemsMessage(), context.isInline()));
        }
        return display(workItemList.getItems(), context);
    }

    /**
     * @param workItems the work items to display, never empty
     * @param context the macro transformation context, telling if the macro is called inline
     * @return the blocks displaying the work items
     */
    protected abstract List<Block> display(List<WorkItem> workItems, MacroTransformationContext context);

    /**
     * @param workItem a work item
     * @return the summary of the work item, as plain text
     */
    protected List<Block> getSummaryBlocks(WorkItem workItem)
    {
        String summary = workItem.getLinkableValue(WorkItem.KEY_SUMMARY);
        if (StringUtils.isEmpty(summary)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(getPropertyDisplayerManager().displayProperty(String.class.getName(), summary,
            Collections.emptyMap()));
    }

    /**
     * @param blocks the blocks displaying a work item
     * @param workItem the displayed work item
     * @return the given blocks, struck through when the work item is closed
     */
    protected List<Block> strikeThroughIfClosed(List<Block> blocks, WorkItem workItem)
    {
        if (workItem.isResolved() && !blocks.isEmpty()) {
            List<Block> struckThrough = new ArrayList<>();
            struckThrough.add(new FormatBlock(blocks, Format.STRIKEDOUT));
            return struckThrough;
        }
        return blocks;
    }
}
