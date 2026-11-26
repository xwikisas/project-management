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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WorkItemFilterCombiner}.
 *
 * @version $Id$
 */
class WorkItemFilterCombinerTest
{
    private static final String NOT_COMBINABLE = "The conditions on [%s] can't be combined: only =, !=, IN and NOT IN "
        + "conditions, and a lower (>=, >) and an upper (<=, <) bound of a number, can be repeated on a property.";

    private static final String NO_MATCH = "The conditions on [%s] can't be true together: no work item matches them.";

    /**
     * A condition of each kind, on the property p.
     */
    private static final List<String> CONDITIONS = List.of("p = 1", "p != 2", "p ALL IN (3)", "p ~ x", "p !~ y",
        "p >= 5", "p <= 9", "p IS EMPTY", "p IS NOT EMPTY", "p = 2026-11-21", "p >= 2026-11-01", "p = today()",
        "p = thisWeek()", "p = 3d", "p = -3d");

    /**
     * The pairs of conditions of {@link #CONDITIONS} that can be combined (written in alphabetical order), and their
     * combination.
     */
    private static final Map<String, String> COMBINED_PAIRS =
        Map.of("p != 2 AND p = 1", "p: = 1", "p <= 9 AND p >= 5", "p: >= 5, <= 9");

    static Stream<Arguments> combinableQueries()
    {
        // The conditions of the queries are combined once their names are resolved, as with the query source.
        return Stream.of(
            Arguments.of("status = 1 AND type = 2", "status: = 1; type: = 2"),
            Arguments.of("priority != 9 AND type = 1 AND priority NOT IN (7, 8)", "priority: ! 9, ! 7, ! 8; type: = 1"),
            Arguments.of("priority IN (9, 7) AND priority NOT IN (7)", "priority: = 9"),
            Arguments.of("priority NOT IN (7) AND priority IN (9, 7)", "priority: = 9"),
            Arguments.of("priority = 9 AND priority != 7", "priority: = 9"),
            Arguments.of("priority != 7 AND priority != 7", "priority: ! 7"),
            Arguments.of("status IN (1, 2, 3) AND status IN (3, 2, 4)", "status: = 2, = 3"),
            Arguments.of("status IN (1, 2) AND status = 2 AND status IN (2, 3)", "status: = 2"),
            Arguments.of("type IN (Bug, Task) AND type != Task", "type: = <Bug>"),
            Arguments.of("Labels ALL IN (a, b) AND Labels ALL IN (c)", "Labels: &= <a>, &= <b>, &= <c>"),
            Arguments.of("Labels ALL IN (a) AND Labels ALL IN (a)", "Labels: &= <a>"),
            Arguments.of("summary ~ alpha AND summary ~ alpha", "summary: ~ alpha"),
            Arguments.of("assignees IS EMPTY AND assignees IS EMPTY", "assignees: !* "),
            Arguments.of("status = open AND type = 1 AND status = open", "status: o ; type: = 1"),
            Arguments.of("dueDate >= 2026-11-01 AND dueDate <= 2026-11-30", "dueDate: between 2026-11-01/2026-11-30"),
            Arguments.of("dueDate = today() AND dueDate = 0d", "dueDate: t "),
            // The bounds of a number.
            Arguments.of("progress >= 20 AND progress <= 50", "progress: >= 20, <= 50"),
            Arguments.of("progress <= 50 AND progress >= 20", "progress: >= 20, <= 50"),
            Arguments.of("progress > 19 AND progress < 51", "progress: > 19, < 51"),
            Arguments.of("progress > 19 AND progress <= 20", "progress: > 19, <= 20"),
            Arguments.of("estimatedTime > 1.5 AND estimatedTime < 1.6", "estimatedTime: > 1.5, < 1.6"),
            Arguments.of("progress >= 50 AND progress <= 50", "progress: >= 50, <= 50"),
            Arguments.of("progress >= 05 AND progress <= 5", "progress: >= 05, <= 5"),
            Arguments.of("estimatedTime >= 1.5 AND estimatedTime <= 2.25", "estimatedTime: >= 1.5, <= 2.25"),
            Arguments.of("progress >= 20 AND progress <= 50 AND progress >= 20", "progress: >= 20, <= 50"),
            Arguments.of("progress >= -10 AND progress <= -5", "progress: >= -10, <= -5")
        );
    }

