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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.async.internal.AsyncRendererExecutor;
import org.xwiki.rendering.async.internal.AsyncRendererExecutorResponse;
import org.xwiki.rendering.async.internal.AsyncRendererJobStatus;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncRenderer;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AsyncMacroCallResource}.
 *
 * @version $Id$
 */
@ComponentTest
class AsyncMacroCallResourceTest
{
    private static final String WIKI = "xwiki";

    private static final String SINGLE = "workItemsSingle";

    @InjectMockComponents
    private AsyncMacroCallResource resource;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private AsyncRendererExecutor asyncRendererExecutor;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

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
        when(this.xcontextProvider.get()).thenReturn(mock(XWikiContext.class));
    }

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.authorizationManager.hasAccess(Right.VIEW)).thenReturn(true);
        when(this.componentManager.getInstance(ProjectManagementAsyncRenderer.class)).thenReturn(this.asyncRenderer);
        when(this.componentManager.getInstance(Macro.class, SINGLE)).thenReturn(this.displayerMacro);
        when(this.rendererResponse.getStatus()).thenReturn(mock(AsyncRendererJobStatus.class));
        when(this.asyncRendererExecutor.render(eq(this.asyncRenderer), any())).thenReturn(this.rendererResponse);
    }

    @Test
    void getAsyncCall() throws Exception
    {
        Response response = this.resource.getAsyncCall(WIKI, SINGLE, "instance", "123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ArgumentCaptor<ProjectManagementMacroParameters> parametersCaptor =
            ArgumentCaptor.forClass(ProjectManagementMacroParameters.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.asyncRenderer).initialize(eq(this.displayerMacro), parametersCaptor.capture(),
            contentCaptor.capture(), any(), any());
        ProjectManagementMacroParameters parameters = parametersCaptor.getValue();
        // The work items displayers read the filters from the parameters, the Live Data one from the content.
        LiveDataQuery.Filter filter = new ObjectMapper().readValue(parameters.getFilters(),
            LiveDataConfiguration.class).getQuery().getFilters().get(0);
        assertEquals("identifier", filter.getProperty());
        assertEquals("123", filter.getConstraints().get(0).getValue());
        assertEquals(parameters.getFilters(), contentCaptor.getValue());
    }
}
