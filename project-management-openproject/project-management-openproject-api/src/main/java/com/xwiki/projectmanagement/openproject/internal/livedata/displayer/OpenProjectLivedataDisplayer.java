package com.xwiki.projectmanagement.openproject.internal.livedata.displayer;

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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.renderer.PrintRenderer;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.livadata.internal.displayer.DefaultProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Livedata displayer for Open Project entries. It uses the Open Project implementation of the
 * {@link WorkItemPropertyDisplayerManager} for the properties of interest and the
 * {@link DefaultProjectManagementLiveDataDisplayer} for any other property.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class OpenProjectLivedataDisplayer extends DefaultProjectManagementLiveDataDisplayer
{
    private static final String KEY_INSTANCE = "instance";

    private static final Set<String> HANDLED_PROPS = Set.of(WorkItem.KEY_STATUS, WorkItem.KEY_TYPE);

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    @Inject
    private ComponentManager componentManager;

    @Override
    public void displayProperty(Map.Entry<String, Object> itemProperty, PrintRenderer renderer)
    {
        if (HANDLED_PROPS.contains(itemProperty.getKey())) {
            String clientId = (String) executionContext.get("client");
            String instanceId = (String) executionContext.getContext().getOrDefault(KEY_INSTANCE, "");
            WorkItemPropertyDisplayerManager displayerManager = defaultDisplayerManager;
            if (clientId != null && !clientId.isEmpty()) {
                try {
                    displayerManager = componentManager.getInstance(WorkItemPropertyDisplayerManager.class, clientId);
                } catch (ComponentLookupException ignored) {
                    logger.debug("No WorkItemPropertyDisplayerManager with id [{}] found. Using the default displayer.",
                        clientId);
                }
                setValueFromBlocksDisplayer(itemProperty, renderer, displayerManager,
                    Collections.singletonMap(KEY_INSTANCE, instanceId));
            }
        } else {
            super.displayProperty(itemProperty, renderer);
        }
    }
}
