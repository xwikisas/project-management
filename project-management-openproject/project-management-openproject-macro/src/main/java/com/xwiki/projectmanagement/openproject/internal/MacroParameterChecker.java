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
package com.xwiki.projectmanagement.openproject.internal;

import java.util.Map;

import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Checks the values of the numeric parameters of the OpenProject macros, so that an invalid value is reported as a
 * macro error instead of being silently replaced.
 *
 * @version $Id$
 * @since 1.4.1
 */
public final class MacroParameterChecker
{
    private static final String LIMIT_PARAMETER = "limit";

    private static final String COUNT_PARAMETER = "count";

    private MacroParameterChecker()
    {
    }

    /**
     * Check the maximum number of items displayed by a macro, given by its {@code limit} parameter, or by its
     * deprecated {@code count} parameter when {@code limit} isn't set.
     *
     * @param limit the value of the limit, once converted by XWiki
     * @param context the macro transformation context
     * @throws MacroExecutionException if the limit is lower than 1 or is not a whole number
     */
    public static void checkLimit(int limit, MacroTransformationContext context) throws MacroExecutionException
    {
        String limitParameter =
            getRawParameter(context, LIMIT_PARAMETER) == null && getRawParameter(context, COUNT_PARAMETER) != null
                ? COUNT_PARAMETER : LIMIT_PARAMETER;
        checkNumber(limitParameter, limit, 1, context);
    }

    /**
     * @param parameterName the name of the macro parameter
     * @param value the value of the parameter, once converted by XWiki
     * @param minimum the minimum value of the parameter
     * @param context the macro transformation context
     * @throws MacroExecutionException if the value is lower than the minimum, or if the parameter, as written in the
     *     macro call, is not a whole number
     */
    public static void checkNumber(String parameterName, int value, int minimum, MacroTransformationContext context)
        throws MacroExecutionException
    {
        String rawValue = getRawParameter(context, parameterName);
        if (value < minimum || (rawValue != null && !isInteger(rawValue))) {
            throw new MacroExecutionException(String.format("The [%s] parameter must be a whole number greater than or"
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
}
