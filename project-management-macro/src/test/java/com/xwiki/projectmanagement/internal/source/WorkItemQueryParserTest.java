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
package com.xwiki.projectmanagement.internal.source;

import java.util.List;
import java.util.Random;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link WorkItemQueryParser}: every operator with every kind of value, the syntax (case, spaces,
 * quotes, escaping) and every error message, with its position. The filters are described as
 * {@code property: operator value, ...; ...}, the names being written {@code <name>}.
 *
 * @version $Id$
 */
class WorkItemQueryParserTest
{
    private static final String NOT_A_DATE_COMPARISON = "The operator [!=] can't compare dates (at position 12).";

    private static final String RELATIVE_RANGE = "The days of [dueDate] relative to today (at position %d) can "
        + "only be compared as dueDate >= Nd, dueDate <= -Nd, dueDate = Nd, or between -Nd and 0d, or between 0d and Nd "
        + "(N >= 0).";

    static Stream<Arguments> equalsQueries()
    {
        return Stream.of(
            // An id, a name, a quoted name (even made of digits), the current user.
            Arguments.of("project = 12", "project: = 12"),
            Arguments.of("project = xwiki", "project: = <xwiki>"),
            Arguments.of("project = \"xwiki platform\"", "project: = <xwiki platform>"),
            Arguments.of("project = \"12\"", "project: = <12>"),
            Arguments.of("project = 017", "project: = 017"),
            Arguments.of("targetVersion = 17.10", "targetVersion: = <17.10>"),
            Arguments.of("progress = -5", "progress: = <-5>"),
            Arguments.of("assignees = me", "assignees: = me"),
            Arguments.of("assignees = mE", "assignees: = me"),
            Arguments.of("assignees = \"me\"", "assignees: = <me>"),
            // The open and closed keywords only for the status, and unquoted.
            Arguments.of("status = open", "status: o "),
            Arguments.of("status = CLOSED", "status: c "),
            Arguments.of("status = \"open\"", "status: = <open>"),
            Arguments.of("type = open", "type: = {open}"),
            Arguments.of("targetVersion = Locked AND type = closed", "targetVersion: = {Locked}; type: = {closed}"),
            Arguments.of("status = locked", "status: = {locked}"),
            Arguments.of("targetVersion = \"open\"", "targetVersion: = <open>"),
            Arguments.of("\"status\" = closed", "status: c "),
            // A date, a day relative to today, a function.
            Arguments.of("dueDate = 2026-11-21", "dueDate: =d 2026-11-21"),
            Arguments.of("dueDate = 2024-02-29", "dueDate: =d 2024-02-29"),
            Arguments.of("dueDate = \"2026-11-21\"", "dueDate: = <2026-11-21>"),
            Arguments.of("dueDate = 0d", "dueDate: t "),
            Arguments.of("dueDate = 3d", "dueDate: t+ 3"),
            Arguments.of("dueDate = +3d", "dueDate: t+ 3"),
            Arguments.of("dueDate = -3d", "dueDate: t- 3"),
            Arguments.of("dueDate = 2w", "dueDate: t+ 14"),
            Arguments.of("dueDate = -1W", "dueDate: t- 7"),
            Arguments.of("dueDate = 99999d", "dueDate: t+ 99999"),
            // Too many digits to be a number of days: a name.
            Arguments.of("dueDate = 123456d", "dueDate: = <123456d>"),
            Arguments.of("dueDate = \"3d\"", "dueDate: = <3d>"),
            Arguments.of("dueDate = today()", "dueDate: t "),
            Arguments.of("dueDate = TODAY()", "dueDate: t "),
            Arguments.of("dueDate = today ( )", "dueDate: t "),
            Arguments.of("startDate = thisWeek()", "startDate: w "),
            Arguments.of("startDate = THISWEEK()", "startDate: w ")
        );
    }

