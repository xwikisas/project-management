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
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.async.internal.AsyncRendererExecutor;
import org.xwiki.rendering.async.internal.AsyncRendererExecutorResponse;
import org.xwiki.rendering.async.internal.AsyncRendererJobStatus;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.internal.displayers.WorkItemLivedataDisplayer;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncRenderer;
import com.xwiki.projectmanagement.internal.rest.WikiRequestExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AsyncMacroCallResource}. The parameters of the displayer are tested in
 * {@link WorkPackageDisplayerParametersTest}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(WikiRequestExecutor.class)
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

    private ProjectManagementAsyncRenderer asyncRenderer;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayerMacro;

    @Mock
    private AsyncRendererExecutorResponse rendererResponse;

    /**
     * The wiki of the context while the work package is rendered.
     */
    private String renderingWiki;

    @BeforeComponent
    void beforeComponent()
    {
        // The context is needed when initializing the resource.
        this.xcontext = new XWikiContext();
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        this.xcontext.setWikiId(CURRENT_WIKI);
        this.asyncRenderer = new ProjectManagementAsyncRenderer();
        when(this.authorizationManager.hasAccess(Right.VIEW, new WikiReference(WIKI))).thenReturn(true);
        when(this.wikiDescriptorManager.exists(WIKI)).thenReturn(true);
        when(this.componentManager.getInstance(ProjectManagementAsyncRenderer.class)).thenReturn(this.asyncRenderer);
        when(this.componentManager.getInstance(Macro.class, SINGLE)).thenReturn(this.displayerMacro);
        when(this.rendererResponse.getStatus()).thenReturn(mock(AsyncRendererJobStatus.class));
        when(this.rendererResponse.getJobIdHTTPPath()).thenReturn("job/id");
        when(this.rendererResponse.getAsyncClientId()).thenReturn("42");
        when(this.asyncRendererExecutor.render(eq(this.asyncRenderer), any())).thenAnswer(invocation -> {
            this.renderingWiki = this.xcontext.getWikiId();
            return this.rendererResponse;
        });
    }

    @Test
    void getAsyncCall()
    {
        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "My&instance=x", "123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("<div class=\"xwiki-async\" data-xwiki-async-id=\"job/id\" data-xwiki-async-client-id=\"42\">"
            + "</div>", response.getEntity());
        // The work package is rendered with the parameters of the work package displayer.
        ProjectManagementMacroParameters parameters =
            (ProjectManagementMacroParameters) this.asyncRenderer.getParameters();
        assertEquals(WorkItemsDisplayer.workItemsSingle, parameters.getWorkItemsDisplayer());
        assertEquals("instance=My%26instance%3Dx&client=openproject&translationPrefix=openproject.",
            parameters.getSourceParameters());
        // The rendering job takes the wiki of the request as context wiki, and the context wiki is restored after.
        assertEquals(WIKI, this.renderingWiki);
        assertEquals(CURRENT_WIKI, this.xcontext.getWikiId());
    }

    @Test
    void getAsyncCallWithLiveDataDisplayer() throws Exception
    {
        // Not the Live Data macro of the platform, which doesn't take the parameters of the displayers.
        when(this.componentManager.getInstance(Macro.class, WorkItemLivedataDisplayer.ROLE_HINT))
            .thenReturn(this.displayerMacro);

        Response response = this.resource.getAsyncCall(WIKI, "liveData", "instance", "123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(WorkItemsDisplayer.liveData,
            ((ProjectManagementMacroParameters) this.asyncRenderer.getParameters()).getWorkItemsDisplayer());
    }

    @Test
    void getAsyncCallWithUnknownDisplayer()
    {
        Response response = this.resource.getAsyncCall(WIKI, "html", "instance", "123");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.asyncRendererExecutor);
    }

    @Test
    void getAsyncCallWithInvalidWorkPackageId()
    {
        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "instance", "1&x=y");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verifyNoInteractions(this.asyncRendererExecutor);
    }

    @Test
    void getAsyncCallWithoutViewRightOnTheWiki()
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
        assertEquals(CURRENT_WIKI, this.xcontext.getWikiId());
    }

    @Test
    void getAsyncCallWhenTheRenderingFails() throws Exception
    {
        when(this.asyncRendererExecutor.render(eq(this.asyncRenderer), any())).thenAnswer(invocation -> {
            this.renderingWiki = this.xcontext.getWikiId();
            throw new RenderingException("Details");
        });

        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "instance", "123");

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to display the work package.", response.getEntity());
        assertEquals("Failed to display the work package.", this.logCapture.getMessage(0));
        // The context wiki is restored even when the rendering fails.
        assertEquals(WIKI, this.renderingWiki);
        assertEquals(CURRENT_WIKI, this.xcontext.getWikiId());
    }
}
