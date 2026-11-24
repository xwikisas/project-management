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
package com.xwiki.projectmanagement.openproject;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks the translations of the OpenProject macros.
 *
 * @version $Id$
 */
class TranslationsTest
{
    private static final Pattern PARAMETER = Pattern.compile("\\{\\d");

    private static final String GENERIC_PREFIX = "projectmanagement.";

    /**
     * The labels of the filter builder that a client can word its own way (see filter_builder.vm).
     */
    private static final List<String> FILTER_BUILDER_LABELS = List.of("anotherCondition", "operatorLabel",
        "valueLabel", "summary", "summaryAll", "summaryIncomplete", "summaryKept", "and", "or", "between",
        "propertiesNotLoaded", "setConditionUnsupported", "keptCondition");

    /**
     * What a series of a chart without filter selects (see projectmanagementfilters/edit.vm).
     */
    private static final String SERIES_NO_FILTER = "macro.chart.series.noFilter";

    /**
     * What a series of a chart with filters selects (see projectmanagementfilters/edit.vm).
     */
    private static final String SERIES_SUMMARY = "macro.chart.series.summary";

    private static final String OPENPROJECT_PREFIX = "openproject.";

    /**
     * The translations without parameter are rendered without being formatted: their apostrophes aren't doubled,
     * otherwise the users would see them doubled.
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "_fr", "_de" })
    void apostrophesOfTranslationsWithoutParameter(String locale) throws Exception
    {
        Properties translations = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/ApplicationResources" + locale + ".properties")) {
            assertNotNull(input);
            translations.load(input);
        }

        List<String> doubled = translations.stringPropertyNames().stream().sorted().filter(key -> {
            String value = translations.getProperty(key);
            return value.contains("''") && !PARAMETER.matcher(value).find();
        }).collect(Collectors.toList());

        assertEquals(List.of(), doubled);
    }

    /**
     * The macro editor names the work items as OpenProject does ("work packages"): the generic labels of the filter
     * builder, of the source widget and of the chart options that name the work items are replaced by OpenProject
     * ones.
     */
    @Test
    void macroEditorLabelsNameTheWorkPackages() throws Exception
    {
        Properties translations = loadAllTranslations();
        Stream<String> filterBuilderKeys = Stream.concat(
            FILTER_BUILDER_LABELS.stream().map(key -> "macro.filter." + key),
            Stream.of(SERIES_NO_FILTER, SERIES_SUMMARY));
        // The options of the chart types (see DefaultChartDisplayerResource) are worded by the client as well.
        Stream<String> sourceAndChartKeys = translations.stringPropertyNames().stream()
            .filter(key -> key.startsWith(GENERIC_PREFIX + "macro.source.")
                || key.startsWith(GENERIC_PREFIX + "chart."))
            .map(key -> key.substring(GENERIC_PREFIX.length()));

        List<String> workItemLabels = Stream.concat(filterBuilderKeys, sourceAndChartKeys).sorted().filter(key -> {
            String label = translations.getProperty(OPENPROJECT_PREFIX + key,
                translations.getProperty(GENERIC_PREFIX + key, ""));
            return label.toLowerCase().contains("work item");
        }).collect(Collectors.toList());

        assertEquals(List.of(), workItemLabels);
    }

    /**
     * The filter builders without filter say what the macro selects in the same words, e.g. those of the filter
     * presets as the macro, except the series of a chart, which count the charted work packages (see
     * chartSeriesSummaries).
     */
    @Test
    void noFilterSummaries() throws Exception
    {
        Properties translations = loadAllTranslations();

        assertEquals("No filter: all the work packages you can see.",
            translations.getProperty("openproject.macro.source.noFilter"));
        assertEquals(translations.getProperty("openproject.macro.source.noFilter"),
            translations.getProperty("openproject.macro.filter.summaryAll"));
    }

    /**
     * The summary of a series of a chart says that it selects among the charted work packages, with or without filter.
     */
    @Test
    void chartSeriesSummaries() throws Exception
    {
        Properties translations = loadAllTranslations();

        assertEquals("Selects the charted work packages where {0}.",
            translations.getProperty(OPENPROJECT_PREFIX + SERIES_SUMMARY));
        assertEquals("No filter: all the charted work packages.",
            translations.getProperty(OPENPROJECT_PREFIX + SERIES_NO_FILTER));
    }

    /**
     * @return the translations of this module and of the generic modules, whose resources are on the class path too
     */
    private Properties loadAllTranslations() throws Exception
    {
        Properties translations = new Properties();
        for (URL resource : Collections.list(
            getClass().getClassLoader().getResources("ApplicationResources.properties")))
        {
            try (InputStream input = resource.openStream()) {
                translations.load(input);
            }
        }
        return translations;
    }
}