    static Stream<Arguments> notEqualsQueries()
    {
        return Stream.of(
            Arguments.of("type != 1", "type: ! 1"),
            Arguments.of("type != Bug", "type: ! <Bug>"),
            Arguments.of("type != \"User story\"", "type: ! <User story>"),
            Arguments.of("type != \"1\"", "type: ! <1>"),
            Arguments.of("assignees != ME", "assignees: ! me"),
            Arguments.of("status != open", "status: c "),
            Arguments.of("status != closed", "status: o "),
            Arguments.of("status != \"closed\"", "status: ! <closed>")
        );
    }

    static Stream<Arguments> textQueries()
    {
        // The values compared with ~ and !~ are texts, whatever they look like.
        return Stream.of(
            Arguments.of("summary ~ page", "summary: ~ page"),
            Arguments.of("summary ~ \"page saving\"", "summary: ~ page saving"),
            Arguments.of("summary ~ 12", "summary: ~ 12"),
            Arguments.of("summary ~ me", "summary: ~ me"),
            Arguments.of("summary ~ 2026-11-21", "summary: ~ 2026-11-21"),
            Arguments.of("summary ~ 3d", "summary: ~ 3d"),
            Arguments.of("summary ~ open", "summary: ~ open"),
            Arguments.of("summary ~ \"\"", "summary: ~ "),
            Arguments.of("description !~ draft", "description: !~ draft"),
            Arguments.of("description !~ \"a \\\"b\\\"\"", "description: !~ a \"b\""),
            // Accents and non-ASCII characters, quoted or not.
            Arguments.of("summary ~ été", "summary: ~ été"),
            Arguments.of("summary ~ \"ça marche\"", "summary: ~ ça marche"),
            Arguments.of("summary ~ 日本語", "summary: ~ 日本語"),
            Arguments.of("summary ~ \"\uD83D\uDE00 smile\"", "summary: ~ \uD83D\uDE00 smile")
        );
    }

    static Stream<Arguments> comparisonQueries()
    {
        return Stream.of(
            // Numbers: > and < are kept, for the client to convert according to the type of the property.
            Arguments.of("progress >= 50", "progress: >= 50"),
            Arguments.of("progress <= 50", "progress: <= 50"),
            Arguments.of("estimatedTime >= 1.5", "estimatedTime: >= 1.5"),
            Arguments.of("progress >= \"50\"", "progress: >= 50"),
            Arguments.of("progress > 50", "progress: > 50"),
            Arguments.of("progress < 50", "progress: < 50"),
            Arguments.of("progress > -1", "progress: > -1"),
            Arguments.of("progress < 0", "progress: < 0"),
            Arguments.of("progress > 99999999999999999999", "progress: > 99999999999999999999"),
            Arguments.of("estimatedTime > 2.5", "estimatedTime: > 2.5"),
            // A quoted date is a text.
            Arguments.of("dueDate >= \"2026-11-21\"", "dueDate: >= 2026-11-21"),
            // Dates: the bounds of a range of days.
            Arguments.of("dueDate >= 2026-11-01", "dueDate: after 2026-11-01"),
            Arguments.of("dueDate > 2026-11-01", "dueDate: after 2026-11-02"),
            Arguments.of("dueDate <= 2026-11-01", "dueDate: before 2026-11-01"),
            Arguments.of("dueDate < 2026-11-01", "dueDate: before 2026-10-31"),
            Arguments.of("dueDate > 2026-12-31", "dueDate: after 2027-01-01"),
            Arguments.of("dueDate >= 2026-11-01 AND dueDate <= 2026-11-30", "dueDate: between 2026-11-01/2026-11-30"),
            Arguments.of("dueDate <= 2026-11-30 AND dueDate >= 2026-11-01", "dueDate: between 2026-11-01/2026-11-30"),
            Arguments.of("dueDate >= 2026-11-21 AND dueDate <= 2026-11-21", "dueDate: between 2026-11-21/2026-11-21"),
            Arguments.of("dueDate > 2026-11-20 AND dueDate < 2026-11-22", "dueDate: between 2026-11-21/2026-11-21"),
            Arguments.of("dueDate >= 2026-11-01 AND status = 1 AND startDate <= 2026-01-01 AND dueDate <= 2026-11-30",
                "dueDate: between 2026-11-01/2026-11-30; status: = 1; startDate: before 2026-01-01"),
            // Days relative to today.
            Arguments.of("dueDate >= 0d", "dueDate: >t+ 0"),
            Arguments.of("dueDate >= 3d", "dueDate: >t+ 3"),
            Arguments.of("dueDate > 3d", "dueDate: >t+ 4"),
            Arguments.of("dueDate >= today()", "dueDate: >t+ 0"),
            Arguments.of("dueDate > today()", "dueDate: >t+ 1"),
            Arguments.of("dueDate > -1d", "dueDate: >t+ 0"),
            Arguments.of("dueDate <= 0d", "dueDate: <t- 0"),
            Arguments.of("dueDate <= -3d", "dueDate: <t- 3"),
            Arguments.of("dueDate < -3d", "dueDate: <t- 4"),
            Arguments.of("dueDate < today()", "dueDate: <t- 1"),
            Arguments.of("dueDate <= Today()", "dueDate: <t- 0"),
            Arguments.of("dueDate < 1d", "dueDate: <t- 0"),
            Arguments.of("dueDate <= -2w", "dueDate: <t- 14"),
            Arguments.of("dueDate >= -3d AND dueDate <= 0d", "dueDate: >t- 3"),
            Arguments.of("dueDate >= -3d AND dueDate <= today()", "dueDate: >t- 3"),
            Arguments.of("dueDate > -4d AND dueDate < 1d", "dueDate: >t- 3"),
            Arguments.of("dueDate >= 0d AND dueDate <= 1w", "dueDate: <t+ 7"),
            Arguments.of("dueDate <= 1w AND dueDate >= today()", "dueDate: <t+ 7"),
            Arguments.of("dueDate >= 2d AND dueDate <= 2d", "dueDate: t+ 2"),
            Arguments.of("dueDate >= -2d AND dueDate <= -2d", "dueDate: t- 2"),
            Arguments.of("dueDate >= today() AND dueDate <= today()", "dueDate: t ")
        );
    }

