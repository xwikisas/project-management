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
package com.xwiki.projectmanagement.openproject.filterconverter.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.openproject.filterconverter.FilterHandler;

/**
 * Open Project Filter Handler.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultFilterHandler implements FilterHandler
{
    private static final String OPERATOR = "operator";

    private static final String VALUES = "values";

    @Override
    public String convertFilters(List<LiveDataQuery.Filter> filters)
    {
        List<Map<String, Object>> convertedFilters = new ArrayList<>();

        for (LiveDataQuery.Filter filter : filters) {

            List<LiveDataQuery.Constraint> validConstraints = filter
                .getConstraints()
                .stream()
                .filter(constraint -> {
                    String constraintValue = (String) constraint.getValue();
                    return constraintValue != null && !constraintValue.isEmpty();
                }).collect(Collectors.toList());

            if (validConstraints.isEmpty()) {
                continue;
            }

            Map<String, Object> filterProperties = new HashMap<>();

            filterProperties.put(OPERATOR, validConstraints.get(0).getOperator());

            List<String> values = validConstraints.stream().map(constraint -> (String) constraint.getValue())
                .collect(Collectors.toList());

            filterProperties.put(VALUES, values);

            Map<String, Object> convertedFilter = new HashMap<>();
            convertedFilter.put(FilterConverter.convertKey(filter.getProperty()), filterProperties);
            convertedFilters.add(convertedFilter);
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(convertedFilters);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
