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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.livedata.displayer.ProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test the flow of the {@link ProjectManagementEntryStore}.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@ComponentTest
class ProjectManagementEntryStoreTest
{
    @InjectMockComponents
    private ProjectManagementEntryStore entryStore;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ProjectManagementLiveDataDisplayer defaultDisplayer;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private ProjectManagementClientExecutionContext clientContext;

    @Test
    void getQuery() throws WorkItemException, LiveDataException
    {
        String clientId = "testclient";
        LiveDataQuery query = createQuery(clientId);
        // The Live Data asks for its third page of 10 entries.
        query.setOffset(20L);
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "equals", "open"));
        query.setFilters(filters);
        List<LiveDataQuery.SortEntry> sortEntries = List.of(new LiveDataQuery.SortEntry("dueDate", true));
        query.setSort(sortEntries);

        PaginatedResult<WorkItem> workItemResult = new PaginatedResult<>();
        workItemResult.setTotalItems(21);
        workItemResult.setPage(3);
        WorkItem wi = new WorkItem();
        wi.setIdentifier(new Linkable("1", "http://xwiki.com/1"));
        workItemResult.getItems().add(wi);

        // The offset of the Live Data is passed as is.
        when(projectManagementManager.getWorkItems(clientId, 20, 10, filters, sortEntries)).thenReturn(workItemResult);

        LiveData result = entryStore.get(query);

        assertEquals(21, result.getCount());
        assertEquals(1, result.getEntries().size());
        // Work Item was flattened.
        assertEquals("1", result.getEntries().get(0).get("identifier.value"));
        assertEquals("http://xwiki.com/1", result.getEntries().get(0).get("identifier.location"));
    }

    @Test
    void getQueryWithClientDisplayer() throws Exception
    {
        String clientId = "testclient";
        PaginatedResult<WorkItem> workItemResult = new PaginatedResult<>();
        workItemResult.setTotalItems(1);
        WorkItem wi = new WorkItem();
        Linkable identifier = new Linkable("1", "http://xwiki.com/1");
        wi.setIdentifier(identifier);
        workItemResult.getItems().add(wi);
        when(projectManagementManager.getWorkItems(any(), anyInt(), anyInt(), any(), any())).thenReturn(workItemResult);
        this.componentManager.registerComponent(ProjectManagementLiveDataDisplayer.class, clientId,
            new LinkDisplayer());

        LiveData result = entryStore.get(createQuery(clientId));

        // The entry shows the display of the client displayer and still exposes the location of the linkable.
        assertEquals("<a href=\"http://xwiki.com/1\">1</a>", result.getEntries().get(0).get(WorkItem.KEY_IDENTIFIER));
        assertEquals("http://xwiki.com/1", result.getEntries().get(0).get("identifier.location"));
        // The work item of the client is left unchanged.
        assertEquals(Map.of(WorkItem.KEY_IDENTIFIER, identifier), wi);
    }

    @Test
    void getQueryWithExceptionWhenRetrievingWorkItems() throws WorkItemException, LiveDataException
    {
        LiveDataQuery query = createQuery("testclient");
        WorkItemException cause = new WorkItemException("Failed to retrieve.");
        when(projectManagementManager.getWorkItems(any(), anyInt(), anyInt(), any(), any())).thenThrow(cause);

        LiveDataException exception = assertThrows(LiveDataException.class, () -> entryStore.get(query));
        assertSame(cause, exception.getCause());
    }

    @Test
    void getQueryWithoutClientSourceParam()
    {
        LiveDataQuery query = createQuery(null);

        LiveDataException exception = assertThrows(LiveDataException.class, () -> entryStore.get(query));
        assertEquals("The client property was not specified in the source parameters.", exception.getMessage());
        verifyNoInteractions(projectManagementManager);
    }

    @Test
    void getEntry() throws LiveDataException
    {
        // The entry id alone doesn't say which client holds the work item.
        assertEquals(Optional.empty(), entryStore.get("1"));
        assertEquals(Optional.empty(), entryStore.get("1", WorkItem.KEY_SUMMARY));
        assertEquals(Optional.empty(), entryStore.update("1", WorkItem.KEY_SUMMARY, "summary"));
        verifyNoInteractions(projectManagementManager);
    }

    @Test
    void saveAndRemoveAreNotSupported()
    {
        Map<String, Object> entry = Map.of(WorkItem.KEY_SUMMARY, "summary");
        assertThrows(UnsupportedOperationException.class, () -> entryStore.save(entry));
        assertThrows(UnsupportedOperationException.class, () -> entryStore.remove("1"));
    }

    /**
     * Displays the linkables as HTML links, like the displayers of the real clients.
     */
    private static final class LinkDisplayer implements ProjectManagementLiveDataDisplayer
    {
        @Override
        public void display(Collection<WorkItem> workItems)
        {
            for (WorkItem workItem : workItems) {
                workItem.entrySet().forEach(property -> displayProperty(property, workItem, null, null));
            }
        }

        @Override
        public void displayProperty(Map.Entry<String, Object> itemProperty, WorkItem workItem, PrintRenderer renderer,
            WorkItemPropertyDisplayerManager displayerManager)
        {
            if (itemProperty.getValue() instanceof Linkable) {
                Linkable linkable = (Linkable) itemProperty.getValue();
                itemProperty.setValue(String.format("<a href=\"%s\">%s</a>", linkable.getLocation(),
                    linkable.getValue()));
            }
        }
    }

    private static LiveDataQuery createQuery(String clientId)
    {
        LiveDataQuery query = new LiveDataQuery();
        query.setSource(new LiveDataQuery.Source());
        query.getSource().setId("test");
        if (clientId != null) {
            query.getSource().setParameter("client", clientId);
        }
        query.setLimit(10);
        query.setOffset(0L);
        return query;
    }
}
