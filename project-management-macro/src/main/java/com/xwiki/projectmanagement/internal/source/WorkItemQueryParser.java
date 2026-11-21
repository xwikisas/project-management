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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.source.WorkItemName;

/**
 * Parses a textual query on work items, such as {@code project = xwiki AND status = closed}, into Live Data filters.
 * The grammar is:
 *
 * <pre>
 * query      := clause ("AND" clause)*
 * clause     := property ("=" | "!=" | "~" | "!~" | "&gt;=" | "&lt;=" | "&gt;" | "&lt;") value
 *             | property ["NOT" | "ALL"] "IN" "(" value ("," value)* ")"
 *             | property "IS" ["NOT"] "EMPTY"
 * property   := word | quoted
 * value      := word | quoted | "today()" | "thisWeek()"
 * </pre>
 *
 * The keywords ignore the case, the property names don't (they are the ids of the Live Data properties, for example
 * {@code targetVersion}). A quoted value is written between double quotes, a double quote or a backslash in it being
 * escaped with a backslash. The values become constraints as follows:
 * <ul>
 * <li>an unquoted number is an id, kept as text;</li>
 * <li>an unquoted {@code me} (whatever the case) is the current user, kept as the text {@code me};</li>
 * <li>the other values compared with {@code =}, {@code !=} or listed in {@code IN} are names
 * ({@link WorkItemName}), which the client resolves into ids: a quoted value is always a name, for example
 * {@code "me"} is the user named me;</li>
 * <li>the values of {@code ~}, {@code !~}, {@code >=} and {@code <=} are text;</li>
 * <li>an unquoted ISO 8601 date ({@code 2026-11-21}) is a date: {@code =} matches this day, and the bounds set with
 * {@code >=}, {@code >}, {@code <=} and {@code <} on a property are merged into a single range of days;</li>
 * <li>{@code status = open} and {@code status = closed} (unquoted, whatever the case) match all the open, or all the
 * closed, statuses, {@code !=} matching the other ones; they can't be listed in {@code IN}, unlike the statuses named
 * {@code "Open"} or {@code "Closed"}, which are written between quotes.</li>
 * </ul>
 * The operators of the constraints are the ones of the filters of the project management clients, for example
 * {@code !} for {@code !=}, {@code *} for {@code IS NOT EMPTY} or {@code t} for {@code = today()}. {@code OR},
 * {@code NOT} before a clause, parentheses and {@code ORDER BY} are rejected as not supported yet.
 *
 * @version $Id$
 * @since 1.5.0
 */
final class WorkItemQueryParser
{
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final String SYMBOL_CHARACTERS = "(),=!~<>\"";

    private static final String OPENING_PARENTHESIS = "(";

    private static final String CLOSING_PARENTHESIS = ")";

    private static final String COMMA = ",";

    private static final String EQUALS = "=";

    private static final String NOT_EQUALS = "!=";

    private static final String CONTAINS = "~";

    private static final String NOT_CONTAINS = "!~";

    private static final String GREATER_OR_EQUAL = ">=";

    private static final String LOWER_OR_EQUAL = "<=";

    private static final String GREATER = ">";

    private static final String LOWER = "<";

    private static final Set<String> SYMBOLS = Set.of(OPENING_PARENTHESIS, CLOSING_PARENTHESIS, COMMA, EQUALS,
        NOT_EQUALS, CONTAINS, NOT_CONTAINS, GREATER_OR_EQUAL, LOWER_OR_EQUAL, GREATER, LOWER);

    private static final Set<String> COMPARISONS =
        Set.of(EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, GREATER_OR_EQUAL, LOWER_OR_EQUAL, GREATER, LOWER);

    private static final String AND = "AND";

    private static final String OR = "OR";

    private static final String NOT = "NOT";

    private static final String IN = "IN";

    private static final String EMPTY = "EMPTY";

    private static final String IS = "IS";

    // The verb agreeing with an unsupported feature.
    private static final String SINGULAR = "is";

    private static final String OPEN = "open";

    private static final String CLOSED = "closed";

    private static final String CURRENT_USER = "me";