    static Stream<Arguments> listQueries()
    {
        return Stream.of(
            Arguments.of("status IN (1)", "status: = 1"),
            Arguments.of("status IN (1, Done, \"In progress\", \"7\")", "status: = 1, = <Done>, = <In progress>, = <7>"),
            Arguments.of("status IN (\"open\", \"Closed\")", "status: = <open>, = <Closed>"),
            Arguments.of("type IN (open, closed)", "type: = {open}, = {closed}"),
            Arguments.of("assignees IN (me, ME, \"me\", meg)", "assignees: = me, = me, = <me>, = <meg>"),
            Arguments.of("dueDate IN (2026-11-21, 3d)", "dueDate: = <2026-11-21>, = <3d>"),
            Arguments.of("status not in (1,2)", "status: ! 1, ! 2"),
            Arguments.of("status NoT In (Done)", "status: ! <Done>"),
            Arguments.of("Labels ALL IN (security, \"user interface\")", "Labels: &= <security>, &= <user interface>"),
            Arguments.of("Labels all in (1)", "Labels: &= 1"),
            Arguments.of("project IN(1,2)", "project: = 1, = 2"),
            Arguments.of("project IN ( 1 , 2 )", "project: = 1, = 2")
        );
    }

    static Stream<Arguments> syntaxQueries()
    {
        return Stream.of(
            // Emptiness, whatever the case.
            Arguments.of("assignees IS EMPTY", "assignees: !* "),
            Arguments.of("assignees is empty", "assignees: !* "),
            Arguments.of("assignees IS NOT EMPTY", "assignees: * "),
            Arguments.of("assignees Is Not Empty", "assignees: * "),
            // The keywords ignore the case, the properties don't.
            Arguments.of("project = 1 and Status = 2 AnD type = 3", "project: = 1; Status: = 2; type: = 3"),
            // Spaces: none, several, tabs, new lines, non-breaking spaces (as written by a wiki editor).
            Arguments.of("status=1", "status: = 1"),
            Arguments.of("status!=1", "status: ! 1"),
            Arguments.of("progress>=5 AND progress<=9", "progress: >= 5; progress: <= 9"),
            Arguments.of("  status   =\t1\n AND\r\n type = 2  ", "status: = 1; type: = 2"),
            Arguments.of("status\u00A0=\u00A01\u00A0AND\u2007type = 2", "status: = 1; type: = 2"),
            Arguments.of("summary ~ \"a\u00A0b\"", "summary: ~ a\u00A0b"),
            // Quoted properties, which may hold spaces, quotes or keywords.
            Arguments.of("\"Observed in versions\" = \"1.2 \\\"beta\\\"\"", "Observed in versions: = <1.2 \"beta\">"),
            Arguments.of("\"AND\" = 1 AND \"a \\\"b\\\"\" = 2", "AND: = 1; a \"b\": = 2"),
            Arguments.of("Catégorie = Économie", "Catégorie: = <Économie>"),
            // Escaping: a backslash escapes the next character.
            Arguments.of("summary ~ \"back\\\\slash\"", "summary: ~ back\\slash"),
            Arguments.of("summary ~ \"\\a\"", "summary: ~ a"),
            Arguments.of("summary ~ \"ends with\\\\\"", "summary: ~ ends with\\"),
            Arguments.of("summary ~ \"(a, b) = c\"", "summary: ~ (a, b) = c"),
            // A repeated condition is kept as it is (the filters are combined afterwards).
            Arguments.of("type = 1 AND type = 1", "type: = 1; type: = 1")
        );
    }

