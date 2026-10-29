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
package com.xwiki.projectmanagement.openproject.internal.rest;

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.async.internal.AsyncRendererConfiguration;
import org.xwiki.rendering.async.internal.AsyncRendererExecutor;
import org.xwiki.rendering.async.internal.AsyncRendererExecutorResponse;
import org.xwiki.rendering.async.internal.AsyncRendererJobStatus;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.internal.displayers.WorkItemLivedataDisplayer;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncRenderer;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AsyncMacroCallResource}.
 *
 * @version $Id$
 */
@ComponentTest
class AsyncMacroCallResourceTest
{
    private static final String WIKI = "subwiki";

    private static final String CURRENT_WIKI = "xwiki";

    private static final String SINGLE = WorkItemsDisplayer.workItemsSingle.name();

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private AsyncMacroCallResource resource;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private AsyncRendererExecutor asyncRendererExecutor;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private XWikiContext xcontext;

    @Mock
    private ProjectManagementAsyncRenderer asyncRenderer;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayerMacro;

    @Mock
    private AsyncRendererExecutorResponse rendererResponse;

    @BeforeComponent
    void beforeComponent()
    {
        // The context is needed when initializing the resource.
        this.xcontext = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.xcontext.getWikiId()).thenReturn(CURRENT_WIKI);
        when(this.authorizationManager.hasAccess(Right.VIEW, new WikiReference(WIKI))).thenReturn(true);
        when(this.wikiDescriptorManager.exists(WIKI)).thenReturn(true);
        when(this.componentManager.getInstance(ProjectManagementAsyncRenderer.class)).thenReturn(this.asyncRenderer);
        when(this.componentManager.getInstance(Macro.class, SINGLE)).thenReturn(this.displayerMacro);
        when(this.rendererResponse.getStatus()).thenReturn(mock(AsyncRendererJobStatus.class));
        when(this.rendererResponse.getJobIdHTTPPath()).thenReturn("job/id");
        when(this.rendererResponse.getAsyncClientId()).thenReturn("42");
        when(this.asyncRendererExecutor.render(eq(this.asyncRenderer), any())).thenReturn(this.rendererResponse);
    }

    @Test
    void getAsyncCall() throws Exception
    {
        // The rendering job takes the context wiki of the caller.
        doAnswer(invocation -> {
            verify(this.xcontext).setWikiId(WIKI);
            return this.rendererResponse;
        }).when(this.asyncRendererExecutor).render(eq(this.asyncRenderer), any(AsyncRendererConfiguration.class));

        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "My&instance=x", "123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ArgumentCaptor<ProjectManagementMacroParameters> parametersCaptor =
            ArgumentCaptor.forClass(ProjectManagementMacroParameters.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.asyncRenderer).initialize(eq(this.displayerMacro), parametersCaptor.capture(),
            contentCaptor.capture(), any(), any());
        ProjectManagementMacroParameters parameters = parametersCaptor.getValue();
        // The connection name is a single source parameter, whatever characters it holds.
        assertEquals("instance=My%26instance%3Dx&client=openproject&translationPrefix=openproject.",
            parameters.getSourceParameters());
        assertEquals(WorkItemsDisplayer.workItemsSingle, parameters.getWorkItemsDisplayer());
        // The work items displayers read the filters from the parameters.
        LiveDataQuery.Filter filter = new ObjectMapper().readValue(parameters.getFilters(),
            LiveDataConfiguration.class).getQuery().getFilters().get(0);
        assertEquals("identifier", filter.getProperty());
        assertEquals("123", filter.getConstraints().get(0).getValue());
        assertEquals(parameters.getFilters(), contentCaptor.getValue());

        InOrder inOrder = inOrder(this.xcontext);
        inOrder.verify(this.xcontext).setWikiId(WIKI);
        inOrder.verify(this.xcontext).setWikiId(CURRENT_WIKI);
    }

    @Test
    void getAsyncCallWithLiveDataDisplayer() throws Exception
    {
        when(this.componentManager.getInstance(Macro.class, WorkItemLivedataDisplayer.ROLE_HINT))
            .thenReturn(this.displayerMacro);

        Response response = this.resource.getAsyncCall(WIKI, "liveData", "instance", "123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("<div class=\"xwiki-async\" data-xwiki-async-id=\"job/id\" data-xwiki-async-client-id=\"42\">"
            + "</div>", response.getEntity());
        // Not the Live Data macro of the platform, which doesn't take the parameters of the displayers.
        verify(this.componentManager, never()).getInstance(Macro.class, "liveData");
    }

    @Test
    void getAsyncCallWithUnknownDisplayer() throws Exception
    {
        Response response = this.resource.getAsyncCall(WIKI, "html", "instance", "123");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verify(this.componentManager, never()).getInstance(any(), anyString());
        verifyNoInteractions(this.asyncRendererExecutor);
    }

    @Test
    void getAsyncCallWithInvalidWorkPackageId() throws Exception
    {
        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "instance", "1&x=y");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.asyncRendererExecutor);
    }

    @Test
    void getAsyncCallWithoutViewRightOnTheWiki() throws Exception
    {
        when(this.authorizationManager.hasAccess(Right.VIEW, new WikiReference(WIKI))).thenReturn(false);
        when(this.authorizationManager.hasAccess(Right.VIEW)).thenReturn(true);

        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "instance", "123");

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.asyncRendererExecutor);
    }

    @Test
    void getAsyncCallOnMissingWiki() throws Exception
    {
        when(this.wikiDescriptorManager.exists(WIKI)).thenReturn(false);

        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "instance", "123");

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.asyncRendererExecutor);
        verify(this.xcontext, never()).setWikiId(WIKI);
    }

    @Test
    void getAsyncCallWhenTheRenderingFails() throws Exception
    {
        RenderingException exception = new RenderingException("Details");
        when(this.asyncRendererExecutor.render(eq(this.asyncRenderer), any())).thenThrow(exception);

        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "instance", "123");

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to display the work package.", response.getEntity());
        assertEquals("Failed to display the work package.", this.logCapture.getMessage(0));
        verify(this.xcontext).setWikiId(CURRENT_WIKI);
    }
}
