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
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.job.JobException;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

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

    private static final String CLASS_ATTRIBUTE = "class";

    private static final String TRUNCATED_TRANSLATION_KEY = "projectmanagement.macro.chart.truncated";

    /**
     * The maximum number of work items of each dataset that a chart retrieves when it isn't given a limit and the
     * client can't count them itself. A request returning 100 OpenProject work packages with all their properties can
     * take about 2 seconds, so retrieving more would make the chart too slow to display.
     */
    private static final int MAX_WORK_ITEMS = 1000;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private ProjectManagementAsyncExecutor asyncExecutor;

    @Inject
    @Named("jsrx")
    private SkinExtension jsrx;

    @Inject
    private PresetFiltersCombiner presetFiltersCombiner;

    @Inject
    private ContextualLocalizationManager localizationManager;

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
            if (StringUtils.isNotEmpty(parameters.getPresetId())) {
                parameters.setFilters(
                    this.presetFiltersCombiner.combine(parameters.getPresetId(), parameters.getFilters()));
            }
            List<List<LiveDataQuery.Filter>> filters = getFiltersList(parameters.getFilters());
            updateFilters(filters, parameters);
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

            List<Block> result = asyncExecutor.execute(new AbstractMacro<ProjectManagementAsyncMacroParams>("")
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
                        prepareContext(parameters);
                        return displayChart(chartTypeDisplayer, filters, parameters, labels, context,
                            typeDisplayerParams);
                    } catch (WorkItemException e) {
                        throw new MacroExecutionException("Failed to retrieve the work packages.", e);
                    }
                }
            }, parameters, content, context, null, true);
            // The chart is drawn by the Chart.js application, on page load and on the xwiki:dom:updated event. When
            // the chart is rendered asynchronously, the event sent by XWiki targets the placeholder replaced by the
            // chart, not the chart, so the wrapper and the script make sure the chart is drawn. Remove them once the
            // minimal supported XWiki version includes the fix of XWIKI-25675 (The xwiki:dom:updated event sent after
            // an asynchronous rendering targets the removed placeholder instead of the rendered content).
            this.jsrx.use("js/projectmanagement/chartAsyncFix.js");
            return Collections.singletonList(
                new GroupBlock(result, Collections.singletonMap(CLASS_ATTRIBUTE, "proj-manag-chart-wrapper")));
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

    protected void updateFilters(List<List<LiveDataQuery.Filter>> filters, T parameters) throws MacroExecutionException
    {
        // The extending class can update the filters if needed.
    }

    protected void prepareContext(T parameters)
    {
        // The extending class can fill things here.
    }

    /**
     * @param parameters the macro parameters.
     * @return how the work items of each dataset should be sorted. This matters when there are more work items than
     *     the limit, since it decides which ones are left out.
     */
    protected List<LiveDataQuery.SortEntry> getSortEntries(T parameters)
    {
        return Collections.emptyList();
    }

    private List<Block> displayChart(ChartTypeDisplayer chartTypeDisplayer, List<List<LiveDataQuery.Filter>> filters,
        T parameters, List<String> labels, MacroTransformationContext context, Object typeDisplayerParams)
        throws WorkItemException, MacroExecutionException
    {
        Optional<List<Map<String, Long>>> counts = countDatasets(filters, parameters);
        if (counts.isPresent()) {
            return chartTypeDisplayer.executeCounts(counts.get(), parameters.getProperty(), labels, context,
                typeDisplayerParams);
        }
        List<PaginatedResult<WorkItem>> datasets = getDatasets(filters, parameters);
        List<Block> chart = new ArrayList<>(chartTypeDisplayer.execute(datasets, parameters.getProperty(), labels,
            context, typeDisplayerParams));
        chart.addAll(getTruncationWarning(datasets, parameters));
        return chart;
    }

    /**
     * Counting the work items by value of the charted property is a single fast request whatever their number, while
     * retrieving them takes several requests when there are many. It's only possible when all the matching work items
     * are charted (no limit nor offset), and when the client can count them by this property (e.g. not by a date,
     * whose values are grouped by period).
     *
     * @return the number of work items of each dataset by value of the charted property, empty when the client can't
     *     count them
     */
    private Optional<List<Map<String, Long>>> countDatasets(List<List<LiveDataQuery.Filter>> filters, T parameters)
        throws WorkItemException
    {
        if (parameters.getLimit() != null || (parameters.getOffset() != null && parameters.getOffset() > 0)) {
            return Optional.empty();
        }
        List<Map<String, Long>> counts = new ArrayList<>();
        for (List<LiveDataQuery.Filter> filter : getDatasetsFilters(filters)) {
            Optional<Map<String, Long>> datasetCounts =
                projectManagementManager.countWorkItems(parameters.getClient(), filter, parameters.getProperty());
            if (datasetCounts.isEmpty()) {
                return Optional.empty();
            }
            counts.add(datasetCounts.get());
        }
        return Optional.of(counts);
    }

    private List<PaginatedResult<WorkItem>> getDatasets(List<List<LiveDataQuery.Filter>> filters, T parameters)
        throws WorkItemException
    {
        List<PaginatedResult<WorkItem>> workItemsList = new ArrayList<>();
        long offset = parameters.getOffset() == null ? 0 : parameters.getOffset();
        for (List<LiveDataQuery.Filter> filter : getDatasetsFilters(filters)) {
            if (parameters.getLimit() != null || offset > 0) {
                int limit = parameters.getLimit() != null ? parameters.getLimit() : MAX_WORK_ITEMS;
                workItemsList.add(projectManagementManager.getWorkItems(parameters.getClient(),
                    Math.toIntExact(offset), limit, filter, getSortEntries(parameters)));
            } else {
                workItemsList.add(projectManagementManager.getAllWorkItems(parameters.getClient(), filter,
                    getSortEntries(parameters), MAX_WORK_ITEMS));
            }
        }
        return workItemsList;
    }

    private static List<List<LiveDataQuery.Filter>> getDatasetsFilters(List<List<LiveDataQuery.Filter>> filters)
    {
        // Without filters, the chart has a single dataset holding all the work items.
        return filters.isEmpty() ? List.of(Collections.emptyList()) : filters;
    }

    /**
     * @return a warning telling that the chart doesn't count all the matching work items, when it reached the maximum
     *     number of work items that it can retrieve without being given a limit, so that it isn't mistaken for the
     *     complete picture
     */
    private List<Block> getTruncationWarning(List<PaginatedResult<WorkItem>> datasets, T parameters)
    {
        if (parameters.getLimit() != null) {
            return Collections.emptyList();
        }
        long total = 0;
        boolean truncated = false;
        for (PaginatedResult<WorkItem> dataset : datasets) {
            int count = dataset.getItems() == null ? 0 : dataset.getItems().size();
            int datasetTotal = dataset.getTotalItems() == null ? count : dataset.getTotalItems();
            truncated |= datasetTotal > count;
            total = Math.max(total, datasetTotal);
        }
        if (!truncated) {
            return Collections.emptyList();
        }
        Translation translation = this.localizationManager.getTranslation(TRUNCATED_TRANSLATION_KEY);
        Block message =
            translation != null ? translation.render(MAX_WORK_ITEMS, total) : new WordBlock(TRUNCATED_TRANSLATION_KEY);
        return Collections.singletonList(new GroupBlock(Collections.singletonList(new ParagraphBlock(
            Collections.singletonList(message))), Collections.singletonMap(CLASS_ATTRIBUTE, "box warningmessage")));
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
