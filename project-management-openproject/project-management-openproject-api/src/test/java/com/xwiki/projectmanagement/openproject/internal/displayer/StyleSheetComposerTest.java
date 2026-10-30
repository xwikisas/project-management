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

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link StyleSheetComposer}.
 *
 * @version $Id$
 */
class StyleSheetComposerTest
{
    @Test
    void compose()
    {
        String styleSheet = StyleSheetComposer.compose("main", Collections.singletonList(status("New", "#ff0000")),
            Collections.singletonList(type("User story", "#00ff00")));

        assertEquals(".openproject-property-status-New-main::before {\n"
            + "\tbackground: #ff0000;\tborder: 1px solid color-mix(in srgb, #ff0000 85%, black 15%);\n}\n"
            + ".openproject-property-type-" + IdGenerator.generate("User story") + "-main {\n\tcolor: #00ff00;\n}\n"
            + "a.fc-event.openproject-color-type-Userstory {\n\tbackground-color: #00ff00;\n}\n", styleSheet);
    }

    @Test
    void composeSkipsInvalidColorsAndMissingNames()
    {
        String styleSheet = StyleSheetComposer.compose("main",
            Arrays.asList(status("Closed", "red;}body{display:none"), status("Empty", ""), status(null, "#fff")),
            Arrays.asList(type("Epic", "#fff}body{display:none"), type("Task", null), type(null, "#fff")));

        assertEquals("", styleSheet);
    }

    @Test
    void composeEscapesTheTypeNameOfTheCalendarEvents()
    {
        String styleSheet = StyleSheetComposer.compose("main", Collections.emptyList(),
            Collections.singletonList(type("Bug{}body", "#000")));

        assertEquals(".openproject-property-type-" + IdGenerator.generate("Bug{}body") + "-main {\n\tcolor: #000;\n}\n"
            + "a.fc-event.openproject-color-type-Bug\\7b \\7d body {\n\tbackground-color: #000;\n}\n", styleSheet);
    }

    private static Status status(String name, String color)
    {
        Status status = new Status();
        status.setName(name);
        status.setColor(color);
        return status;
    }

    private static Type type(String name, String color)
    {
        Type type = new Type();
        type.setName(name);
        type.setColor(color);
        return type;
    }
}
