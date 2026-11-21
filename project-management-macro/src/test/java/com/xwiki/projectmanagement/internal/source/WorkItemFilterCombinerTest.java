package com.xwiki.projectmanagement.internal.source;

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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link WorkItemFilterCombiner}.
 *
 * @version $Id$
 */
class WorkItemFilterCombinerTest
{
    static Stream<Arguments> combinableQueries()
    {
        // The conditions of the queries are combined once their names are resolved, as with the query source.
        return Stream.of(
            Arguments.of("status = 1 AND type = 2", "status: = 1; type: = 2"),
            Arguments.of("priority != 9 AND type = 1 AND priority NOT IN (7, 8)", "priority: ! 9, ! 7, ! 8; type: = 1"),
            Arguments.of("priority IN (9, 7) AND priority NOT IN (7)", "priority: = 9"),
            Arguments.of("status IN (1, 2, 3) AND status IN (3, 2, 4)", "status: = 2, = 3"),
            Arguments.of("Labels ALL IN (a, b) AND Labels ALL IN (c)", "Labels: &= a, &= b, &= c"),
            Arguments.of("summary ~ alpha AND summary ~ alpha", "summary: ~ alpha"),
            Arguments.of("status = open AND type = 1 AND status = open", "status: o ; type: = 1"),
            Arguments.of("dueDate >= 2026-11-01 AND dueDate <= 2026-11-30", "dueDate: between 2026-11-01/2026-11-30")
        );
    }

    static Stream<Arguments> uncombinableQueries()
    {
        String notCombinable = "The conditions on [%s] can't be combined: only =, !=, IN and NOT IN conditions can be "
            + "repeated on a property.";
        String noMatch = "The conditions on [%s] can't be true together: no work item matches them.";
        return Stream.of(
            Arguments.of("progress >= 50 AND progress <= 100", String.format(notCombinable, "progress")),
            Arguments.of("summary !~ alpha AND summary !~ beta", String.format(notCombinable, "summary")),
            Arguments.of("dueDate = today() AND dueDate >= 2026-11-01", String.format(notCombinable, "dueDate")),
            Arguments.of("status = open AND status = 1", String.format(notCombinable, "status")),
            Arguments.of("status = open AND status = closed", String.format(noMatch, "status")),
            Arguments.of("status = 1 AND status = 7", String.format(noMatch, "status")),
            Arguments.of("priority IN (9, 7) AND priority NOT IN (7, 9)", String.format(noMatch, "priority"))
        );
    }

    @ParameterizedTest
    @MethodSource("combinableQueries")
    void combinesTheFiltersOnTheSameProperty(String query, String expectedFilters) throws Exception
    {
        assertEquals(expectedFilters, describe(WorkItemFilterCombiner.combine(WorkItemQueryParser.parse(query))));
    }

    @ParameterizedTest
    @MethodSource("uncombinableQueries")
    void failsWhenTheFiltersOnTheSamePropertyCantBeCombined(String query, String expectedMessage) throws Exception
    {
        List<LiveDataQuery.Filter> filters = WorkItemQueryParser.parse(query);

        assertEquals(expectedMessage,
            assertThrows(WorkItemSourceException.class, () -> WorkItemFilterCombiner.combine(filters)).getMessage());
    }

    private static String describe(List<LiveDataQuery.Filter> filters)
    {
        return filters.stream().map(filter -> filter.getProperty() + ": " + filter.getConstraints().stream()
            .map(constraint -> constraint.getOperator() + ' ' + constraint.getValue())
            .collect(Collectors.joining(", "))).collect(Collectors.joining("; "));
    }
}
