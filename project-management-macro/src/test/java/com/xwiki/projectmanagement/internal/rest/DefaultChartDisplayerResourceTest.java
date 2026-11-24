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
package com.xwiki.projectmanagement.internal.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.chart.model.ChartDisplayerParameterInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultChartDisplayerResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultChartDisplayerResourceTest
{
    private static final String PERIOD = "period";

    private static final String LABEL = "label";

    private static final String METRIC = "metric";

    @InjectMockComponents
    private DefaultChartDisplayerResource resource;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @Test
    void getChartDisplayerInfoTranslatesTheValues() throws Exception
    {
        ChartTypeDisplayer displayer = this.componentManager.registerMockComponent(ChartTypeDisplayer.class, "line");
        when(displayer.getParameterTypeTemplate()).thenReturn(Map.of(PERIOD, "DAILY", LABEL, ""));
        when(displayer.getParameterTypeValues()).thenReturn(Map.of(PERIOD, List.of("HOURLY", "DAILY")));
        when(this.l10n.getTranslationPlain("projectmanagement.chart.line.parameter.period"))
            .thenReturn("Date Grouping");
        when(this.l10n.getTranslationPlain("projectmanagement.chart.parameter.period.value.HOURLY"))
            .thenReturn("Hourly");

        Map<String, ChartDisplayerParameterInfo> parameters = new LinkedHashMap<>();
        this.resource.getChartDisplayerInfo("line", null).forEach(info -> parameters.put(info.getId(), info));

        ChartDisplayerParameterInfo period = parameters.get(PERIOD);
        assertEquals("Date Grouping", period.getLabel());
        assertEquals(List.of("HOURLY", "DAILY"), period.getValues());
        // The values without translation are displayed as is, in the order of the values.
        Map<String, String> expectedLabels = new LinkedHashMap<>();
        expectedLabels.put("HOURLY", "Hourly");
        expectedLabels.put("DAILY", "DAILY");
        assertEquals(expectedLabels, period.getValueLabels());
        assertEquals(List.of("HOURLY", "DAILY"), List.copyOf(period.getValueLabels().keySet()));
        // A parameter that takes any value has no value labels.
        assertNull(parameters.get(LABEL).getValueLabels());
    }

    @Test
    void getChartDisplayerInfoUsesTheTextsOfTheClient() throws Exception
    {
        ChartTypeDisplayer displayer = this.componentManager.registerMockComponent(ChartTypeDisplayer.class, "pie");
        when(displayer.getParameterTypeTemplate()).thenReturn(Map.of(METRIC, "count"));
        when(displayer.getParameterTypeValues()).thenReturn(Map.of(METRIC, List.of("count")));
        when(this.l10n.getTranslationPlain("projectmanagement.chart.pie.parameter.metric")).thenReturn("Measure");
        when(this.l10n.getTranslationPlain("projectmanagement.chart.pie.parameter.metric.description"))
            .thenReturn("Default: the number of work items.");
        when(this.l10n.getTranslationPlain("openproject.chart.pie.parameter.metric.description"))
            .thenReturn("Default: the number of work packages.");
        when(this.l10n.getTranslationPlain("projectmanagement.chart.parameter.metric.value.count")).thenReturn("Count");

        ChartDisplayerParameterInfo metric = this.resource.getChartDisplayerInfo("pie", "openproject").get(0);

        // The client words the texts it has its own way, the other texts are the generic ones.
        assertEquals("Default: the number of work packages.", metric.getDescription());
        assertEquals("Measure", metric.getLabel());
        assertEquals(Map.of("count", "Count"), metric.getValueLabels());
        // Without client, the texts are the generic ones.
        assertEquals("Default: the number of work items.",
            this.resource.getChartDisplayerInfo("pie", "").get(0).getDescription());
    }
}