    private static final String OPERATOR_NOT_EQUALS = "!";

    private static final Map<String, String> LIST_OPERATORS = Map.of(IN, EQUALS, NOT, OPERATOR_NOT_EQUALS, "ALL", "&=");

    private static final Map<String, String> FUNCTION_OPERATORS = Map.of("today", "t", "thisweek", "w");

    private static final String EXPECTED_OPERATOR =
        "an operator (=, !=, ~, !~, >=, <=, >, <, IN, NOT IN, ALL IN, IS EMPTY or IS NOT EMPTY)";

    private final List<Token> tokens;

    private int index;

    private final List<LiveDataQuery.Filter> filters = new ArrayList<>();

    private final Map<String, DateRange> dateRanges = new LinkedHashMap<>();

    private enum TokenType
    {
        WORD,
        QUOTED,
        SYMBOL,
        END
    }

    private enum ValueType
    {
        WORD,
        QUOTED,
        FUNCTION
    }

    private static final class Token
    {
        private final TokenType type;

        private final String text;

        /**
         * The 1-based position of the first character of the token in the query.
         */
        private final int position;

        Token(TokenType type, String text, int position)
        {
            this.type = type;
            this.text = text;
            this.position = position;
        }

        boolean isSymbol(String symbol)
        {
            return this.type == TokenType.SYMBOL && this.text.equals(symbol);
        }

        boolean isKeyword(String keyword)
        {
            return this.type == TokenType.WORD && this.text.equalsIgnoreCase(keyword);
        }

        boolean isComparison()
        {
            return this.type == TokenType.SYMBOL && COMPARISONS.contains(this.text);
        }
    }

    private static final class Value
    {
        private final ValueType type;

        private final String text;

        private final Token token;

        Value(ValueType type, String text, Token token)
        {
            this.type = type;
            this.text = text;
            this.token = token;
        }

        boolean isNumber()
        {
            return this.type == ValueType.WORD && NUMBER.matcher(this.text).matches();
        }

        boolean isDate()
        {
            return this.type == ValueType.WORD && DATE.matcher(this.text).matches();
        }

        boolean isStatusKeyword()
        {
            return this.type == ValueType.WORD
                && (OPEN.equalsIgnoreCase(this.text) || CLOSED.equalsIgnoreCase(this.text));
        }

        /**
         * @return an unquoted number as text (an id), an unquoted {@code me} as the text {@code me} (the current user),
         *     the other values as names
         */
        Object toIdOrName()
        {
            if (isNumber()) {
                return this.text;
            } else if (this.type == ValueType.WORD && CURRENT_USER.equalsIgnoreCase(this.text)) {
                return CURRENT_USER;
            }
            return new WorkItemName(this.text);
        }
    }

    /**
     * The bounds of the days matched by a date property, whose filter is added where its first bound is written.
     */
    private static final class DateRange
    {
        private final LiveDataQuery.Filter filter;

        private LocalDate lowerBound;

        private LocalDate upperBound;

        DateRange(LiveDataQuery.Filter filter)
        {
            this.filter = filter;
        }
    }

