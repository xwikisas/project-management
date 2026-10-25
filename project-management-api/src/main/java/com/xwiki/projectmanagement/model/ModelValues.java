package com.xwiki.projectmanagement.model;

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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts the values of the model maps to their typed form. The model classes are maps for Jackson, so a deserialized
 * work item or project holds plain JSON values: maps instead of {@link Linkable}s and numbers (timestamps) instead of
 * {@link Date}s.
 *
 * @version $Id$
 * @since 1.3.1
 */
final class ModelValues
{
    private ModelValues()
    {
    }

    static Linkable toLinkable(Object value)
    {
        if (value instanceof Map && !(value instanceof Linkable)) {
            Linkable linkable = new Linkable();
            ((Map<?, ?>) value).forEach((key, entryValue) -> linkable.put(String.valueOf(key), entryValue));
            return linkable;
        }
        return (Linkable) value;
    }

    @SuppressWarnings("unchecked")
    static List<Linkable> toLinkables(Object value)
    {
        if (value instanceof List && !((List<?>) value).stream().allMatch(Linkable.class::isInstance)) {
            return ((List<?>) value).stream().map(ModelValues::toLinkable).collect(Collectors.toList());
        }
        return (List<Linkable>) value;
    }

    static Date toDate(Object value)
    {
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        return (Date) value;
    }

    static Integer toInteger(Object value)
    {
        if (value instanceof Number && !(value instanceof Integer)) {
            return ((Number) value).intValue();
        }
        return (Integer) value;
    }
}
