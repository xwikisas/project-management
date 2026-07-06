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

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.job.JobException;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.exception.WorkItemException;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
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

    @Inject
    private ProjectManagementAsyncExecutor asyncExecutor;

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
        try {
            List<List<LiveDataQuery.Filter>> filters = getFiltersList(parameters.getFilters());
            ChartTypeDisplayer chartTypeDisplayer = componentManager.getInstance(ChartTypeDisplayer.class,
                parameters.getType());

            String typeParamsJSON = StringUtils.isEmpty(parameters.getTypeParams()) ? "{}" : parameters.getTypeParams();
            Object typeDisplayerParams =
                objectMapper.readValue(typeParamsJSON,
                    chartTypeDisplayer.getParameterTypeTemplate().getClass());

            String labelsJSON = StringUtils.isEmpty(parameters.getDatasetsLabels()) ? JSON_EMPTY_ARRAY
                : parameters.getDatasetsLabels();
            List<String> labels = objectMapper.readValue(labelsJSON, new TypeReference<List<String>>()
            {
            });

            return asyncExecutor.execute(new AbstractMacro<ProjectManagementAsyncMacroParams>("")
            {
                @Override
                public boolean supportsInlineMode()
                {
                    return false;
                }

                @Override
                public List<Block> execute(ProjectManagementAsyncMacroParams ignored, String content,
                    MacroTransformationContext context) throws MacroExecutionException
                {
                    try {
                        return chartTypeDisplayer.execute(getDatasets(filters, parameters), parameters.getProperty(),
                            labels, context, typeDisplayerParams);
                    } catch (WorkItemException e) {
                        throw new MacroExecutionException("Failed to retrieve the work packages.", e);
                    }
                }
            }, parameters, content, context);
        } catch (LiveDataException e) {
            throw new MacroExecutionException("Failed to parse the provided filters.", e);
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException("Failed to find the chart type.", e);
        } catch (JsonProcessingException e) {
            throw new MacroExecutionException("Failed to process the parameters of the chart type.");
        } catch (JobException | RenderingException e) {
            throw new MacroExecutionException("The execution of the displayer failed.", e);
        }
    }

    private List<PaginatedResult<WorkItem>> getDatasets(List<List<LiveDataQuery.Filter>> filters, T parameters)
        throws WorkItemException
    {
        List<PaginatedResult<WorkItem>> workItemsList = new ArrayList<>();

        for (List<LiveDataQuery.Filter> filter : filters) {
            workItemsList.add(projectManagementManager.getWorkItems(parameters.getClient(),
                Math.toIntExact(parameters.getOffset()),
                parameters.getLimit(), filter, Collections.emptyList()));
        }

        if (workItemsList.isEmpty()) {
            workItemsList.add(projectManagementManager.getWorkItems(parameters.getClient(),
                Math.toIntExact(parameters.getOffset()), parameters.getLimit(), Collections.emptyList(),
                Collections.emptyList()));
        }
        return workItemsList;
    }

    private List<List<LiveDataQuery.Filter>> getFiltersList(String filters)
        throws JsonProcessingException, LiveDataException
    {
        List<List<LiveDataQuery.Filter>> filtersList = new ArrayList<>();
        JsonNode smth = objectMapper.readTree(StringUtils.isEmpty(filters) ? JSON_EMPTY_ARRAY : filters);
        if (smth.isArray()) {
            for (JsonNode jsonNode : smth) {
                String sublist = objectMapper.writeValueAsString(jsonNode);
                filtersList.add(getFilters(sublist));
            }
        } else {
            filtersList.add(getFilters(filters));
        }

        return filtersList;
    }
}
