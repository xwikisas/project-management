package com.xwiki.projectmanagement.rest;

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
import java.util.Date;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Default implementation of {@link WorkItemsResource}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.projectmanagement.rest.DefaultWorkItemsResource")
@Singleton
public class DefaultWorkItemsResource extends XWikiResource implements WorkItemsResource
{
    private final List<WorkItem> workItems = new ArrayList<>();

    @Override
    public Response getWorkItem(String wiki, String projectManagementHint, String workItemId)
    {
        WorkItem tmp = new WorkItem();
        tmp.setIdentifier(new Linkable<>("smth", "https://store.xwiki.com"));
        tmp.setCloseDate(new Date());
        tmp.setDescription("Some nice description");
        tmp.setProgress(100);
        tmp.setResolved(true);
        WorkItem workItem =
            workItems.stream().filter(item -> item.getIdentifier().getValue().equals(workItemId)).findFirst()
                .orElse(tmp);
        return Response.ok().entity(workItem).build();
    }

    @Override
    public Response getWorkItems(String wiki, String projectManagementHint, int page, int pageSize)
    {
        return Response.ok().entity(new PaginatedResult<>(workItems, 1, 10, workItems.size())).build();
    }

    @Override
    public Response createWorkItem(String wiki, String projectManagementHint, WorkItem workItem)
    {
        if (workItem == null || workItem.getIdentifier() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (workItems.stream()
            .anyMatch(wi -> wi.getIdentifier().getValue().equals(workItem.getIdentifier().getValue())))
        {
            return Response.status(Response.Status.CONFLICT).build();
        }
        workItems.add(workItem);
        return Response.ok(workItem).build();
    }

    @Override
    public Response updateWorkItem(String wiki, String projectManagementHint, WorkItem workItem)
    {
        WorkItem dbWorkItem =
            workItems.stream().filter(i -> i.getIdentifier().getValue().equals(workItem.getIdentifier().getValue()))
                .findFirst().orElse(null);
        if (dbWorkItem == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        int index = workItems.indexOf(dbWorkItem);
        workItems.remove(index);
        workItems.add(index, workItem);
        return Response.ok(workItem).build();
    }

    @Override
    public Response deleteWorkItem(String wiki, String projectManagementHint, String workItemId)
    {
        WorkItem dbWorkItem =
            workItems.stream().filter(i -> i.getIdentifier().getValue().equals(workItemId))
                .findFirst().orElse(null);
        if (dbWorkItem == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        workItems.remove(dbWorkItem);
        return Response.ok(dbWorkItem).build();
    }
}
