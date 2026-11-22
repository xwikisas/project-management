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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link OpenProjectConditionChecker} (see also {@code OpenProjectQueryTest}, which checks the
 * conditions of queries).
 *
 * @version $Id$
 */
class OpenProjectConditionCheckerTest
{
    private static final String LABELS = "Labels";

    private final OpenProjectConditionChecker checker = new OpenProjectConditionChecker(
        new OpenProjectWorkItemFields(List.of(
            field("customField1", LABELS, List.of("=", "&=", "!")),
            field("customField2", "Remarks", List.of()),
            field("status", "Status", List.of("o", "=", "c", "!", "*")),
            field("search", "Search", List.of("**")),
            field("startDate", "Start date", List.of("=d", "<>d")))),
        Set.of("customField1"));

    @Test
    void checkAcceptsTheSupportedConditions()
    {
        assertDoesNotThrow(() -> this.checker.check(List.of(
            // The operators converted into another one: a range of dates, a search.
            new LiveDataQuery.Filter("startDate", "between", "2026-11-01/2026-11-30"),
            new LiveDataQuery.Filter("search", "~", "wiki"),
            // A condition repeated as it is, on a field having several values.
            new LiveDataQuery.Filter(LABELS, "=", "a"), new LiveDataQuery.Filter(LABELS, "=", "a"),
            // The fields whose operators are unknown, and the unknown properties.
            new LiveDataQuery.Filter("Remarks", "~", "x"), new LiveDataQuery.Filter("colour", "~", "x"),
            // A range of percentages, or of numbers of an unknown property.
            new LiveDataQuery.Filter("progress", ">=", "5"), new LiveDataQuery.Filter("progress", "<=", "9"),
            new LiveDataQuery.Filter("status", "=", "1"), new LiveDataQuery.Filter("status", "!", "2"))));
    }