    static Stream<Arguments> invalidQueries()
    {
        return Stream.of(
            // Missing or unexpected tokens.
            Arguments.of("", "Syntax error at position 1: expected a property, but the query ends."),
            Arguments.of(" \t\n", "Syntax error at position 4: expected a property, but the query ends."),
            Arguments.of("project", "Syntax error at position 8: expected an operator (=, !=, ~, !~, >=, <=, >, <, IN, "
                + "NOT IN, ALL IN, IS EMPTY or IS NOT EMPTY), but the query ends."),
            Arguments.of("project xwiki", "Syntax error at position 9: expected an operator (=, !=, ~, !~, >=, <=, >, "
                + "<, IN, NOT IN, ALL IN, IS EMPTY or IS NOT EMPTY), but found [xwiki]."),
            Arguments.of("project = ", "Syntax error at position 11: expected a value, but the query ends."),
            Arguments.of("project = ,", "Syntax error at position 11: expected a value, but found [,]."),
            Arguments.of("= 1", "Syntax error at position 1: expected a property, but found [=]."),
            Arguments.of("AND status = 1", "Syntax error at position 1: expected a property, but found [AND]."),
            Arguments.of("project = xwiki status = 1",
                "Syntax error at position 17: expected AND or the end of the query, but found [status]."),
            Arguments.of("status=1AND type=2", "Syntax error at position 13: expected AND or the end of the query, but "
                + "found [type]."),
            Arguments.of("status = 1 2", "Syntax error at position 12: expected AND or the end of the query, but found "
                + "[2]."),
            Arguments.of("project = xwiki AND", "Syntax error at position 20: expected a property, but the query ends."),
            Arguments.of("project = xwiki AND AND type = 1",
                "Syntax error at position 21: expected a property, but found [AND]."),
            Arguments.of("status == 1", "Syntax error at position 9: expected a value, but found [=]."),
            Arguments.of("status <> 1", "Syntax error at position 9: expected a value, but found [>]."),
            Arguments.of("status =! 1", "Syntax error at position 9: unexpected character [!]."),
            Arguments.of("project ! xwiki", "Syntax error at position 9: unexpected character [!]."),
            Arguments.of("project = x!", "Syntax error at position 12: unexpected character [!]."),
            // Unsupported features.
            Arguments.of("project = xwiki OR project = 2",
                "OR (at position 17) is not supported yet: the query can only combine conditions with AND."),
            Arguments.of("project = 1 ORDER BY status",
                "ORDER BY (at position 13) is not supported yet: the query can only combine conditions with AND."),
            Arguments.of("(project = 1)",
                "Parentheses (at position 1) are not supported yet: the query can only combine conditions with AND."),
            Arguments.of("NOT project = 1",
                "NOT (at position 1) is not supported yet: the query can only combine conditions with AND."),
            Arguments.of("type = 1 AND not project = 1",
                "NOT (at position 14) is not supported yet: the query can only combine conditions with AND."),
            // Quotes.
            Arguments.of("project = \"xwiki", "Syntax error at position 11: the quoted value isn't closed."),
            Arguments.of("\"", "Syntax error at position 1: the quoted value isn't closed."),
            Arguments.of("summary ~ \"escaped\\\"", "Syntax error at position 11: the quoted value isn't closed."),
            Arguments.of("\"Observed in = 1", "Syntax error at position 1: the quoted value isn't closed."),
            // Lists.
            Arguments.of("status IN 1, 2", "Syntax error at position 11: expected '(', but found [1]."),
            Arguments.of("status IN (1 2)", "Syntax error at position 14: expected ',' or ')', but found [2]."),
            Arguments.of("status IN ()", "Syntax error at position 12: expected a value, but found [)]."),
            Arguments.of("status IN (1,)", "Syntax error at position 14: expected a value, but found [)]."),
            Arguments.of("status IN (1", "Syntax error at position 13: expected ',' or ')', but the query ends."),
            Arguments.of("status NOT 1", "Syntax error at position 12: expected IN, but found [1]."),
            Arguments.of("Labels ALL (1)", "Syntax error at position 12: expected IN, but found [(]."),
            Arguments.of("status NOT IN (1, Closed)",
                "[Closed] at position 19 can't be listed: write status = Closed or status != Closed instead."),
            Arguments.of("status IN (open)",
                "[open] at position 12 can't be listed: write status = open or status != open instead."),
            Arguments.of("dueDate IN (today())", "The function [today()] at position 13 can't be listed."),
            // Emptiness.
            Arguments.of("status IS NULL", "Syntax error at position 11: expected EMPTY or NOT EMPTY, but found [NULL]."),
            Arguments.of("status IS NOT NULL", "Syntax error at position 15: expected EMPTY, but found [NULL]."),
            Arguments.of("status IS", "Syntax error at position 10: expected EMPTY or NOT EMPTY, but the query ends."),
            // Functions.
            Arguments.of("dueDate = tomorrow()",
                "Unknown function [tomorrow()] at position 11: the supported functions are today() and thisWeek()."),
            Arguments.of("dueDate = today(", "Syntax error at position 17: expected ')', but the query ends."),
            Arguments.of("dueDate = today(1)", "Syntax error at position 17: expected ')', but found [1]."),
            Arguments.of("dueDate != today()", "The function [today()] at position 12 can't be used with the operator "
                + "[!=]."),
            Arguments.of("summary ~ today()", "The function [today()] at position 11 can't be used with the operator "
                + "[~]."),
            Arguments.of("summary !~ thisWeek()", "The function [thisWeek()] at position 12 can't be used with the "
                + "operator [!~]."),
            Arguments.of("dueDate >= thisWeek()", "The function [thisWeek()] at position 12 can't be used with the "
                + "operator [>=]."),
            Arguments.of("dueDate != thisWeek()", "The function [thisWeek()] at position 12 can't be used with the "
                + "operator [!=]."),
            // Dates.
            Arguments.of("dueDate = 2026-13-01", "Syntax error at position 11: invalid date [2026-13-01]."),
            Arguments.of("dueDate >= 2026-02-30", "Syntax error at position 12: invalid date [2026-02-30]."),
            Arguments.of("dueDate != 2026-11-01", NOT_A_DATE_COMPARISON),
            Arguments.of("dueDate != -3d", NOT_A_DATE_COMPARISON),
            Arguments.of("dueDate > 2026-11-21 AND dueDate < 2026-11-22",
                "The bounds of the dates of [dueDate] match no day (at position 36)."),
            Arguments.of("dueDate >= 2026-11-01 AND dueDate > 2026-11-02",
                "The property [dueDate] has several lower bounds (the second one at position 37)."),
            Arguments.of("dueDate <= 2026-11-01 AND dueDate <= 2026-11-02",
                "The property [dueDate] has several upper bounds (the second one at position 38)."),
            Arguments.of("dueDate >= 2026-11-01 AND dueDate <= 3d",
                "The bounds of the dates of [dueDate] can't mix a date and a day relative to today (at position 38)."),
            Arguments.of("dueDate >= today() AND dueDate <= 2026-11-01",
                "The bounds of the dates of [dueDate] can't mix a date and a day relative to today (at position 35)."),
            Arguments.of("dueDate >= 1d AND dueDate <= -1d",
                "The bounds of the dates of [dueDate] match no day (at position 30)."),
            Arguments.of("dueDate >= 1d AND dueDate >= 2d",
                "The property [dueDate] has several lower bounds (the second one at position 30)."),
            // The ranges of days relative to today that the clients can't match.
            Arguments.of("dueDate >= -3d", String.format(RELATIVE_RANGE, 12)),
            Arguments.of("dueDate <= 3d", String.format(RELATIVE_RANGE, 12)),
            Arguments.of("dueDate >= -3d AND dueDate <= 3d", String.format(RELATIVE_RANGE, 31)),
            Arguments.of("dueDate >= 1d AND dueDate <= 3d", String.format(RELATIVE_RANGE, 30)),
            Arguments.of("dueDate >= -3d AND dueDate <= -1d", String.format(RELATIVE_RANGE, 31)),
            // Strict comparisons of what isn't a date or a number.
            Arguments.of("progress > 1.5.2", "The operator [>] at position 10 can only compare dates (for example "
                + "2026-11-21) and numbers: write >= instead."),
            Arguments.of("progress < \"5\"", "The operator [<] at position 10 can only compare dates (for example "
                + "2026-11-21) and numbers: write <= instead."),
            Arguments.of("summary > abc", "The operator [>] at position 9 can only compare dates (for example "
                + "2026-11-21) and numbers: write >= instead.")
        );
    }

