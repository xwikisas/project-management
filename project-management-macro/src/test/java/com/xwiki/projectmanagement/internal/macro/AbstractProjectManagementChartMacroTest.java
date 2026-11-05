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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementChartMacroParameters;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private static final String CLIENT = "test";

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

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    private ContextualLocalizationManager localizationManager;

    @Mock
    private ChartTypeDisplayer displayer;

    private final ProjectManagementChartMacroParameters parameters = new ProjectManagementChartMacroParameters();

    private final List<Block> chart = List.of(new WordBlock("chart"));

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

    @Test
    void chartsTheNumberOfWorkItemsCountedByTheClient() throws Exception
    {
        Map<String, Long> counts = Map.of("New", 3L);
        when(this.projectManagementManager.countWorkItems(CLIENT, List.of(), WorkItem.KEY_STATUS))
            .thenReturn(Optional.of(counts));
        when(this.displayer.executeCounts(List.of(counts), WorkItem.KEY_STATUS, List.of(),
            this.macroTransformationContext, new HashMap<>())).thenReturn(this.chart);

        assertEquals(this.chart, execute());
        // The work items aren't retrieved.
        verify(this.projectManagementManager, never()).getAllWorkItems(any(), any(), any(), anyInt());
    }

    @Test
    void chartsAllTheWorkItemsWhenTheClientCantCountThem() throws Exception
    {
        PaginatedResult<WorkItem> workItems = new PaginatedResult<>(List.of(new WorkItem()), 0, 1000, 1);
        when(this.projectManagementManager.getAllWorkItems(CLIENT, List.of(), List.of(), 1000)).thenReturn(workItems);
        when(this.displayer.execute(List.of(workItems), WorkItem.KEY_STATUS, List.of(),
            this.macroTransformationContext, new HashMap<>())).thenReturn(this.chart);

        assertEquals(this.chart, execute());
    }

    @Test
    void warnsWhenTheChartDoesntCountAllTheWorkItems() throws Exception
    {
        PaginatedResult<WorkItem> workItems = new PaginatedResult<>(List.of(new WorkItem()), 0, 1000, 1500);
        when(this.projectManagementManager.getAllWorkItems(CLIENT, List.of(), List.of(), 1000)).thenReturn(workItems);
        when(this.displayer.execute(List.of(workItems), WorkItem.KEY_STATUS, List.of(),
            this.macroTransformationContext, new HashMap<>())).thenReturn(this.chart);
        Translation translation = mock(Translation.class);
        WordBlock message = new WordBlock("truncated");
        when(translation.render(1000, 1500L)).thenReturn(message);
        when(this.localizationManager.getTranslation("projectmanagement.macro.chart.truncated"))
            .thenReturn(translation);

        List<Block> result = execute();

        assertEquals(2, result.size());
        assertEquals(this.chart.get(0), result.get(0));
        assertEquals("box warningmessage", result.get(1).getParameter("class"));
        assertEquals(List.of(message), result.get(1).getChildren().get(0).getChildren());
    }

    @Test
    void chartsTheFirstWorkItemsWhenGivenALimit() throws Exception
    {
        this.parameters.setLimit(10);
        PaginatedResult<WorkItem> workItems = new PaginatedResult<>(List.of(new WorkItem()), 0, 10, 1500);
        when(this.projectManagementManager.getWorkItems(CLIENT, 0, 10, List.of(), List.of())).thenReturn(workItems);
        when(this.displayer.execute(List.of(workItems), WorkItem.KEY_STATUS, List.of(),
            this.macroTransformationContext, new HashMap<>())).thenReturn(this.chart);

        // The work items beyond the limit are left out on purpose: no warning.
        assertEquals(this.chart, execute());
        verify(this.projectManagementManager, never()).countWorkItems(any(), any(), any());
    }

    private List<Block> execute() throws Exception
    {
        when(this.asyncExecutor.execute(any(), any(), any(), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            Macro<ProjectManagementAsyncMacroParams> asyncMacro = invocation.getArgument(0);
            return asyncMacro.execute(invocation.getArgument(1), invocation.getArgument(2),
                invocation.getArgument(3));
        });
        when(this.componentManager.getInstance(ChartTypeDisplayer.class, "pie")).thenReturn(this.displayer);
        when(this.displayer.getParameterTypeTemplate()).thenReturn(new HashMap<>());
        this.parameters.setClient(CLIENT);
        this.parameters.setType("pie");
        this.parameters.setProperty(WorkItem.KEY_STATUS);

        List<Block> result = this.macro.execute(this.parameters, null, this.macroTransformationContext);

        // The chart is wrapped, so that it can be drawn after an asynchronous rendering.
        assertEquals(1, result.size());
        return result.get(0).getChildren();
    }
}
