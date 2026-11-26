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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

/**
 * Checks that OpenProject can apply the conditions of a query on work items (see the query source of the work item
 * macros), before they are combined into one filter per property, so that the macro reports what OpenProject can't
 * do instead of displaying the wrong work packages, or failing with the OpenProject error:
 * <ul>
 * <li>each condition must use an operator that the OpenProject filter of its property supports, for example
 * {@code status IS EMPTY} is rejected, the status of a work package being always set, and a strict comparison
 * ({@code >}, {@code <}) is only applied to the fields holding whole numbers, OpenProject only comparing
 * inclusively;</li>
 * <li>OpenProject keeps a single condition per filter: the conditions repeated on a property are combined into one
 * (see {@code WorkItemFilterCombiner}), which only expresses them all for a single-value property, and the range of
 * numbers that a lower ({@code >=}) and an upper ({@code <=}) bound make can only be expressed on the percentage done,
 * whose values are whole numbers (see {@link OpenProjectFilterTranslator}).</li>
 * </ul>
 * Nothing is checked for the properties whose OpenProject filter is unknown, apart from the texts (the summary and the
 * description), whose filters OpenProject always has, with the same operators.
 *
 * @version $Id$
 * @since 1.5.0
 */
public final class OpenProjectConditionChecker
{
    private static final String OP_RANGE = "<>d";

    private static final String OP_LOWER_BOUND = ">=";

    private static final String OP_UPPER_BOUND = "<=";

    private static final String OP_CONTAINS = "~";

    private static final String OP_NOT_CONTAINS = "!~";

    private static final String PERCENTAGE_DONE = "percentageDone";

    private static final BigDecimal PERCENTAGE_MAX = BigDecimal.valueOf(100);

    private static final String GREATER = ">";

    private static final String LOWER = "<";

    private static final Set<String> LOWER_BOUNDS = Set.of(OP_LOWER_BOUND, GREATER);

    private static final Set<String> UPPER_BOUNDS = Set.of(OP_UPPER_BOUND, LOWER);

    /**
     * The operators of the query converted by {@link OpenProjectFilterTranslator} into another OpenProject operator:
     * the ranges of dates, and the strict comparisons of whole numbers.
     */
    private static final Map<String, String> TRANSLATED_OPERATORS = Map.of("between", OP_RANGE, "after", OP_RANGE,
        "before", OP_RANGE, GREATER, OP_LOWER_BOUND, LOWER, OP_UPPER_BOUND);

    /**
     * The OpenProject operators comparing dates.
     */
    private static final Map<String, String> DAY_OPERATORS_SYNTAX = orderedMap("t", "= today()", "w", "= thisWeek()",
        "=d", "= a date", OP_RANGE, ">= or <= a date", "t+", "= Nd", "t-", "= -Nd", ">t+", ">= Nd", "<t-", "<= -Nd",
        "<t+", ">= 0d AND <= Nd", ">t-", ">= -Nd AND <= 0d");

    private static final Set<String> DAY_OPERATORS = DAY_OPERATORS_SYNTAX.keySet();

    /**
     * The number fields holding hours, which aren't whole numbers although their filter takes integers.
     */
    private static final Set<String> HOUR_FIELDS =
        Set.of("estimatedTime", "remainingTime", "derivedEstimatedTime", "derivedRemainingTime", "spentTime");

    /**
     * How the conditions of the query using the OpenProject operators are written, ordered as OpenProject lists them.
     */
    private static final Map<String, String> QUERY_SYNTAX = orderedMap(
        "=", "= or IN", "!", "!= or NOT IN", "&=", "ALL IN", OP_CONTAINS, OP_CONTAINS, OP_NOT_CONTAINS, OP_NOT_CONTAINS,
        OpenProjectFilterTranslator.SEARCH_OPERATOR, OP_CONTAINS, OP_LOWER_BOUND, OP_LOWER_BOUND, OP_UPPER_BOUND,
        OP_UPPER_BOUND, "o", "= open", "c", "= closed", "l", "= locked");

    static {
        QUERY_SYNTAX.putAll(DAY_OPERATORS_SYNTAX);
        QUERY_SYNTAX.put("*", "IS NOT EMPTY");
        QUERY_SYNTAX.put("!*", "IS EMPTY");
    }

