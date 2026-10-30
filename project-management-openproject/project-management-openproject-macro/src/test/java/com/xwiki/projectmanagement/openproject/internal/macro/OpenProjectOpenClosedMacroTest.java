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
package com.xwiki.projectmanagement.openproject.internal.macro;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectChartMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectOpenClosedMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectOpenClosedMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectOpenClosedMacroTest
{
    @InjectMockComponents
    private OpenProjectOpenClosedMacro macro;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    private LiveDataConfigurationResolver<String> liveDataConfigurationResolver;

    @MockComponent
    @Named("line")
    private ChartTypeDisplayer lineDisplayer;

    @Mock
    private MacroTransformationContext context;

    private final List<Block> chart = List.of(new WordBlock("chart"));

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(List.of());
        OpenProjectMacroTestUtils.executeSynchronously(this.asyncExecutor);
        when(this.l10n.getTranslationPlain("openproject.openprojectopenclosed.labels.open")).thenReturn("Open");
        when(this.l10n.getTranslationPlain("openproject.openprojectopenclosed.labels.closed")).thenReturn("Closed");
        // Each data set is filtered on the status.
        when(this.liveDataConfigurationResolver.resolve(anyString())).thenAnswer(invocation -> {
            LiveDataQuery query = new LiveDataQuery();
            query.setFilters(new ArrayList<>(List.of(new LiveDataQuery.Filter("status", "o", ""))));
            LiveDataConfiguration configuration = new LiveDataConfiguration();
            configuration.setQuery(query);
            return configuration;
        });
        when(this.lineDisplayer.getParameterTypeTemplate()).thenReturn(new HashMap<>());
        when(this.projectManagementManager.getWorkItems(any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(new PaginatedResult<>(List.of(), 0, 500, 0));
        when(this.lineDisplayer.execute(any(), any(), any(), any(), any())).thenReturn(this.chart);
    }

    @Test
    @SuppressWarnings("unchecked")
    void comparesTheOpenAndClosedWorkPackagesCreatedDuringTheLastDays() throws Exception
    {
        OpenProjectOpenClosedMacroParameters parameters = new OpenProjectOpenClosedMacroParameters();
        parameters.setInstance("main");
        parameters.setDays(10);

        assertEquals(this.chart, this.macro.execute(parameters, "", this.context));

        ArgumentCaptor<List<LiveDataQuery.Filter>> filtersCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<LiveDataQuery.SortEntry>> sortCaptor = ArgumentCaptor.forClass(List.class);
        // One data set for the open work packages, one for the closed ones.
        verify(this.projectManagementManager, times(2)).getWorkItems(eq("openproject"), eq(0), eq(500),
            filtersCaptor.capture(), sortCaptor.capture());
        // The newest work packages are kept when there are more than the limit.
        for (List<LiveDataQuery.SortEntry> sortEntries : sortCaptor.getAllValues()) {
            assertEquals(1, sortEntries.size());
            assertEquals(WorkItem.KEY_CREATION_DATE, sortEntries.get(0).getProperty());
            assertTrue(sortEntries.get(0).isDescending());
        }
        for (List<LiveDataQuery.Filter> filters : filtersCaptor.getAllValues()) {
            assertEquals(2, filters.size());
            LiveDataQuery.Filter creationDateFilter = filters.get(1);
            assertEquals(WorkItem.KEY_CREATION_DATE, creationDateFilter.getProperty());
            assertEquals("between", creationDateFilter.getConstraints().get(0).getOperator());
            assertCreationDateRange((String) creationDateFilter.getConstraints().get(0).getValue(), 10);
        }
        Map<String, Object> typeParams = Map.of("period", "DAILY", "metric", "accumulate");
        verify(this.lineDisplayer).execute(any(), eq(WorkItem.KEY_CREATION_DATE), eq(List.of("Open", "Closed")),
            eq(this.context), eq(new HashMap<>(typeParams)));
    }

    private void assertCreationDateRange(String range, int days)
    {
        String[] bounds = range.split("/");
        assertEquals(2, bounds.length, range);
        OffsetDateTime start = OffsetDateTime.parse(bounds[0]);
        OffsetDateTime end = OffsetDateTime.parse(bounds[1]);
        LocalDate today = LocalDate.now();
        // The range goes from the start of the first day to the end of today.
        assertEquals(LocalTime.MIDNIGHT, start.toLocalTime());
        assertEquals(LocalTime.MAX, end.toLocalTime());
        assertTrue(end.toLocalDate().equals(today) || end.toLocalDate().equals(today.minusDays(1)), range);
        assertEquals(end.toLocalDate().minusDays(days), start.toLocalDate());
    }

    @Test
    void returnsTheMissingLicenseMessage() throws Exception
    {
        List<Block> licenseBlock = List.of(new WordBlock("license"));
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(licenseBlock);
        OpenProjectOpenClosedMacroParameters parameters = new OpenProjectOpenClosedMacroParameters();
        parameters.setInstance("main");

        assertSame(licenseBlock, this.macro.execute(parameters, "", this.context));

        verifyNoInteractions(this.asyncExecutor, this.projectManagementManager, this.lineDisplayer);
    }

    @Test
    void failsWithParametersOfAnotherMacro()
    {
        OpenProjectChartMacroParameters parameters = new OpenProjectChartMacroParameters();
        parameters.setInstance("main");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters, "", this.context));

        assertEquals("Something went wrong", exception.getMessage());
    }
}
