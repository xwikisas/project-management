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

/**
 * Tool for css escaping.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
public final class IdGenerator
{
    private IdGenerator()
    {

    }

    /**
     * Generate an identifier consisting of alphanumeric values for a given input. The function will always return the
     * same value for a given input.
     *
     * @param input the string for which an id will be generated.
     * @return the alphanumeric representation of the given input together with its hash value if it was modified.
     */
    public static String generate(String input)
    {
        if (input == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (!Character.isAlphabetic(c)) {
                continue;
            }
            escaped.append(c);
        }
        if (!escaped.toString().equals(input)) {
            escaped.append(input.hashCode());
        }
        return escaped.toString();
    }
}
