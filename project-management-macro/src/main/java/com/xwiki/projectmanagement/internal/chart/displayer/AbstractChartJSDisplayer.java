package com.xwiki.projectmanagement.internal.chart.displayer;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.chart.ChartPeriod;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.internal.chart.model.ChartJSData;
import com.xwiki.projectmanagement.internal.chart.model.ChartJSDataset;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

import static com.xwiki.projectmanagement.chart.ChartPeriod.DAILY;
import static com.xwiki.projectmanagement.chart.ChartPeriod.MONTHLY;

/**
 * Abstract chartjs displayer. Handles the general logic and conversion of parameters to chartjs specific params.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
public abstract class AbstractChartJSDisplayer implements ChartTypeDisplayer
{
    /**
     * The metric key.
     */
    public static final String PARAM_METRIC = "metric";

    /**
     * The count value of the metric parameter.
     */
    public static final String PARAM_METRIC_COUNT = "count";

    /**
     * The accumulate value of the metric parameter.
     */
    public static final String PARAM_METRIC_ACCUMULATE = "accumulate";

    /**
     * The key for the period parameter.
     */
    public static final String PARAM_PERIOD = "period";

    private static final String UNSET = "UNSET";

    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    @Named("xwiki/2.1")
    private BlockRenderer renderer;

    @Inject
    private MacroContentParser contentParser;

    @Override
    public List<Block> execute(List<PaginatedResult<WorkItem>> workItems, String property, List<String> labels,
        MacroTransformationContext context, Object typeDisplayerParams) throws MacroExecutionException
    {

        Set<String> chartJSLabels = new TreeSet<>();

        String metric = PARAM_METRIC_COUNT;
        if (typeDisplayerParams instanceof Map && ((Map<?, ?>) typeDisplayerParams).containsKey(PARAM_METRIC)) {
            metric = (String) ((Map<?, ?>) typeDisplayerParams).get(PARAM_METRIC);
        }
        ChartJSData chartJSData =
            initModel(workItems, property, (Map<String, String>) typeDisplayerParams, chartJSLabels);

        populateModel(workItems, property, labels, (Map<String, String>) typeDisplayerParams, chartJSData,
            chartJSLabels, metric);

        String data = "";
        try {
            data = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(chartJSData);

            WikiPrinter wikiPrinter = new DefaultWikiPrinter();
            renderer.render(new MacroBlock("chartjs", Map.of("type", getChartType()), data, false),
                wikiPrinter);
            String renderedChartJsMacro = wikiPrinter.toString();

            XDOM xdom = contentParser.parse(renderedChartJsMacro, context, true, false);

            return Collections.singletonList(new GroupBlock(xdom.getChildren()));
        } catch (JsonProcessingException e) {
            throw new MacroExecutionException("Failed to generate the data for the ChartJS macro.", e);
        }
    }

    private void populateModel(List<PaginatedResult<WorkItem>> workItems, String property, List<String> labels,
        Map<String, String> typeDisplayerParams, ChartJSData chartJSData, Set<String> chartJSLabels, String metric)
    {
        boolean anyUnset = false;
        Map<Integer, Integer> unsetValuesCount = new HashMap<>();
        for (int i = 0; i < workItems.size(); i++) {
            ChartJSDataset dataset = new ChartJSDataset();
            chartJSData.getDatasets().add(dataset);
            Map<String, Integer> dataSet = new TreeMap<>();
            chartJSLabels.forEach(label -> dataSet.put(label, 0));
            for (WorkItem item : workItems.get(i).getItems()) {
                String propValue = getChartJSLabel(item, property, typeDisplayerParams);
                if (UNSET.equals(propValue)) {
                    unsetValuesCount.put(i, unsetValuesCount.getOrDefault(i, 0) + 1);
                    anyUnset = true;
                } else {
                    dataSet.put(propValue, dataSet.getOrDefault(propValue, 0) + 1);
                }
            }
            chartJSData.getDatasets().get(i).setLabel(labels.size() > i ? labels.get(i) : "Unlabeled");
            applyMetric(metric, chartJSData, i, dataSet);
        }

        chartJSData.setLabels(new ArrayList<>(chartJSLabels));
        if (anyUnset) {
            chartJSData.getLabels().add(UNSET);
            for (Integer i : unsetValuesCount.keySet()) {
                chartJSData.getDatasets().get(i).getData().add(Long.valueOf(unsetValuesCount.get(i)));
            }
        }
    }

    @Nonnull
    private ChartJSData initModel(List<PaginatedResult<WorkItem>> workItems, String property,
        Map<String, String> typeDisplayerParams, Set<String> chartJSLabels)
    {
        ChartJSData chartJSData = new ChartJSData();
        chartJSData.setDatasets(new ArrayList<>());
        // Create the set of all possible labels across all datasets.
        for (int i = 0; i < workItems.size(); i++) {
            for (WorkItem item : workItems.get(i).getItems()) {
                String propValue = getChartJSLabel(item, property, typeDisplayerParams);
                if (UNSET.equals(propValue)) {
                    continue;
                }
                chartJSLabels.add(propValue);
            }
        }
        return chartJSData;
    }

    private String getChartJSLabel(WorkItem workItem, String property, Map<String, String> typeDisplayerParams)
        throws IllegalArgumentException
    {
        if (workItem.isDate(property) && workItem.get(property) instanceof Date) {
            Date date = (Date) workItem.get(property);

            SimpleDateFormat dateFormat = new SimpleDateFormat();
            String period = typeDisplayerParams.get(PARAM_PERIOD);
            period = StringUtils.isEmpty(period) ? MONTHLY.name() : period;
            switch (ChartPeriod.valueOf(period)) {
                case MONTHLY:
                    dateFormat.applyPattern("yyyy-MM");
                    break;

                case YEARLY:
                    dateFormat.applyPattern("yyyy");
                    break;

                case HOURLY:
                    dateFormat.applyPattern("yyyy-MM-dd hh");
                    break;
                case DAILY:
                default:
                    dateFormat.applyPattern("yyyy-MM-dd");
                    break;
            }

            return dateFormat.format(date);
        }

        String propValue = workItem.getStringValue(property);
        if (propValue.isEmpty()) {
            propValue = UNSET;
        }

        return propValue;
    }

    private static void applyMetric(String metric, ChartJSData chartJSData, int i, Map<String, Integer> dataSet)
    {
        if (metric.equals(PARAM_METRIC_ACCUMULATE)) {
            chartJSData.getDatasets().get(i).setData(new ArrayList<>());
            int currentVal = 0;
            for (Integer value : dataSet.values()) {
                currentVal += value;
                chartJSData.getDatasets().get(i).getData().add((long) currentVal);
            }
        } else {
            chartJSData.getDatasets().get(i)
                .setData(dataSet.values().stream().map(Integer::longValue).collect(Collectors.toList()));
        }
    }

    /**
     * @return the chart type that will be passed to the ChartJS implementation.
     */
    public abstract String getChartType();

    @Override
    public Map<String, Object> getParameterTypeTemplate()
    {
        return Map.of(PARAM_METRIC, PARAM_METRIC_COUNT, PARAM_PERIOD, DAILY.toString());
    }

    @Override
    public Map<String, List<String>> getParameterTypeValues()
    {
        return Map.of(
            PARAM_METRIC, Arrays.asList(PARAM_METRIC_COUNT, PARAM_METRIC_ACCUMULATE),
            PARAM_PERIOD, Arrays.stream(ChartPeriod.values()).map(Enum::name).collect(Collectors.toList())
        );
    }
}