    private WorkItemQueryParser(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    /**
     * @param query the query, for example {@code project = xwiki AND status IN (1, 2)}
     * @return the filters of the query, one by clause (the bounds of a date property being merged into one filter),
     *     in the order of the clauses
     * @throws WorkItemSourceException if the query isn't valid, the message giving the position of the error and what
     *     was expected
     */
    static List<LiveDataQuery.Filter> parse(String query) throws WorkItemSourceException
    {
        return new WorkItemQueryParser(tokenize(query)).parseQuery();
    }

    private List<LiveDataQuery.Filter> parseQuery() throws WorkItemSourceException
    {
        while (true) {
            parseClause();
            Token token = next();
            if (token.type == TokenType.END) {
                break;
            } else if (token.isKeyword(OR)) {
                throw unsupported(OR, SINGULAR, token);
            } else if (token.isKeyword("ORDER")) {
                throw unsupported("ORDER BY", SINGULAR, token);
            } else if (!token.isKeyword(AND)) {
                throw syntaxError(token, "AND or the end of the query");
            }
        }
        for (DateRange range : this.dateRanges.values()) {
            addDateRange(range);
        }
        return this.filters;
    }

    private void parseClause() throws WorkItemSourceException
    {
        String property = parseProperty();
        Token operator = next();
        if (operator.isComparison()) {
            parseComparison(property, operator, parseValue());
        } else if (operator.isKeyword(IS)) {
            parseEmptiness(property);
        } else if (operator.type == TokenType.WORD
            && LIST_OPERATORS.containsKey(operator.text.toUpperCase(Locale.ROOT)))
        {
            if (!operator.isKeyword(IN)) {
                expectKeyword(IN, IN);
            }
            parseList(property, LIST_OPERATORS.get(operator.text.toUpperCase(Locale.ROOT)));
        } else {
            throw syntaxError(operator, EXPECTED_OPERATOR);
        }
    }

    private String parseProperty() throws WorkItemSourceException
    {
        Token token = next();
        if (token.isSymbol(OPENING_PARENTHESIS)) {
            throw unsupported("Parentheses", "are", token);
        } else if (token.isKeyword(NOT)) {
            throw unsupported(NOT, SINGULAR, token);
        } else if ((token.type != TokenType.WORD && token.type != TokenType.QUOTED) || token.isKeyword(AND)) {
            throw syntaxError(token, "a property");
        }
        return token.text;
    }

    private void parseEmptiness(String property) throws WorkItemSourceException
    {
        boolean not = peek().isKeyword(NOT);
        if (not) {
            next();
        }
        expectKeyword(EMPTY, not ? EMPTY : "EMPTY or NOT EMPTY");
        addFilter(property, not ? "*" : "!*", "");
    }

    private Value parseValue() throws WorkItemSourceException
    {
        Token token = next();
        if (token.type == TokenType.QUOTED) {
            return new Value(ValueType.QUOTED, token.text, token);
        } else if (token.type != TokenType.WORD) {
            throw syntaxError(token, "a value");
        } else if (peek().isSymbol(OPENING_PARENTHESIS)) {
            next();
            Token closing = next();
            if (!closing.isSymbol(CLOSING_PARENTHESIS)) {
                throw syntaxError(closing, "')'");
            }
            if (!FUNCTION_OPERATORS.containsKey(token.text.toLowerCase(Locale.ROOT))) {
                throw new WorkItemSourceException(String.format(
                    "Unknown function [%s()] at position %d: the supported functions are today() and thisWeek().",
                    token.text, token.position));
            }
            return new Value(ValueType.FUNCTION, token.text, token);
        }
        return new Value(ValueType.WORD, token.text, token);
    }

    private void parseList(String property, String operator) throws WorkItemSourceException
    {
        Token opening = next();
        if (!opening.isSymbol(OPENING_PARENTHESIS)) {
            throw syntaxError(opening, "'('");
        }
        LiveDataQuery.Filter filter = addFilter(property);
        while (true) {
            Value value = parseValue();
            if (value.type == ValueType.FUNCTION) {
                throw invalidFunction(value);
            } else if (WorkItem.KEY_STATUS.equals(property) && value.isStatusKeyword()) {
                // A list of statuses can't mix all the open or closed statuses with other statuses.
                throw new WorkItemSourceException(String.format("[%s] at position %d can't be listed: write status = "
                    + "%s or status != %s instead.", value.text, value.token.position, value.text, value.text));
            }
            filter.getConstraints().add(new LiveDataQuery.Constraint(value.toIdOrName(), operator));
            Token separator = next();
            if (separator.isSymbol(CLOSING_PARENTHESIS)) {
                break;
            } else if (!separator.isSymbol(COMMA)) {
                throw syntaxError(separator, "',' or ')'");
            }
        }
    }

    private void parseComparison(String property, Token operatorToken, Value value) throws WorkItemSourceException
    {
        String operator = operatorToken.text;
        if (value.type == ValueType.FUNCTION) {
            addFunctionFilter(property, operator, value);
        } else if (isStatusComparison(property, operator, value)) {
            boolean open = OPEN.equalsIgnoreCase(value.text) == EQUALS.equals(operator);
            addFilter(property, open ? "o" : "c", "");
        } else if (value.isDate() && !isTextComparison(operator)) {
            parseDateComparison(property, operator, value);
        } else if (isEquality(operator)) {
            addFilter(property, EQUALS.equals(operator) ? EQUALS : OPERATOR_NOT_EQUALS, value.toIdOrName());
        } else if (GREATER.equals(operator) || LOWER.equals(operator)) {
            throw new WorkItemSourceException(String.format("The operator [%s] at position %d can only compare "
                + "dates (for example 2026-11-21): write %s= instead.", operator, operatorToken.position, operator));
        } else {
            addFilter(property, operator, value.text);
        }
    }

    private void addFunctionFilter(String property, String operator, Value value) throws WorkItemSourceException
    {
        if (!EQUALS.equals(operator)) {
            throw invalidFunction(value);
        }
        addFilter(property, FUNCTION_OPERATORS.get(value.text.toLowerCase(Locale.ROOT)), "");
    }

    private static boolean isTextComparison(String operator)
    {
        return CONTAINS.equals(operator) || NOT_CONTAINS.equals(operator);
    }

    private static boolean isEquality(String operator)
    {
        return EQUALS.equals(operator) || NOT_EQUALS.equals(operator);
    }

    private static boolean isStatusComparison(String property, String operator, Value value)
    {
        return isEquality(operator) && WorkItem.KEY_STATUS.equals(property) && value.isStatusKeyword();
    }

    private void parseDateComparison(String property, String operator, Value value) throws WorkItemSourceException
    {
        LocalDate date;
        try {
            date = LocalDate.parse(value.text);
        } catch (DateTimeParseException e) {
            // The message of the parser (for example "Invalid value for MonthOfYear") doesn't give the position.
            throw new WorkItemSourceException(String.format("Syntax error at position %d: invalid date [%s].",
                value.token.position, value.text));
        }
        if (EQUALS.equals(operator)) {
            addFilter(property, "=d", value.text);
        } else if (NOT_EQUALS.equals(operator)) {
            throw new WorkItemSourceException(String.format(
                "The operator [!=] can't compare dates (at position %d).", value.token.position));
        } else {
            addDateBound(property, operator, date, value);
        }
    }

    private void addDateBound(String property, String operator, LocalDate date, Value value)
        throws WorkItemSourceException
    {
        DateRange range = this.dateRanges.get(property);
        if (range == null) {
            range = new DateRange(addFilter(property));
            this.dateRanges.put(property, range);
        }
        // The dates have no time: a strict bound excludes its day.
        if (GREATER_OR_EQUAL.equals(operator) || GREATER.equals(operator)) {
            if (range.lowerBound != null) {
                throw severalBounds("lower", property, value);
            }
            range.lowerBound = GREATER.equals(operator) ? date.plusDays(1) : date;
        } else {
            if (range.upperBound != null) {
                throw severalBounds("upper", property, value);
            }
            range.upperBound = LOWER.equals(operator) ? date.minusDays(1) : date;
        }
    }

    private static void addDateRange(DateRange range) throws WorkItemSourceException
    {
        if (range.lowerBound != null && range.upperBound != null && range.lowerBound.isAfter(range.upperBound)) {
            throw new WorkItemSourceException(String.format("The bounds of the dates of [%s] match no day.",
                range.filter.getProperty()));
        }
        LiveDataQuery.Constraint constraint;
        if (range.lowerBound != null && range.upperBound != null) {
            constraint = new LiveDataQuery.Constraint(range.lowerBound + "/" + range.upperBound, "between");
        } else if (range.lowerBound != null) {
            constraint = new LiveDataQuery.Constraint(range.lowerBound.toString(), "after");
        } else {
            constraint = new LiveDataQuery.Constraint(range.upperBound.toString(), "before");
        }
        range.filter.getConstraints().add(constraint);
    }

    private LiveDataQuery.Filter addFilter(String property)
    {
        LiveDataQuery.Filter filter = new LiveDataQuery.Filter();
        filter.setProperty(property);
        this.filters.add(filter);
        return filter;
    }

    private void addFilter(String property, String operator, Object value)
    {
        addFilter(property).getConstraints().add(new LiveDataQuery.Constraint(value, operator));
    }

    private void expectKeyword(String keyword, String expected) throws WorkItemSourceException
    {
        Token token = next();
        if (!token.isKeyword(keyword)) {
            throw syntaxError(token, expected);
        }
    }

    private Token next()
    {
        Token token = this.tokens.get(this.index);
        if (token.type != TokenType.END) {
            this.index++;
        }
        return token;
    }

    private Token peek()
    {
        return this.tokens.get(this.index);
    }

    private static WorkItemSourceException syntaxError(Token token, String expected)
    {
        String found = token.type == TokenType.END ? "the query ends"
            : String.format("found [%s]", token.text);
        return new WorkItemSourceException(String.format("Syntax error at position %d: expected %s, but %s.",
            token.position, expected, found));
    }

    /**
     * @param verb the verb agreeing with the feature, {@code is} or {@code are}
     */
    private static WorkItemSourceException unsupported(String feature, String verb, Token token)
    {
        return new WorkItemSourceException(String.format("%s (at position %d) %s not supported yet: the query can only "
            + "combine conditions with AND.", feature, token.position, verb));
    }

    private static WorkItemSourceException invalidFunction(Value value)
    {
        return new WorkItemSourceException(String.format("The function [%s()] at position %d can only be used with "
            + "the operator [=].", value.text, value.token.position));
    }

    private static WorkItemSourceException severalBounds(String bound, String property, Value value)
    {
        return new WorkItemSourceException(String.format("The property [%s] has several %s bounds (the second one "
            + "at position %d).", property, bound, value.token.position));
    }

    private static List<Token> tokenize(String query) throws WorkItemSourceException
    {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < query.length()) {
            char character = query.charAt(i);
            if (Character.isWhitespace(character)) {
                i++;
            } else if (character == '"') {
                i = readQuoted(query, i, tokens);
            } else if (SYMBOL_CHARACTERS.indexOf(character) >= 0) {
                i = readSymbol(query, i, tokens);
            } else {
                i = readWord(query, i, tokens);
            }
        }
        tokens.add(new Token(TokenType.END, "", query.length() + 1));
        return tokens;
    }

