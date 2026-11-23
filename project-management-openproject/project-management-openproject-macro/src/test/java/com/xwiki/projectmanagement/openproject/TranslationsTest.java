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
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
}
