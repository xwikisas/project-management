package com.xwiki.projectmanagement.internal.chart;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Abstract chartjs displayer.
 *
 * @version $Id$
 * @since 1.1.0
 */
public abstract class AbstractChartJSDisplayer implements ChartTypeDisplayer
{
    /**
     * The type of the chart.
     */
    public static final String TYPE = "pie";

    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    @Named("xwiki/2.1")
    private BlockRenderer renderer;

    @Inject
    private MacroContentParser contentParser;

    @Override
    public List<Block> execute(List<PaginatedResult<WorkItem>> workItems, List<String> properties,
        MacroTransformationContext context, Object typeDisplayerParams) throws MacroExecutionException
    {

        ChartJSData chartJSData = new ChartJSData();

        for (String prop : properties) {

        }


        Map<String, Integer> dataSet = new HashMap<>();
        for (WorkItem item : workItems.getItems()) {
            if (StringUtils.isEmpty(item.getStatus())) {
                continue;
            }
            String propValue = item.getStringValue(properties);
            if (propValue.isEmpty()) {
                continue;
            }
            dataSet.put(propValue, dataSet.getOrDefault(propValue, 0) + 1);
        }
        String data = "";
        try {
            data = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("labels", dataSet.keySet(),
                    "datasets", Collections.singletonList(Collections.singletonMap("data", dataSet.values()))));

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

    /**
     * @return the chart type that will be passed to the ChartJS implementation.
     */
    public abstract String getChartType();

    @Override
    public Class<?> getParameterType()
    {
        return Map.class;
    }
}
