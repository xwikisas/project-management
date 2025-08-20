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
package com.xwiki.projectmanagement.livedata;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.livadata.ProjectManagementEntryStore;
import com.xwiki.projectmanagement.livadata.displayer.ProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the flow of the {@link ProjectManagementEntryStore}.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@ComponentTest
public class ProjectManagementEntryStoreTest
{
    @InjectMockComponents
    private ProjectManagementEntryStore entryStore;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ProjectManagementLiveDataDisplayer defaultDisplayer;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private Logger logger;

    @MockComponent
    private ProjectManagementClientExecutionContext clientContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getQuery() throws WorkItemException, LiveDataException
    {
        String clientId = "testclient";
        LiveDataConfiguration liveDataConfiguration = new LiveDataConfiguration();
        liveDataConfiguration.setQuery(new LiveDataQuery());
        liveDataConfiguration.getQuery().setSource(new LiveDataQuery.Source());
        liveDataConfiguration.getQuery().getSource().setId("test");
        liveDataConfiguration.getQuery().getSource().setParameter("client", clientId);
        liveDataConfiguration.getQuery().setLimit(10);
        liveDataConfiguration.getQuery().setOffset(0L);

        PaginatedResult<WorkItem> workItemResult = new PaginatedResult<>();
        workItemResult.setPage(0);
        workItemResult.setTotalItems(2);
        workItemResult.setPage(1);
        WorkItem wi = new WorkItem();
        wi.setIdentifier(new Linkable("1", "http://xwiki.com/1"));
        workItemResult.getItems().add(wi);

        when(projectManagementManager.getWorkItems(any(), anyInt(), anyInt(), any(), any())).thenReturn(workItemResult);
        when(componentManager.hasComponent(ProjectManagementLiveDataDisplayer.class, clientId)).thenReturn(false);

        LiveData result = entryStore.get(liveDataConfiguration.getQuery());

        // Work item was modified in order to have a nice display.
        verify(componentManager).hasComponent(ProjectManagementLiveDataDisplayer.class, clientId);
        verify(defaultDisplayer).display(argThat(list -> list.contains(wi)));

        assertEquals(2, result.getCount());
        assertEquals(1, result.getEntries().size());
        // Work Item was flattened.
        assertTrue(result.getEntries().get(0).containsKey("identifier.value"));
        assertTrue(result.getEntries().get(0).containsKey("identifier.location"));

        assertEquals("1", result.getEntries().get(0).get("identifier.value"));
        assertEquals("http://xwiki.com/1", result.getEntries().get(0).get("identifier.location"));
    }

    @Test
    void getQueryWithExceptionWhenRetrievingWorkItems() throws WorkItemException, LiveDataException
    {
        LiveDataConfiguration liveDataConfiguration = new LiveDataConfiguration();
        liveDataConfiguration.setQuery(new LiveDataQuery());
        liveDataConfiguration.getQuery().setSource(new LiveDataQuery.Source());
        liveDataConfiguration.getQuery().getSource().setId("test");
        liveDataConfiguration.getQuery().getSource().setParameter("client", "testclient");
        liveDataConfiguration.getQuery().setLimit(10);
        liveDataConfiguration.getQuery().setOffset(0L);

        when(projectManagementManager.getWorkItems(any(), anyInt(), anyInt(), any(), any())).thenThrow(
            new WorkItemException("Failed to retrieve."));

        assertThrows(LiveDataException.class, () -> entryStore.get(liveDataConfiguration.getQuery()));
    }

    @Test
    void getQueryWithoutClientSourceParam()
    {
        LiveDataConfiguration liveDataConfiguration = new LiveDataConfiguration();
        liveDataConfiguration.setQuery(new LiveDataQuery());
        liveDataConfiguration.getQuery().setSource(new LiveDataQuery.Source());
        liveDataConfiguration.getQuery().getSource().setId("test");
        assertThrows(LiveDataException.class, () -> entryStore.get(liveDataConfiguration.getQuery()));
    }
}
