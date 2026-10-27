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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.internal.macro.AbstractWorkItemsMacro;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Abstract macro that processes the parameters before sending them to the project management client, retrieving the
 * work items.
 *
 * @version $Id$
 */
public abstract class AbstractWorkItemsDisplayer extends AbstractWorkItemsMacro<ProjectManagementMacroParameters>
{
    private static final String KEY_CLIENT = "client";

    @Inject
    @Named("ssrx")
    protected SkinExtension ssrx;

    @Inject
    protected ContextualLocalizationManager localizationManager;

    /**
     * @param name the name of the work item.
     * @param description the description of the implemented macro.
     */
    public AbstractWorkItemsDisplayer(String name, String description)
    {
        super(name, description, ProjectManagementMacroParameters.class);
    }

    @Override
    public List<Block> execute(ProjectManagementMacroParameters parameters, String content,
        MacroTransformationContext context)
        throws MacroExecutionException
    {
        if (macroContext instanceof DefaultProjectManagementClientExecutionContext) {
            Map<String, Object> clientContext =
                URLEncodedUtils.parse(parameters.getSourceParameters(), StandardCharsets.UTF_8)
                    .stream()
                    .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
            ((DefaultProjectManagementClientExecutionContext) macroContext).setContext(clientContext);
        }
        String clientId = (String) macroContext.get(KEY_CLIENT);

        if (clientId == null || clientId.isEmpty()) {
            throw new MacroExecutionException("Failed to retrieve the client id from the source params.");
        }

        List<LiveDataQuery.Filter> filters;
        try {
            filters = getFilters(parameters.getFilters());
        } catch (LiveDataException e) {
            throw new MacroExecutionException("Failed to parse the filters.");
        }
        List<LiveDataQuery.SortEntry> sortEntries = getSortEntries(parameters.getSort());

        PaginatedResult<WorkItem> workItemList = null;
        try {
            // TODO: Maybe separate this logic in a separate method and allow the implementations to override it?
            workItemList = getWorkItems(clientId, parameters, filters, sortEntries);
        } catch (WorkItemException e) {
            throw new MacroExecutionException(
                String.format("Failed to retrieve the work items from the client [%s].", clientId), e);
        }

        return internalExecute(workItemList, parameters, context);
    }

    /**
     * Execute the macro being provided the work items that need displaying.
     *
     * @param workItemList a paginated result of work items matching the filter from the parameters.
     * @param parameters the parameters received in order to render the work items.
     * @param context the macro transformation context that can be used, among other things, to determine if the
     *     call of the macro is inline or not.
     * @return a list of blocks that will be rendered.
     */
    protected abstract List<Block> internalExecute(PaginatedResult<WorkItem> workItemList,
        ProjectManagementMacroParameters parameters,
        MacroTransformationContext context);

    /**
     * @return the message telling that no work item matches the filters, translated with the translation prefix of
     *     the client when it has one
     */
    protected String getNoItemsMessage()
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

    /**
     * @param workItem a work item
     * @return the key of the work item (e.g. {@code XWIKI-123}) or, when it has none, its identifier prefixed by
     *     {@code #} (e.g. {@code #123}), linked to the work item when it has a location
     * @since 1.3.1
     */
    protected List<Block> getIdentifierBlocks(WorkItem workItem)
    {
        List<Block> label = getIdentifierLabelBlocks(workItem);
        String location = workItem.getLinkableLocation(WorkItem.KEY_IDENTIFIER);
        if (label.isEmpty() || StringUtils.isEmpty(location)) {
            return label;
        }
        List<Block> blocks = new ArrayList<>();
        blocks.add(new LinkBlock(label, new ResourceReference(location, ResourceType.URL), false));
        return blocks;
    }

    /**
     * @param workItem a work item
     * @return the key of the work item (e.g. {@code XWIKI-123}) or, when it has none, its identifier prefixed by
     *     {@code #} (e.g. {@code #123}), not linked; empty when the work item has neither
     * @since 1.3.1
     */
    protected List<Block> getIdentifierLabelBlocks(WorkItem workItem)
    {
        String key = workItem.getKey();
        if (StringUtils.isNotEmpty(key)) {
            return new ArrayList<>(Collections.singletonList(new WordBlock(key)));
        }
        String value = workItem.getLinkableValue(WorkItem.KEY_IDENTIFIER);
        if (value != null) {
            return new ArrayList<>(Arrays.asList(new SpecialSymbolBlock('#'), new WordBlock(value)));
        }
        return new ArrayList<>();
    }

    @Override
    protected void setDefaultCategories(Set<String> defaultCategories)
    {
        super.setDefaultCategories(Collections.singleton("Internal"));
    }
}
