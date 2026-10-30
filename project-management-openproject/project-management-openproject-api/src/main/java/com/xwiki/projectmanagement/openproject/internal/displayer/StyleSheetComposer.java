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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;

/**
 * Compose the style sheet of an OpenProject instance from the colors of its statuses and types.
 *
 * @version $Id$
 */
public final class StyleSheetComposer
{
    private static final String BRACKET_START = " {\n";

    private static final String BRACKET_END = "\n}\n";

    private static final Logger LOGGER = LoggerFactory.getLogger(StyleSheetComposer.class);

    private StyleSheetComposer()
    {
    }

    /**
     * @param instanceId the CSS identifier of the OpenProject instance, used as suffix of the class names
     * @param statuses the statuses of the instance
     * @param types the types of the instance
     * @return the style sheet, empty when no status or type has a usable color
     */
    public static String compose(String instanceId, List<Status> statuses, List<Type> types)
    {
        StringBuilder stringBuilder = new StringBuilder();
        composeStatusStyles(instanceId, statuses, stringBuilder);
        composeTypeStyles(instanceId, types, stringBuilder);
        return stringBuilder.toString();
    }

    private static void composeTypeStyles(String instanceId, List<Type> types, StringBuilder stringBuilder)
    {
        for (Type type : types) {
            if (!StyleValues.isValidColor(type.getColor()) || type.getName() == null) {
                LOGGER.debug("Skipping the type [{}] with color [{}].", type.getName(), type.getColor());
                continue;
            }
            stringBuilder.append(".openproject-property-type-");
            stringBuilder.append(IdGenerator.generate(type.getName()));
            stringBuilder.append('-');
            stringBuilder.append(instanceId);
            stringBuilder.append(BRACKET_START);
            stringBuilder.append("\tcolor: ");
            stringBuilder.append(type.getColor());
            stringBuilder.append(';');
            stringBuilder.append(BRACKET_END);
            composeTypeColorStyle(type, stringBuilder);
        }
    }

    private static void composeTypeColorStyle(Type type, StringBuilder stringBuilder)
    {
        stringBuilder.append("a.fc-event.openproject-color-type-");
        // The calendar events get the type name without the white spaces as class name.
        stringBuilder.append(StyleValues.escapeClassNamePart(type.getName().replaceAll("\\s+", "")));
        stringBuilder.append(BRACKET_START);
        stringBuilder.append("\tbackground-color: ");
        stringBuilder.append(type.getColor());
        stringBuilder.append(';');
        stringBuilder.append(BRACKET_END);
    }

    private static void composeStatusStyles(String instanceId, List<Status> statuses, StringBuilder stringBuilder)
    {
        for (Status status : statuses) {
            if (!StyleValues.isValidColor(status.getColor()) || status.getName() == null) {
                LOGGER.debug("Skipping the status [{}] with color [{}].", status.getName(), status.getColor());
                continue;
            }
            stringBuilder.append(".openproject-property-status-");
            stringBuilder.append(IdGenerator.generate(status.getName()));
            stringBuilder.append('-');
            stringBuilder.append(instanceId);
            stringBuilder.append("::before");
            stringBuilder.append(BRACKET_START);
            stringBuilder.append("\tbackground: ");
            stringBuilder.append(status.getColor());
            stringBuilder.append(";");
            stringBuilder.append("\tborder: 1px solid ");
            stringBuilder.append("color-mix(in srgb, ");
            stringBuilder.append(status.getColor());
            stringBuilder.append(" 85%, black 15%);");
            stringBuilder.append(BRACKET_END);
        }
    }
}
