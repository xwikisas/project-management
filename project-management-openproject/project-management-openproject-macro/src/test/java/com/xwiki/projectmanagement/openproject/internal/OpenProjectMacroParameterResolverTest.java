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
package com.xwiki.projectmanagement.openproject.internal;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.observation.ObservationManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.macro.BaseDirectOpenProjectMacroParameters;

import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE;
import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link OpenProjectMacroParameterResolver}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectMacroParameterResolverTest
{
    @InjectMockComponents
    private OpenProjectMacroParameterResolver resolver;

    @MockComponent
    private ObservationManager observationManager;

    private final BaseDirectOpenProjectMacroParameters parameters = new BaseDirectOpenProjectMacroParameters();

    /**
     * Simulate a listener that answers the event by putting the given entry in the event data.
     */
    @SuppressWarnings("unchecked")
    private void answerEventWith(String key, String value)
    {
        doAnswer(invocation -> {
            ((Map<String, String>) invocation.getArgument(2)).put(key, value);
            return null;
        }).when(this.observationManager).notify(any(BeforeOpenProjectMacroExecutionEvent.class), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveInstanceSendsTheCurrentInstanceToListeners()
    {
        this.parameters.setInstance("main");

        this.resolver.resolveInstance(this.parameters);

        ArgumentCaptor<Object> dataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(this.observationManager).notify(any(BeforeOpenProjectMacroExecutionEvent.class), same(this.resolver),
            dataCaptor.capture());
        Map<String, String> expected = new HashMap<>();
        expected.put("instance", "main");
        assertEquals(expected, dataCaptor.getValue());
    }

    @Test
    void resolveInstanceKeepsAnExplicitInstance()
    {
        this.parameters.setInstance("main");
        answerEventWith("effectiveInstance", "other");

        assertEquals("main", this.resolver.resolveInstance(this.parameters));
        assertEquals("main", this.parameters.getInstance());
    }

    @Test
    void resolveInstanceUsesTheInstanceProvidedByListenersWhenMissing()
    {
        answerEventWith("effectiveInstance", "dashboard");

        assertEquals("dashboard", this.resolver.resolveInstance(this.parameters));
        assertEquals("dashboard", this.parameters.getInstance());
    }

    @Test
    void resolveInstanceReplacesTheDashboardPlaceholder()
    {
        this.parameters.setInstance(USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
        answerEventWith("effectiveInstance", "dashboard");

        assertEquals("dashboard", this.resolver.resolveInstance(this.parameters));
        assertEquals("dashboard", this.parameters.getInstance());
    }

    @Test
    void resolveInstanceWithoutAnyListenerAnswer()
    {
        this.parameters.setInstance(USE_SELECTED_DASHBOARD_CONNECTION_VALUE);

        assertNull(this.resolver.resolveInstance(this.parameters));
        // The parameters are left untouched when no listener provides an instance.
        assertEquals(USE_SELECTED_DASHBOARD_CONNECTION_VALUE, this.parameters.getInstance());
    }

    @Test
    void resolveProjectKeepsAnExplicitProject()
    {
        this.parameters.setProject("12");
        answerEventWith("effectiveProject", "34");

        this.resolver.resolveProject(this.parameters);

        assertEquals("12", this.parameters.getProject());
    }

    @Test
    void resolveProjectUsesTheProjectProvidedByListeners()
    {
        this.parameters.setProject(USE_SELECTED_DASHBOARD_PROJECT_VALUE);
        answerEventWith("effectiveProject", "34");

        this.resolver.resolveProject(this.parameters);

        assertEquals("34", this.parameters.getProject());
    }

    @Test
    void resolveProjectWhenMissing()
    {
        answerEventWith("effectiveProject", "34");

        this.resolver.resolveProject(this.parameters);

        assertEquals("34", this.parameters.getProject());
    }
}
