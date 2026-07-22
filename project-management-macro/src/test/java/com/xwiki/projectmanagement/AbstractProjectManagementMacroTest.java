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
package com.xwiki.projectmanagement;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.async.internal.block.BlockAsyncRendererExecutor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.TestProjManagementMacro;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the {@link com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro} through a test
 * implementation.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@ComponentTest
public class AbstractProjectManagementMacroTest
{
    @InjectMockComponents
    private TestProjManagementMacro abstractMacro;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private BlockAsyncRendererExecutor executor;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @Mock
    private MacroTransformationContext macroTransformationContext;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayerMacro;

    @Test
    void executeMacroWithDefaultValuesTest() throws MacroExecutionException, ComponentLookupException
    {
        ProjectManagementMacroParameters params = new ProjectManagementMacroParameters();

        when(componentManager.getInstance(Macro.class, "liveData")).thenReturn(displayerMacro);
        abstractMacro.execute(params, "", macroTransformationContext);

        assertEquals("projectmanagement", params.getSource());
        assertEquals("smth=smth2&smth3=smth4", params.getSourceParameters());

        verify(displayerMacro).execute(params, "", macroTransformationContext);
    }

    @Test
    void executeMacroWithSingleDisplayerTest()
        throws Exception
    {
        ProjectManagementMacroParameters params = new ProjectManagementMacroParameters();
        params.setWorkItemsDisplayer(WorkItemsDisplayer.workItemsSingle);
        when(componentManager.getInstance(Macro.class, WorkItemsDisplayer.workItemsSingle.name())).thenReturn(
            displayerMacro);
        List<Block> result = abstractMacro.execute(params, "", macroTransformationContext);

        // Gets executed in the async renderer.
        verify(asyncExecutor).execute(eq(displayerMacro), any(), any(), any());
    }
}
