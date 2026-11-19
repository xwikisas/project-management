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
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.MacroBlock;
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
import com.xwiki.projectmanagement.internal.displayer.property.DatePropertyDisplayer;
import com.xwiki.projectmanagement.internal.utility.TableBuilder;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.FilterBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro;
import com.xwiki.projectmanagement.openproject.internal.LastDaysPeriod;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSpentTimeMacroParameters;
import com.xwiki.projectmanagement.openproject.model.TimeEntry;

/**
 * OpenProject macro that retrieves and displays time entries logged for a specific project.
 *
 * @version $Id$
 * @since 1.2
 */
@Component
@Singleton
@Named("openproject-spent-time")
public class OpenProjectSpentTimeMacro
    extends AbstractOpenProjectDirectMacro<OpenProjectSpentTimeMacroParameters>
{
    private static final String SPENT_ON_PROPERTY = "spent_on";

    private static final String BETWEEN_DATES_OPERATOR = "<>d";

    private static final String EMPTY_FILTER = "";

    private static final String TRANSLATION_PREFIX = "openproject.openproject-spent-time.";

    private static final String EMPTY_CELL_PLACEHOLDER = "-";

    private static final String LIMIT_PARAMETER = "limit";

    private static final String COUNT_PARAMETER = "count";

    private static final String DAYS_PARAMETER = "days";

    /**
     * The most recent days first, so that the limit drops the time entries of the oldest days, and the time entries of
     * the same day in a stable order.
     */
    private static final String SORT_BY = "[[\"spent_on\",\"desc\"],[\"id\",\"desc\"]]";

    @Inject
    @Named("wiki")
    private ConfigurationSource wikiConfigSource;

    @Inject
    private Provider<Clock> clockProvider;

    /**
     * Default constructor.
     */
    public OpenProjectSpentTimeMacro()
    {
        super("OpenProject - Spent Time",
            "Displays the time spent on a specific project on a configured OpenProject instance.",
            OpenProjectSpentTimeMacroParameters.class);
    }

    @Override
    protected void checkParameters(OpenProjectSpentTimeMacroParameters parameters,
        MacroTransformationContext context) throws MacroExecutionException
    {
        // The limit is taken from the deprecated count parameter when it isn't set.
        String limitParameter =
            getRawParameter(context, LIMIT_PARAMETER) == null && getRawParameter(context, COUNT_PARAMETER) != null
                ? COUNT_PARAMETER : LIMIT_PARAMETER;
        checkNumber(limitParameter, parameters.getLimit(), 1, context);
        checkNumber(DAYS_PARAMETER, parameters.getDays(), 0, context);
    }

    private void checkNumber(String parameterName, int value, int minimum, MacroTransformationContext context)
        throws MacroExecutionException
    {
        String rawValue = getRawParameter(context, parameterName);
        if (value < minimum || (rawValue != null && !isInteger(rawValue))) {
            throw new MacroExecutionException(String.format("The [%s] parameter must be a number greater than or"
                + " equal to %d, but is [%s].", parameterName, minimum, rawValue != null ? rawValue : value));
        }
    }

    /**
     * XWiki may convert the value of a numeric macro parameter that isn't a number to 0 before the macro is executed
     * (depending on the context class loader, it can also fail with "Invalid macro parameters"), so an invalid value
     * can only be told apart from 0 by its raw value.
     *
     * @param context the macro transformation context
     * @param name the name of a macro parameter, whatever its case
     * @return the value of the parameter as written in the macro call, or {@code null} when it isn't set
     */
    private static String getRawParameter(MacroTransformationContext context, String name)
    {
        MacroBlock macroBlock = context.getCurrentMacroBlock();
        if (macroBlock == null) {
            return null;
        }
        return macroBlock.getParameters().entrySet().stream().filter(entry -> name.equalsIgnoreCase(entry.getKey()))
            .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static boolean isInteger(String value)
    {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected List<Block> executeInternal(OpenProjectSpentTimeMacroParameters parameters, String content,
        MacroTransformationContext context, OpenProjectApiClient apiClient, String instance)
        throws MacroExecutionException
    {
        boolean hasProject = hasProject(parameters.getProject());
        String filters =
            buildFilters(hasProject ? getProjectId(parameters.getProject(), apiClient) : null, parameters.getDays());

        PaginatedResult<TimeEntry> result;
        try {
            result = apiClient.getTimeEntries(null, parameters.getLimit(), filters, SORT_BY);
        } catch (ProjectManagementException e) {
            throw new MacroExecutionException("Failed to retrieve time entries from OpenProject.", e);
        }
        List<TimeEntry> entries = result.getItems();
        if (entries.isEmpty()) {
            List<Block> warning = getEmptyAnonymousResultWarning(instance, context);
            if (!warning.isEmpty()) {
                return warning;
            }
        }

        String serverUrl = getOpenProjectConfiguration().getConnection(instance).getServerURL();
        String linkUrl = hasProject
            ? getProjectURL(serverUrl, parameters.getProject())
            : String.format("%s/time_entries", serverUrl);
        String linkLabel = translate(hasProject ? "openproject.openproject-spent-time.viewProjectDashboard"
            : "openproject.openproject-spent-time.viewTimeEntries");

        return Collections.singletonList(
            new GroupBlock(buildContent(entries, result.getTotalItems(), linkLabel, linkUrl),
                Collections.emptyMap()));
    }

    private String buildFilters(String project, int days)
    {
        boolean hasProject = project != null;
        if (!hasProject && days <= 0) {
            return EMPTY_FILTER;
        }
        FilterBuilder filterBuilder = FilterBuilder.get();
        if (hasProject) {
            filterBuilder.addFilter("project", FilterBuilder.Operator.EQUALS, project);
        }
        if (days > 0) {
            // The days on which the time was spent, not the days on which it was logged.
            LastDaysPeriod period = new LastDaysPeriod(days, this.clockProvider.get());
            filterBuilder.addFilter(SPENT_ON_PROPERTY, BETWEEN_DATES_OPERATOR,
                List.of(period.getFirstDay().toString(), period.getLastDay().toString()));
        }
        return filterBuilder.build();
    }

    private List<Block> buildContent(List<TimeEntry> entries, Integer totalEntries, String linkLabel,
        String linkUrl)
    {
        List<Block> blocks = new ArrayList<>();

        if (entries.isEmpty()) {
            blocks.add(new ParagraphBlock(List.of(new WordBlock(translate("openproject.openproject-spent-time.empty"))),
                Collections.emptyMap()));
        } else {
            if (totalEntries != null && totalEntries > entries.size()) {
                // The totals only count the displayed time entries.
                String partial = entries.size() == 1
                    ? translate(TRANSLATION_PREFIX + "partial.singular", totalEntries)
                    : translate(TRANSLATION_PREFIX + "partial", entries.size(), totalEntries);
                blocks.add(new ParagraphBlock(List.of(new WordBlock(partial))));
            }

            double totalHours = entries.stream().mapToDouble(e -> parseHours(e.getHours())).sum();

            Block totalLabel = new WordBlock(translate("openproject.openproject-spent-time.total") + " ");
            blocks.add(new ParagraphBlock(List.of(new FormatBlock(List.of(totalLabel), Format.BOLD),
                new WordBlock(formatTotalHours(totalHours)))));

            blocks.add(buildTable(entries));
        }

        blocks.add(
            new ParagraphBlock(Collections.singletonList(buildLinkBlock(linkLabel, linkUrl))));

        return blocks;
    }

    private Block buildHeader(String column)
    {
        return new FormatBlock(List.of(new WordBlock(translate(TRANSLATION_PREFIX + "header." + column))),
            Format.BOLD);
    }

    private Block buildTable(List<TimeEntry> entries)
    {
        TableBuilder tableBuilder = new TableBuilder();

        tableBuilder.newRow()
            .newCell(List.of(buildHeader("activity")))
            .newCell(List.of(buildHeader("loggedFor")))
            .newCell(List.of(buildHeader("comment")))
            .newCell(List.of(buildHeader("hours")));

        Map<Date, List<TimeEntry>> byDate = groupByDate(entries);
        // The time entries are logged on a day, at no meaningful time.
        String dateFormat = wikiConfigSource.getProperty("dateformat", "dd/MM/yyyy");
        SimpleDateFormat sdf = new SimpleDateFormat(DatePropertyDisplayer.toDayFormat(dateFormat));

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
        // The most recent day first, whatever the order in which the time entries are returned. The entries of the
        // same day keep their order.
        Map<Date, List<TimeEntry>> byDate = new TreeMap<>(Comparator.reverseOrder());
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

        Block projectLink = buildLinkBlock(project.getValue(), project.getLocation());
        // The time can be logged on the project itself, without work package.
        if (workPackage == null) {
            return List.of(projectLink);
        }
        Block wpLink = buildLinkBlock(workPackage.getValue(), workPackage.getLocation());
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
        return formatHours(hours) + " " + translate(TRANSLATION_PREFIX + "hoursUnit");
    }
}
