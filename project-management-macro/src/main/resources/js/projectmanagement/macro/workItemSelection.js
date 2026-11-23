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
 * How a work item macro selects its work items, as edited in the macro editor: the model behind the "Select the work
 * packages by" widget, without DOM.
 *
 * The macro parameters (source, content, identifier, filters, presetId) are read into a state holding one way (the
 * selected one) and the values of every way, so that the user can switch between the ways without losing what they
 * typed. Only the fields of the selected way are written back.
 *
 * The ways:
 * - query: the work items matching filters, written as a query in the content (the default source);
 * - list: specific work items, listed in the content with the list source;
 * - url: the work items of a link to the tool (e.g. an OpenProject work package list), with the url source;
 * - advanced: any other source (e.g. filters, the Live Data JSON filters), or a content starting with a curly bracket
 *   without source (the advanced Live Data configuration of the macro), which is then locked: kept as it is.
 */
define([], function () {
  'use strict';

  const QUERY = 'query';
  const LIST = 'list';
  const URL = 'url';
  const ADVANCED = 'advanced';

  const trim = function (value) {
    return String(value === undefined || value === null ? '' : value).trim();
  };

  /**
   * @param {string} content the content of a macro using the list source
   * @return {Array<string>} the listed work items, split like the list source does (on white spaces and commas),
   *     without the repeated ones (like the work item picker)
   */
  const parseList = function (content) {
    return [...new Set(trim(content).split(/[\s,]+/).filter(id => id !== ''))];
  };

  /**
   * @param {Array<string>} list work items
   * @return {string} the content listing them, e.g. "236, 237, PROJ-42"
   */
  const formatList = function (list) {
    return (list || []).map(trim).filter(id => id !== '').join(', ');
  };

  /**
   * @param {string} value the value of the deprecated identifier parameter: work items or a link
   * @return {{way: string, list: Array<string>, url: string}} the way replacing it (list or url) and its value
   */
  const convertIdentifier = function (value) {
    const identifier = trim(value);
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(identifier) || identifier.startsWith('/')) {
      return {way: URL, list: [], url: identifier};
    }
    return {way: LIST, list: parseList(identifier), url: ''};
  };

  /**
   * @param {{source: string, content: string, identifier: string, filters: string, presetId: string,
   *     isChart: boolean}} fields the values of the macro parameters; the filters of a chart are its series, not
   *     filters selecting the work items
   * @return {{way: string, locked: boolean, values: Object, initial: Object}} the state of the widget: the selected
   *     way, whether it's locked (the advanced Live Data configuration, kept as it is), the values of all the ways
   *     ({query, presetId, legacyFilters, list, url, advanced: {source, content}, identifier}) and the initial fields
   */
  const readInitial = function (fields) {
    const initial = {
      source: trim(fields && fields.source),
      content: trim(fields && fields.content),
      identifier: trim(fields && fields.identifier),
      filters: trim(fields && fields.filters),
      presetId: trim(fields && fields.presetId),
      isChart: !!(fields && fields.isChart)
    };
    const values = {
      query: '',
      presetId: initial.presetId,
      // The deprecated filters parameter, edited by the filter builder of the query way.
      legacyFilters: initial.isChart ? '' : initial.filters,
      list: [],
      url: '',
      advanced: {source: '', content: ''},
      // The deprecated identifier parameter, kept until it's converted, or replaced by a content.
      identifier: initial.identifier
    };
    let way = QUERY;
    let locked = false;
    const source = initial.source.toLowerCase();
    if (source === '' && initial.content.startsWith('{')) {
      way = ADVANCED;
      locked = true;
      values.advanced = {source: '', content: initial.content};
    } else if (source === '' && initial.content === '' && initial.identifier !== '') {
      // The macro is opened on the way replacing the deprecated identifier, which is kept until it's converted.
      way = convertIdentifier(initial.identifier).way;
    } else if (source === '' || source === QUERY) {
      values.query = initial.content;
    } else if (source === LIST) {
      way = LIST;
      values.list = parseList(initial.content);
    } else if (source === URL) {
      way = URL;
      values.url = initial.content;
    } else {
      way = ADVANCED;
      values.advanced = {source: initial.source, content: initial.content};
    }
    return {way, locked, values, initial};
  };

  /**
   * @param {Object} state the state of the widget
   * @return {boolean} whether the advanced way is offered: only when the macro uses it
   */
  const hasAdvancedWay = function (state) {
    return state.locked
      || (state.initial.source !== '' && ![QUERY, LIST, URL].includes(state.initial.source.toLowerCase()));
  };

  const getContent = function (state) {
    const values = state.values;
    switch (state.way) {
      case LIST:
        return formatList(values.list);
      case URL:
        return trim(values.url);
      case ADVANCED:
        return trim(values.advanced.content);
      default:
        return trim(values.query);
    }
  };

  const getSource = function (state) {
    switch (state.way) {
      case LIST:
        return LIST;
      case URL:
        return URL;
      case ADVANCED:
        return state.values.advanced.source;
      default:
        // An explicit query source is kept, the query being the default.
        return state.initial.source.toLowerCase() === QUERY ? state.initial.source : '';
    }
  };

  /**
   * @param {Object} state the state of the widget
   * @return {{content: string, source: string, presetId: string, filters: string, identifier: string}} the values
   *     of the macro parameters written for the selected way; the fields of the other ways are cleared (the macro
   *     editor doesn't write empty parameters), except the series of a chart, which are kept
   */
  const toFields = function (state) {
    if (state.locked) {
      const initial = state.initial;
      return {content: initial.content, source: initial.source, presetId: initial.presetId, filters: initial.filters,
        identifier: initial.identifier};
    }
    const content = getContent(state);
    const values = state.values;
    // The macros reject the deprecated identifier and filters parameters together with a content, and ignore the
    // source without content.
    const keepLegacy = content === '';
    let filters = keepLegacy ? values.legacyFilters : '';
    if (state.initial.isChart) {
      filters = state.initial.filters;
    }
    return {
      content,
      source: keepLegacy && state.way !== ADVANCED ? '' : getSource(state),
      // The preset is only combined with the filters (see the Matching filters way).
      presetId: state.way === QUERY ? values.presetId : '',
      filters,
      identifier: keepLegacy ? values.identifier : ''
    };
  };

  /**
   * @param {Object} state the state of the widget
   * @return {Array<{type: string, value: string}>} the values that the user entered (or that the macro had) and that
   *     submitting the selected way drops: the values of the other ways, the preset (outside the Matching filters),
   *     the deprecated filters and identifier parameters replaced by a content
   */
  const droppedValues = function (state) {
    if (state.locked) {
      return [];
    }
    const values = state.values;
    const fields = toFields(state);
    const dropped = [];
    if (state.way !== QUERY && trim(values.query) !== '') {
      dropped.push({type: QUERY, value: trim(values.query)});
    }
    if (state.way !== LIST && values.list.length > 0) {
      dropped.push({type: LIST, value: String(values.list.length)});
    }
    if (state.way !== URL && trim(values.url) !== '') {
      dropped.push({type: URL, value: trim(values.url)});
    }
    if (state.way !== ADVANCED && trim(values.advanced.content) !== '') {
      dropped.push({type: ADVANCED, value: values.advanced.source});
    }
    if (values.presetId !== '' && fields.presetId === '') {
      dropped.push({type: 'preset', value: values.presetId});
    }
    if (values.legacyFilters !== '' && fields.filters === '') {
      dropped.push({type: 'filters', value: values.legacyFilters});
    }
    if (values.identifier !== '' && fields.identifier === '') {
      dropped.push({type: 'identifier', value: values.identifier});
    }
    return dropped;
  };

  /**
   * @param {Object} state the state of the widget
   * @return {boolean} whether the specific work items are selected without any work item picked: the macro then
   *     displays all the work items, which the user is warned about
   */
  const isEmptyList = function (state) {
    return !state.locked && state.way === LIST && state.values.list.length === 0;
  };

  /**
   * @param {Object} state the state of the widget
   * @return {Object} the state where the deprecated identifier is converted to the way replacing it, selected
   */
  const withConvertedIdentifier = function (state) {
    if (state.values.identifier === '') {
      return state;
    }
    const converted = convertIdentifier(state.values.identifier);
    const values = Object.assign({}, state.values, {identifier: ''});
    if (converted.way === URL) {
      values.url = converted.url;
    } else {
      values.list = converted.list;
    }
    return Object.assign({}, state, {way: converted.way, values});
  };

  return {
    QUERY,
    LIST,
    URL,
    ADVANCED,
    parseList,
    formatList,
    convertIdentifier,
    readInitial,
    hasAdvancedWay,
    toFields,
    droppedValues,
    isEmptyList,
    withConvertedIdentifier
  };
});
