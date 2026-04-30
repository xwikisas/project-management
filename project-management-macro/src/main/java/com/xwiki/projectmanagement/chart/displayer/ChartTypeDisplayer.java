package com.xwiki.projectmanagement.chart.displayer;

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

import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.stability.Unstable;

import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * Defines the template for a chart type displayer. These displayers will be used by the chart macro.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Unstable
@Role
public interface ChartTypeDisplayer
{
    /**
     * @param workItems a list of work items that will be used to display the chart.
     * @param property the main property that will be used to generate the chart on.
     * @param labels the labels associated to each set of work items.
     * @param transformationContext the transformation context of the macro.
     * @param typeDisplayerParams the configuration class for the implementing displayer. It should have the same
     *     type as the {@link #getParameterTypeTemplate()} return value.
     * @return a list of blocks ready for rendering.
     * @throws MacroExecutionException in case of errors.
     */
    List<Block> execute(List<PaginatedResult<WorkItem>> workItems, String property, List<String> labels,
        MacroTransformationContext transformationContext, Object typeDisplayerParams) throws MacroExecutionException;

    /**
     * @return the class of the configuration used by the implementer.
     */
    Map<String, Object> getParameterTypeTemplate();

    /**
     * @return a map containing the attributes of {@link #getParameterTypeTemplate()} as keys and a list of possible
     *     values for said attribute as values.
     */
    Map<String, List<String>> getParameterTypeValues();
}
