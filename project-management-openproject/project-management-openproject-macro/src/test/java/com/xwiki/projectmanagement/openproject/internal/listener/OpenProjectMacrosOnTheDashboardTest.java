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

import java.util.HashMap;
import java.util.List;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.internal.DefaultObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.macro.OpenProjectChartMacro;
import com.xwiki.projectmanagement.openproject.internal.macro.OpenProjectMacro;
import com.xwiki.projectmanagement.openproject.internal.macro.OpenProjectProjectsMacro;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectChartMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The OpenProject macros use the connection selected on the OpenProject dashboard when they are displayed on it
 * without a connection: the real {@link OpenProjectMacroDisplayListeners} receives the event that the macros send
 * through the real observation manager, and reads the selection from the dashboard page.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList({ DefaultObservationManager.class, OpenProjectMacroDisplayListeners.class,
    DefaultProjectManagementClientExecutionContext.class })
class OpenProjectMacrosOnTheDashboardTest
{
    private static final String SERVER_URL = "https://dashboard.example.com";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private OpenProjectProjectsMacro projectsMacro;

    @InjectMockComponents
    private OpenProjectChartMacro chartMacro;

    @InjectMockComponents
    private OpenProjectMacro openProjectMacro;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    @Named("bar")
    private ChartTypeDisplayer barDisplayer;

    @Mock
    private OpenProjectApiClient apiClient;

    private final MacroTransformationContext context = new MacroTransformationContext();

    @BeforeEach
    void setUp() throws Exception
    {
        XWikiContext xcontext = this.oldcore.getXWikiContext();
        XWikiDocument dashboard = new XWikiDocument(new DocumentReference("xwiki", "OpenProject", "WebHome"));
        BaseObject selection = dashboard.newXObject(
            new LocalDocumentReference(List.of("OpenProject", "Code"), "DashboardConnectionConfigClass"), xcontext);
        selection.setStringValue("selectedConnection", "dashboard");
        xcontext.setDoc(dashboard);

        OpenProjectConnection connection = new OpenProjectConnection("dashboard", SERVER_URL, "id", "secret", "1");
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(List.of(connection));
        when(this.openProjectConfiguration.getConnection("dashboard")).thenReturn(connection);
        when(this.openProjectAccess.resolve("dashboard"))
            .thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        OpenProjectMacroTestUtils.executeSynchronously(this.asyncExecutor);
    }

    @Test
    void directMacroUsesTheSelectedConnection() throws Exception
    {
        Project project = new Project();
        project.setName("Alpha");
        project.setSelf(new Linkable("Alpha", SERVER_URL + "/projects/alpha"));
        when(this.apiClient.getProjects(null, 5, "")).thenReturn(new PaginatedResult<>(List.of(project), 1, 5, 1));

        List<Block> result =
            this.projectsMacro.execute(new OpenProjectProjectsMacroParameters(), "", this.context);

        assertEquals(List.of("Alpha -> " + SERVER_URL + "/projects/alpha"), links(result));
    }

    @Test
    void chartMacroUsesTheSelectedConnection() throws Exception
    {
        DefaultProjectManagementClientExecutionContext executionContext =
            this.componentManager.getInstance(ProjectManagementClientExecutionContext.class);
        PaginatedResult<WorkItem> workItems = new PaginatedResult<>(List.of(new WorkItem()), 0, 100, 1);
        when(this.projectManagementManager.getWorkItems("openproject", 0, 100, List.of(), List.of()))
            .thenReturn(workItems);
        when(this.barDisplayer.getParameterTypeTemplate()).thenReturn(new HashMap<>());
        List<Block> chart = List.of(new WordBlock("chart"));
        when(this.barDisplayer.execute(List.of(workItems), WorkItem.KEY_STATUS, List.of(), this.context,
            new HashMap<>())).thenReturn(chart);

        assertEquals(chart, this.chartMacro.execute(new OpenProjectChartMacroParameters(), "", this.context));
        // The connection is passed to the OpenProject client through the execution context.
        assertEquals("dashboard", executionContext.get("instance"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void workItemsMacroUsesTheSelectedConnection() throws Exception
    {
        Macro<ProjectManagementAsyncMacroParams> liveData = mock(Macro.class);
        this.componentManager.registerComponent(Macro.class, "proj-manag-liveData", liveData);
        OpenProjectMacroParameters parameters = new OpenProjectMacroParameters();

        this.openProjectMacro.execute(parameters, "", this.context);

        assertEquals("client=openproject&instance=dashboard&translationPrefix=openproject.",
            parameters.getSourceParameters());
    }
}
