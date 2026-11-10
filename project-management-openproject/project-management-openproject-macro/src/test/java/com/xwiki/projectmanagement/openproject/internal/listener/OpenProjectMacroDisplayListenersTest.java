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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.event.Event;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.macro.BaseDirectOpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCalendarMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;
import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

    private static final DocumentReference SELECTION =
        new DocumentReference("xwiki", "OpenProject", "DashboardSelection");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectMacroDisplayListeners listener;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWikiDocument document;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument selectionDocument;

    @Mock
    private BaseObject dashboardConfig;

    @Mock
    private OpenProjectApiClient apiClient;

    private final BaseDirectOpenProjectMacroParameters parameters = new BaseDirectOpenProjectMacroParameters();

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getDoc()).thenReturn(this.document);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.document.getDocumentReference()).thenReturn(DASHBOARD);
        when(this.xwiki.getDocument(SELECTION, this.xcontext)).thenReturn(this.selectionDocument);
        when(this.selectionDocument.getXObject(DASHBOARD_CLASS)).thenReturn(this.dashboardConfig);
        configuredConnections("dashboard");
    }

    private void configuredConnections(String... names)
    {
        List<OpenProjectConnection> connections = new ArrayList<>();
        for (String name : names) {
            connections.add(new OpenProjectConnection(name, "https://" + name + ".example.com", "id", "secret", name));
        }
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(connections);
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
        selectedConnection("dashboard");

        this.listener.onEvent(mock(Event.class), null, this.parameters);

        assertNull(this.parameters.getInstance());
    }

    @Test
    void ignoresPagesOtherThanTheDashboard()
    {
        when(this.document.getDocumentReference()).thenReturn(new DocumentReference("xwiki", "OpenProject", "Other"));

        fireEvent(this.parameters);

        assertNull(this.parameters.getInstance());
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
        when(this.selectionDocument.getXObject(DASHBOARD_CLASS)).thenReturn(null);

        fireEvent(this.parameters);

        assertNull(this.parameters.getInstance());
        assertNull(this.parameters.getProject());
    }

    @Test
    void fallsBackOnTheSelectionStoredOnTheDashboardBefore140()
    {
        BaseObject legacyConfig = mock(BaseObject.class);
        when(legacyConfig.getStringValue("selectedConnection")).thenReturn("legacy");
        when(legacyConfig.getStringValue("selectedProject")).thenReturn("7");
        when(this.selectionDocument.getXObject(DASHBOARD_CLASS)).thenReturn(null);
        when(this.document.getXObject(DASHBOARD_CLASS)).thenReturn(legacyConfig);
        configuredConnections("dashboard", "legacy");

        fireEvent(this.parameters);

        assertEquals("legacy", this.parameters.getInstance());
        assertEquals("7", this.parameters.getProject());
    }

    @Test
    void theSelectionPageTakesPrecedenceOverTheSelectionStoredOnTheDashboard()
    {
        BaseObject legacyConfig = mock(BaseObject.class);
        when(legacyConfig.getStringValue("selectedConnection")).thenReturn("legacy");
        when(this.document.getXObject(DASHBOARD_CLASS)).thenReturn(legacyConfig);
        configuredConnections("dashboard", "legacy");
        selectedConnection("dashboard");
        selectedProject("3");

        fireEvent(this.parameters);

        assertEquals("dashboard", this.parameters.getInstance());
        assertEquals("3", this.parameters.getProject());
    }

    @Test
    void fallsBackOnTheSelectionStoredOnTheDashboardWhenTheSelectionPageCannotBeRead() throws Exception
    {
        BaseObject legacyConfig = mock(BaseObject.class);
        when(legacyConfig.getStringValue("selectedConnection")).thenReturn("legacy");
        when(this.document.getXObject(DASHBOARD_CLASS)).thenReturn(legacyConfig);
        when(legacyConfig.getStringValue("selectedProject")).thenReturn("7");
        when(this.xwiki.getDocument(SELECTION, this.xcontext)).thenThrow(new XWikiException(0, 0, "Store failure"));
        configuredConnections("dashboard", "legacy");

        fireEvent(this.parameters);

        assertEquals("legacy", this.parameters.getInstance());
        assertEquals("7", this.parameters.getProject());
        assertEquals("Failed to read the dashboard selection from [xwiki:OpenProject.DashboardSelection]. Root cause: "
            + "[XWikiException: Error number 0 in 0: Store failure]", this.logCapture.getMessage(0));
    }

    @Test
    void usesTheDefaultConnectionWhenTheMacroDoesntSetOne()
    {
        when(this.document.getDocumentReference()).thenReturn(new DocumentReference("xwiki", "Sandbox", "WebHome"));
        when(this.openProjectConfiguration.getDefaultConnection()).thenReturn(connection("default"));

        fireEvent(this.parameters);
        assertEquals("default", this.parameters.getInstance());

        OpenProjectMacroParameters blankInstance = new OpenProjectMacroParameters();
        blankInstance.setInstance(" ");
        fireEvent(blankInstance);
        assertEquals("default", blankInstance.getInstance());
    }

    @Test
    void keepsTheConnectionSetByTheMacro()
    {
        when(this.document.getDocumentReference()).thenReturn(new DocumentReference("xwiki", "Sandbox", "WebHome"));
        when(this.openProjectConfiguration.getDefaultConnection()).thenReturn(connection("default"));
        this.parameters.setInstance("other");

        fireEvent(this.parameters);

        assertEquals("other", this.parameters.getInstance());
    }

    @Test
    void theDashboardConnectionTakesPrecedenceOverTheDefaultConnection()
    {
        configuredConnections("default", "dashboard");
        selectedConnection("dashboard");
        when(this.openProjectConfiguration.getDefaultConnection()).thenReturn(connection("default"));
        OpenProjectMacroParameters macroParameters = new OpenProjectMacroParameters();

        fireEvent(macroParameters);

        assertEquals("dashboard", macroParameters.getInstance());
    }

    private static OpenProjectConnection connection(String name)
    {
        return new OpenProjectConnection(name, "https://" + name + ".example.com", "id", "secret", name);
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
        this.parameters.setInstance(OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
        this.parameters.setProject(OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE);
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
    }

    @Test
    void resolvesTheInstanceOfTheCalendarMacro()
    {
        OpenProjectCalendarMacroParameters calendarParameters = new OpenProjectCalendarMacroParameters();
        calendarParameters.setInstance(OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
        selectedConnection("dashboard");

        fireEvent(calendarParameters);

        assertEquals("dashboard", calendarParameters.getInstance());
    }

    @Test
    void fallsBackOnTheFirstConfiguredConnection() throws Exception
    {
        configuredConnections("first", "second");
        when(this.openProjectAccess.resolve("first")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        Project project = new Project();
        project.setId(42);
        when(this.apiClient.getProjects(1, 1, "")).thenReturn(new PaginatedResult<>(List.of(project), 1, 1, 1));

        fireEvent(this.parameters);

        assertEquals("first", this.parameters.getInstance());
        // Without a selected project, the first project of the effective connection is used.
        assertEquals("42", this.parameters.getProject());
    }

    @Test
    void fallsBackOnTheFirstConfiguredConnectionWhenTheSelectedOneWasDeleted() throws Exception
    {
        configuredConnections("first", "second");
        selectedConnection("deleted");
        // The project of the deleted connection.
        selectedProject("12");
        when(this.openProjectAccess.resolve("first")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        Project project = new Project();
        project.setId(42);
        when(this.apiClient.getProjects(1, 1, "")).thenReturn(new PaginatedResult<>(List.of(project), 1, 1, 1));

        fireEvent(this.parameters);

        assertEquals("first", this.parameters.getInstance());
        assertEquals("42", this.parameters.getProject());
    }

    @Test
    void leavesTheInstanceUnsetWithoutAnyConnection()
    {
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(List.of());
        this.parameters.setInstance(OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
        this.parameters.setProject(OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE);

        fireEvent(this.parameters);

        assertEquals(OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE, this.parameters.getInstance());
        assertEquals(OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE, this.parameters.getProject());
    }

    @Test
    void leavesTheProjectUnsetWhenTheConnectionHasNoClient()
    {
        selectedConnection("dashboard");
        when(this.openProjectAccess.resolve("dashboard")).thenReturn(OpenProjectAccessResult.notConnected());

        fireEvent(this.parameters);

        assertEquals("dashboard", this.parameters.getInstance());
        assertNull(this.parameters.getProject());
    }

    @Test
    void leavesTheProjectUnsetWhenTheConnectionHasNoProject() throws Exception
    {
        selectedConnection("dashboard");
        when(this.openProjectAccess.resolve("dashboard"))
            .thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.apiClient.getProjects(1, 1, "")).thenReturn(new PaginatedResult<>(List.of(), 1, 1, 0));

        fireEvent(this.parameters);

        assertNull(this.parameters.getProject());
    }

    @Test
    void leavesTheProjectUnsetWhenTheProjectsCannotBeRetrieved() throws Exception
    {
        selectedConnection("dashboard");
        when(this.openProjectAccess.resolve("dashboard"))
            .thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.apiClient.getProjects(1, 1, "")).thenThrow(new ProjectManagementException("unreachable"));

        fireEvent(this.parameters);

        assertEquals("dashboard", this.parameters.getInstance());
        assertNull(this.parameters.getProject());
    }
}
