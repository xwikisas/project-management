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
package com.xwiki.projectmanagement.internal.macro;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.macro.ProjectManagementChartMacroParameters;
import com.xwiki.projectmanagement.presets.PresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test the {@link AbstractProjectManagementChartMacro} through a test implementation.
 *
 * @version $Id$
 */
@ComponentTest
class AbstractProjectManagementChartMacroTest
{
    /**
     * Implementation of the abstract chart macro for testing purposes.
     */
    @Component
    @Named("testChart")
    public static class TestChartMacro
        extends AbstractProjectManagementChartMacro<ProjectManagementChartMacroParameters>
    {
        /**
         * Default constructor.
         */
        public TestChartMacro()
        {
            super("testChart", "testChart", ProjectManagementChartMacroParameters.class);
        }

        @Override
        public boolean supportsInlineMode()
        {
            return false;
        }
    }

    @InjectMockComponents
    private TestChartMacro macro;

    @MockComponent
    private PresetsManager presetsManager;

    @Mock
    private MacroTransformationContext macroTransformationContext;

    @Test
    void executeWithMissingPreset()
    {
        ProjectManagementChartMacroParameters parameters = new ProjectManagementChartMacroParameters();
        parameters.setPresetId("42");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters, null, this.macroTransformationContext));

        // The message names the missing preset, like the one of the work items macro.
        assertEquals("There is no preset with id [42].", exception.getMessage());
        verify(this.presetsManager).getPreset(42);
    }

    @Test
    void executeWithInvalidPresetId()
    {
        ProjectManagementChartMacroParameters parameters = new ProjectManagementChartMacroParameters();
        parameters.setPresetId("notANumber");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters, null, this.macroTransformationContext));

        assertEquals("The preset id should be a number.", exception.getMessage());
        verify(this.presetsManager, never()).getPreset(any());
    }
}
