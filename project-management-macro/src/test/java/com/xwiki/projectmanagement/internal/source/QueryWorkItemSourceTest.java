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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemName;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link QueryWorkItemSource}.
 *
 * @version $Id$
 */
@ComponentTest
class QueryWorkItemSourceTest
{
    private static final WorkItemSourceContext CONTEXT = new WorkItemSourceContext(null);

    @InjectMockComponents
    private QueryWorkItemSource source;

    static Stream<Arguments> validQueries()
    {
        // The filters are described as "property: operator value, ...; ...", the names being written <name>.
        return Stream.of(
            Arguments.of("project = xwiki", "project: = <xwiki>"),
            Arguments.of("project = 12", "project: = 12"),
            Arguments.of("targetVersion = \"17\"", "targetVersion: = <17>"),
            Arguments.of("targetVersion = 17.10", "targetVersion: = <17.10>"),
            Arguments.of("type != Bug", "type: ! <Bug>"),
            Arguments.of("\"Observed in versions\" = \"1.2 \\\"beta\\\"\"",
                "Observed in versions: = <1.2 \"beta\">"),
            Arguments.of("project = xwiki and status = closed AND type=1",
                "project: = <xwiki>; status: c ; type: = 1"),
            Arguments.of("status = Open", "status: o "),
            // A quoted value is always a name, unlike the keywords open and closed.
            Arguments.of("status != \"closed\"", "status: ! <closed>"),
            Arguments.of("status = \"Closed\"", "status: = <Closed>"),
            Arguments.of("status IN (\"Closed\", \"open\", New)", "status: = <Closed>, = <open>, = <New>"),
            Arguments.of("status != open", "status: c "),
            Arguments.of("type = closed", "type: = <closed>"),
            Arguments.of("status IN (1, Done, \"In progress\")", "status: = 1, = <Done>, = <In progress>"),
            Arguments.of("type IN (open, closed)", "type: = <open>, = <closed>"),
            Arguments.of("status not in (1,2)", "status: ! 1, ! 2"),
            Arguments.of("Labels ALL IN (security, ui)", "Labels: &= <security>, &= <ui>"),
            Arguments.of("summary ~ \"page saving\"", "summary: ~ page saving"),
            Arguments.of("summary !~ 12", "summary: !~ 12"),
            Arguments.of("progress >= 50 AND progress <= 80", "progress: >= 50; progress: <= 80"),
            // The current user, whatever the case, unlike the user named "me".
            Arguments.of("assignees = me", "assignees: = me"),
            Arguments.of("assignees = ME", "assignees: = me"),
            Arguments.of("assignees = \"me\"", "assignees: = <me>"),
            Arguments.of("assignees IN (Me, \"me\", meg)", "assignees: = me, = <me>, = <meg>"),
            Arguments.of("assignees IS EMPTY", "assignees: !* "),
            Arguments.of("assignees is not empty", "assignees: * "),
            Arguments.of("dueDate = today()", "dueDate: t "),
            Arguments.of("startDate = THISWEEK()", "startDate: w "),
            Arguments.of("dueDate = 2026-11-21", "dueDate: =d 2026-11-21"),
            Arguments.of("dueDate >= 2026-11-01 AND status = 1 AND dueDate <= 2026-11-30",
                "dueDate: between 2026-11-01/2026-11-30; status: = 1"),
            Arguments.of("dueDate > 2026-11-30 AND dueDate < 2027-01-01", "dueDate: between 2026-12-01/2026-12-31"),
            Arguments.of("dueDate >= 2026-11-01", "dueDate: after 2026-11-01"),
            Arguments.of("dueDate < 2026-11-01", "dueDate: before 2026-10-31"),
            Arguments.of("dueDate = \"2026-11-21\"", "dueDate: = <2026-11-21>"),
            Arguments.of("summary ~ 2026-11-21", "summary: ~ 2026-11-21"),
            // A query written by the filter builder of the macro editor.
            Arguments.of("project = 14 AND targetVersion IN (\"17\", 1.19) AND status NOT IN (1, 2) AND Labels ALL IN "
                + "(ui, \"a b\") AND summary ~ \"say \\\"hi\\\"\" AND assignees IS NOT EMPTY AND status = open AND "
                + "dueDate = today() AND startDate = 2026-11-21 AND updateDate >= 2026-11-01 AND updateDate <= "
                + "2026-11-30 AND creationDate <= 2026-11-30 AND \"Observed in\" = \"2026-11-21\" AND type = \"and\"",
                "project: = 14; targetVersion: = <17>, = <1.19>; status: ! 1, ! 2; Labels: &= <ui>, &= <a b>; "
                    + "summary: ~ say \"hi\"; assignees: * ; status: o ; dueDate: t ; startDate: =d 2026-11-21; "
                    + "updateDate: between 2026-11-01/2026-11-30; creationDate: before 2026-11-30; "
                    + "Observed in: = <2026-11-21>; type: = <and>")
        );
    }

