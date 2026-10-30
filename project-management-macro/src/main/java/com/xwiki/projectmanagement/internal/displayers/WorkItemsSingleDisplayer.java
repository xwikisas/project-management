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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
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

    /**
     * The properties shown outside of the property tables: in the header, the project row and the description. The
     * resolution flag isn't shown since the status already tells whether the work item is closed.
     */
    private static final Set<String> NOT_LISTED_PROPERTIES = Set.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_KEY,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_PROJECT, WorkItem.KEY_DESCRIPTION, WorkItem.KEY_RESOLVED);

    /**
     * The order of the known properties in the property tables; the other properties come after them, in
     * alphabetical order.
     */
    private static final List<String> PROPERTY_ORDER = List.of(WorkItem.KEY_TYPE, WorkItem.KEY_STATUS,
        WorkItem.KEY_PRIORITY, WorkItem.KEY_RESOLUTION, WorkItem.KEY_PROGRESS, WorkItem.KEY_LABELS,
        WorkItem.KEY_ASSIGNEES, WorkItem.KEY_CREATOR, WorkItem.KEY_REPORTER, WorkItem.KEY_CLOSED_BY,
        WorkItem.KEY_MILESTONES, WorkItem.KEY_START_DATE, WorkItem.KEY_DUE_DATE, WorkItem.KEY_CREATION_DATE,
        WorkItem.KEY_UPDATE_DATE, WorkItem.KEY_CLOSE_DATE);

    private static final Comparator<String> PROPERTY_COMPARATOR =
        Comparator.<String>comparingInt(property -> PROPERTY_ORDER.contains(property)
            ? PROPERTY_ORDER.indexOf(property) : PROPERTY_ORDER.size()).thenComparing(Comparator.naturalOrder());

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
        if (workItemList.getItems().isEmpty()) {
            return Collections.singletonList(
                new MacroBlock("warning", Collections.emptyMap(), getNoItemsMessage(), false));
        }
        WorkItem workItem = workItemList.getItems().get(0);

        Block header = getHeaderBlock(workItem);
        Block project = getProjectBlock(workItem);
        Block description = getDescriptionBlock(workItem);
        Block bodyBlocks = getBodyBlocks(workItem);

        return Collections.singletonList(new GroupBlock(Arrays.asList(header, project, bodyBlocks, description),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-page-displayer")));
    }

    private Block getDescriptionBlock(WorkItem workItem)
    {
        List<Block> propertyNameBlock = getTranslationBlocks(PREFIX_PROPERTY + WorkItem.KEY_DESCRIPTION);
        List<Block> descriptionBlocks = getPropertyDisplayerManager().displayProperty(WorkItem.KEY_DESCRIPTION,
            workItem.getDescription(), Collections.emptyMap());

        return new GroupBlock(
            Arrays.asList(new GroupBlock(propertyNameBlock, PROP_NAME_PARAMS), new ParagraphBlock(descriptionBlocks)),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-package-description"));
    }

    private Block getBodyBlocks(WorkItem workItem)
    {
        TableBuilder tableGeneralProps = new TableBuilder();
        TableBuilder tableLinkables = new TableBuilder();
        TableBuilder tableTimeProps = new TableBuilder();
        Map<String, String> propertyNames = getPropertyNames();

        Map<String, Object> displayerParams = Map.of(
            KEY_INSTANCE, macroContext.getContext().getOrDefault(KEY_INSTANCE, "").toString(),
            "workItem", workItem
        );
        // Sort the properties so that they're always displayed in the same order.
        Map<String, Object> listedProperties = new TreeMap<>(PROPERTY_COMPARATOR);
        listedProperties.putAll(workItem);
        listedProperties.keySet().removeAll(NOT_LISTED_PROPERTIES);
        for (Map.Entry<String, Object> property : listedProperties.entrySet()) {
            if (property.getValue() == null) {
                continue;
            }
            List<Block> propertyNameBlock = getPropertyNameBlocks(property.getKey(), propertyNames);
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
    private Block getProjectBlock(WorkItem workItem)
    {
        List<Block> projectBlocks = getTranslationBlocks("displayer.single.project.prefix");

        projectBlocks.add(new SpecialSymbolBlock(':'));
        projectBlocks.add(new SpaceBlock());

        projectBlocks.addAll(
            getPropertyDisplayerManager().displayProperty(WorkItem.KEY_PROJECT, workItem.getProject(),
                Collections.emptyMap()));

        Block colBlock = getBootstrap1Col(Collections.singletonList(new ParagraphBlock(projectBlocks)));

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
    //    <p>Work Item <a href="#">#123</a></p> (or <a href="#">PROJ-123</a> when the work item has a key)
    //    <p>Some nice title.</p>
    //  </div>
    private GroupBlock getHeaderBlock(WorkItem workItem)
    {
        List<Block> headerId = getTranslationBlocks("displayer.single.header.workItem");
        headerId.add(new SpaceBlock());
        headerId.addAll(getIdentifierBlocks(workItem));
        List<Block> headerName = getPropertyDisplayerManager().displayProperty(String.class.getName(),
            workItem.getLinkableValue(WorkItem.KEY_SUMMARY), Collections.emptyMap());
        GroupBlock headerIdGroup = new GroupBlock(Collections.singletonList(new ParagraphBlock(headerId)),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-header-id"));
        GroupBlock headerNameGroup = new GroupBlock(Collections.singletonList(new ParagraphBlock(headerName)),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-header-name"));

        return new GroupBlock(Arrays.asList(headerNameGroup, headerIdGroup),
            Collections.singletonMap(ATTRIBUTE_CLASS, "work-item-header"));
    }

    private List<Block> getPropertyNameBlocks(String property, Map<String, String> propertyNames)
    {
        // The name given by the Live Data configuration of the client wins over the generic translation.
        String name = propertyNames.get(property);
        if (name != null
            && this.localizationManager.getTranslation(getTranslationPrefix() + PREFIX_PROPERTY + property) == null)
        {
            return new ArrayList<>(getPropertyDisplayerManager().displayProperty(String.class.getName(), name,
                Collections.emptyMap()));
        }
        return getTranslationBlocks(PREFIX_PROPERTY + property);
    }

    private List<Block> getTranslationBlocks(String key)
    {
        Translation translation = getTranslation(key);
        if (translation == null) {
            // Show the key that was supposed to be used.
            return new ArrayList<>(getPropertyDisplayerManager().displayProperty(String.class.getName(),
                getTranslationPrefix() + key, Collections.emptyMap()));
        }
        List<Block> blocks = new ArrayList<>();
        blocks.add(translation.render());
        return blocks;
    }
}
