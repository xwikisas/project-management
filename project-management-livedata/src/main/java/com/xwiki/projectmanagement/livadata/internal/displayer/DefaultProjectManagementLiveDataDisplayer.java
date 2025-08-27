package com.xwiki.projectmanagement.livadata.internal.displayer;

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

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.livadata.displayer.ProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Default displayer implementation that handles the more complex work item properties (i.e. list of assignees) in a
 * generic way (i.e. transforms a list of linkables into a coherent html structure) or in a way that the livedata
 * displayers expect to receive the data.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultProjectManagementLiveDataDisplayer implements ProjectManagementLiveDataDisplayer
{
    @Inject
    protected WorkItemPropertyDisplayerManager defaultDisplayerManager;

    @Inject
    @Named("html/5.0")
    protected PrintRendererFactory htmlRendererFactory;

    @Inject
    protected Logger logger;

    @Inject
    protected ComponentManager componentManager;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    @Override
    public void display(Collection<WorkItem> workItems)
    {
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        PrintRenderer renderer = htmlRendererFactory.createRenderer(printer);

        for (WorkItem item : workItems) {
            for (Map.Entry<String, Object> itemProperty : item.entrySet()) {
                WorkItemPropertyDisplayerManager displayerManager = getPropertyDisplayerManager();
                displayProperty(itemProperty, renderer, displayerManager);
                printer.clear();
            }
        }
    }

    @Override
    public void displayProperty(Map.Entry<String, Object> itemProperty, PrintRenderer renderer,
        WorkItemPropertyDisplayerManager displayerManager)
    {
        if (displayerManager.getDisplayerForProperty(itemProperty.getKey()) != null) {
            setValueFromBlocksDisplayer(itemProperty, renderer, displayerManager, Collections.emptyMap());
        } else if (itemProperty.getValue() instanceof Date) {
            displayDateProperty(itemProperty);
        }
    }

    protected void setValueFromBlocksDisplayer(Map.Entry<String, Object> itemProperty, PrintRenderer renderer,
        WorkItemPropertyDisplayerManager propertyDisplayerManager, Map<String, String> displayerParams)
    {
        List<Block> representation =
            propertyDisplayerManager.displayProperty(itemProperty.getKey(), itemProperty.getValue(), displayerParams);
        XDOM xdom = new XDOM(representation);
        xdom.traverse(renderer);
        String html = renderer.getPrinter().toString();
        itemProperty.setValue(html);
    }

    /**
     * Since the livedata "date" displayer expects a UNIX Time Stamp, we will provide it here. We can't use the
     * {@link com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager} since it will generate some blocks
     * that can't be used by the aforementioned displayer.
     *
     * @param itemProperty the map entry of a work item that represents a date property.
     */
    private void displayDateProperty(Map.Entry<String, Object> itemProperty)
    {
        itemProperty.setValue(((Date) itemProperty.getValue()).getTime());
    }

    protected WorkItemPropertyDisplayerManager getPropertyDisplayerManager()
    {
        String clientId = (String) executionContext.get("client");
        if (clientId == null || clientId.isEmpty()) {
            return defaultDisplayerManager;
        }

        if (!componentManager.hasComponent(WorkItemPropertyDisplayerManager.class, clientId)) {
            return defaultDisplayerManager;
        }
        try {
            return componentManager.getInstance(WorkItemPropertyDisplayerManager.class, clientId);
        } catch (ComponentLookupException e) {
            return defaultDisplayerManager;
        }
    }
}
