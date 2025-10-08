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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import com.xwiki.projectmanagement.internal.utility.TableBuilder;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Limits the work items matched by the filters and parameters to a single work item. Displays it in a document-like
 * format.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("workItemsSingle")
public class WorkItemsSingleDisplayer extends AbstractWorkItemsDisplayer
{
    private static final String ATTRIBUTE_CLASS = "class";

    private static final String KEY_INSTANCE = "instance";

    private static final Map<String, String> CLASS_ROW = Collections.singletonMap(ATTRIBUTE_CLASS, "row");

    private static final Map<String, String> CLASS_COL_3 =
        Collections.singletonMap(ATTRIBUTE_CLASS, "col-md-4 col-xs-12");

    private static final Map<String, String> CLASS_COL_1 =
        Collections.singletonMap(ATTRIBUTE_CLASS, "col-md-12");

    private static final Map<String, String> PROP_NAME_PARAMS =
        Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-property-name");

    private static final Map<String, String> VALUE_PARAMS =
        Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-property-value");

    private static final String PREFIX_PROPERTY = "property.";

    @Inject
    private ContextualLocalizationManager localizationManager;

    /**
     * Default constructor.
     */
    public WorkItemsSingleDisplayer()
    {
        super("Single work item displayer.", "Display the first resulted work item in a page style.");
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
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
        this.ssrx.use("css/projectmanagement/displayer/single.css", Collections.singletonMap("forceSkinAction", true));
        String translationPrefix = (String) macroContext.get("translationPrefix");
        if (workItemList.getItems().size() <= 0) {
            String noWorkItemMessage = null;
            if (translationPrefix != null) {
                noWorkItemMessage =
                    localizationManager.getTranslationPlain(String.format("%s.%s", translationPrefix, "macro.noItems"));
            }
            if (noWorkItemMessage == null) {
                noWorkItemMessage = "There are no work items matching this filter.";
            }
            return Collections.singletonList(
                new MacroBlock("warning", Collections.emptyMap(), noWorkItemMessage, false));
        }
        WorkItem workItem = workItemList.getItems().get(0);

        Block header = getHeaderBlock(translationPrefix, workItem);
        Block project = getProjectBlock(translationPrefix, workItem);
        Block description = getDescriptionBlock(translationPrefix, workItem);
        Block bodyBlocks = getBodyBlocks(workItem, translationPrefix);

        return Collections.singletonList(new GroupBlock(Arrays.asList(header, project, bodyBlocks, description),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-page-displayer")));
    }

    private Block getDescriptionBlock(String translationPrefix, WorkItem workItem)
    {
        String description = (String) workItem.remove(WorkItem.KEY_DESCRIPTION);
        List<Block> propertyNameBlock =
            getTranslationBlocks(WorkItem.KEY_DESCRIPTION, translationPrefix + PREFIX_PROPERTY);
        List<Block> descriptionBlocks =
            getPropertyDisplayerManager().displayProperty(WorkItem.KEY_DESCRIPTION, description,
                Collections.emptyMap());

        return new GroupBlock(
            Arrays.asList(new GroupBlock(propertyNameBlock, PROP_NAME_PARAMS), new ParagraphBlock(descriptionBlocks)),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-package-description"));
    }

    private Block getBodyBlocks(WorkItem workItem, String translationPrefix)
    {
        TableBuilder tableGeneralProps = new TableBuilder();
        TableBuilder tableLinkables = new TableBuilder();
        TableBuilder tableTimeProps = new TableBuilder();
        String propertyPrefix = translationPrefix + PREFIX_PROPERTY;

        Map<String, Object> displayerParams = Map.of(
            KEY_INSTANCE, macroContext.getContext().getOrDefault(KEY_INSTANCE, "").toString(),
            "workItem", workItem
        );
        for (Map.Entry<String, Object> property : workItem.entrySet()) {
            if (property.getValue() == null) {
                continue;
            }
            List<Block> propertyNameBlock = getTranslationBlocks(property.getKey(), propertyPrefix);
            propertyNameBlock.add(new SpecialSymbolBlock(':'));
            List<Block> propertyValueBlock = getPropertyDisplayerManager().displayProperty(property.getKey(),
                property.getValue(), displayerParams);
            // Group them per type.
            TableBuilder chosenSection = tableGeneralProps;
            if (property.getValue() instanceof Date) {
                chosenSection = tableTimeProps;
            } else if (property.getValue() instanceof Linkable || isCollectionOfLinkables(property)) {
                chosenSection = tableLinkables;
            }
            chosenSection.newRow().newCell(propertyNameBlock, PROP_NAME_PARAMS).newCell(propertyValueBlock,
                VALUE_PARAMS);
        }

        Block generalPropsBlock = getBootstrap3Col(Collections.singletonList(tableGeneralProps.build()));
        Block linkablePropsBlock = getBootstrap3Col(Collections.singletonList(tableLinkables.build()));
        Block timePropsBlock = getBootstrap3Col(Collections.singletonList(tableTimeProps.build()));

        return getBootstrapRow(Arrays.asList(generalPropsBlock, linkablePropsBlock, timePropsBlock));
    }

    private static boolean isCollectionOfLinkables(Map.Entry<String, Object> property)
    {
        Object value = property.getValue();
        if (!(value instanceof Collection)) {
            return false;
        }
        if (((Collection<?>) value).isEmpty()) {
            return false;
        }
        Object valueFromCollection = ((Collection<?>) value).iterator().next();

        return valueFromCollection instanceof Linkable;
    }

    // <div class="row">
    //    <div class="col-md-12">
    //      <p>Work item from project: <a href="#">> Some project name</a></p>
    //    </div>
    //  </div>
    private Block getProjectBlock(String translationPrefix, WorkItem workItem)
    {
        List<Block> projectBlocks = getTranslationBlocks("displayer.single.project.prefix", translationPrefix);

        projectBlocks.add(new SpecialSymbolBlock(':'));
        projectBlocks.add(new SpaceBlock());

        projectBlocks.addAll(
            getPropertyDisplayerManager().displayProperty(WorkItem.KEY_PROJECT, workItem.getProject(),
                Collections.emptyMap()));

        Block colBlock = getBootstrap1Col(Collections.singletonList(new ParagraphBlock(projectBlocks)));

        // Remove the project property so it won't get displayed again.
        workItem.remove(WorkItem.KEY_PROJECT);

        return getBootstrapRow(Collections.singletonList(colBlock));
    }

    private Block getBootstrapRow(List<Block> children)
    {
        return new GroupBlock(children, CLASS_ROW);
    }

    private Block getBootstrap3Col(List<Block> children)
    {
        return new GroupBlock(children, CLASS_COL_3);
    }

    private Block getBootstrap1Col(List<Block> children)
    {
        return new GroupBlock(children, CLASS_COL_1);
    }

    //  <div class="work-item-header">
    //    <p>Work Item <a href="#">#123</a></p>
    //    <p>Some nice title.</p>
    //  </div>
    private GroupBlock getHeaderBlock(String translationPrefix, WorkItem workItem)
    {
        List<Block> headerId = getTranslationBlocks("displayer.single.header.workItem", translationPrefix);
        headerId.add(new SpaceBlock());
        headerId.add(
            new LinkBlock(
                Arrays.asList(new SpecialSymbolBlock('#'),
                    new WordBlock(workItem.getLinkableValue(WorkItem.KEY_IDENTIFIER))),
                new ResourceReference(workItem.getLinkableLocation(WorkItem.KEY_IDENTIFIER), ResourceType.URL),
                false
            ));
        List<Block> headerName = getPropertyDisplayerManager().displayProperty(String.class.getName(),
            workItem.getLinkableValue(WorkItem.KEY_SUMMARY), Collections.emptyMap());
        // Remove id and summary so they won't be displayed again.
        workItem.remove(WorkItem.KEY_IDENTIFIER);
        workItem.remove(WorkItem.KEY_SUMMARY);
        GroupBlock headerIdGroup = new GroupBlock(Collections.singletonList(new ParagraphBlock(headerId)),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-header-id"));
        GroupBlock headerNameGroup = new GroupBlock(Collections.singletonList(new ParagraphBlock(headerName)),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-header-name"));

        return new GroupBlock(Arrays.asList(headerNameGroup, headerIdGroup),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-header"));
    }

    private List<Block> getTranslationBlocks(String key, String translationPrefix)
    {
        List<Block> returnList = null;
        String translationKey = translationPrefix + key;

        if (localizationManager.getTranslation(translationKey) == null) {
            // Returned the key that was supposed to be used.
            returnList = getPropertyDisplayerManager().displayProperty(String.class.getName(), translationKey,
                Collections.emptyMap());
        } else {
            returnList = new ArrayList<>();
            returnList.add(localizationManager.getTranslation(translationKey).render());
        }
        return returnList;
    }
}
