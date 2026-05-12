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

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;
import com.xwiki.projectmanagement.openproject.model.Priority;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;
import com.xwiki.projectmanagement.openproject.model.User;
import com.xwiki.projectmanagement.openproject.model.UserAvatar;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

/**
 * Implementation of {@link OpenProjectApiClient} that uses a cache for storing and retrieving entries.
 *
 * @version $Id$
 */
public class CachingOpenProjectApiClient implements OpenProjectApiClient
{
    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> workPackagesCache;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> projectsCache;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> prioritiesCache;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> usersCache;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> statusesCache;

    private Cache<PaginatedResult<? extends BaseOpenProjectObject>> typesCache;

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
        setWorkPackagesCache(cache);
        setUsersCache(cache);
        setProjectsCache(cache);
        setTypesCache(cache);
        setPrioritiesCache(cache);
        setStatusesCache(cache);
    }

    @Override
    public PaginatedResult<WorkPackage> getWorkPackages(int offset, int pageSize, String filters, String sortBy)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey("workItems", offset, pageSize, filters, sortBy);
        PaginatedResult<WorkPackage> result = (PaginatedResult<WorkPackage>) getFromCache(workPackagesCache, cacheKey);
        if (result == null) {
            result = client.getWorkPackages(offset, pageSize, filters, sortBy);
            setInCache(workPackagesCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<WorkPackage> getProjectWorkPackages(String project, int offset, int pageSize,
        String filters, String sortBy)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey(String.format("project%sWorkItems", project), offset, pageSize, filters, sortBy);
        PaginatedResult<WorkPackage> result = (PaginatedResult<WorkPackage>) getFromCache(workPackagesCache, cacheKey);
        if (result == null) {
            result = client.getProjectWorkPackages(project, offset, pageSize, filters, sortBy);
            setInCache(workPackagesCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<User> getUsers(int offset, int pageSize, String filters) throws ProjectManagementException
    {
        String cacheKey = getCacheKey("users", offset, pageSize, filters, "");
        PaginatedResult<User> result = (PaginatedResult<User>) getFromCache(usersCache, cacheKey);
        if (result == null) {
            result = client.getUsers(offset, pageSize, filters);
            setInCache(usersCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Project> getProjects(int offset, int pageSize, String filters)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey("projects", offset, pageSize, filters, "");
        PaginatedResult<Project> result = (PaginatedResult<Project>) getFromCache(projectsCache, cacheKey);
        if (result == null) {
            result = client.getProjects(offset, pageSize, filters);
            setInCache(projectsCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Project> getAvailableProjects(String url, int offset, int pageSize, String filters)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey(String.format("availableProjects/%s", url), offset, pageSize, filters, "");
        PaginatedResult<Project> result = (PaginatedResult<Project>) getFromCache(projectsCache, cacheKey);
        if (result == null) {
            result = client.getAvailableProjects(url, offset, pageSize, filters);
            setInCache(projectsCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Type> getTypes() throws ProjectManagementException
    {
        String cacheKey = getCacheKey("types", 1, Integer.MAX_VALUE, "", "");
        PaginatedResult<Type> result = (PaginatedResult<Type>) getFromCache(typesCache, cacheKey);
        if (result == null) {
            result = client.getTypes();
            setInCache(typesCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Status> getStatuses() throws ProjectManagementException
    {
        String cacheKey = getCacheKey("statuses", 1, Integer.MAX_VALUE, "", "");
        PaginatedResult<Status> result = (PaginatedResult<Status>) getFromCache(statusesCache, cacheKey);
        if (result == null) {
            result = client.getStatuses();
            setInCache(statusesCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public PaginatedResult<Priority> getPriorities() throws ProjectManagementException
    {
        String cacheKey = getCacheKey("priorities", 1, Integer.MAX_VALUE, "", "");
        PaginatedResult<Priority> result = (PaginatedResult<Priority>) getFromCache(prioritiesCache, cacheKey);
        if (result == null) {
            result = client.getPriorities();
            setInCache(prioritiesCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public UserAvatar getUserAvatar(String userId) throws ProjectManagementException
    {
        // We can't really cache this.
        return client.getUserAvatar(userId);
    }

    @Override
    public JsonNode getWorkPackagesFormResponse(String jsonBody) throws ProjectManagementException
    {
        return client.getWorkPackagesFormResponse(jsonBody);
    }

    @Override
    public PaginatedResult<User> getAvailableUsers(String url, int offset, int pageSize, String filters)
        throws ProjectManagementException
    {
        String cacheKey = getCacheKey(String.format("availableUsers/%s", url), offset, pageSize, filters, "");
        PaginatedResult<User> result = (PaginatedResult<User>) getFromCache(usersCache, cacheKey);
        if (result == null) {
            result = client.getAvailableUsers(url, offset, pageSize, filters);
            setInCache(usersCache, cacheKey, result);
        }
        return result;
    }

    @Override
    public JsonNode createWorkPackage(String url, String jsonBody) throws ProjectManagementException
    {
        return client.createWorkPackage(url, jsonBody);
    }

    private void setInCache(Cache<PaginatedResult<?
        extends BaseOpenProjectObject>> cache, String key, PaginatedResult<? extends BaseOpenProjectObject> result)
    {
        if (cache == null) {
            return;
        }
        cache.set(key, result);
    }

    private PaginatedResult<? extends BaseOpenProjectObject> getFromCache(Cache<PaginatedResult<?
        extends BaseOpenProjectObject>> cache, String key)
    {
        if (cache == null) {
            return null;
        }
        return cache.get(key);
    }

    public Cache<PaginatedResult<? extends BaseOpenProjectObject>> getWorkPackagesCache()
    {
        return workPackagesCache;
    }

    public void setWorkPackagesCache(
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> workPackagesCache)
    {
        this.workPackagesCache = workPackagesCache;
    }

    public Cache<PaginatedResult<? extends BaseOpenProjectObject>> getProjectsCache()
    {
        return projectsCache;
    }

    public void setProjectsCache(
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> projectsCache)
    {
        this.projectsCache = projectsCache;
    }

    public Cache<PaginatedResult<? extends BaseOpenProjectObject>> getPrioritiesCache()
    {
        return prioritiesCache;
    }

    public void setPrioritiesCache(
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> prioritiesCache)
    {
        this.prioritiesCache = prioritiesCache;
    }

    public Cache<PaginatedResult<? extends BaseOpenProjectObject>> getUsersCache()
    {
        return usersCache;
    }

    public void setUsersCache(
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> usersCache)
    {
        this.usersCache = usersCache;
    }

    public Cache<PaginatedResult<? extends BaseOpenProjectObject>> getStatusesCache()
    {
        return statusesCache;
    }

    public void setStatusesCache(
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> statusesCache)
    {
        this.statusesCache = statusesCache;
    }

    public Cache<PaginatedResult<? extends BaseOpenProjectObject>> getTypesCache()
    {
        return typesCache;
    }

    public void setTypesCache(
        Cache<PaginatedResult<? extends BaseOpenProjectObject>> typesCache)
    {
        this.typesCache = typesCache;
    }

    private String getCacheKey(String entity, int offset, int pageSize, String filters, String sortBy)
    {
        return String.format("%s/%s/%d/%d/%s/%s", clientId, entity, offset, pageSize, filters, sortBy);
    }
}
