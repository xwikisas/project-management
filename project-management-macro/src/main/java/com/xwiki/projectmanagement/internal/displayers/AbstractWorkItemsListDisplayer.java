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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
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
     * The maximum number of work items displayed, whatever the limit. A long list is still fast to display, since only
     * the displayed properties are retrieved (e.g. OpenProject returns 1000 work packages with their main properties in
     * less than half a second), but showing more than this is better left to a paginated table.
     */
    private static final int MAX_WORK_ITEMS = 1000;

    private static final String KEY_CLIENT = "client";

    /**
     * The properties displayed for each work item: its identifier (or key), its summary, and its status, telling if
     * it's closed.
     */
    private static final List<String> DISPLAYED_PROPERTIES = List.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_KEY,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_STATUS, WorkItem.KEY_RESOLVED);

    /**
     * @param name the name of the displayer.
     * @param description the description of the displayer.
     */
    public AbstractWorkItemsListDisplayer(String name, String description)
    {
        super(name, description);
    }

    @Override
    protected PaginatedResult<WorkItem> getWorkItems(String clientId, ProjectManagementMacroParameters parameters,
        List<LiveDataQuery.Filter> filters, List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemException
    {
        // Only the displayed properties are needed, which the client may retrieve much faster than all of them.
        Map<String, Object> context = new HashMap<>(macroContext.getContext());
        context.put(ProjectManagementClientExecutionContext.PROPERTIES, DISPLAYED_PROPERTIES);
        long offset = parameters.getOffset() == null ? 0 : parameters.getOffset();
        int limit = parameters.getLimit() == null ? MAX_WORK_ITEMS : Math.min(parameters.getLimit(), MAX_WORK_ITEMS);
        // The other client calls (e.g. to get the location of all the work items) don't restrict the properties.
        try (DefaultProjectManagementClientExecutionContext.ContextScope scope =
            DefaultProjectManagementClientExecutionContext.pushContext(macroContext, context))
        {
            return projectManagementManager.getWorkItems(clientId, Math.toIntExact(offset), limit, filters,
                sortEntries);
        }
    }

    @Override
    protected List<Block> internalExecute(PaginatedResult<WorkItem> workItemList,
        ProjectManagementMacroParameters parameters, MacroTransformationContext context)
    {
        if (workItemList.getItems().isEmpty()) {
            return Collections.singletonList(
                new MacroBlock("warning", Collections.emptyMap(), getNoItemsMessage(), context.isInline()));
        }
        return display(workItemList.getItems(), getMoreBlocks(workItemList, parameters), context);
    }

    /**
     * The work items left out because of the limit are counted, rather than silently missing, and linked to the list
     * of all the matching work items when the client can tell its location.
     *
     * @return the blocks telling how many matching work items aren't displayed, empty when they are all displayed
     */
    private List<Block> getMoreBlocks(PaginatedResult<WorkItem> workItemList,
        ProjectManagementMacroParameters parameters)
    {
        long offset = parameters.getOffset() == null ? 0 : parameters.getOffset();
        long more = workItemList.getTotalItems() == null ? 0
            : workItemList.getTotalItems() - offset - workItemList.getItems().size();
        if (more <= 0) {
            return new ArrayList<>();
        }
        Translation translation = getTranslation(getMoreTranslationKey());
        List<Block> label = new ArrayList<>();
        label.add(translation != null ? translation.render(more) : new WordBlock(String.valueOf(more)));
        Optional<String> location = getWorkItemsLocation(parameters);
        if (location.isEmpty()) {
            return label;
        }
        return new ArrayList<>(Collections.singletonList(
            new LinkBlock(label, new ResourceReference(location.get(), ResourceType.URL), false)));
    }

    private Optional<String> getWorkItemsLocation(ProjectManagementMacroParameters parameters)
    {
        try {
            return projectManagementManager.getWorkItemsLocation((String) macroContext.get(KEY_CLIENT),
                getFilters(parameters.getFilters()), getSortEntries(parameters.getSort()));
        } catch (LiveDataException | WorkItemException e) {
            return Optional.empty();
        }
    }

    /**
     * @return the key, without the prefix of the client, of the translation telling how many matching work items
     *     aren't displayed, whose parameter is their number
     */
    protected abstract String getMoreTranslationKey();

    /**
     * @param workItems the work items to display, never empty
     * @param moreBlocks the blocks telling how many matching work items aren't displayed (because of the limit), to
     *     display after the work items; empty when they are all displayed
     * @param context the macro transformation context, telling if the macro is called inline
     * @return the blocks displaying the work items
     */
    protected abstract List<Block> display(List<WorkItem> workItems, List<Block> moreBlocks,
        MacroTransformationContext context);

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
