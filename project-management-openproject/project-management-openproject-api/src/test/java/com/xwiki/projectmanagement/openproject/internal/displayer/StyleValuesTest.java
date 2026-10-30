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
package com.xwiki.projectmanagement.openproject.internal.displayer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StyleValues}.
 *
 * @version $Id$
 */
class StyleValuesTest
{
    @ParameterizedTest
    @ValueSource(strings = { "#fff", "#FFFA", "#1a2B3c", "#1a2b3c80" })
    void isValidColorWhenHexadecimal(String color)
    {
        assertTrue(StyleValues.isValidColor(color));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "red", "#ff", "#fffff", "#1a2b3c4", "#gggggg", "rgb(0, 0, 0)", "#fff; color: red",
        "#fff}body{display:none", "#ffffff\n", " #ffffff" })
    void isValidColorWhenNotHexadecimal(String color)
    {
        assertFalse(StyleValues.isValidColor(color));
    }

    @Test
    void escapeClassNamePartKeepsSafeCharacters()
    {
        assertEquals("User-Story_2", StyleValues.escapeClassNamePart("User-Story_2"));
        assertEquals("Fonctionnalité", StyleValues.escapeClassNamePart("Fonctionnalité"));
    }

    @Test
    void escapeClassNamePartEscapesSelectorSyntax()
    {
        assertEquals("Bug\\7b \\7d body\\7b color\\3a red\\7d ",
            StyleValues.escapeClassNamePart("Bug{}body{color:red}"));
        assertEquals("a\\2e b\\23 c\\5c d\\2f e", StyleValues.escapeClassNamePart("a.b#c\\d/e"));
    }

    @Test
    void escapeClassNamePartEscapesControlCharacters()
    {
        assertEquals("a\\a b\\fffd c\\85 d", StyleValues.escapeClassNamePart("a\nb\0c\u0085d"));
    }
}
