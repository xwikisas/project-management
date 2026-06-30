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
package com.xwiki.projectmanagement.script;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.descriptor.ComponentRole;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.script.service.ScriptService;
import org.xwiki.script.service.ScriptServiceManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Project management script service. Offers useful methods with regards to the project management implementers.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named(ProjectManagementScriptService.ROLE_HINT)
@Singleton
public class ProjectManagementScriptService implements ScriptService
{
    /**
     * The hint of this component.
     */
    public static final String ROLE_HINT = "projectmanagement";

    @Inject
    private ComponentManager componentManager;

    @Inject
    private ProjectManagementManager projectManagementManager;

    @Inject
    private ProjectManagementClientExecutionContext clientExecutionContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private ScriptServiceManager scriptServiceManager;

    /**
     * @param serviceName name of a script service related to project management.
     * @return the ScriptService with the given name.
     */
    public ScriptService get(String serviceName)
    {
        return scriptServiceManager.get(ROLE_HINT + '.' + serviceName);
    }

    /**
     * @return a list of existing {@link ProjectManagementClient} implementer hints.
     */
    public List<String> getClientHints()
    {
        return componentManager.getComponentDescriptorList(ProjectManagementClient.class).stream()
            .map(ComponentRole::getRoleHint).collect(Collectors.toList());
    }

    /**
     * Retrieve a list of work items based on a filter.
     *
     * @param client the hint of the client implementation.
     * @param page the number identifying the page that needs retrieval.
     * @param pageSize the maximum number of items the result can have.
     * @param filters a list of filters that the returned items must satisfy.
     * @param sortEntries a list of sort entries that denote how the results should be arranged.
     * @param clientContext the context passed to the clients.
     * @return a paginated result containing the list of items that satisfy the filters.
     * @throws WorkItemException if there was an exception during the retrieval of the tasks.
     */
    public PaginatedResult<WorkItem> getWorkItems(String client, int page, int pageSize,
        List<Object> filters, List<Object> sortEntries,
        Map<String, Object> clientContext) throws WorkItemException
    {
        if (clientExecutionContext instanceof DefaultProjectManagementClientExecutionContext) {
            ((DefaultProjectManagementClientExecutionContext) clientExecutionContext).setContext(clientContext);
        }
        List<LiveDataQuery.Filter> livedataFilters =
            filters.stream().map(filter -> objectMapper.convertValue(filter, LiveDataQuery.Filter.class))
                .collect(Collectors.toList());
        List<LiveDataQuery.SortEntry> livedataSortEntries =
            sortEntries.stream().map(sortEntry -> objectMapper.convertValue(sortEntry, LiveDataQuery.SortEntry.class))
                .collect(Collectors.toList());
        return projectManagementManager.getWorkItems(client, page, pageSize, livedataFilters, livedataSortEntries);
    }
}
