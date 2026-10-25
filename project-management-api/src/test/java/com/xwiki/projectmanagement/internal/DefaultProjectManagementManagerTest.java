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
package com.xwiki.projectmanagement.internal;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultProjectManagementManager}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultProjectManagementManagerTest
{
    private static final String CLIENT = "test";

    private static final String OTHER_CLIENT = "other";

    private static final String WORK_ITEM_ID = "42";

    @InjectMockComponents
    private DefaultProjectManagementManager manager;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    private ProjectManagementClient client;

    private ProjectManagementClient otherClient;

    @BeforeEach
    void setUp() throws Exception
    {
        this.client = this.componentManager.registerMockComponent(ProjectManagementClient.class, CLIENT);
        this.otherClient = this.componentManager.registerMockComponent(ProjectManagementClient.class, OTHER_CLIENT);
    }

    @Test
    void getWorkItemUsesTheClientMatchingTheHint() throws Exception
    {
        WorkItem workItem = new WorkItem();
        when(this.client.getWorkItem(WORK_ITEM_ID)).thenReturn(workItem);

        assertSame(workItem, this.manager.getWorkItem(CLIENT, WORK_ITEM_ID));
        verifyNoInteractions(this.otherClient);
    }

    @Test
    void getWorkItemsPassesAllTheParametersToTheClient() throws Exception
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "equals", "open"));
        List<LiveDataQuery.SortEntry> sortEntries = List.of(new LiveDataQuery.SortEntry("dueDate", true));
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        when(this.otherClient.getWorkItems(2, 15, filters, sortEntries)).thenReturn(result);

        assertSame(result, this.manager.getWorkItems(OTHER_CLIENT, 2, 15, filters, sortEntries));
        verifyNoInteractions(this.client);
    }

    @Test
    void createUpdateAndDeleteWorkItem() throws Exception
    {
        WorkItem workItem = new WorkItem();
        WorkItem created = new WorkItem();
        WorkItem updated = new WorkItem();
        when(this.client.createWorkItem(workItem)).thenReturn(created);
        when(this.client.updateWorkItem(workItem)).thenReturn(updated);
        when(this.client.deleteWorkItem(WORK_ITEM_ID)).thenReturn(true);

        assertSame(created, this.manager.createWorkItem(CLIENT, workItem));
        assertSame(updated, this.manager.updateWorkItem(CLIENT, workItem));
        assertTrue(this.manager.deleteWorkItem(CLIENT, WORK_ITEM_ID));
        verifyNoInteractions(this.otherClient);
    }

    @Test
    void clientExceptionsArePropagated() throws Exception
    {
        WorkItemNotFoundException exception = new WorkItemNotFoundException("Not found");
        when(this.client.getWorkItem(WORK_ITEM_ID)).thenThrow(exception);

        assertSame(exception, assertThrows(WorkItemException.class,
            () -> this.manager.getWorkItem(CLIENT, WORK_ITEM_ID)));
    }

    @Test
    void unknownClient()
    {
        String expectedMessage = "Failed to retrieve the client with hint [unknown].";

        WorkItemException exception =
            assertThrows(WorkItemException.class, () -> this.manager.getWorkItem("unknown", WORK_ITEM_ID));
        assertEquals(expectedMessage, exception.getMessage());
        assertInstanceOf(ComponentLookupException.class, exception.getCause());
        assertEquals(expectedMessage, assertThrows(WorkItemException.class,
            () -> this.manager.getWorkItems("unknown", 1, 10, Collections.emptyList(), Collections.emptyList()))
            .getMessage());
        assertEquals(expectedMessage,
            assertThrows(WorkItemException.class, () -> this.manager.createWorkItem("unknown", new WorkItem()))
                .getMessage());
        assertEquals(expectedMessage,
            assertThrows(WorkItemException.class, () -> this.manager.updateWorkItem("unknown", new WorkItem()))
                .getMessage());
        assertEquals(expectedMessage,
            assertThrows(WorkItemException.class, () -> this.manager.deleteWorkItem("unknown", WORK_ITEM_ID))
                .getMessage());
        verifyNoInteractions(this.client, this.otherClient);
    }
}
