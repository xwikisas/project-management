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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.script.service.ScriptService;
import org.xwiki.script.service.ScriptServiceManager;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectManagementScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(DefaultProjectManagementClientExecutionContext.class)
class ProjectManagementScriptServiceTest
{
    private static final String CLIENT = "openproject";

    @InjectMockComponents
    private ProjectManagementScriptService scriptService;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ScriptServiceManager scriptServiceManager;

    @Test
    void getSubScriptService()
    {
        ScriptService subService = mock(ScriptService.class);
        when(this.scriptServiceManager.get("projectmanagement.openproject")).thenReturn(subService);

        assertSame(subService, this.scriptService.get(CLIENT));
    }

    @Test
    void getClientHints() throws Exception
    {
        assertTrue(this.scriptService.getClientHints().isEmpty());

        this.componentManager.registerMockComponent(ProjectManagementClient.class, CLIENT);
        this.componentManager.registerMockComponent(ProjectManagementClient.class, "jira");

        assertEquals(List.of("jira", CLIENT), this.scriptService.getClientHints().stream().sorted()
            .collect(Collectors.toList()));
    }

    @Test
    void getWorkItemsConvertsTheFiltersAndSortEntries() throws Exception
    {
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        when(this.projectManagementManager.getWorkItems(eq(CLIENT), eq(2), eq(10), anyList(), anyList()))
            .thenReturn(result);
        List<Object> filters = List.of(Map.of("property", "status", "matchAll", true, "constraints",
            List.of(Map.of("operator", "equals", "value", "open"))));
        List<Object> sortEntries = List.of(Map.of("property", "dueDate", "descending", true));

        assertSame(result, this.scriptService.getWorkItems(CLIENT, 2, 10, filters, sortEntries, Map.of()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LiveDataQuery.Filter>> filtersCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LiveDataQuery.SortEntry>> sortCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.projectManagementManager).getWorkItems(eq(CLIENT), eq(2), eq(10), filtersCaptor.capture(),
            sortCaptor.capture());

        assertEquals(1, filtersCaptor.getValue().size());
        LiveDataQuery.Filter filter = filtersCaptor.getValue().get(0);
        assertEquals("status", filter.getProperty());
        assertTrue(filter.isMatchAll());
        assertEquals(1, filter.getConstraints().size());
        assertEquals("equals", filter.getConstraints().get(0).getOperator());
        assertEquals("open", filter.getConstraints().get(0).getValue());

        assertEquals(1, sortCaptor.getValue().size());
        assertEquals("dueDate", sortCaptor.getValue().get(0).getProperty());
        assertTrue(sortCaptor.getValue().get(0).isDescending());
    }

    @Test
    void getWorkItemsSetsTheClientContext() throws Exception
    {
        ProjectManagementClientExecutionContext executionContext =
            this.componentManager.getInstance(ProjectManagementClientExecutionContext.class);
        Map<String, Object> clientContext = Map.of("instance", "main");
        when(this.projectManagementManager.getWorkItems(eq(CLIENT), anyInt(), anyInt(), anyList(), anyList()))
            .thenAnswer(invocation -> {
                assertEquals(clientContext, executionContext.getContext());
                return new PaginatedResult<>();
            });

        this.scriptService.getWorkItems(CLIENT, 1, 10, List.of(), List.of(), clientContext);

        verify(this.projectManagementManager).getWorkItems(CLIENT, 1, 10, List.of(), List.of());
    }

    @Test
    void getWorkItemsPropagatesErrors() throws Exception
    {
        WorkItemException exception = new WorkItemException("Failure");
        when(this.projectManagementManager.getWorkItems(anyString(), anyInt(), anyInt(), anyList(), anyList()))
            .thenThrow(exception);

        assertSame(exception, assertThrows(WorkItemException.class,
            () -> this.scriptService.getWorkItems(CLIENT, 1, 10, List.of(), List.of(), Map.of())));
    }
}
