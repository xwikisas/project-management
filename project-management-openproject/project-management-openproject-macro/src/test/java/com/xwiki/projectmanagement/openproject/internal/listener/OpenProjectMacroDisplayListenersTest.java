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
package com.xwiki.projectmanagement.openproject.internal.listener;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.event.Event;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.macro.BaseDirectOpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCalendarMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE;
import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectMacroDisplayListeners}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectMacroDisplayListenersTest
{
    private static final LocalDocumentReference DASHBOARD_CLASS =
        new LocalDocumentReference(List.of("OpenProject", "Code"), "DashboardConnectionConfigClass");

    private static final DocumentReference DASHBOARD = new DocumentReference("xwiki", "OpenProject", "WebHome");

    @InjectMockComponents
    private OpenProjectMacroDisplayListeners listener;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWikiDocument document;

    @Mock
    private BaseObject dashboardConfig;

    @Mock
    private OpenProjectApiClient apiClient;

    private final BaseDirectOpenProjectMacroParameters parameters = new BaseDirectOpenProjectMacroParameters();

    @BeforeEach
    void setUp()
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getDoc()).thenReturn(this.document);
        when(this.document.getDocumentReference()).thenReturn(DASHBOARD);
        when(this.document.getXObject(DASHBOARD_CLASS)).thenReturn(this.dashboardConfig);
    }

    private void fireEvent(Object data)
    {
        this.listener.onEvent(new BeforeOpenProjectMacroExecutionEvent(), null, data);
    }

    private void selectedConnection(String connection)
    {
        when(this.dashboardConfig.getStringValue("selectedConnection")).thenReturn(connection);
    }

    private void selectedProject(String project)
    {
        when(this.dashboardConfig.getStringValue("selectedProject")).thenReturn(project);
    }

    @Test
    void listensToTheBeforeMacroExecutionEvent()
    {
        assertEquals(1, this.listener.getEvents().size());
        assertTrue(this.listener.getEvents().get(0) instanceof BeforeOpenProjectMacroExecutionEvent);
    }

    @Test
    void ignoresOtherEvents()
    {
        this.listener.onEvent(mock(Event.class), null, this.parameters);

        verifyNoInteractions(this.xcontextProvider);
    }

    @Test
    void ignoresPagesOtherThanTheDashboard()
    {
        when(this.document.getDocumentReference()).thenReturn(new DocumentReference("xwiki", "OpenProject", "Other"));

        fireEvent(this.parameters);

        assertNull(this.parameters.getInstance());
        verify(this.document, never()).getXObject(DASHBOARD_CLASS);
    }

    @Test
    void ignoresDashboardHomeOfAnotherSpace()
    {
        when(this.document.getDocumentReference()).thenReturn(new DocumentReference("xwiki", "Sandbox", "WebHome"));

        fireEvent(this.parameters);

        assertNull(this.parameters.getInstance());
    }

    @Test
    void ignoresMissingCurrentDocument()
    {
        when(this.xcontext.getDoc()).thenReturn(null);

        fireEvent(this.parameters);

        assertNull(this.parameters.getInstance());
    }

    @Test
    void ignoresDashboardWithoutConfiguration()
    {
        when(this.document.getXObject(DASHBOARD_CLASS)).thenReturn(null);

        fireEvent(this.parameters);

        assertNull(this.parameters.getInstance());
        assertNull(this.parameters.getProject());
    }

    @Test
    void ignoresDataThatIsNotMacroParameters()
    {
        selectedConnection("dashboard");
        Map<String, String> data = new HashMap<>();

        fireEvent(data);

        assertEquals(Collections.emptyMap(), data);
    }

    @Test
    void usesTheSelectedDashboardConnectionAndProject()
    {
        selectedConnection("dashboard");
        selectedProject("12");

        fireEvent(this.parameters);

        assertEquals("dashboard", this.parameters.getInstance());
        assertEquals("12", this.parameters.getProject());
    }

    @Test
    void replacesTheDashboardPlaceholders()
    {
        this.parameters.setInstance(USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
        this.parameters.setProject(USE_SELECTED_DASHBOARD_PROJECT_VALUE);
        selectedConnection("dashboard");
        selectedProject("12");

        fireEvent(this.parameters);

        assertEquals("dashboard", this.parameters.getInstance());
        assertEquals("12", this.parameters.getProject());
    }

    @Test
    void keepsExplicitInstanceAndProject()
    {
        this.parameters.setInstance("main");
        this.parameters.setProject("3");
        selectedConnection("dashboard");
        selectedProject("12");

        fireEvent(this.parameters);

        assertEquals("main", this.parameters.getInstance());
        assertEquals("3", this.parameters.getProject());
    }

    @Test
    void resolvesOnlyTheInstanceOfParametersWithoutProject()
    {
        OpenProjectMacroParameters macroParameters = new OpenProjectMacroParameters();
        selectedConnection("dashboard");

        fireEvent(macroParameters);

        assertEquals("dashboard", macroParameters.getInstance());
        verify(this.dashboardConfig, never()).getStringValue("selectedProject");
    }

    @Test
    void resolvesTheInstanceOfTheCalendarMacro()
    {
        OpenProjectCalendarMacroParameters calendarParameters = new OpenProjectCalendarMacroParameters();
        calendarParameters.setInstance(USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
        selectedConnection("dashboard");

        fireEvent(calendarParameters);

        assertEquals("dashboard", calendarParameters.getInstance());
    }

    @Test
    void fallsBackOnTheFirstConfiguredConnection() throws Exception
    {
        OpenProjectConnection first = mock(OpenProjectConnection.class);
        when(first.getConnectionName()).thenReturn("first");
        OpenProjectConnection second = mock(OpenProjectConnection.class);
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(List.of(first, second));
        when(this.openProjectConfiguration.getOpenProjectApiClient("first")).thenReturn(this.apiClient);
        Project project = new Project();
        project.setId(42);
        when(this.apiClient.getProjects(1, 1, "")).thenReturn(new PaginatedResult<>(List.of(project), 1, 1, 1));

        fireEvent(this.parameters);

        assertEquals("first", this.parameters.getInstance());
        // Without a selected project, the first project of the effective connection is used.
        assertEquals("42", this.parameters.getProject());
    }

    @Test
    void leavesTheInstanceUnsetWithoutAnyConnection()
    {
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(List.of());
        this.parameters.setInstance(USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
        this.parameters.setProject(USE_SELECTED_DASHBOARD_PROJECT_VALUE);

        fireEvent(this.parameters);

        assertEquals(USE_SELECTED_DASHBOARD_CONNECTION_VALUE, this.parameters.getInstance());
        assertEquals(USE_SELECTED_DASHBOARD_PROJECT_VALUE, this.parameters.getProject());
        verify(this.openProjectConfiguration, never()).getOpenProjectApiClient(any());
    }

    @Test
    void leavesTheProjectUnsetWhenTheConnectionHasNoClient()
    {
        selectedConnection("dashboard");

        fireEvent(this.parameters);

        assertEquals("dashboard", this.parameters.getInstance());
        assertNull(this.parameters.getProject());
    }

    @Test
    void leavesTheProjectUnsetWhenTheConnectionHasNoProject() throws Exception
    {
        selectedConnection("dashboard");
        when(this.openProjectConfiguration.getOpenProjectApiClient("dashboard")).thenReturn(this.apiClient);
        when(this.apiClient.getProjects(1, 1, "")).thenReturn(new PaginatedResult<>(List.of(), 1, 1, 0));

        fireEvent(this.parameters);

        assertNull(this.parameters.getProject());
    }

    @Test
    void leavesTheProjectUnsetWhenTheProjectsCannotBeRetrieved() throws Exception
    {
        selectedConnection("dashboard");
        when(this.openProjectConfiguration.getOpenProjectApiClient("dashboard")).thenReturn(this.apiClient);
        when(this.apiClient.getProjects(1, 1, "")).thenThrow(new ProjectManagementException("unreachable"));

        fireEvent(this.parameters);

        assertEquals("dashboard", this.parameters.getInstance());
        assertNull(this.parameters.getProject());
    }
}
