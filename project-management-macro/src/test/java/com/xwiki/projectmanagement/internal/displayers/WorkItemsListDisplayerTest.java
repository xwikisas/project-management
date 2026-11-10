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
package com.xwiki.projectmanagement.internal.displayers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkItemsListDisplayer} and the handling of the client context by the work item displayers.
 *
 * @version $Id$
 */
@ComponentTest
class WorkItemsListDisplayerTest
{
    private static final String CLIENT = "test";

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private WorkItemsListDisplayer displayer;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    private final DefaultProjectManagementClientExecutionContext executionContext =
        new DefaultProjectManagementClientExecutionContext();

    @BeforeComponent
    void registerExecutionContext() throws Exception
    {
        this.componentManager.registerComponent(ProjectManagementClientExecutionContext.class,
            this.executionContext);
    }

    @Test
    void restrictsThePropertiesOnlyWhenRetrievingTheDisplayedWorkItems() throws Exception
    {
        Map<String, Object> getWorkItemsContext = new HashMap<>();
        WorkItem workItem = new WorkItem();
        workItem.setIdentifier(new Linkable("1", null));
        // 3 work items match, only 1 is displayed: the others are linked to.
        when(this.projectManagementManager.getWorkItems(CLIENT, 0, 1000, List.of(), List.of()))
            .thenAnswer(invocation -> {
                getWorkItemsContext.putAll(this.executionContext.getContext());
                return new PaginatedResult<>(List.of(workItem), 0, 1000, 3);
            });
        Map<String, Object> getLocationContext = new HashMap<>();
        when(this.projectManagementManager.getWorkItemsLocation(CLIENT, List.of(), List.of()))
            .thenAnswer(invocation -> {
                getLocationContext.putAll(this.executionContext.getContext());
                return Optional.of("https://op/work_packages");
            });
        Map<String, Object> callerContext = Map.of("client", "caller");
        this.executionContext.setContext(callerContext);
        ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();
        parameters.setSourceParameters("client=test&instance=main");

        this.displayer.execute(parameters, null, new MacroTransformationContext());

        assertEquals(List.of(WorkItem.KEY_IDENTIFIER, WorkItem.KEY_KEY, WorkItem.KEY_SUMMARY, WorkItem.KEY_STATUS,
            WorkItem.KEY_RESOLVED), getWorkItemsContext.get(ProjectManagementClientExecutionContext.PROPERTIES));
        assertEquals("main", getWorkItemsContext.get("instance"));
        // The location of all the work items is retrieved with the context of the macro, without the properties.
        assertFalse(getLocationContext.containsKey(ProjectManagementClientExecutionContext.PROPERTIES));
        assertEquals("main", getLocationContext.get("instance"));
        // The context of the caller is restored.
        assertEquals(callerContext, this.executionContext.getContext());
    }

    @Test
    void removesTheContextWhenNoneWasSet() throws Exception
    {
        when(this.projectManagementManager.getWorkItems(CLIENT, 0, 1000, List.of(), List.of()))
            .thenReturn(new PaginatedResult<>(List.of(), 0, 1000, 0));
        ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();
        parameters.setSourceParameters("client=test");

        this.displayer.execute(parameters, null, new MacroTransformationContext());

        assertTrue(this.executionContext.getContext().isEmpty());
    }
}