    /**
     * @return the index following the symbol
     */
    private static int readSymbol(String query, int start, List<Token> tokens) throws WorkItemSourceException
    {
        String symbol = start + 1 < query.length() ? query.substring(start, start + 2) : null;
        if (symbol == null || !SYMBOLS.contains(symbol)) {
            symbol = String.valueOf(query.charAt(start));
        }
        if (!SYMBOLS.contains(symbol)) {
            throw new WorkItemSourceException(String.format(
                "Syntax error at position %d: unexpected character [%s].", start + 1, symbol));
        }
        tokens.add(new Token(TokenType.SYMBOL, symbol, start + 1));
        return start + symbol.length();
    }

    /**
     * @return the index following the word
     */
    private static int readWord(String query, int start, List<Token> tokens)
    {
        int i = start;
        while (i < query.length() && !Character.isWhitespace(query.charAt(i))
            && SYMBOL_CHARACTERS.indexOf(query.charAt(i)) < 0)
        {
            i++;
        }
        tokens.add(new Token(TokenType.WORD, query.substring(start, i), start + 1));
        return i;
    }

    /**
     * @return the index following the closing quote
     */
    private static int readQuoted(String query, int start, List<Token> tokens) throws WorkItemSourceException
    {
        StringBuilder text = new StringBuilder();
        int i = start + 1;
        while (i < query.length()) {
            char character = query.charAt(i);
            if (character == '"') {
                tokens.add(new Token(TokenType.QUOTED, text.toString(), start + 1));
                return i + 1;
            } else if (character == '\\' && i + 1 < query.length()) {
                i++;
            }
            text.append(query.charAt(i));
            i++;
        }
        throw new WorkItemSourceException(String.format(
            "Syntax error at position %d: the quoted value isn't closed.", start + 1));
    }
}