    @ParameterizedTest
    @MethodSource({ "equalsQueries", "notEqualsQueries", "textQueries", "comparisonQueries", "listQueries",
        "syntaxQueries" })
    void parse(String query, String expectedFilters) throws Exception
    {
        assertEquals(expectedFilters, describe(WorkItemQueryParser.parse(query)));
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void parseInvalidQuery(String query, String expectedMessage)
    {
        WorkItemSourceException exception =
            assertThrows(WorkItemSourceException.class, () -> WorkItemQueryParser.parse(query));
        assertEquals(expectedMessage, exception.getMessage());
    }

    /**
     * Random sequences of the tokens of the grammar (and of other characters) are either parsed into one filter or more,
     * or rejected with a syntax error: nothing else is thrown.
     */
    @Test
    void parseRandomQueries()
    {
        String[] pieces = { "status", "dueDate", "\"Custom field\"", "=", "!=", "~", "!~", ">=", "<=", ">", "<", "IN",
            "NOT", "ALL", "IS", "EMPTY", "AND", "OR", "ORDER", "BY", "(", ")", ",", "\"", "\\", "\"a b\"", "1", "-1",
            "1.5", "me", "open", "closed", "today()", "thisWeek()", "today", "2026-11-21", "2026-02-30", "-3d", "2w",
            "0d", "99999d", "é", "!", "\u00A0", " ", "\t" };
        Random random = new Random(337);
        int parsed = 0;
        for (int i = 0; i < 20000; i++) {
            StringBuilder query = new StringBuilder();
            int length = random.nextInt(12);
            for (int j = 0; j < length; j++) {
                query.append(pieces[random.nextInt(pieces.length)]).append(random.nextBoolean() ? " " : "");
            }
            try {
                List<LiveDataQuery.Filter> filters = WorkItemQueryParser.parse(query.toString());
                assertFalse(filters.isEmpty(), query::toString);
                filters.forEach(filter -> assertFalse(filter.getConstraints().isEmpty(), query::toString));
                parsed++;
            } catch (WorkItemSourceException e) {
                assertNotNull(e.getMessage(), query::toString);
            }
        }
        // The random queries are not all rejected.
        assertFalse(parsed == 0);
    }

    static String describe(List<LiveDataQuery.Filter> filters)
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
