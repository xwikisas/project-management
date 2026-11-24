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
/*
 * Writes the Live Data filters of the filter builder as the textual query read by the default "query" source of the
 * work item macros, e.g. project = 12 AND status = open. The query is parsed back on the server (WorkItemQueryParser),
 * and parsing the written query gives back the same filters (tested by src/test/node, with Vitest, on the cases
 * of src/test/resources/queryWriter/roundTripCases.json, which QueryWriterRoundTripTest parses in Java).
 *
 * This module has no dependency, so that it can be run outside of a browser.
 */
define([], function () {
  // The bounds that the "between" operator takes for the ranges of dates open on one side, on the date properties
  // without "on or before" and "on or after" operators, which the query writes with a single comparison, e.g.
  // dueDate >= 2026-11-01 (the same as in the OpenProject client).
  const NO_LOWER_BOUND = '1970-01-01';
  const NO_UPPER_BOUND = '3000-01-01';
  // The query parser also splits the words on the information separators (U+001C to U+001F).
  const BARE_WORD = /^[^\s\u001c-\u001f(),=!~<>"]+$/;
  const DATE = /^\d{4}-\d{2}-\d{2}$/;
  // A day relative to today, e.g. -3d or 2w.
  const RELATIVE_DAY = /^[+-]?\d{1,5}[dw]$/i;
  const KEYWORDS = ['and', 'or', 'not', 'in', 'all', 'is', 'empty', 'order'];
  // The values read as keywords when they're not quoted: all the open or closed statuses (or versions), the locked
  // versions, and the current user.
  const VALUE_KEYWORDS = ['open', 'closed', 'locked', 'me'];
  // The operators that take no value, e.g. "is not set" or "is today".
  const NO_VALUE_OPERATORS = ['*', '!*', 'empty', 'o', 'c', 'l', 't', 'w'];
  let takesValue = operator => !NO_VALUE_OPERATORS.includes(operator);

  /*
   * A word that would be read otherwise when unquoted (a keyword, a date, a day relative to today) is quoted, as is a
   * text that isn't a single word.
   */
  let quote = function (text, forced) {
    text = String(text);
    if (!forced && BARE_WORD.test(text) && !KEYWORDS.includes(text.toLowerCase()) && !DATE.test(text)
        && !RELATIVE_DAY.test(text)) {
      return text;
    }
    return '"' + text.replace(/["\\]/g, '\\$&') + '"';
  };

  let toDate = function (value) {
    return String(value || '').substring(0, 10);
  };

  /**
   * @param filters the Live Data filters, e.g. [{property: 'status', constraints: [{operator: '=', value: '1'}]}]
   * @param names the values read as names from the query, by property (a Map of Sets): a name made of digits, or
   *   looking like a keyword, is written between quotes, so that it isn't read as an id or a keyword
   * @return the query, e.g. status = 1
   */
  let toQuery = function (filters, names) {
    // A value written unquoted is read as an id when it's a number, and as a keyword when it's me, open, closed or
    // locked: a name looking so is quoted.
    let quoteValue = function (property, value) {
      let text = String(value);
      let isName = !!names && names.has(property) && names.get(property).has(text);
      return quote(value, isName && (/^\d+$/.test(text) || VALUE_KEYWORDS.includes(text.toLowerCase())));
    };
    let listClause = function (property, values, operator, keyword) {
      let quoteFilterValue = value => quoteValue(property, value);
      return values.length === 1 && operator ? `${quote(property)} ${operator} ${quoteFilterValue(values[0])}`
        : `${quote(property)} ${keyword} (${values.map(quoteFilterValue).join(', ')})`;
    };
    let clauses = [];
    let addClauses = function (property, operator, values) {
      let name = quote(property);
      let each = format => values.forEach(value => clauses.push(format(value)));
      switch (operator) {
        case '!':
          clauses.push(listClause(property, values, '!=', 'NOT IN'));
          break;
        case '&=':
          clauses.push(listClause(property, values, null, 'ALL IN'));
          break;
        case '~':
        case '!~':
        case '>=':
        case '<=':
        case '>':
        case '<':
          each(value => `${name} ${operator} ${quote(value)}`);
          break;
        // The generic operators of the Live Data, found in the filters written by hand.
        case 'contains':
        case 'startsWith':
          each(value => `${name} ~ ${quote(value)}`);
          break;
        // Strict, as in the OpenProject client (greater than 5 is greater than or equal to 6).
        case 'greater':
          each(value => `${name} > ${quote(value)}`);
          break;
        case 'less':
          each(value => `${name} < ${quote(value)}`);
          break;
        case '*':
          clauses.push(`${name} IS NOT EMPTY`);
          break;
        case '!*':
        case 'empty':
          clauses.push(`${name} IS EMPTY`);
          break;
        // All the open or closed statuses, or the open, locked or closed versions.
        case 'o':
          clauses.push(`${name} = open`);
          break;
        case 'c':
          clauses.push(`${name} = closed`);
          break;
        case 'l':
          clauses.push(`${name} = locked`);
          break;
        case 't':
          clauses.push(`${name} = today()`);
          break;
        case 'w':
          clauses.push(`${name} = thisWeek()`);
          break;
        // The days relative to today.
        case 't+':
          each(value => `${name} = ${value}d`);
          break;
        case 't-':
          each(value => `${name} = -${value}d`);
          break;
        case '>t+':
          each(value => `${name} >= ${value}d`);
          break;
        case '<t-':
          each(value => `${name} <= -${value}d`);
          break;
        case '<t+':
          each(value => `${name} >= 0d AND ${name} <= ${value}d`);
          break;
        case '>t-':
          each(value => `${name} >= -${value}d AND ${name} <= 0d`);
          break;
        case '=d':
          each(value => `${name} = ${toDate(value)}`);
          break;
        // On or after a day, on or before a day.
        case 'after':
          each(value => `${name} >= ${toDate(value)}`);
          break;
        case 'before':
          each(value => `${name} <= ${toDate(value)}`);
          break;
        case 'between':
          values.forEach(value => {
            let bounds = String(value).split('/');
            let lower = toDate(bounds[0]);
            let upper = toDate(bounds.length > 1 ? bounds[1] : bounds[0]);
            if (lower && lower !== NO_LOWER_BOUND) {
              clauses.push(`${name} >= ${lower}`);
            }
            if (upper && upper !== NO_UPPER_BOUND) {
              clauses.push(`${name} <= ${upper}`);
            }
          });
          break;
        default:
          clauses.push(listClause(property, values, '=', 'IN'));
      }
    };
    (filters || []).forEach(filter => {
      let valuesByOperator = new Map();
      (filter.constraints || []).forEach(constraint => {
        let values = valuesByOperator.get(constraint.operator) || [];
        values.push(constraint.value);
        valuesByOperator.set(constraint.operator, values);
      });
      valuesByOperator.forEach((values, operator) => {
        // A filter without value (e.g. a row of the builder whose value isn't chosen yet) selects nothing: it isn't
        // written, instead of comparing the property with an empty text.
        if (!takesValue(operator) || values.some(value => value !== '' && value !== null && value !== undefined)) {
          addClauses(filter.property, operator, values);
        }
      });
    });
    return clauses.join(' AND ');
  };

  // A date property without "on or before" and "on or after" operators shows them as a range of dates.
  let toRangeConstraint = function (constraint) {
    if (constraint.operator === 'after') {
      return { operator: 'between', value: constraint.value + '/' + NO_UPPER_BOUND };
    } else if (constraint.operator === 'before') {
      return { operator: 'between', value: NO_LOWER_BOUND + '/' + constraint.value };
    }
    return constraint;
  };

  // A whole number compared strictly is compared inclusively with the next (or previous) number, as in the OpenProject
  // client: greater than 5 is at least 6. A decimal number is compared inclusively, as there.
  let toInclusiveBound = function (value, step) {
    let text = String(value).trim();
    return /^-?\d+$/.test(text) ? String(parseInt(text, 10) + step) : text;
  };

  /*
   * The operators of the builder replacing the generic operators of the Live Data (and the strict comparisons of the
   * query), by type of filter, as the clients read them (see OpenProjectFilterTranslator): each function returns the
   * constraint of the builder selecting the same work items, or nothing when the type has no equivalent.
   */
  const GENERIC_OPERATORS = {
    text: {
      contains: value => ({ operator: '~', value }),
      // The project management tools have no prefix match on texts.
      startsWith: value => ({ operator: '~', value }),
      equals: (value, operators) => ({ operator: operators.includes('=') ? '=' : '~', value }),
      notEquals: value => ({ operator: '!', value })
    },
    list: {
      // A list filter matches the values picked in a list: containing a value, or starting with it, is equal to it.
      equals: value => ({ operator: '=', value }),
      contains: value => ({ operator: '=', value }),
      startsWith: value => ({ operator: '=', value }),
      notEquals: value => ({ operator: '!', value })
    },
    boolean: {
      equals: value => ({ operator: '=', value }),
      notEquals: value => ({ operator: '!', value })
    },
    number: {
      equals: value => ({ operator: '=', value }),
      notEquals: value => ({ operator: '!', value }),
      greater: value => ({ operator: '>=', value: toInclusiveBound(value, 1) }),
      '>': value => ({ operator: '>=', value: toInclusiveBound(value, 1) }),
      less: value => ({ operator: '<=', value: toInclusiveBound(value, -1) }),
      '<': value => ({ operator: '<=', value: toInclusiveBound(value, -1) })
    },
    date: {
      // The "on or after" and "on or before" operators of the properties that don't have them.
      after: value => toRangeConstraint({ operator: 'after', value }),
      before: value => toRangeConstraint({ operator: 'before', value }),
      // A range of dates, or a single day.
      contains: value => ({ operator: String(value).includes('/') ? 'between' : '=d', value }),
      equals: value => ({ operator: String(value).includes('/') ? 'between' : '=d', value })
    }
  };

  /**
   * Converts the constraints of a filter into the operators of the builder for its property, e.g. the filters
   * parameter written by hand, or by an older version, with the generic operators of the Live Data (contains is ~
   * for a text). The constraints whose operator is one of the property, or has no equivalent, are kept as they are.
   *
   * @param filter the Live Data filter, e.g. {property: 'summary', constraints: [{operator: 'contains', value: 'a'}]}
   * @param type the type of the filter of the property, e.g. text, list, number, date or boolean
   * @param operators the ids of the operators of the builder for the property, e.g. ['~', '!~']
   * @return the filter, whose constraints use the operators of the builder when they have an equivalent
   */
  let toBuilderFilter = function (filter, type, operators) {
    let available = operators || [];
    let genericOperators = GENERIC_OPERATORS[type] || {};
    let constraints = (filter.constraints || []).map(constraint => {
      let operator = constraint.operator;
      if (operator === null || operator === undefined || available.includes(operator)) {
        return constraint;
      }
      let converted = operator === 'empty' ? { operator: '!*', value: '' }
        : genericOperators.hasOwnProperty(operator) && genericOperators[operator](constraint.value, available);
      return converted && available.includes(converted.operator) ? converted : constraint;
    });
    return Object.assign({}, filter, { constraints });
  };

  // The generic operators of the Live Data, which the clients replace by an operator of the property, ignoring the
  // constraints of the properties without equivalent (or failing on them, for notEquals).
  const LIVE_DATA_OPERATORS = ['equals', 'notEquals', 'contains', 'startsWith', 'empty', 'greater', 'less', 'before',
    'after'];

  /**
   * Converts the constraints of a filter with toBuilderFilter, and splits them between the ones that the builder keeps
   * and the others: those whose generic Live Data operator has no equivalent for the property, e.g. the empty operator
   * of the filters parameter written by an older version on a text that the tool can't filter on being empty (the
   * description in OpenProject), which the client ignores, while the query would write it. The other operators that
   * the property doesn't have are the ones of the tool (e.g. >t- on a date), which the query writes as they are.
   *
   * @param filter the Live Data filter
   * @param type the type of the filter of the property, e.g. text, list, number, date or boolean
   * @param operators the ids of the operators of the builder for the property
   * @return {{filter, unsupported}} the filter with the constraints that the builder keeps, and the other constraints,
   *   as they were
   */
  let splitSupported = function (filter, type, operators) {
    let available = operators || [];
    let original = filter.constraints || [];
    let converted = toBuilderFilter(filter, type, available);
    let supported = [];
    let unsupported = [];
    converted.constraints.forEach((constraint, index) => {
      if (LIVE_DATA_OPERATORS.includes(constraint.operator) && !available.includes(constraint.operator)) {
        unsupported.push(original[index]);
      } else {
        supported.push(constraint);
      }
    });
    return { filter: Object.assign({}, converted, { constraints: supported }), unsupported };
  };

  /**
   * Reads the filters of a query, as parsed by the REST API: the constraints of the builder, and the values read as
   * names.
   *
   * @param parsedFilters the filters returned by the REST API, whose constraints are flagged with "name: true" when
   *   their value is a name
   * @param names the values read as names, by property (a Map of Sets), to which the names of the filters are added
   * @return the filters of the builder, with the operators of the parsed filters, e.g. "before" (on or before) for
   *   dueDate <= 2026-12-31, which toBuilderFilter converts for the properties that don't have them
   */
  let fromParsedFilters = function (parsedFilters, names) {
    return (parsedFilters || []).map(filter => {
      (filter.constraints || []).filter(constraint => constraint.name).forEach(constraint => {
        names.set(filter.property, (names.get(filter.property) || new Set()).add(String(constraint.value)));
      });
      return { property: filter.property, constraints: (filter.constraints || []).slice() };
    });
  };

  /**
   * Replaces the values of the filters by their names, e.g. the id of a project by its name, so that the written
   * query is readable; the client resolves the names into ids when the work items are retrieved. The keywords (e.g.
   * me) and the values without name are kept.
   *
   * @param filters the Live Data filters
   * @param getName returns the name of a value of a property, or undefined to keep the value: (property, value)
   * @param names the values read as names, by property (a Map of Sets), to which the written names are added, so
   *   that a name made of digits or looking like a keyword is written between quotes
   * @return the filters with the names of their values
   */
  let withValueNames = function (filters, getName, names) {
    return filters.map(filter => {
      let changed = false;
      let constraints = (filter.constraints || []).map(constraint => {
        let value = constraint.value;
        if (typeof value !== 'string' || value === '' || VALUE_KEYWORDS.includes(value.toLowerCase())) {
          return constraint;
        }
        let name = getName(filter.property, value);
        if (typeof name !== 'string' || name.trim() === '' || name === value) {
          return constraint;
        }
        names.set(filter.property, (names.get(filter.property) || new Set()).add(name));
        changed = true;
        return Object.assign({}, constraint, { value: name });
      });
      return changed ? Object.assign({}, filter, { constraints }) : filter;
    });
  };

  return { toQuery, fromParsedFilters, takesValue, withValueNames, toBuilderFilter, splitSupported };
});
