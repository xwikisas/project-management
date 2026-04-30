package com.xwiki.projectmanagement.internal.macro;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.macro.ProjectManagementChartMacroParameters;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Abstract chart macro meant to be implemented by project management implementers.
 *
 * @param <T> macro parameter type.
 * @version $Id$
 * @since 1.2.0-rc-1
 */
public abstract class AbstractProjectManagementChartMacro<T extends ProjectManagementChartMacroParameters>
    extends AbstractWorkItemsMacro<T>
{
    private static final String JSON_EMPTY_ARRAY = "[]";

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * constructor.
     *
     * @param name macro name.
     * @param description description name.
     * @param clazz class name.
     */
    public AbstractProjectManagementChartMacro(String name, String description, Class<?> clazz)
    {
        super(name, description, clazz);
    }

    /**
     * @param parameters the macro parameters in the form of a bean defined by the
     *     {@link org.xwiki.rendering.macro.Macro} implementation
     * @param content the content of the macro
     * @param context the context of the macros transformation process
     * @return the executed blocks.
     * @throws MacroExecutionException if bad.
     */
    @Override
    public List<Block> execute(T parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        List<List<LiveDataQuery.Filter>> filters = null;
        try {
            filters = getFiltersList(parameters.getFilters());
            ChartTypeDisplayer chartTypeDisplayer = componentManager.getInstance(ChartTypeDisplayer.class,
                parameters.getType());

            String typeParamsJSON = StringUtils.isEmpty(parameters.getTypeParams()) ? "{}" : parameters.getTypeParams();
            Object typeDisplayerParams =
                objectMapper.readValue(typeParamsJSON,
                    chartTypeDisplayer.getParameterTypeTemplate().getClass());

            List<PaginatedResult<WorkItem>> workItemsList = new ArrayList<>();

            for (List<LiveDataQuery.Filter> filter : filters) {
                workItemsList.add(projectManagementManager.getWorkItems(parameters.getClient(),
                    Math.toIntExact(parameters.getOffset()),
                    parameters.getLimit(), filter, Collections.emptyList()));
            }

            String labelsJSON = StringUtils.isEmpty(parameters.getDatasetsLabels()) ? JSON_EMPTY_ARRAY
                : parameters.getDatasetsLabels();
            List<String> labels = objectMapper.readValue(labelsJSON, new TypeReference<List<String>>()
            {
            });

            if (workItemsList.isEmpty()) {
                workItemsList.add(projectManagementManager.getWorkItems(parameters.getClient(),
                    Math.toIntExact(parameters.getOffset()), parameters.getLimit(), Collections.emptyList(),
                    Collections.emptyList()));
            }

            return chartTypeDisplayer.execute(workItemsList, parameters.getProperty(), labels, context,
                typeDisplayerParams);
        } catch (LiveDataException e) {
            throw new MacroExecutionException("Failed to parse the provided filters.", e);
        } catch (WorkItemException e) {
            throw new MacroExecutionException("Failed to retrieve the work packages.", e);
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException("Failed to find the chart type.", e);
        } catch (JsonProcessingException e) {
            throw new MacroExecutionException("Failed to process the parameters of the chart type.");
        }
    }

    private List<List<LiveDataQuery.Filter>> getFiltersList(String filters)
        throws JsonProcessingException, LiveDataException
    {
        List<List<LiveDataQuery.Filter>> filtersList = new ArrayList<>();
        JsonNode smth = objectMapper.readTree(StringUtils.isEmpty(filters) ? JSON_EMPTY_ARRAY : filters);
        for (JsonNode jsonNode : smth) {
            String sublist = objectMapper.writeValueAsString(jsonNode);
            filtersList.add(getFilters(sublist));
        }
        return filtersList;
    }
}
