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

package com.xwiki.projectmanagement.openproject.internal;

import org.xwiki.cache.Cache;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

/**
 * Implementation of {@link OpenProjectApiClient} that uses a cache for storing and retrieving entries.
 *
 * @version $Id$
 */
public class CachingOpenProjectApiClient implements OpenProjectApiClient
{
    private final Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache;

    private final OpenProjectApiClient client;

    private final String clientId;

    /**
     * Create a OpenProjectApiClient that tries to retrieve the results from a cache.
     *
     * @param client the OpenProjectApi client that needs to be cached
     * @param clientId the identifier of the current client.
     * @param cache the cache that will be used to store and retrieve entries from.
     */
    public CachingOpenProjectApiClient(OpenProjectApiClient client, String clientId,
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> cache)
    {
        this.client = client;
        this.clientId = clientId;
        this.cache = cache;
    }

    @Override
    public PaginatedResult<WorkPackage> getWorkPackages(int offset, int pageSize, String filters, String sortBy)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey("workItems", offset, pageSize, filters, sortBy);
        PaginatedResult<WorkPackage> result = (PaginatedResult<WorkPackage>) cache.get(cacheKey);
        if (result == null) {
            result = client.getWorkPackages(offset, pageSize, filters, sortBy);
            cache.set(cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<WorkPackage> getProjectWorkPackages(String project, int offset, int pageSize,
        String filters, String sortBy)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey(String.format("project%sWorkItems", project), offset, pageSize, filters, sortBy);
        PaginatedResult<WorkPackage> result = (PaginatedResult<WorkPackage>) cache.get(cacheKey);
        if (result == null) {
            result = client.getProjectWorkPackages(project, offset, pageSize, filters, sortBy);
            cache.set(cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<User> getUsers(int offset, int pageSize, String filters) throws ProjectManagementException
    {
        String cacheKey = getCacheKey("users", offset, pageSize, filters, "");
        PaginatedResult<User> result = (PaginatedResult<User>) cache.get(cacheKey);
        if (result == null) {
            result = client.getUsers(offset, pageSize, filters);
            cache.set(cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Project> getProjects(int offset, int pageSize, String filters)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey("projects", offset, pageSize, filters, "");
        PaginatedResult<Project> result = (PaginatedResult<Project>) cache.get(cacheKey);
        if (result == null) {
            result = client.getProjects(offset, pageSize, filters);
            cache.set(cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Type> getTypes() throws ProjectManagementException
    {
        String cacheKey = getCacheKey("types", 1, Integer.MAX_VALUE, "", "");
        PaginatedResult<Type> result = (PaginatedResult<Type>) cache.get(cacheKey);
        if (result == null) {
            result = client.getTypes();
            cache.set(cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Status> getStatuses() throws ProjectManagementException
    {
        String cacheKey = getCacheKey("statuses", 1, Integer.MAX_VALUE, "", "");
        PaginatedResult<Status> result = (PaginatedResult<Status>) cache.get(cacheKey);
        if (result == null) {
            result = client.getStatuses();
            cache.set(cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Priority> getPriorities() throws ProjectManagementException
    {
        String cacheKey = getCacheKey("priorities", 1, Integer.MAX_VALUE, "", "");
        PaginatedResult<Priority> result = (PaginatedResult<Priority>) cache.get(cacheKey);
        if (result == null) {
            result = client.getPriorities();
            cache.set(cacheKey, result);
        }
        return result;
    }

    private String getCacheKey(String entity, int offset, int pageSize, String filters, String sortBy)
    {
        return String.format("%s/%s/%d/%d/%s/%s", clientId, entity, offset, pageSize, filters, sortBy);
    }
}
