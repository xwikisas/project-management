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
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.NotImplementedException;
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
import com.xwiki.projectmanagement.api.ProjectsApi;
import com.xwiki.projectmanagement.exception.WorkItemNotFoundException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.Project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultProjectsResource}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(DefaultProjectManagementClientExecutionContext.class)
class DefaultProjectsResourceTest
{
    private static final String WIKI = "xwiki";

    private static final String CLIENT = "test";

    private static final String UNKNOWN_CLIENT = "unknown";

    private static final String PROJECT_ID = "7";

    private static final String FILTER = "{\"query\":{\"filters\":[]}}";

    @InjectMockComponents
    private DefaultProjectsResource resource;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private LiveDataConfigurationResolver<String> configurationResolver;

    private final ProjectsApi projectsApi = mock(ProjectsApi.class);

    @BeforeEach
    void setUp() throws Exception
    {
        ProjectManagementClient client =
            this.componentManager.registerMockComponent(ProjectManagementClient.class, CLIENT);
        when(client.projects()).thenReturn(this.projectsApi);

        UriInfo uriInfo = mock(UriInfo.class);
        @SuppressWarnings("unchecked")
        MultivaluedMap<String, String> queryParameters = mock(MultivaluedMap.class);
        when(queryParameters.entrySet()).thenReturn(Map.of("instance", List.of("main")).entrySet());
        when(uriInfo.getQueryParameters()).thenReturn(queryParameters);
        FieldUtils.writeField(this.resource, "uriInfo", uriInfo, true);
    }

    @Test
    void getProject() throws Exception
    {
        Project project = new Project();
        when(this.projectsApi.get(PROJECT_ID)).thenReturn(project);

        Response response = this.resource.getProject(WIKI, CLIENT, PROJECT_ID);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertSame(project, response.getEntity());
        assertEquals(Map.of("instance", "main"),
            this.componentManager.<ProjectManagementClientExecutionContext>getInstance(
                ProjectManagementClientExecutionContext.class).getContext());
    }

    @Test
    void getProjectWithUnknownClient()
    {
        Response response = this.resource.getProject(WIKI, UNKNOWN_CLIENT, PROJECT_ID);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("No project management client with id [unknown].", response.getEntity());
    }

    @Test
    void getProjectNotFound() throws Exception
    {
        when(this.projectsApi.get(PROJECT_ID)).thenThrow(new WorkItemNotFoundException("Not found"));

        Response response = this.resource.getProject(WIKI, CLIENT, PROJECT_ID);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void getProjectRetrievalFailure() throws Exception
    {
        when(this.projectsApi.get(PROJECT_ID)).thenThrow(new WorkItemRetrievalException("Failure"));

        Response response = this.resource.getProject(WIKI, CLIENT, PROJECT_ID);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to retrieve the project.", response.getEntity());
        assertEquals("Failed to retrieve the project."
            + " Root cause: [WorkItemRetrievalException: Failure]", this.logCapture.getMessage(0));
    }

    @Test
    void getProjects() throws Exception
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("name", "contains", "xwiki"));
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setQuery(new LiveDataQuery());
        configuration.getQuery().setFilters(filters);
        when(this.configurationResolver.resolve(FILTER)).thenReturn(configuration);
        PaginatedResult<Project> result = new PaginatedResult<>();
        when(this.projectsApi.get(3, 25, filters)).thenReturn(result);

        Response response = this.resource.getProjects(WIKI, CLIENT, 3, 25, FILTER);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertSame(result, response.getEntity());
    }

    @Test
    void getProjectsWithoutFilter() throws Exception
    {
        PaginatedResult<Project> result = new PaginatedResult<>();
        when(this.projectsApi.get(1, 10, Collections.emptyList())).thenReturn(result);

        assertSame(result, this.resource.getProjects(WIKI, CLIENT, 1, 10, null).getEntity());
    }

    @Test
    void getProjectsWithInvalidFilter() throws Exception
    {
        when(this.configurationResolver.resolve("invalid")).thenThrow(new LiveDataException("Invalid"));

        Response response = this.resource.getProjects(WIKI, CLIENT, 1, 10, "invalid");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("The passed filter is invalid.", response.getEntity());
        verifyNoInteractions(this.projectsApi);
    }

    @Test
    void getProjectsWithUnknownClient()
    {
        Response response = this.resource.getProjects(WIKI, UNKNOWN_CLIENT, 1, 10, FILTER);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("No project management client with id [unknown] exists.", response.getEntity());
    }

    @Test
    void getProjectsRetrievalFailure() throws Exception
    {
        when(this.projectsApi.get(anyInt(), anyInt(), anyList())).thenThrow(new WorkItemRetrievalException("Failure"));

        Response response = this.resource.getProjects(WIKI, CLIENT, 1, 10, null);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to retrieve the projects.", response.getEntity());
        assertEquals("Failed to retrieve the projects."
            + " Root cause: [WorkItemRetrievalException: Failure]", this.logCapture.getMessage(0));
    }

    @Test
    void projectsNotSupportedByTheClient() throws Exception
    {
        ProjectManagementClient client =
            this.componentManager.registerMockComponent(ProjectManagementClient.class, "noprojects");
        when(client.projects()).thenThrow(new NotImplementedException("Not implemented"));
        String expectedMessage = "The project management client [noprojects] doesn't support projects.";

        Response response = this.resource.getProject(WIKI, "noprojects", PROJECT_ID);
        assertEquals(501, response.getStatus());
        assertEquals(expectedMessage, response.getEntity());

        response = this.resource.getProjects(WIKI, "noprojects", 1, 10, null);
        assertEquals(501, response.getStatus());
        assertEquals(expectedMessage, response.getEntity());
    }
}
