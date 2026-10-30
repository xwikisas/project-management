package com.xwiki.projectmanagement.livedata;

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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataEntryStore;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.livedata.displayer.ProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Provides the storing and retrieval operations for the entries coming from an
 * {@link com.xwiki.projectmanagement.ProjectManagementClient} identified by a hint present in the livedata
 * configuration.
 *
 * @version $Id$
 */
@Component
@Named("projectmanagement")
@Singleton
public class ProjectManagementEntryStore implements LiveDataEntryStore
{
    private static final List<String> LINK_PROPERTIES = List.of(WorkItem.KEY_IDENTIFIER,
        WorkItem.KEY_SUMMARY, WorkItem.KEY_CREATOR, WorkItem.KEY_PROJECT, WorkItem.KEY_REPORTER);

    private static final String FLATTEN_FORMAT = "%s.%s";

    @Inject
    private ProjectManagementManager projectManagementManager;

    @Inject
    private ProjectManagementLiveDataDisplayer defaultDisplayer;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private Logger logger;

    @Inject
    private ProjectManagementClientExecutionContext clientContext;

    @Override
    public Optional<Map<String, Object>> get(Object entryId) throws LiveDataException
    {
        // The entry id alone doesn't identify the client holding the work item (it's a source parameter, which only
        // the query provides), so single entries can't be retrieved.
        return Optional.empty();
    }

    @Override
    public LiveData get(LiveDataQuery query) throws LiveDataException
    {
        LiveData ld = new LiveData();
        String clientId = (String) query.getSource().getParameters().getOrDefault("client", "");
        if (clientId.isEmpty()) {
            throw new LiveDataException("The client property was not specified in the source parameters.");
        }

        // Set the context for the client implementation.
        if (clientContext instanceof DefaultProjectManagementClientExecutionContext) {
            ((DefaultProjectManagementClientExecutionContext) clientContext).setContext(
                query.getSource().getParameters());
        }

        // GET ENTRIES FROM ACTUAL SOURCE ---------------
        PaginatedResult<WorkItem> workItems = null;
        try {
            workItems =
                projectManagementManager.getWorkItems(clientId, Math.toIntExact(query.getOffset()), query.getLimit(),
                    query.getFilters(), query.getSort());
        } catch (WorkItemException e) {
            throw new LiveDataException("Failed to retrieve the work items.", e);
        }
        ld.getEntries().addAll(workItems.getItems());
        ld.setCount(workItems.getTotalItems());
        // PREPARE THE RESULTS FOR THE LIVEDATA - TURN THE LINKABLES TO PROPERTIES OF THE WORK ITEM -----------
        // The displayers replace the linkables by their HTML display, so keep their locations to expose them as raw
        // URLs, e.g. for the actions that open the work item.
        Map<WorkItem, Map<String, Object>> locations = getLocations(workItems.getItems());
        applyDisplayers(workItems.getItems(), clientId);
        flatten(ld);
        // The entries are the work items themselves.
        locations.forEach(Map::putAll);

        return ld;
    }

    // TODO: Use the WorkItemPropertyDisplayerManager in order to display certain properties. (linkables, props that
    //  generate html structures).
    private void applyDisplayers(List<WorkItem> workItems, String clientId)
    {
        if (!componentManager.hasComponent(ProjectManagementLiveDataDisplayer.class, clientId)) {
            defaultDisplayer.display(workItems);
            return;
        }
        try {
            ProjectManagementLiveDataDisplayer displayer =
                componentManager.getInstance(ProjectManagementLiveDataDisplayer.class, clientId);
            displayer.display(workItems);
        } catch (ComponentLookupException e) {
            logger.warn("Failed to find the project management livedata displayer with hint [{}].",
                clientId);
        }
    }

    private static Map<WorkItem, Map<String, Object>> getLocations(List<WorkItem> workItems)
    {
        // The work items are maps whose content changes when they are displayed, so they are matched by identity.
        Map<WorkItem, Map<String, Object>> locations = new IdentityHashMap<>();
        for (WorkItem workItem : workItems) {
            Map<String, Object> itemLocations = new HashMap<>();
            for (String linkWorkItemProperty : LINK_PROPERTIES) {
                Object property = workItem.get(linkWorkItemProperty);
                if (property instanceof Map && ((Map<?, ?>) property).get(Linkable.KEY_LOCATION) != null) {
                    itemLocations.put(String.format(FLATTEN_FORMAT, linkWorkItemProperty, Linkable.KEY_LOCATION),
                        ((Map<?, ?>) property).get(Linkable.KEY_LOCATION));
                }
            }
            locations.put(workItem, itemLocations);
        }
        return locations;
    }

    // TODO: Have a more generic method of flattening a work item.
    private static void flatten(LiveData ld)
    {
        for (Map<String, Object> entry : ld.getEntries()) {
            for (String linkWorkItemProperty : LINK_PROPERTIES) {
                Object property = entry.get(linkWorkItemProperty);
                if (property == null) {
                    continue;
                }
                if (!(property instanceof Map)) {
                    continue;
                }
                Map<String, Object> linkableProperty = (Map<String, Object>) property;
                entry.put(String.format(FLATTEN_FORMAT, linkWorkItemProperty, Linkable.KEY_LOCATION),
                    linkableProperty.get(Linkable.KEY_LOCATION));
                entry.put(String.format(FLATTEN_FORMAT, linkWorkItemProperty, Linkable.KEY_VALUE),
                    linkableProperty.get(Linkable.KEY_VALUE));
                entry.remove(linkWorkItemProperty);
            }
        }
    }
}
