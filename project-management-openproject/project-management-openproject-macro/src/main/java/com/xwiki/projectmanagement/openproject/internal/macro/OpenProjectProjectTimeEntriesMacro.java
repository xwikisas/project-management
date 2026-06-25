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

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.internal.utility.TableBuilder;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectTimeEntriesMacroParameters;
import com.xwiki.projectmanagement.openproject.model.TimeEntry;

/**
 * OpenProject macro that retrieves and displays time entries logged for a specific project.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Singleton
@Named("openproject-project-time-entries")
public class OpenProjectProjectTimeEntriesMacro
    extends AbstractOpenProjectDirectMacro<OpenProjectProjectTimeEntriesMacroParameters>
{
    private static final String PROJECT_FILTER = "[{\"project\":{\"operator\":\"=\",\"values\":[\"%s\"]}}]";

    private static final String PROJECT_AND_DATE_FILTER = "[{\"project\":{\"operator\":\"=\",\"values\":[\"%s\"]}}"
        + ",{\"created_at\":{\"operator\":\"<>d\",\"values\":[\"%s\",\"%s\"]}}]";

    private static final String DATE_FILTER = "[{\"created_at\":{\"operator\":\"<>d\",\"values\":[\"%s\",\"%s\"]}}]";

    private static final String EMPTY_FILTER = "";

    private static final String ACTIVITY_TABLE_HEADER = "ACTIVITY";

    private static final String LOGGED_FOR_TABLE_HEADER = "LOGGED FOR";

    private static final String COMMENT_TABLE_HEADER = "COMMENT";

    private static final String HOURS_TABLE_HEADER = "HOURS";

    private static final String EMPTY_CELL_PLACEHOLDER = "-";

    @Inject
    @Named("wiki")
    private ConfigurationSource wikiConfigSource;

    /**
     * Default constructor.
     */
    public OpenProjectProjectTimeEntriesMacro()
    {
        super("OpenProject - Project Time Entries",
            "Displays the time entries logged for a specific project on a configured OpenProject instance.",
            OpenProjectProjectTimeEntriesMacroParameters.class);
    }

    @Override
    protected List<Block> executeInternal(OpenProjectProjectTimeEntriesMacroParameters parameters, String content,
        MacroTransformationContext context, OpenProjectApiClient apiClient, String instance)
        throws MacroExecutionException
    {
        String filters = buildFilters(parameters.getProject(), parameters.getDays());

        List<TimeEntry> entries;
        try {
            entries = apiClient.getTimeEntries(null, parameters.getCount(), filters).getItems();
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException("Failed to retrieve time entries from OpenProject.", e);
        }

        String serverUrl = getOpenProjectConfiguration().getConnection(instance).getServerURL();
        boolean hasProject = isNotBlank(parameters.getProject());
        String linkUrl = hasProject
            ? String.format("%s/projects/%s", serverUrl, parameters.getProject())
            : String.format("%s/time_entries", serverUrl);
        String linkLabel = hasProject ? "View project dashboard" : "View time entries";

        return Collections.singletonList(
            new GroupBlock(buildContent(entries, linkLabel, linkUrl), Collections.emptyMap()));
    }

    private String buildFilters(String project, int days)
    {
        boolean hasProject = isNotBlank(project);
        if (days > 0) {
            String endDate = LocalDate.now().plusDays(1).toString();
            String startDate = LocalDate.now().minusDays(days).toString();
            return hasProject
                ? String.format(PROJECT_AND_DATE_FILTER, project, startDate, endDate)
                : String.format(DATE_FILTER, startDate, endDate);
        }
        return hasProject ? String.format(PROJECT_FILTER, project) : EMPTY_FILTER;
    }

    private static boolean isNotBlank(String value)
    {
        return value != null && !value.isBlank();
    }

    private List<Block> buildContent(List<TimeEntry> entries, String linkLabel, String linkUrl)
    {
        List<Block> blocks = new ArrayList<>();

        if (entries.isEmpty()) {
            blocks.add(new ParagraphBlock(List.of(new WordBlock("No time entries found.")), Collections.emptyMap()));
        } else {
            double totalHours = entries.stream().mapToDouble(e -> parseHours(e.getHours())).sum();

            blocks.add(new ParagraphBlock(List.of(new FormatBlock(List.of(new WordBlock("Total: ")), Format.BOLD),
                new WordBlock(formatTotalHours(totalHours)))));

            blocks.add(buildTable(entries));
        }

        blocks.add(
            new ParagraphBlock(Collections.singletonList(buildLinkBlock(linkLabel, linkUrl))));

        return blocks;
    }

    private Block buildTable(List<TimeEntry> entries)
    {
        TableBuilder tableBuilder = new TableBuilder();

        tableBuilder.newRow()
            .newCell(List.of(new FormatBlock(List.of(new WordBlock(ACTIVITY_TABLE_HEADER)), Format.BOLD)))
            .newCell(List.of(new FormatBlock(List.of(new WordBlock(LOGGED_FOR_TABLE_HEADER)), Format.BOLD)))
            .newCell(List.of(new FormatBlock(List.of(new WordBlock(COMMENT_TABLE_HEADER)), Format.BOLD)))
            .newCell(List.of(new FormatBlock(List.of(new WordBlock(HOURS_TABLE_HEADER)), Format.BOLD)));

        Map<Date, List<TimeEntry>> byDate = groupByDate(entries);
        String dateFormat = wikiConfigSource.getProperty("dateformat", "dd/MM/yyyy");
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);

        for (Map.Entry<Date, List<TimeEntry>> dateGroup : byDate.entrySet()) {
            Date date = dateGroup.getKey();
            List<TimeEntry> groupEntries = dateGroup.getValue();

            double dayTotal = groupEntries.stream().mapToDouble(e -> parseHours(e.getHours())).sum();
            String dateStr = sdf.format(date);

            tableBuilder.newRow()
                .newCell(List.of(new FormatBlock(List.of(new WordBlock(dateStr)), Format.BOLD)), Collections.emptyMap())
                .newCell(Collections.emptyList())
                .newCell(Collections.emptyList())
                .newCell(List.of(new FormatBlock(List.of(new WordBlock(formatHours(dayTotal))), Format.BOLD)),
                    Collections.emptyMap());

            for (TimeEntry entry : groupEntries) {
                addEntryRow(tableBuilder, entry);
            }
        }

        return tableBuilder.build();
    }

    private Map<Date, List<TimeEntry>> groupByDate(List<TimeEntry> entries)
    {
        Map<Date, List<TimeEntry>> byDate = new LinkedHashMap<>();
        for (TimeEntry entry : entries) {
            byDate.computeIfAbsent(entry.getSpentOn(), k -> new ArrayList<>()).add(entry);
        }
        return byDate;
    }

    private void addEntryRow(TableBuilder tableBuilder, TimeEntry entry)
    {
        tableBuilder
            .newRow()
            .newCell(getActivityContent(entry.getActivity()))
            .newCell(getLoggedForContent(entry))
            .newCell(List.of(new WordBlock(entry.getComment() != null ? entry.getComment() : "")))
            .newCell(List.of(new WordBlock(formatHours(parseHours(entry.getHours())))));
    }

    private List<Block> getActivityContent(Linkable activity)
    {
        if (activity != null && !activity.getValue().isBlank()) {
            return List.of(new WordBlock(activity.getValue()));
        }
        return List.of(new WordBlock(EMPTY_CELL_PLACEHOLDER));
    }

    private List<Block> getLoggedForContent(TimeEntry entry)
    {
        Linkable workPackage = entry.getWorkPackage();
        Linkable project = entry.getProjectLink();

        String wpLabel = workPackage.getValue();
        Block wpLink = buildLinkBlock(wpLabel, workPackage.getLocation());

        Block projectLink = buildLinkBlock(project.getValue(), project.getLocation());
        return List.of(projectLink, new SpaceBlock(), new SpecialSymbolBlock('/'), new SpaceBlock(), wpLink);
    }

    private Block buildLinkBlock(String label, String url)
    {
        return new LinkBlock(List.of(new WordBlock(label)), new ResourceReference(url, ResourceType.URL), true);
    }

    private double parseHours(String isoDuration)
    {
        if (isoDuration == null || isoDuration.isBlank()) {
            return 0.0;
        }
        try {
            return Duration.parse(isoDuration).toMinutes() / 60.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String formatHours(double hours)
    {
        return String.format("%.2f", hours);
    }

    private String formatTotalHours(double hours)
    {
        return formatHours(hours) + " h";
    }
}