    private final OpenProjectWorkItemFields fields;

    private final Set<String> multiValueFields;

    /**
     * @param fields the work package fields of the instance
     * @param multiValueFields the keys of the fields that a work package can have several values for (for example a
     *     multi-select custom field)
     */
    public OpenProjectConditionChecker(OpenProjectWorkItemFields fields, Set<String> multiValueFields)
    {
        this.fields = fields;
        this.multiValueFields = multiValueFields;
    }

    /**
     * @param filters the filters of the conditions of a query, one per condition (before they are combined), for
     *     example a filter on {@code status} with the constraint {@code !* ""} for {@code status IS EMPTY}
     * @throws OpenProjectFilterValueException if OpenProject can't apply a condition, or the conditions repeated on a
     *     property, the message naming the property
     */
    public void check(List<LiveDataQuery.Filter> filters) throws OpenProjectFilterValueException
    {
        Map<String, Set<List<String>>> conditionsByProperty = new LinkedHashMap<>();
        for (LiveDataQuery.Filter filter : filters) {
            WorkPackageField field = getField(filter.getProperty());
            for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
                checkOperator(filter.getProperty(), constraint.getOperator(), field);
            }
            conditionsByProperty.computeIfAbsent(filter.getProperty(), key -> new LinkedHashSet<>())
                .add(filter.getConstraints().stream()
                    .map(constraint -> constraint.getOperator() + ':' + constraint.getValue())
                    .collect(Collectors.toList()));
        }
        for (Map.Entry<String, Set<List<String>>> conditions : conditionsByProperty.entrySet()) {
            if (conditions.getValue().size() > 1) {
                checkRepeatedConditions(conditions.getKey(), conditions.getValue());
            }
        }
    }

    /**
     * @return the OpenProject field of a property: a field of the instance (for example the target version or a
     *     custom field), or the field of a generic work item property (for example the status), or {@code null}
     */
    private WorkPackageField getField(String property)
    {
        WorkPackageField field = this.fields.getField(property);
        return field == null ? this.fields.getMappedField(OpenProjectMapper.mapLivedataProperty(property)) : field;
    }

    /**
     * @return the operators of the OpenProject filter of a property: the ones of its field, or, for the texts without
     *     field (the description, which isn't a column of the work package lists, or the subject when the fields of
     *     the instance are unknown), the ones that OpenProject has on their filter; empty if unknown
     */
    private static Collection<String> getSupportedOperators(String property, WorkPackageField field)
    {
        Collection<String> supportedOperators = field == null
            ? OpenProjectFilterTranslator.getTextFilterOperators(property) : field.getFilterOperators();
        return supportedOperators == null ? List.of() : supportedOperators;
    }

    private void checkOperator(String property, String operator, WorkPackageField field)
        throws OpenProjectFilterValueException
    {
        Collection<String> supportedOperators = getSupportedOperators(property, field);
        // A condition without operator gets the default operator of its property (see OpenProjectFilterTranslator),
        // which the property supports.
        if (supportedOperators.isEmpty() || StringUtils.isBlank(operator)) {
            return;
        }
        if (field != null && (GREATER.equals(operator) || LOWER.equals(operator))) {
            checkStrictComparison(property, operator, field);
        }
        String openProjectOperator = TRANSLATED_OPERATORS.getOrDefault(operator, operator);
        if (OP_CONTAINS.equals(openProjectOperator)
            && supportedOperators.contains(OpenProjectFilterTranslator.SEARCH_OPERATOR))
        {
            openProjectOperator = OpenProjectFilterTranslator.SEARCH_OPERATOR;
        }
        if (!supportedOperators.contains(openProjectOperator)) {
            throw unsupportedCondition(property, operator, openProjectOperator, supportedOperators);
        }
    }

    /**
     * @return the error telling that a condition isn't supported, written as the user wrote it, with the supported
     *     conditions
     */
    private static OpenProjectFilterValueException unsupportedCondition(String property, String operator,
        String openProjectOperator, Collection<String> supportedOperators)
    {
        String supported = QUERY_SYNTAX.entrySet().stream()
            .filter(entry -> supportedOperators.contains(entry.getKey())).map(Map.Entry::getValue).distinct()
            .collect(Collectors.joining(", "));
        boolean day = DAY_OPERATORS.contains(openProjectOperator);
        // The strict comparisons are written as they are, the other conditions as they are written in the query.
        String condition = TRANSLATED_OPERATORS.containsKey(operator) && !day ? operator
            : QUERY_SYNTAX.getOrDefault(openProjectOperator, operator).replaceAll(" or (NOT )?IN", "");
        String message = String.format("The condition [%s %s] isn't supported by OpenProject, which supports on "
            + "[%s]: %s.", property, condition, property, supported);
        if (day && supportedOperators.stream().noneMatch(DAY_OPERATORS::contains)) {
            message += " A value looking like a date or a number of days (for example 3d) is a day: write it "
                + "between quotes to compare a name.";
        }
        return new OpenProjectFilterValueException(message);
    }

    /**
     * OpenProject only compares the numbers inclusively: a strict comparison of whole numbers compares the next or the
     * previous number, which the other numbers can't.
     */
    private static void checkStrictComparison(String property, String operator, WorkPackageField field)
        throws OpenProjectFilterValueException
    {
        if (field.getFilterOperators().contains(OP_LOWER_BOUND) && !isWholeNumberField(field)) {
            throw new OpenProjectFilterValueException(String.format("The condition [%1$s %2$s] isn't supported by "
                + "OpenProject, which only compares the values of [%1$s] inclusively, and they aren't whole numbers: "
                + "write %2$s= instead.", property, operator));
        }
    }

    private static boolean isWholeNumberField(WorkPackageField field)
    {
        return StringUtils.endsWith(field.getFilterValueType(), "Integer") && !HOUR_FIELDS.contains(field.getKey());
    }

    private void checkRepeatedConditions(String property, Set<List<String>> conditions)
        throws OpenProjectFilterValueException
    {
        WorkPackageField field = getField(property);
        if (field != null && this.multiValueFields.contains(field.getKey())) {
            throw new OpenProjectFilterValueException(String.format("The conditions on [%1$s] can't be combined: "
                + "OpenProject keeps a single condition on a field having several values, for example write "
                + "%1$s ALL IN (a, b) instead of %1$s = a AND %1$s = b.", property));
        }
        Set<String> operators = conditions.stream().flatMap(List::stream)
            .map(condition -> condition.substring(0, condition.indexOf(':'))).collect(Collectors.toSet());
        String filterKey = field == null ? OpenProjectMapper.mapLivedataProperty(property) : field.getFilterKey();
        if (operators.stream().anyMatch(LOWER_BOUNDS::contains) && operators.stream().anyMatch(UPPER_BOUNDS::contains))
        {
            if (!Objects.equals(PERCENTAGE_DONE, filterKey)) {
                throw new OpenProjectFilterValueException(String.format("The conditions on [%s] can't be combined: "
                    + "OpenProject can't match a range of numbers, keep only the lower (>=) or the upper (<=) bound.",
                    property));
            }
            checkPercentageRange(property, conditions);
        }
    }

    /**
     * A range of percentages is matched by the percentages from 0 to 100 that it holds, which can be none.
     */
    private static void checkPercentageRange(String property, Set<List<String>> conditions)
        throws OpenProjectFilterValueException
    {
        BigDecimal lower = BigDecimal.ZERO;
        BigDecimal upper = PERCENTAGE_MAX;
        for (String condition : conditions.stream().flatMap(List::stream).collect(Collectors.toList())) {
            String operator = condition.substring(0, condition.indexOf(':'));
            BigDecimal value;
            try {
                value = new BigDecimal(condition.substring(condition.indexOf(':') + 1).trim());
            } catch (NumberFormatException e) {
                // OpenProject reports the values that aren't numbers.
                return;
            }
            // The percentages are whole numbers.
            if (LOWER_BOUNDS.contains(operator)) {
                lower = lower.max(GREATER.equals(operator) ? value.add(BigDecimal.ONE) : value);
            } else if (UPPER_BOUNDS.contains(operator)) {
                upper = upper.min(LOWER.equals(operator) ? value.subtract(BigDecimal.ONE) : value);
            }
        }
        if (lower.compareTo(upper) > 0) {
            throw new OpenProjectFilterValueException(String.format("The conditions on [%s] can't be true together: "
                + "no work item matches them.", property));
        }
    }

    private static Map<String, String> orderedMap(String... keysAndValues)
    {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
