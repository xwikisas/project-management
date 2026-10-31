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

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementChartMacroParameters;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the {@link AbstractProjectManagementChartMacro} through a test implementation.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(PresetFiltersCombiner.class)
class AbstractProjectManagementChartMacroTest
{
    private static final String STATUS_NEW =
        "{\"property\":\"status\",\"constraints\":[{\"operator\":\"=\",\"value\":\"1\"}]}";

    private static final String TYPE_BUG =
        "{\"property\":\"type\",\"constraints\":[{\"operator\":\"=\",\"value\":\"bug\"}]}";

    @InjectMockComponents
    private TestChartMacro macro;

    @MockComponent
    private PresetsManager presetsManager;

    @MockComponent
    private ComponentManager componentManager;

    @Mock
    private MacroTransformationContext macroTransformationContext;

    @Test
    void executeCombinesThePresetDatasetsWithTheMacroFilters() throws Exception
    {
        Preset preset = mock(Preset.class);
        when(preset.getFilter()).thenReturn(datasets(STATUS_NEW, ""));
        when(this.presetsManager.getPreset(42)).thenReturn(preset);
        // Stop the execution once the filters are resolved.
        when(this.componentManager.getInstance(ChartTypeDisplayer.class, "pie"))
            .thenThrow(new ComponentLookupException("stop"));
        ProjectManagementChartMacroParameters parameters = new ProjectManagementChartMacroParameters();
        parameters.setPresetId("42");
        parameters.setFilters("{\"query\":{\"filters\":[" + TYPE_BUG + "]}}");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters, null, this.macroTransformationContext));

        assertEquals("Failed to find the chart type.", exception.getMessage());
        // The filters of the macro narrow down each dataset of the preset.
        assertEquals(datasets(STATUS_NEW + ',' + TYPE_BUG, TYPE_BUG), parameters.getFilters());
    }

    private static String datasets(String first, String second)
    {
        return "[{\"query\":{\"filters\":[" + first + "]}},{\"query\":{\"filters\":[" + second + "]}}]";
    }

    @Test
    void executeWithMissingPreset()
    {
        ProjectManagementChartMacroParameters parameters = new ProjectManagementChartMacroParameters();
        parameters.setPresetId("42");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters, null, this.macroTransformationContext));

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