    @Test
    void checkRejectsAnUnsupportedOperator()
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("startDate", "t", ""));

        assertEquals("The condition [startDate = today()] isn't supported by OpenProject, which supports on "
            + "[startDate]: = a date, >= or <= a date.",
            assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(filters)).getMessage());
    }

    @Test
    void checkRejectsAnOperatorOutsideOfTheQuery()
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("status", "contains", "1"));

        assertEquals("The condition [status contains] isn't supported by OpenProject, which supports on [status]: "
            + "= or IN, != or NOT IN, = open, = closed, IS NOT EMPTY.",
            assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(filters)).getMessage());
    }

    @Test
    void checkStrictComparisonsOfNumbers()
    {
        OpenProjectConditionChecker numberChecker = new OpenProjectConditionChecker(new OpenProjectWorkItemFields(
            List.of(field("estimatedTime", "Work", "[1]Integer"), field("customField3", "Count", "[1]Integer"),
                field("customField4", "Ratio", "[1]Float"))), Set.of());
        String message = "The condition [%1$s %2$s] isn't supported by OpenProject, which only compares the values of "
            + "[%1$s] inclusively, and they aren't whole numbers: write %2$s= instead.";

        // The whole numbers are compared strictly by comparing the next or the previous one.
        assertDoesNotThrow(() -> numberChecker.check(List.of(new LiveDataQuery.Filter("Count", ">", "2"))));
        assertDoesNotThrow(() -> numberChecker.check(List.of(new LiveDataQuery.Filter("Count", "<", "9"))));
        // The hours and the decimal numbers can't.
        assertEquals(String.format(message, "estimatedTime", ">"), assertThrows(OpenProjectFilterValueException.class,
            () -> numberChecker.check(List.of(new LiveDataQuery.Filter("estimatedTime", ">", "2")))).getMessage());
        assertEquals(String.format(message, "Ratio", "<"), assertThrows(OpenProjectFilterValueException.class,
            () -> numberChecker.check(List.of(new LiveDataQuery.Filter("Ratio", "<", "2")))).getMessage());
        // Nor the fields without number.
        assertEquals("The condition [status >] isn't supported by OpenProject, which supports on [status]: = or IN, "
            + "!= or NOT IN, = open, = closed, IS NOT EMPTY.", assertThrows(OpenProjectFilterValueException.class,
            () -> this.checker.check(List.of(new LiveDataQuery.Filter("status", ">", "2")))).getMessage());
        // A range of whole numbers other than percentages can't be matched either.
        assertEquals("The conditions on [Count] can't be combined: OpenProject can't match a range of numbers, keep "
            + "only the lower (>=) or the upper (<=) bound.", assertThrows(OpenProjectFilterValueException.class,
            () -> numberChecker.check(List.of(new LiveDataQuery.Filter("Count", ">", "2"),
                new LiveDataQuery.Filter("Count", "<=", "9")))).getMessage());
    }

    @Test
    void checkRangesOfPercentages()
    {
        String noMatch = "The conditions on [progress] can't be true together: no work item matches them.";
        assertDoesNotThrow(() -> this.checker.check(List.of(new LiveDataQuery.Filter("progress", ">=", "20"),
            new LiveDataQuery.Filter("progress", "<=", "50"))));
        assertDoesNotThrow(() -> this.checker.check(List.of(new LiveDataQuery.Filter("progress", ">", "99"),
            new LiveDataQuery.Filter("progress", "<=", "300"))));
        // The values that aren't numbers are reported by OpenProject.
        assertDoesNotThrow(() -> this.checker.check(List.of(new LiveDataQuery.Filter("progress", ">=", "abc"),
            new LiveDataQuery.Filter("progress", "<=", "5"))));
        // The ranges holding no percentage from 0 to 100.
        assertEquals(noMatch, assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(List.of(
            new LiveDataQuery.Filter("progress", ">=", "150"), new LiveDataQuery.Filter("progress", "<=", "200"))))
            .getMessage());
        assertEquals(noMatch, assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(List.of(
            new LiveDataQuery.Filter("progress", ">", "99"), new LiveDataQuery.Filter("progress", "<", "100"))))
            .getMessage());
        assertEquals(noMatch, assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(List.of(
            new LiveDataQuery.Filter("progress", ">=", "-20"), new LiveDataQuery.Filter("progress", "<", "0"))))
            .getMessage());
    }

    @Test
    void checkHintsToQuoteTheNamesLookingLikeDays()
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter(LABELS, "t+", "3"));

        assertEquals("The condition [Labels = Nd] isn't supported by OpenProject, which supports on [Labels]: = or IN, "
            + "!= or NOT IN, ALL IN. A value looking like a date or a number of days (for example 3d) is a day: write "
            + "it between quotes to compare a name.",
            assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(filters)).getMessage());
    }

    @Test
    void checkRejectsTheConditionsRepeatedOnAMultiValueField()
    {
        List<LiveDataQuery.Filter> filters =
            List.of(new LiveDataQuery.Filter(LABELS, "&=", "a"), new LiveDataQuery.Filter(LABELS, "&=", "b"));

        assertEquals("The conditions on [Labels] can't be combined: OpenProject keeps a single condition on a field "
            + "having several values, for example write Labels ALL IN (a, b) instead of Labels = a AND Labels = b.",
            assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(filters)).getMessage());
    }

    @Test
    void checkRejectsARangeOfNumbersOtherThanPercentages()
    {
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("estimatedTime", ">=", "1"),
            new LiveDataQuery.Filter("estimatedTime", "<=", "5"));

        assertEquals("The conditions on [estimatedTime] can't be combined: OpenProject can't match a range of "
            + "numbers, keep only the lower (>=) or the upper (<=) bound.",
            assertThrows(OpenProjectFilterValueException.class, () -> this.checker.check(filters)).getMessage());
    }

    private static WorkPackageField field(String key, String name, List<String> operators)
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey(key);
        field.setName(name);
        field.setFilterKey(key);
        field.setFilterValueType("[]CustomOption");
        field.setFilterOperators(operators);
        return field;
    }

    private static WorkPackageField field(String key, String name, String filterValueType)
    {
        WorkPackageField field = field(key, name, List.of("=", "!", ">=", "<=", "*", "!*"));
        field.setFilterValueType(filterValueType);
        return field;
    }
}
