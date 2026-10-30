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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.ProjectManagementClient;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultWorkItemsResource} and the common code of {@link AbstractProjectManagementResource}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(DefaultProjectManagementClientExecutionContext.class)
class DefaultWorkItemsResourceTest
{
    private static final String WIKI = "xwiki";

    private static final String CLIENT = "test";

    private static final String UNKNOWN_CLIENT = "unknown";

    private static final String WORK_ITEM_ID = "42";

    private static final String FILTER = "{\"query\":{\"filters\":[]}}";

    @InjectMockComponents
    private DefaultWorkItemsResource resource;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private LiveDataConfigurationResolver<String> configurationResolver;

    private ProjectManagementClient client;

    private final Map<String, List<String>> queryParameters = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws Exception
    {
        this.client = this.componentManager.registerMockComponent(ProjectManagementClient.class, CLIENT);

        UriInfo uriInfo = mock(UriInfo.class);
        @SuppressWarnings("unchecked")
        MultivaluedMap<String, String> multivaluedMap = mock(MultivaluedMap.class);
        when(multivaluedMap.entrySet()).thenReturn(this.queryParameters.entrySet());
        when(uriInfo.getQueryParameters()).thenReturn(multivaluedMap);
        FieldUtils.writeField(this.resource, "uriInfo", uriInfo, true);
    }

    @Test
    void getWorkItem() throws Exception
    {
        WorkItem workItem = new WorkItem();
        when(this.client.getWorkItem(WORK_ITEM_ID)).thenReturn(workItem);

        Response response = this.resource.getWorkItem(WIKI, CLIENT, WORK_ITEM_ID);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertSame(workItem, response.getEntity());
    }

    @Test
    void getWorkItemSetsTheClientContextFromTheQueryParametersForTheRequestOnly() throws Exception
    {
        this.queryParameters.put("instance", List.of("main"));
        this.queryParameters.put("ids", List.of("1", "2"));
        ProjectManagementClientExecutionContext executionContext =
            this.componentManager.getInstance(ProjectManagementClientExecutionContext.class);
        // The client returns a work item holding the context it was called with.
        when(this.client.getWorkItem(WORK_ITEM_ID)).thenAnswer(invocation -> {
            WorkItem workItem = new WorkItem();
            workItem.putAll(executionContext.getContext());
            return workItem;
        });

        Response response = this.resource.getWorkItem(WIKI, CLIENT, WORK_ITEM_ID);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(Map.of("instance", "main", "ids", List.of("1", "2")), response.getEntity());
        // The thread is reused for other requests, which must not see the context of this one.
        assertEquals(Map.of(), executionContext.getContext());
    }

    @Test
    void getWorkItemWithUnknownClient()
    {
        Response response = this.resource.getWorkItem(WIKI, UNKNOWN_CLIENT, WORK_ITEM_ID);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("No project management client with id [unknown].", response.getEntity());
    }

    @Test
    void getWorkItemNotFound() throws Exception
    {
        when(this.client.getWorkItem(WORK_ITEM_ID)).thenThrow(new WorkItemNotFoundException("Not found"));

        Response response = this.resource.getWorkItem(WIKI, CLIENT, WORK_ITEM_ID);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void getWorkItemRetrievalFailure() throws Exception
    {
        when(this.client.getWorkItem(WORK_ITEM_ID)).thenThrow(new WorkItemRetrievalException("Failure"));

        Response response = this.resource.getWorkItem(WIKI, CLIENT, WORK_ITEM_ID);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to retrieve the work item.", response.getEntity());
        assertEquals("Failed to retrieve the work item."
            + " Root cause: [WorkItemRetrievalException: Failure]", this.logCapture.getMessage(0));
    }

    @Test
    void getWorkItems() throws Exception
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "equals", "open"));
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setQuery(new LiveDataQuery());
        configuration.getQuery().setFilters(filters);
        when(this.configurationResolver.resolve(FILTER)).thenReturn(configuration);
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        // The second page of 20 work items starts at the offset 20.
        when(this.client.getWorkItems(20, 20, filters, Collections.emptyList())).thenReturn(result);

        Response response = this.resource.getWorkItems(WIKI, CLIENT, 2, 20, FILTER);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertSame(result, response.getEntity());
    }

    @Test
    void getWorkItemsReturnsTheFirstPageForAPageSmallerThanOne() throws Exception
    {
        when(this.configurationResolver.resolve("")).thenReturn(null);
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        when(this.client.getWorkItems(0, 15, Collections.emptyList(), Collections.emptyList())).thenReturn(result);

        assertSame(result, this.resource.getWorkItems(WIKI, CLIENT, 0, 15, null).getEntity());
    }

    @Test
    void getWorkItemsWithoutFilters() throws Exception
    {
        PaginatedResult<WorkItem> result = new PaginatedResult<>();
        when(this.client.getWorkItems(0, 10, Collections.emptyList(), Collections.emptyList())).thenReturn(result);

        // No filter parameter: the empty configuration is resolved.
        when(this.configurationResolver.resolve("")).thenReturn(null);
        assertSame(result, this.resource.getWorkItems(WIKI, CLIENT, 1, 10, null).getEntity());

        // A configuration without query.
        when(this.configurationResolver.resolve(FILTER)).thenReturn(new LiveDataConfiguration());
        assertSame(result, this.resource.getWorkItems(WIKI, CLIENT, 1, 10, FILTER).getEntity());

        // A query without filters.
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setQuery(new LiveDataQuery());
        when(this.configurationResolver.resolve(FILTER)).thenReturn(configuration);
        assertSame(result, this.resource.getWorkItems(WIKI, CLIENT, 1, 10, FILTER).getEntity());
    }

    @Test
    void getWorkItemsWithInvalidFilter() throws Exception
    {
        when(this.configurationResolver.resolve("invalid")).thenThrow(new LiveDataException("Invalid"));

        Response response = this.resource.getWorkItems(WIKI, CLIENT, 1, 10, "invalid");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("The passed filter is invalid.", response.getEntity());
        verifyNoInteractions(this.client);
    }

    @Test
    void getWorkItemsWithUnknownClient()
    {
        Response response = this.resource.getWorkItems(WIKI, UNKNOWN_CLIENT, 1, 10, FILTER);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("No project management client with id [unknown] exists.", response.getEntity());
    }

    @Test
    void getWorkItemsRetrievalFailure() throws Exception
    {
        when(this.client.getWorkItems(anyInt(), anyInt(), anyList(), anyList()))
            .thenThrow(new WorkItemRetrievalException("Failure"));

        Response response = this.resource.getWorkItems(WIKI, CLIENT, 1, 10, null);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to retrieve the work items.", response.getEntity());
        assertEquals("Failed to retrieve the work items."
            + " Root cause: [WorkItemRetrievalException: Failure]", this.logCapture.getMessage(0));
    }

    @Test
    void modificationsAreNotImplemented()
    {
        Response response = this.resource.createWorkItem(WIKI, CLIENT, new WorkItem());
        assertEquals(501, response.getStatus());
        assertEquals("The REST API doesn't support creating work items.", response.getEntity());
        response = this.resource.updateWorkItem(WIKI, CLIENT, new WorkItem());
        assertEquals(501, response.getStatus());
        assertEquals("The REST API doesn't support updating work items.", response.getEntity());
        response = this.resource.deleteWorkItem(WIKI, CLIENT, WORK_ITEM_ID);
        assertEquals(501, response.getStatus());
        assertEquals("The REST API doesn't support deleting work items.", response.getEntity());
        verifyNoInteractions(this.client);
    }
}
