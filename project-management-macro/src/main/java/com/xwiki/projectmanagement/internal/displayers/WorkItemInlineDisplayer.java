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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Displays a single work item inline.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("workItemInline")
public class WorkItemInlineDisplayer extends AbstractWorkItemsDisplayer
{
    private static final String ATTRIBUTE_CLASS = "class";

    private static final String KEY_INSTANCE = "instance";

    @Inject
    private ContextualLocalizationManager localizationManager;

    /**
     * Default constructor.
     */
    public WorkItemInlineDisplayer()
    {
        super("Highlight work item displayer.",
            "Display the first resulted work item as a compact highlighted inline badge.");
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    @Override
    protected PaginatedResult<WorkItem> getWorkItems(String clientId, ProjectManagementMacroParameters parameters,
        List<LiveDataQuery.Filter> filters, List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemException
    {
        parameters.setLimit(1);
        return super.getWorkItems(clientId, parameters, filters, sortEntries);
    }

    @Override
    protected List<Block> internalExecute(PaginatedResult<WorkItem> workItemList,
        ProjectManagementMacroParameters parameters, MacroTransformationContext context)
    {
        this.ssrx.use("css/projectmanagement/displayer/inline.css", Collections.singletonMap("forceSkinAction", true));
        if (workItemList.getItems().isEmpty()) {

            return Collections.singletonList(
                new MacroBlock("warning", Collections.emptyMap(), getNoItemsMessage(), false));
        }

        WorkItem workItem = workItemList.getItems().get(0);

        List<Block> children = new ArrayList<>();
        addTypeBadge(children, workItem);
        addIdentifierLink(children, workItem);
        addSummaryBadge(children, workItem);

        return Collections.singletonList(
            new GroupBlock(children));
    }

    private String getNoItemsMessage()
    {
        String translationPrefix = (String) macroContext.get("translationPrefix");
        if (translationPrefix != null) {
            String translated =
                localizationManager.getTranslationPlain(String.format("%s.%s", translationPrefix, "macro.noItems"));
            if (translated != null) {
                return translated;
            }
        }
        return "There are no work items matching this filter.";
    }

    private void addTypeBadge(List<Block> children, WorkItem workItem)
    {
        String type = workItem.getType();
        if (type != null && !type.isEmpty()) {
            Map<String, Object> displayerParams = Map.of(
                KEY_INSTANCE, macroContext.getContext().getOrDefault(KEY_INSTANCE, "").toString(),
                "workItem", workItem
            );
            List<Block> typeBlocks = getPropertyDisplayerManager().displayProperty(
                WorkItem.KEY_TYPE, type, displayerParams);
            children.addAll(typeBlocks);
            children.add(new SpaceBlock());
        }
    }

    private void addIdentifierLink(List<Block> children, WorkItem workItem)
    {
        String value = workItem.getLinkableValue(WorkItem.KEY_IDENTIFIER);
        String location = workItem.getLinkableLocation(WorkItem.KEY_IDENTIFIER);
        if (value != null) {
            LinkBlock idLink = new LinkBlock(
                Arrays.asList(new SpecialSymbolBlock('#'), new WordBlock(value)),
                new ResourceReference(location != null ? location : "", ResourceType.URL),
                false,
                Collections.singletonMap(ATTRIBUTE_CLASS, "text-primary")
            );
            children.add(idLink);
            children.add(new SpaceBlock());
        }
    }

    private void addSummaryBadge(List<Block> children, WorkItem workItem)
    {
        String summary = workItem.getLinkableValue(WorkItem.KEY_SUMMARY);

        if (summary != null && !summary.isEmpty()) {
            List<Block> summaryBlocks = getPropertyDisplayerManager().displayProperty(String.class.getName(), summary,
                Collections.emptyMap());
            GroupBlock summaryGroup = new GroupBlock(Collections.singletonList(new ParagraphBlock(summaryBlocks)),
                Collections.singletonMap(ATTRIBUTE_CLASS, "text-muted work-package-inline-summary"));
            children.add(summaryGroup);
        }
    }
}
