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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
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
import com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectChartMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectOpenClosedMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectOpenClosedMacro}. The period of the chart is tested in
 * {@link com.xwiki.projectmanagement.openproject.internal.LastDaysPeriodTest}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectOpenClosedMacroTest
{
    private static final String STATUS_FILTER = "{\"query\":{\"filters\":[{\"property\":\"status\","
        + "\"constraints\":[{\"operator\":\"%s\",\"value\":\"\"}]}]}}";

    @InjectMockComponents
    private OpenProjectOpenClosedMacro macro;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    private LiveDataConfigurationResolver<String> liveDataConfigurationResolver;

    @MockComponent
    private Provider<Clock> clockProvider;

    @MockComponent
    @Named("line")
    private ChartTypeDisplayer lineDisplayer;

    private final MacroTransformationContext context = new MacroTransformationContext();

    @BeforeEach
    void setUp() throws Exception
    {
        OpenProjectMacroTestUtils.executeSynchronously(this.asyncExecutor);
        when(this.clockProvider.get())
            .thenReturn(Clock.fixed(Instant.parse("2026-03-30T10:00:00Z"), ZoneId.of("Europe/Paris")));
        when(this.l10n.getTranslationPlain("openproject.openprojectopenclosed.labels.open")).thenReturn("Open");
        when(this.l10n.getTranslationPlain("openproject.openprojectopenclosed.labels.closed")).thenReturn("Closed");
        when(this.liveDataConfigurationResolver.resolve(String.format(STATUS_FILTER, "o")))
            .thenReturn(statusFilter("o"));
        when(this.liveDataConfigurationResolver.resolve(String.format(STATUS_FILTER, "c")))
            .thenReturn(statusFilter("c"));
        when(this.lineDisplayer.getParameterTypeTemplate()).thenReturn(new HashMap<>());
    }

    private static LiveDataConfiguration statusFilter(String operator)
    {
        LiveDataQuery query = new LiveDataQuery();
        // The macro adds its own filter to the list.
        query.setFilters(new ArrayList<>(List.of(new LiveDataQuery.Filter("status", operator, ""))));
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setQuery(query);
        return configuration;
    }

    private static ArgumentMatcher<List<LiveDataQuery.Filter>> filters(String expected)
    {
        return filters -> expected.equals(filters.stream()
            .flatMap(filter -> filter.getConstraints().stream()
                .map(constraint -> filter.getProperty() + ' ' + constraint.getOperator() + ' '
                    + constraint.getValue()))
            .collect(Collectors.joining(", ")));
    }

    private static ArgumentMatcher<List<LiveDataQuery.SortEntry>> newestFirst()
    {
        return sortEntries -> sortEntries.size() == 1
            && WorkItem.KEY_CREATION_DATE.equals(sortEntries.get(0).getProperty())
            && sortEntries.get(0).isDescending();
    }

    @Test
    void comparesTheOpenAndClosedWorkPackagesCreatedDuringTheLastDays() throws Exception
    {
        OpenProjectOpenClosedMacroParameters parameters = new OpenProjectOpenClosedMacroParameters();
        parameters.setInstance("main");
        parameters.setDays(10);
        // The newest work packages are kept when there are more than the limit.
        String period = "creationDate between 2026-03-20T00:00:00+01:00/2026-03-30T23:59:59.999999999+02:00";
        PaginatedResult<WorkItem> open = new PaginatedResult<>(List.of(new WorkItem()), 0, 500, 1);
        when(this.projectManagementManager.getWorkItems(eq("openproject"), eq(0), eq(500),
            argThat(filters("status o , " + period)), argThat(newestFirst()))).thenReturn(open);
        PaginatedResult<WorkItem> closed = new PaginatedResult<>(List.of(), 0, 500, 0);
        when(this.projectManagementManager.getWorkItems(eq("openproject"), eq(0), eq(500),
            argThat(filters("status c , " + period)), argThat(newestFirst()))).thenReturn(closed);
        List<Block> chart = List.of(new WordBlock("chart"));
        when(this.lineDisplayer.execute(List.of(open, closed), WorkItem.KEY_CREATION_DATE, List.of("Open", "Closed"),
            this.context, new HashMap<>(Map.of("period", "DAILY", "metric", "accumulate")))).thenReturn(chart);

        assertEquals(chart, this.macro.execute(parameters, "", this.context));
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