    static Stream<Arguments> invalidQueries()
    {
        return Stream.of(
            Arguments.of("project", "Syntax error at position 8: expected an operator (=, !=, ~, !~, >=, <=, >, <, IN, "
                + "NOT IN, ALL IN, IS EMPTY or IS NOT EMPTY), but the query ends."),
            Arguments.of("project = ", "Syntax error at position 11: expected a value, but the query ends."),
            Arguments.of("project = xwiki status = 1",
                "Syntax error at position 17: expected AND or the end of the query, but found [status]."),
            Arguments.of("project = xwiki AND",
                "Syntax error at position 20: expected a property, but the query ends."),
            Arguments.of("project = xwiki OR project = 2",
                "OR (at position 17) is not supported yet: the query can only combine conditions with AND."),
            Arguments.of("project = 1 ORDER BY status",
                "ORDER BY (at position 13) is not supported yet: the query can only combine conditions with AND."),
            Arguments.of("(project = 1)",
                "Parentheses (at position 1) are not supported yet: the query can only combine conditions with AND."),
            Arguments.of("NOT project = 1",
                "NOT (at position 1) is not supported yet: the query can only combine conditions with AND."),
            Arguments.of("project = \"xwiki", "Syntax error at position 11: the quoted value isn't closed."),
            Arguments.of("project ! xwiki", "Syntax error at position 9: unexpected character [!]."),
            Arguments.of("status IN 1, 2", "Syntax error at position 11: expected '(', but found [1]."),
            Arguments.of("status IN (1 2)", "Syntax error at position 14: expected ',' or ')', but found [2]."),
            Arguments.of("status NOT 1", "Syntax error at position 12: expected IN, but found [1]."),
            Arguments.of("status IS NULL",
                "Syntax error at position 11: expected EMPTY or NOT EMPTY, but found [NULL]."),
            Arguments.of("progress > 50",
                "The operator [>] at position 10 can only compare dates (for example 2026-11-21): write >= instead."),
            Arguments.of("dueDate = tomorrow()",
                "Unknown function [tomorrow()] at position 11: the supported functions are today() and thisWeek()."),
            Arguments.of("dueDate = today(",
                "Syntax error at position 17: expected ')', but the query ends."),
            Arguments.of("dueDate != today()",
                "The function [today()] at position 12 can only be used with the operator [=]."),
            Arguments.of("dueDate IN (today())",
                "The function [today()] at position 13 can only be used with the operator [=]."),
            Arguments.of("dueDate = 2026-13-01", "Syntax error at position 11: invalid date [2026-13-01]."),
            Arguments.of("status NOT IN (1, Closed)",
                "[Closed] at position 19 can't be listed: write status = Closed or status != Closed instead."),
            Arguments.of("dueDate > 2026-11-21 AND dueDate < 2026-11-22",
                "The bounds of the dates of [dueDate] match no day."),
            Arguments.of("dueDate != 2026-11-01", "The operator [!=] can't compare dates (at position 12)."),
            Arguments.of("dueDate >= 2026-11-01 AND dueDate > 2026-11-02",
                "The property [dueDate] has several lower bounds (the second one at position 37)."),
            Arguments.of("dueDate <= 2026-11-01 AND dueDate <= 2026-11-02",
                "The property [dueDate] has several upper bounds (the second one at position 38).")
        );
    }

    @ParameterizedTest
    @MethodSource("validQueries")
    void readsTheFiltersOfTheQuery(String query, String expectedFilters) throws Exception
    {
        WorkItemSelection selection = this.source.read(query, CONTEXT);

        assertEquals(expectedFilters, describe(selection.getFilters()));
        assertTrue(selection.getIds().isEmpty());
        assertTrue(selection.getClientScope().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void failsOnAnInvalidQuery(String query, String expectedMessage)
    {
        WorkItemSourceException exception =
            assertThrows(WorkItemSourceException.class, () -> this.source.read(query, CONTEXT));
        assertEquals(expectedMessage, exception.getMessage());
        // The macro error box displays the message of the root cause.
        assertNull(exception.getCause());
    }

    @Test
    void failsOnAnEmptyQuery()
    {
        assertEquals("Syntax error at position 3: expected a property, but the query ends.",
            assertThrows(WorkItemSourceException.class, () -> this.source.read("  ", CONTEXT)).getMessage());
    }

    private static String describe(List<LiveDataQuery.Filter> filters)
    {
        return filters.stream().map(filter -> filter.getProperty() + ": " + filter.getConstraints().stream()
            .map(constraint -> constraint.getOperator() + ' ' + describe(constraint.getValue()))
            .collect(Collectors.joining(", "))).collect(Collectors.joining("; "));
    }

    private static String describe(Object value)
    {
        return value instanceof WorkItemName ? "<" + value + ">" : String.valueOf(value);
    }
}
