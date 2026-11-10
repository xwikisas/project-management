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
package com.xwiki.projectmanagement.openproject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.csrf.script.CSRFTokenScriptService;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.XWikiServletRequestStub;
import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code OpenProject.WebHome}, the OpenProject dashboard.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList(ModelScriptService.class)
class DashboardPageTest extends PageTest
{
    private static final DocumentReference DASHBOARD = new DocumentReference("xwiki", "OpenProject", "WebHome");

    private static final String PUBLIC_CONNECTION = "public";

    private static final String CONNECTION_NAME = "<img src=\"x\" class=\"injected\"/>{{/html}}";

    private static final DocumentReference SELECTION =
        new DocumentReference("xwiki", "OpenProject", "DashboardSelection");

    private static final LocalDocumentReference CONFIG_CLASS =
        new LocalDocumentReference(List.of("OpenProject", "Code"), "DashboardConnectionConfigClass");

    private static final String SELECTED_CONNECTION = "selectedConnection";

    private static final String SELECTED_PROJECT = "selectedProject";

    private static final String SELECTED_PROJECT_LABEL = "selectedProjectLabel";

    private static final String PROJECT_OPTION = "#openproject-dashboard-project-select option";

    private static final String TOKEN = "token";

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    @MockComponent(classToMock = CSRFTokenScriptService.class)
    @Named("csrf")
    private ScriptService csrfScriptService;

    @MockComponent
    private UserReferenceResolver<CurrentUserReference> currentUserReferenceResolver;

    private OpenProjectScriptService openProject;

