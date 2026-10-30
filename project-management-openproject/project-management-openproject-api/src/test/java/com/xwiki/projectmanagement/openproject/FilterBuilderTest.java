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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link FilterBuilder}.
 *
 * @version $Id$
 */
class FilterBuilderTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildWithoutFilters()
    {
        assertEquals("[]", FilterBuilder.get().build());
    }

    @Test
    void buildWithSingleValue()
    {
        assertEquals("[{\"name\":{\"operator\":\"~\",\"values\":[\"demo\"]}}]",
            FilterBuilder.get().addFilter("name", FilterBuilder.Operator.CONTAINS, "demo").build());
    }

    @Test
    void buildWithSeveralFilters()
    {
        String filters = new FilterBuilder()
            .addFilter("type", FilterBuilder.Operator.EQUALS, List.of(1, 2))
            .addFilter("subject", FilterBuilder.Operator.CONTAINS, "task")
            .addFilter("status", FilterBuilder.Operator.NOT_EQUAL, 3)
            .addFilter("version", FilterBuilder.Operator.NONE_OF, Arrays.asList("4", null))
            .addFilter("created_at", "<>d", List.of("2026-01-01", "2026-01-31"))
            .build();

        assertEquals("[{\"type\":{\"operator\":\"=\",\"values\":[\"1\",\"2\"]}},"
            + "{\"subject\":{\"operator\":\"~\",\"values\":[\"task\"]}},"
            + "{\"status\":{\"operator\":\"!=\",\"values\":[\"3\"]}},"
            + "{\"version\":{\"operator\":\"!\",\"values\":[\"4\",\"null\"]}},"
            + "{\"created_at\":{\"operator\":\"<>d\",\"values\":[\"2026-01-01\",\"2026-01-31\"]}}]", filters);
    }

    @Test
    void emptyValuesAreIgnored()
    {
        String filters = new FilterBuilder()
            .addFilter("name", FilterBuilder.Operator.CONTAINS, null)
            .addFilter("name", FilterBuilder.Operator.CONTAINS, "")
            .addFilter("type", FilterBuilder.Operator.EQUALS, List.of())
            .build();

        assertEquals("[]", filters);
    }

    @ParameterizedTest
    @ValueSource(strings = { "a\"b", "a\\b", "a\\", "\"]}},{\"id\":{\"operator\":\"!\",\"values\":[\"0",
        "a,b", "a]b", "été ☃ 😀", "line\nbreak" })
    void valuesAreKeptAsStrings(String value) throws Exception
    {
        String filters = new FilterBuilder()
            .addFilter("name", FilterBuilder.Operator.CONTAINS, value)
            .addFilter("type", FilterBuilder.Operator.EQUALS, List.of(value, "1"))
            .build();

        List<Map<String, Map<String, Object>>> parsed =
            MAPPER.readValue(filters, new TypeReference<List<Map<String, Map<String, Object>>>>()
            {
            });
        assertEquals(List.of(Map.of("name", Map.of("operator", "~", "values", List.of(value))),
            Map.of("type", Map.of("operator", "=", "values", List.of(value, "1")))), parsed);
    }
}
