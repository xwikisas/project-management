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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the translations of {@code OpenProject.Code.Translations}.
 *
 * @version $Id$
 */
class TranslationsTest
{
    private static final Pattern PARAMETER = Pattern.compile("\\{\\d+[},]");

    @Test
    void parameterizedTranslationsEscapeTheirApostrophes() throws Exception
    {
        Properties translations = new Properties();
        try (InputStream page = getClass().getResourceAsStream("/OpenProject/Code/Translations.xml")) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(page);
            String content = document.getElementsByTagName("content").item(0).getTextContent();
            translations.load(new StringReader(content));
        }

        // The translations with parameters go through MessageFormat, which drops a single apostrophe and doesn't
        // replace the parameters that follow it: an apostrophe must be written ''.
        List<String> invalidKeys = new ArrayList<>();
        for (String key : translations.stringPropertyNames()) {
            String value = translations.getProperty(key);
            if (PARAMETER.matcher(value).find() && value.replace("''", "").contains("'")) {
                invalidKeys.add(key);
            }
        }
        assertEquals(List.of(), invalidKeys);
    }
}
