package com.xwiki.projectmanagement.livadata;

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
import com.xwiki.projectmanagement.livadata.displayer.ProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Something.
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

    private Map<String, Map<String, Object>> db = new HashMap<>();

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

    /**
     * @param entryId identifies the entry to return
     * @return sadas
     * @throws LiveDataException sadasd
     */
    @Override
    public Optional<Map<String, Object>> get(Object entryId) throws LiveDataException
    {
        return Optional.of(db.get(entryId));
    }

    @Override
    public Optional<Object> get(Object entryId, String property) throws LiveDataException
    {
        return LiveDataEntryStore.super.get(entryId, property);
    }

    @Override
    public LiveData get(LiveDataQuery query) throws LiveDataException
    {
        LiveData ld = new LiveData();
        String clientId = (String) query.getSource().getParameters().getOrDefault("client", "");
        if (clientId.isEmpty()) {
            throw new LiveDataException("The client property was not specified in the source parameters.");
        }

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
        applyDisplayers(workItems.getItems(), clientId);
        flatten(ld);

        return ld;
    }

    private void applyDisplayers(List<WorkItem> workItems, String clientId)
    {
        defaultDisplayer.display(workItems);

        if (!componentManager.hasComponent(ProjectManagementLiveDataDisplayer.class, clientId)) {
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

    @Override
    public Optional<Object> save(Map<String, Object> entry) throws LiveDataException
    {
        return LiveDataEntryStore.super.save(entry);
    }

    @Override
    public Optional<Object> update(Object entryId, String property, Object value) throws LiveDataException
    {
        return LiveDataEntryStore.super.update(entryId, property, value);
    }

    @Override
    public Optional<Map<String, Object>> remove(Object entryId) throws LiveDataException
    {
        return LiveDataEntryStore.super.remove(entryId);
    }
}