    static Stream<Arguments> uncombinableQueries()
    {
        return Stream.of(
            Arguments.of("summary !~ alpha AND summary !~ beta", String.format(NOT_COMBINABLE, "summary")),
            Arguments.of("summary ~ alpha AND summary ~ beta", String.format(NOT_COMBINABLE, "summary")),
            Arguments.of("dueDate = today() AND dueDate >= 2026-11-01", String.format(NOT_COMBINABLE, "dueDate")),
            Arguments.of("dueDate = 2026-11-01 AND dueDate = 2026-11-02", String.format(NOT_COMBINABLE, "dueDate")),
            Arguments.of("status = open AND status = 1", String.format(NOT_COMBINABLE, "status")),
            Arguments.of("status = open AND status = closed", String.format(NO_MATCH, "status")),
            Arguments.of("status != open AND status = open", String.format(NO_MATCH, "status")),
            Arguments.of("status = 1 AND status = 7", String.format(NO_MATCH, "status")),
            Arguments.of("status IN (1, 2) AND status IN (3)", String.format(NO_MATCH, "status")),
            Arguments.of("priority IN (9, 7) AND priority NOT IN (7, 9)", String.format(NO_MATCH, "priority")),
            Arguments.of("priority = 7 AND priority != 7", String.format(NO_MATCH, "priority")),
            Arguments.of("Labels ALL IN (a) AND Labels = b", String.format(NOT_COMBINABLE, "Labels")),
            Arguments.of("Labels ALL IN (a) AND Labels != b", String.format(NOT_COMBINABLE, "Labels")),
            Arguments.of("assignees IS EMPTY AND assignees IS NOT EMPTY", String.format(NOT_COMBINABLE, "assignees")),
            Arguments.of("assignees IS EMPTY AND assignees = 1", String.format(NOT_COMBINABLE, "assignees")),
            // The bounds of a number.
            Arguments.of("progress >= 50 AND progress <= 20", String.format(NO_MATCH, "progress")),
            Arguments.of("progress > 51 AND progress < 51", String.format(NO_MATCH, "progress")),
            Arguments.of("progress >= 51 AND progress < 51", String.format(NO_MATCH, "progress")),
            Arguments.of("progress > 51 AND progress <= 51", String.format(NO_MATCH, "progress")),
            Arguments.of("progress > 20 AND progress >= 30", "The conditions on [progress] have several lower bounds."),
            Arguments.of("progress >= 20 AND progress >= 30", "The conditions on [progress] have several lower bounds."),
            Arguments.of("progress <= 20 AND progress <= 30", "The conditions on [progress] have several upper bounds."),
            Arguments.of("progress >= 20 AND progress <= 50 AND progress <= 60",
                "The conditions on [progress] have several upper bounds."),
            Arguments.of("progress >= abc AND progress <= 50", String.format(NOT_COMBINABLE, "progress")),
            Arguments.of("progress >= 20 AND progress = 30", String.format(NOT_COMBINABLE, "progress")),
            Arguments.of("progress >= 20 AND progress IS EMPTY", String.format(NOT_COMBINABLE, "progress"))
        );
    }

    static Stream<Arguments> pairsOfConditions()
    {
        return CONDITIONS.stream().flatMap(first -> CONDITIONS.stream().filter(second -> !second.equals(first))
            .map(second -> Arguments.of(first + " AND " + second)));
    }

    /**
     * Every pair of different conditions on a property is either combined into one filter, or rejected.
     */
    @ParameterizedTest
    @MethodSource("pairsOfConditions")
    void combinePairOfConditions(String query) throws Exception
    {
        List<LiveDataQuery.Filter> filters = WorkItemQueryParser.parse(query);
        String sortedQuery = Stream.of(query.split(" AND ")).sorted().collect(Collectors.joining(" AND "));
        String combined = COMBINED_PAIRS.get(sortedQuery);
        if (combined == null) {
            assertEquals(String.format(NOT_COMBINABLE, "p"),
                assertThrows(WorkItemSourceException.class, () -> WorkItemFilterCombiner.combine(filters)).getMessage());
        } else {
            assertEquals(combined, describe(WorkItemFilterCombiner.combine(filters)));
        }
    }

    @Test
    void combineBoundsIntoARangeMatchingBoth() throws Exception
    {
        List<LiveDataQuery.Filter> filters =
            WorkItemFilterCombiner.combine(WorkItemQueryParser.parse("progress >= 20 AND progress <= 50"));

        // The constraints of the range are all matched (the default of a Live Data filter is to match any of them).
        assertTrue(filters.get(0).isMatchAll());
    }

    /**
     * A filter whose constraints are already combined (e.g. read from the filters parameter) is kept as it is.
     */
    @Test
    void combineFilterHoldingABoundTwice() throws Exception
    {
        LiveDataQuery.Filter filter = new LiveDataQuery.Filter("progress", ">=", "5");
        filter.getConstraints().add(new LiveDataQuery.Constraint("5", ">="));

        assertEquals("progress: >= 5",
            describe(WorkItemFilterCombiner.combine(List.of(new LiveDataQuery.Filter("progress", ">=", "5"), filter))));
    }

    /**
     * The operator of a Live Data constraint is optional (for example in the content of the filters source), and its
     * default depends on the tool, so the filters on a property, one having a constraint without operator, can't be
     * combined.
     */
    @Test
    void failsWhenAFilterOnARepeatedPropertyHasNoOperator() throws Exception
    {
        LiveDataQuery.Filter withoutOperator = new LiveDataQuery.Filter();
        withoutOperator.setProperty("status");
        withoutOperator.getConstraints().add(new LiveDataQuery.Constraint("1", null));
        List<LiveDataQuery.Filter> filters = List.of(withoutOperator, new LiveDataQuery.Filter("status", "!", "2"));

        assertEquals("The conditions on [status] can't be combined: each of them must have an operator.",
            assertThrows(WorkItemSourceException.class, () -> WorkItemFilterCombiner.combine(filters)).getMessage());
        // A filter without operator that isn't repeated is kept as it is.
        assertEquals("status: null 1", describe(WorkItemFilterCombiner.combine(List.of(withoutOperator))));
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
            .map(constraint -> constraint.getOperator() + ' ' + describe(constraint.getValue()))
            .collect(Collectors.joining(", "))).collect(Collectors.joining("; "));
    }

    private static String describe(Object value)
    {
        if (value instanceof WorkItemName) {
            // The keyword names (open, closed, locked written without quotes) are written {name}.
            return ((WorkItemName) value).isKeyword() ? "{" + value + "}" : "<" + value + ">";
        }
        return String.valueOf(value);
    }
}
