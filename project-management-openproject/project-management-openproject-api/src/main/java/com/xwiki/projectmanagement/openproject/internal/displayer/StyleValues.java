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

import java.util.regex.Pattern;

/**
 * Helpers for the values received from OpenProject that are written in the generated style sheet.
 *
 * @version $Id$
 */
public final class StyleValues
{
    private static final Pattern HEX_COLOR_PATTERN =
        Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    private StyleValues()
    {
    }

    /**
     * @param color a color received from OpenProject
     * @return {@code true} if the color is a hexadecimal CSS color, the format used by OpenProject
     */
    public static boolean isValidColor(String color)
    {
        return color != null && HEX_COLOR_PATTERN.matcher(color).matches();
    }

    /**
     * Escape a value used in a CSS class selector, after the beginning of the class name.
     *
     * @param value the value to escape
     * @return the value, with the characters that are not letters, digits, hyphens or underscores escaped
     */
    public static String escapeClassNamePart(String value)
    {
        StringBuilder escaped = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (isKept(codePoint)) {
                escaped.appendCodePoint(codePoint);
            } else {
                // Use the replacement character for NULL, which can't be escaped.
                escaped.append('\\').append(Integer.toHexString(codePoint == 0 ? 0xFFFD : codePoint)).append(' ');
            }
        });
        return escaped.toString();
    }

    private static boolean isKept(int codePoint)
    {
        if (codePoint < 0x80) {
            return codePoint == '-' || codePoint == '_' || Character.isLetterOrDigit(codePoint);
        }
        return !Character.isISOControl(codePoint);
    }
}
