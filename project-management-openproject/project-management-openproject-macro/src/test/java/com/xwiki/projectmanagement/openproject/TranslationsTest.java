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
        "valueLabel", "summary", "summaryAll", "summaryIncomplete", "and", "or", "between", "setConditionUnsupported");

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
     * builder and of the source widget that name the work items are replaced by OpenProject ones.
     */
    @Test
    void macroEditorLabelsNameTheWorkPackages() throws Exception
    {
        // The generic labels are in the resources of the generic modules, on the class path too.
        Properties translations = new Properties();
        for (URL resource : Collections.list(
            getClass().getClassLoader().getResources("ApplicationResources.properties")))
        {
            try (InputStream input = resource.openStream()) {
                translations.load(input);
            }
        }
        Stream<String> filterBuilderKeys = FILTER_BUILDER_LABELS.stream().map(key -> "macro.filter." + key);
        Stream<String> sourceKeys = translations.stringPropertyNames().stream()
            .filter(key -> key.startsWith(GENERIC_PREFIX + "macro.source."))
            .map(key -> key.substring(GENERIC_PREFIX.length()));

        List<String> workItemLabels = Stream.concat(filterBuilderKeys, sourceKeys).sorted().filter(key -> {
            String label = translations.getProperty("openproject." + key,
                translations.getProperty(GENERIC_PREFIX + key, ""));
            return label.toLowerCase().contains("work item");
        }).collect(Collectors.toList());

        assertEquals(List.of(), workItemLabels);
    }
}
