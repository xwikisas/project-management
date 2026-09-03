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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectChartMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectOpenClosedMacroParameters;

/**
 * Defines the open v closed macro that is a wrapper for the chart macro with predefined parameters.
 *
 * @version $Id$
 * @since 1.3.0
 */
@Component
@Named("openprojectopenclosed")
@Singleton
public class OpenProjectOpenClosedMacro extends OpenProjectChartMacro
{
    @Inject
    private ContextualLocalizationManager l10n;

    /**
     * Default constructor.
     */
    public OpenProjectOpenClosedMacro()
    {
        super("OpenProject Open v Closed work packages",
            "Display a chart comparing the open work packages against the closed work packages.",
            OpenProjectOpenClosedMacroParameters.class);
    }

    @Override
    public List<Block> execute(OpenProjectChartMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        try {
            parameters.setDatasetsLabels(objectMapper.writeValueAsString(Arrays.asList(
                l10n.getTranslationPlain("openproject.openprojectopenclosed.labels.open"),
                l10n.getTranslationPlain("openproject.openprojectopenclosed.labels.closed"))));
        } catch (JsonProcessingException e) {
            throw new MacroExecutionException("Failed to get the chart labels.", e);
        }
        return super.execute(parameters, content, context);
    }

    @Override
    protected void updateFilters(List<List<LiveDataQuery.Filter>> filters, OpenProjectChartMacroParameters parameters)
        throws MacroExecutionException
    {
        if (parameters instanceof OpenProjectOpenClosedMacroParameters) {
            int days = ((OpenProjectOpenClosedMacroParameters) parameters).getDays();
            String value = getBetweenValue(days);
            for (List<LiveDataQuery.Filter> filterSet : filters) {
                filterSet.add(new LiveDataQuery.Filter(WorkItem.KEY_CREATION_DATE, "between", value));
            }
        } else {
            throw new MacroExecutionException("Something went wrong");
        }
    }

    private String getBetweenValue(int days)
    {
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(Instant.now());

        OffsetDateTime end = LocalDate.now()
            .atTime(LocalTime.MAX)
            .atOffset(offset);

        OffsetDateTime start = end
            .minusDays(days)
            .with(LocalTime.MIDNIGHT);

        String result = start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            + "/"
            + end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return result;
    }
}
