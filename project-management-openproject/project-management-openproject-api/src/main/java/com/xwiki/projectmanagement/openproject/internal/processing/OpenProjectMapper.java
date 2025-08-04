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
package com.xwiki.projectmanagement.openproject.internal.processing;

import java.util.HashMap;
import java.util.Map;

/**
 * Class for converting keys and operators.
 *
 * @version $Id$
 */
public final class OpenProjectMapper
{
    /**
     * The mapping between original and converted key.
     */
    public static final Map<String, String> KEY_MAPPING = new HashMap<>();

    /**
     * The mapping between original and converted operators.
     */
    public static final Map<String, String> OPERATOR_MAPPING = new HashMap<>();

    static {
        KEY_MAPPING.put("date", "start_date");
        KEY_MAPPING.put("identifier.value", "id");
        KEY_MAPPING.put("summary.value", "subject");
        KEY_MAPPING.put("assignees", "assigned_to");
        KEY_MAPPING.put("creator.value", "author");
        KEY_MAPPING.put("project.value", "project");
        KEY_MAPPING.put("progress", "percentageDone");
        OPERATOR_MAPPING.put("contains", "=");
        OPERATOR_MAPPING.put("between", "<>d");
        OPERATOR_MAPPING.put("empty", "!*");
    }

    private OpenProjectMapper()
    {

    }

    /**
     * A mapping of original filter keys to their corresponding converted keys.
     *
     * @param key original filter key
     * @return the converted key if the mapping exists. otherwise, returns the original key
     */
    public static String mapLivedataProperty(String key)
    {
        return KEY_MAPPING.getOrDefault(key, key);
    }

    /**
     * A mapping of original operator keys to their corresponding converted keys.
     *
     * @param operator original filter operator
     * @return the converted operator if the mapping exists. otherwise, returns the original operator
     */
    public static String mapLivedataOperator(String operator)
    {
        return OPERATOR_MAPPING.getOrDefault(operator, operator);
    }
}
