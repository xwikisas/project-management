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

package com.xwiki.projectmanagement.internal.rest;

import javax.inject.Named;
import javax.ws.rs.core.Response;

import org.apache.tika.utils.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.livedata.LiveDataException;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.Project;
import com.xwiki.projectmanagement.rest.ProjectsResource;
import com.xwiki.projectmanagement.rest.WorkItemsResource;

/**
 * Default implementation of {@link WorkItemsResource}.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named("com.xwiki.projectmanagement.internal.rest.DefaultProjectsResource")
public class DefaultProjectsResource extends AbstractProjectManagementResource implements ProjectsResource
{
    @Override
    public Response getProject(String wiki, String projectManagementHint, String workItemId)
    {
        prepareClientContext();
        try {
            ProjectManagementClient client = componentManager.getInstance(ProjectManagementClient.class,
                projectManagementHint);
            return Response.ok(client.projects().get(workItemId)).build();
        } catch (ComponentLookupException e) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(String.format("No project management client with id [%s].", projectManagementHint))
                .build();
        } catch (WorkItemNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (WorkItemRetrievalException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ExceptionUtils.getStackTrace(e))
                .build();
        }
    }

    @Override
    public Response getProjects(String wiki, String projectManagementHint, int page, int pageSize, String filter)
    {
        prepareClientContext();
        try {
            ProjectManagementClient client = componentManager.getInstance(ProjectManagementClient.class,
                projectManagementHint);
            PaginatedResult<Project> workItems =
                client.projects().get(page, pageSize, getFilters(filter));
            return Response.ok(workItems).build();
        } catch (ComponentLookupException e) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(String.format("No project management client with id [%s] exists.", projectManagementHint))
                .build();
        } catch (LiveDataException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("The passed filter is invalid.").build();
        } catch (WorkItemRetrievalException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ExceptionUtils.getStackTrace(e))
                .build();
        }
    }
}
