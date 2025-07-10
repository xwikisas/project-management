package com.xwiki.projectmanagement.internal;

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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Default implementation of the {@link ProjectManagementManager}. It searches the component manager for the client
 * implementation matching the hint and passes the parameters further.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultProjectManagementManager implements ProjectManagementManager
{
    @Inject
    private ComponentManager componentManager;

    @Override
    public WorkItem getWorkItem(String client, String workItemId) throws WorkItemException
    {
        return getClient(client).getWorkItem(workItemId);
    }

    @Override
    public PaginatedResult<WorkItem> getWorkItems(String client, int page, int pageSize,
        List<LiveDataQuery.Filter> filters, List<LiveDataQuery.SortEntry> sortEntries) throws WorkItemException
    {
        return getClient(client).getWorkItems(page, pageSize, filters, sortEntries);
    }

    @Override
    public WorkItem createWorkItem(String client, WorkItem workItem) throws WorkItemException
    {
        return getClient(client).createWorkItem(workItem);
    }

    @Override
    public WorkItem updateWorkItem(String client, WorkItem workItem) throws WorkItemException
    {
        return getClient(client).updateWorkItem(workItem);
    }

    @Override
    public boolean deleteWorkItem(String client, String workItemId) throws WorkItemException
    {
        return getClient(client).deleteWorkItem(workItemId);
    }

    private ProjectManagementClient getClient(String hint) throws WorkItemException
    {
        try {
            return componentManager.getInstance(ProjectManagementClient.class, hint);
        } catch (ComponentLookupException e) {
            throw new WorkItemException(String.format("Failed to retrieve the client with hint [%s].", hint));
        }
    }
}