    @BeforeEach
    void setUp() throws Exception
    {
        this.openProject = (OpenProjectScriptService) this.openProjectScriptService;
        // The first option is the "use the dashboard connection" option, removed by the dashboard.
        when(this.openProject.getConnectionOptions()).thenReturn(new ArrayList<>(List.of(Map.of(),
            Map.of("name", "first", "value", "first"), Map.of("name", "second", "value", "second"))));
        when(this.openProject.canReadData(anyString())).thenReturn(true);
        when(((CSRFTokenScriptService) this.csrfScriptService).isTokenValid(TOKEN)).thenReturn(true);
        this.context.setUserReference(new DocumentReference("xwiki", "XWiki", "User"));
        when(this.oldcore.getMockRightService().hasAccessLevel(anyString(), anyString(), anyString(), any()))
            .thenReturn(true);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(eq(Right.EDIT), any(), any()))
            .thenReturn(true);
        when(this.currentUserReferenceResolver.resolve(CurrentUserReference.INSTANCE))
            .thenReturn(mock(UserReference.class));
        registerVelocityTool("hasEdit", true);
        loadPage(new DocumentReference("xwiki", List.of("OpenProject", "Code"), "DashboardConnectionConfigClass"));
    }

    @Test
    void connectionNamesAreEscaped() throws Exception
    {
        when(this.openProject.getConnectionOptions()).thenReturn(new ArrayList<>(List.of(Map.of(),
            Map.of("name", CONNECTION_NAME, "value", CONNECTION_NAME),
            Map.of("name", "other", "value", "other"))));

        Document document = renderHTMLPage(DASHBOARD);

        assertTrue(document.select(".injected").isEmpty(), document.html());
        List<String> options = new ArrayList<>();
        for (Element option : document.select("#openproject-dashboard-connection-select option")) {
            options.add(option.attr("value"));
        }
        assertEquals(List.of(CONNECTION_NAME, "other"), options);
    }

    @Test
    void guestsSeeThePublicDataOfTheConnection() throws Exception
    {
        OpenProjectScriptService openProject = mockConnection();
        when(openProject.canReadData(PUBLIC_CONNECTION)).thenReturn(true);

        Document document = renderHTMLPage(DASHBOARD);

        assertEquals(1, document.select("#openproject-dashboard-gadgets").size(), document.html());
        assertTrue(document.select(".warningmessage:not(.hidden)").isEmpty(), document.html());
    }

    @Test
    void guestsCantSeeTheConnectionsThatDontShowPublicData() throws Exception
    {
        OpenProjectScriptService openProject = mockConnection();
        when(openProject.canReadData(PUBLIC_CONNECTION)).thenReturn(false);
        when(openProject.getUserAccountConnections()).thenReturn(List.of(PUBLIC_CONNECTION));

        Document document = renderHTMLPage(DASHBOARD);

        assertTrue(document.select("#openproject-dashboard-gadgets").isEmpty(), document.html());
        // Guests can't connect an OpenProject account.
        assertEquals("openproject.dashboard.guest", document.select(".warningmessage:not(.hidden)").text());
        verify(openProject, never()).getNotAuthorizedMessage(any(), any());
    }

    @Test
    void guestsGetTheWarningOfAConnectionShowingOnlyPublicData() throws Exception
    {
        // E.g. a connection showing only public data, whose OpenProject instance refuses anonymous requests.
        OpenProjectScriptService openProject = mockConnection();
        when(openProject.canReadData(PUBLIC_CONNECTION)).thenReturn(false);
        when(openProject.getUserAccountConnections()).thenReturn(List.of());
        when(openProject.getNotAuthorizedMessage(eq(PUBLIC_CONNECTION), any()))
            .thenReturn("<div class=\"box warningmessage\">refused</div>");

        Document document = renderHTMLPage(DASHBOARD);

        assertTrue(document.select("#openproject-dashboard-gadgets").isEmpty(), document.html());
        assertEquals("refused", document.select(".warningmessage:not(.hidden)").text());
    }

    private OpenProjectScriptService mockConnection() throws Exception
    {
        // A guest, who can't edit the dashboard.
        this.context.setUserReference(null);
        registerVelocityTool("hasEdit", false);
        OpenProjectScriptService openProject = (OpenProjectScriptService) this.openProjectScriptService;
        when(openProject.getConnectionOptions()).thenReturn(new ArrayList<>(List.of(Map.of(),
            Map.of("name", PUBLIC_CONNECTION, "value", PUBLIC_CONNECTION))));
        return openProject;
    }

    @Test
    void chartLegendsAreTranslated() throws Exception
    {
        XWikiDocument dashboard = loadPage(new DocumentReference("xwiki", "OpenProject", "WebHome"));
        List<String> charts = new ArrayList<>();
        for (BaseObject gadget : dashboard.getXObjects(new LocalDocumentReference("XWiki", "GadgetClass"))) {
            String content = gadget.getLargeStringValue("content");
            if (content.contains("datasetsLabels")) {
                charts.add(content);
            }
        }

        assertEquals(2, charts.size());
        for (String chart : charts) {
            // The chart macro translates the labels that are translation keys. The gadgets need no script, since
            // they are executed with the rights of the last author of the dashboard, who may not have script right.
            assertTrue(chart.startsWith("{{openprojectchart datasetsLabels=\"[~\"openproject.dashboard.chart.open~\","
                + "~\"openproject.dashboard.chart.closed~\"]\" "), chart);
        }
    }

    @Test
    void selectingAProjectSavesTheSelectionPageAndNotTheDashboard() throws Exception
    {
        XWikiDocument dashboard = loadPage(DASHBOARD);
        clearInvocations(this.xwiki);
        post(Map.of("action", "saveProject", "csrf_token", TOKEN, "connection", "second", "project", "3",
            "projectLabel", "Demo project"));

        render(dashboard);

        // Saving the dashboard as a user without script right would disable its scripts and extensions.
        verify(this.xwiki, never()).saveDocument(argThat(document -> DASHBOARD.equals(document.getDocumentReference())),
            anyString(), anyBoolean(), any());
        XWikiDocument selectionDocument = this.xwiki.getDocument(SELECTION, this.context);
        assertFalse(selectionDocument.isNew());
        assertTrue(selectionDocument.isHidden());
        BaseObject selection = selectionDocument.getXObject(CONFIG_CLASS);
        assertEquals("second", selection.getStringValue(SELECTED_CONNECTION));
        assertEquals("3", selection.getStringValue(SELECTED_PROJECT));
        assertEquals("Demo project", selection.getStringValue(SELECTED_PROJECT_LABEL));
    }

    @Test
    void selectionIsNotSavedWithAnInvalidCSRFToken() throws Exception
    {
        XWikiDocument dashboard = loadPage(DASHBOARD);
        post(Map.of("action", "saveProject", "csrf_token", "invalid", "connection", "second", "project", "3",
            "projectLabel", "Demo project"));

        render(dashboard);

        assertTrue(this.xwiki.getDocument(SELECTION, this.context).isNew());
    }

    @Test
    void selectionIsNotSavedWithoutEditRightOnTheDashboard() throws Exception
    {
        registerVelocityTool("hasEdit", false);
        XWikiDocument dashboard = loadPage(DASHBOARD);
        post(Map.of("action", "saveProject", "csrf_token", TOKEN, "connection", "second", "project", "3",
            "projectLabel", "Demo project"));

        Document document = render(dashboard);

        assertTrue(this.xwiki.getDocument(SELECTION, this.context).isNew());
        assertNull(document.getElementById("openproject-dashboard-project-form"));
    }

    @Test
    void selectionIsNotSavedWithoutEditRightOnTheSelectionPage() throws Exception
    {
        when(this.oldcore.getMockRightService().hasAccessLevel(eq("edit"), anyString(),
            eq("xwiki:OpenProject.DashboardSelection"), any())).thenReturn(false);
        XWikiDocument dashboard = loadPage(DASHBOARD);

        assertNull(render(dashboard).getElementById("openproject-dashboard-project-form"));

        post(Map.of("action", "saveProject", "csrf_token", TOKEN, "connection", "second", "project", "3",
            "projectLabel", "Demo project"));
        Document document = render(dashboard);

        // The dashboard is displayed: the selection isn't even attempted to be saved, which would fail.
        assertNotNull(document.getElementById("openproject-dashboard-data"), document.html());
        assertTrue(this.xwiki.getDocument(SELECTION, this.context).isNew());
    }

    @Test
    void guestsCannotChangeTheSelection() throws Exception
    {
        // Guests with edit right must not change the selection shared by all users.
        this.context.setUserReference(null);
        XWikiDocument dashboard = loadPage(DASHBOARD);
        post(Map.of("action", "saveProject", "csrf_token", TOKEN, "connection", "second", "project", "3",
            "projectLabel", "Demo project"));

        Document document = render(dashboard);

        assertTrue(this.xwiki.getDocument(SELECTION, this.context).isNew());
        assertNull(document.getElementById("openproject-dashboard-project-form"));
    }

    @Test
    void selectionIsReadFromTheSelectionPage() throws Exception
    {
        XWikiDocument selectionDocument = this.xwiki.getDocument(SELECTION, this.context);
        setSelection(selectionDocument, "second", "3", "Demo project");
        this.xwiki.saveDocument(selectionDocument, this.context);
        // The selection page takes precedence over the selection stored on the dashboard by 1.3.0.
        XWikiDocument dashboard = loadPage(DASHBOARD);
        setSelection(dashboard, "first", "1", "Legacy project");

        assertSelection(render(dashboard), "second", "3", "Demo project");
    }

    @Test
    void selectionStoredOnTheDashboardBefore140IsStillUsed() throws Exception
    {
        XWikiDocument dashboard = loadPage(DASHBOARD);
        setSelection(dashboard, "second", "7", "Legacy project");

        assertSelection(render(dashboard), "second", "7", "Legacy project");
    }

    private Document render(XWikiDocument dashboard) throws Exception
    {
        this.context.setDoc(dashboard);
        return renderHTMLPage(dashboard);
    }

    private void post(Map<String, String> parameters)
    {
        parameters.forEach(this.request::put);
        XWikiServletRequestStub postRequest = spy(this.request);
        when(postRequest.getMethod()).thenReturn("POST");
        this.context.setRequest(postRequest);
    }

    private void setSelection(XWikiDocument document, String connection, String project, String label)
        throws Exception
    {
        BaseObject selection = document.getXObject(CONFIG_CLASS, true, this.context);
        selection.setStringValue(SELECTED_CONNECTION, connection);
        selection.setStringValue(SELECTED_PROJECT, project);
        selection.setStringValue(SELECTED_PROJECT_LABEL, label);
    }

    private static void assertSelection(Document document, String connection, String project, String label)
    {
        Element selectedConnection =
            document.selectFirst("#openproject-dashboard-connection-select option[selected]");
        assertEquals(connection, selectedConnection.attr("value"));
        Element selectedProject = document.selectFirst(PROJECT_OPTION);
        assertEquals(project, selectedProject.attr("value"));
        assertEquals(label, selectedProject.text());
    }
}
