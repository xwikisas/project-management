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
 *   without source (the advanced Live Data configuration of the macro), which is then locked: kept as it is, unless
 *   the deprecated identifier is converted.
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
   *     ({query, queryEdited, presetId, legacyFilters, unsupportedFilters, list, url, advanced: {source, content},
   *     identifier, unsupportedSeries}) and the initial fields
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
      // Whether the user edited the query (its filters or its text), which then replaces the deprecated parameters,
      // even when it's empty.
      queryEdited: false,
      presetId: initial.presetId,
      // The deprecated filters parameter, edited by the filter builder of the query way.
      legacyFilters: initial.isChart ? '' : initial.filters,
      // The conditions of the deprecated filters parameter that the filter builder can't show, as text (e.g.
      // "Description empty"), which a query replacing them doesn't have.
      unsupportedFilters: [],
      list: [],
      url: '',
      advanced: {source: '', content: ''},
      // The deprecated identifier parameter, kept until it's converted, or replaced by a content.
      identifier: initial.identifier,
      // The conditions of the series of a chart that their filter builders can't show, as text, dropped from the
      // series once the user edits them (see filterParameter.js).
      unsupportedSeries: []
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
   * @return {Array<string>} the macro parameters that the widget edits, hidden from the macro editor: the content, the
   *     deprecated identifier, which the widget converts, and the deprecated filters, which the filter builder of the
   *     query way imports (not the series of a chart)
   */
  const getEditedParameters = function (state) {
    return state.initial.isChart ? ['$content', 'identifier'] : ['$content', 'identifier', 'filters'];
  };

  /**
   * @param {Object} state the state of the widget
   * @return {boolean} whether the query way shows the deprecated filters parameter, which the macro has without query
   *     (unless the user emptied it): its filter builder imports them, and its query is the one of the imported filters
   */
  const showsLegacyFilters = function (state) {
    return !state.values.queryEdited && trim(state.values.query) === '' && state.values.legacyFilters !== '';
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
    // Only the selected way is written. The deprecated filters belong to the matching filters, whose builder shows
    // them until the user edits the query (which then replaces them, even without filter), and the deprecated
    // identifier to the way it converts to, which the macro opens on. The macros reject both with a content, and
    // ignore the source without content: each is kept while its way is selected without content, and dropped
    // otherwise.
    const withoutContent = content === '';
    const keepFilters = withoutContent && state.way === QUERY && !values.queryEdited;
    const keepIdentifier = withoutContent && values.identifier !== ''
      && state.way === convertIdentifier(values.identifier).way;
    let filters = keepFilters ? values.legacyFilters : '';
    if (state.initial.isChart) {
      filters = state.initial.filters;
    }
    return {
      content,
      source: withoutContent && state.way !== ADVANCED ? '' : getSource(state),
      // The preset is only combined with the filters (see the Matching filters way).
      presetId: state.way === QUERY ? values.presetId : '',
      filters,
      identifier: keepIdentifier ? values.identifier : ''
    };
  };

  /**
   * @param {Object} state the state of the widget
   * @return {Array<{type: string, value: string}>} the values that the user entered (or that the macro had) and that
   *     submitting the selected way drops: the values of the other ways, the preset (outside the Matching filters),
   *     the deprecated filters and identifier parameters not kept by toFields. The query replacing the deprecated
   *     filters is written from them (see showsLegacyFilters): only their conditions that the filter builder can't
   *     show are dropped (unsupportedFilters)
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
    if (values.liveData) {
      // The advanced Live Data configuration of a converted macro: only what no parameter holds is dropped.
      if (state.way !== ADVANCED && values.liveData.rest !== '') {
        dropped.push({type: 'liveData', value: values.liveData.rest});
      }
    } else if (state.way !== ADVANCED && trim(values.advanced.content) !== '') {
      dropped.push({type: ADVANCED, value: values.advanced.source});
    }
    if (values.presetId !== '' && fields.presetId === '') {
      dropped.push({type: 'preset', value: values.presetId});
    }
    if (values.legacyFilters !== '' && fields.filters === '') {
      const unsupported = values.unsupportedFilters || [];
      // The query replacing them is written from them, unless the user removed all its filters.
      if (state.way !== QUERY || fields.content === '') {
        dropped.push({type: 'filters', value: values.legacyFilters});
      } else if (unsupported.length > 0) {
        dropped.push({type: 'unsupportedFilters', value: unsupported.join(', ')});
      }
    }
    if (values.identifier !== '' && fields.identifier === '') {
      dropped.push({type: 'identifier', value: values.identifier});
    }
    return dropped;
  };

  /**
   * @param {Object} state the state of the widget
   * @return {boolean} whether the specific work items are selected without any work item picked, nor deprecated
   *     identifier kept: the macro then displays all the work items, which the user is warned about
   */
  const isEmptyList = function (state) {
    return !state.locked && state.way === LIST && state.values.list.length === 0 && toFields(state).identifier === '';
  };

  const formatMessage = function (l10n, key, parameter) {
    return String(l10n[key] || '').replace('{0}', () => parameter);
  };

  const describeDropped = function (item, l10n) {
    if (item.type === 'preset') {
      return l10n['dropped.preset'];
    }
    // A single picked work item has its own text, without number.
    if (item.type === LIST && item.value === '1') {
      return l10n['dropped.list.singular'];
    }
    return formatMessage(l10n, 'dropped.' + item.type, item.value);
  };

  /**
   * @param {Object} state the state of the widget
   * @param {Object} l10n the messages of the widget, by key (see projectmanagementworkitemsource/edit.vm), {0} being
   *     replaced by their parameter
   * @param {boolean} written whether the fields of the selected way are written, i.e. whether the user changed the
   *     selection: otherwise, Submit keeps the macro parameters as they are
   * @return {string} the warning displayed in the footer of the macro editor, empty when there's nothing to tell: what
   *     Convert moved, that all the work items are displayed when no work item is picked, and what Submit drops, the
   *     conditions dropped from the series of a chart included
   */
  const getWarning = function (state, l10n, written) {
    const messages = [];
    const dropped = [];
    if (written) {
      const liveData = state.values.liveData;
      if (liveData && state.way !== ADVANCED && Object.keys(liveData.parameters).length) {
        messages.push(l10n['liveData.moved']);
      }
      if (isEmptyList(state)) {
        messages.push(l10n.emptyList);
      }
      dropped.push(...droppedValues(state));
    }
    const series = [...new Set(state.values.unsupportedSeries || [])];
    if (series.length > 0) {
      dropped.push({type: 'unsupportedSeries', value: series.join(', ')});
    }
    if (dropped.length > 0) {
      messages.push(formatMessage(l10n, 'dropped',
        dropped.map(item => describeDropped(item, l10n)).join(l10n['dropped.separator'] || '; ')));
    }
    return messages.join(' ');
  };

  const isObject = function (value) {
    return !!value && typeof value === 'object' && !Array.isArray(value);
  };

  /**
   * Splits the advanced Live Data configuration of a macro (its content, e.g. {"query":{"properties":["status"],
   * "limit":3}}) into the values of the macro parameters holding the same settings, and the rest.
   *
   * @param {string} content the advanced Live Data configuration, as JSON
   * @param {Array<string>} parameters the parameters that can hold its settings: properties (the properties of its
   *     query) and limit (the limit of its query)
   * @return {{parameters: Object, rest: string}} the values of the parameters, by name (e.g. {properties: 'status',
   *     limit: '3'}), and the rest of the configuration as JSON, empty when nothing is left
   */
  const splitLiveDataConfiguration = function (content, parameters) {
    let configuration;
    try {
      configuration = JSON.parse(content);
    } catch (e) {
      return {parameters: {}, rest: trim(content)};
    }
    if (!isObject(configuration) || !isObject(configuration.query)) {
      return {parameters: {}, rest: trim(content)};
    }
    const query = Object.assign({}, configuration.query);
    const values = {};
    if ((parameters || []).includes('properties') && Array.isArray(query.properties) && query.properties.length
        && query.properties.every(property => typeof property === 'string' && property.trim() !== '')) {
      values.properties = query.properties.map(property => property.trim()).join(',');
      delete query.properties;
    }
    if ((parameters || []).includes('limit') && Number.isInteger(query.limit) && query.limit > 0) {
      values.limit = String(query.limit);
      delete query.limit;
    }
    const rest = Object.assign({}, configuration, {query});
    if (Object.keys(query).length === 0) {
      delete rest.query;
    }
    return {parameters: values, rest: Object.keys(rest).length ? JSON.stringify(rest) : ''};
  };

  /**
   * @param {Object} state the state of the widget
   * @param {Array<string>} parameters the parameters that can hold the settings of the advanced Live Data
   *     configuration of the macro, if it has one (see splitLiveDataConfiguration)
   * @return {Object} the state where the deprecated identifier is converted to the way replacing it, selected. The
   *     advanced Live Data configuration is unlocked, since the content now holds the converted value: its settings
   *     that a parameter holds are in values.liveData.parameters, and the rest, dropped, in values.liveData.rest
   */
  const withConvertedIdentifier = function (state, parameters) {
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
    if (state.locked) {
      values.liveData = splitLiveDataConfiguration(state.values.advanced.content, parameters);
    }
    return Object.assign({}, state, {way: converted.way, locked: false, values});
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
    getEditedParameters,
    showsLegacyFilters,
    toFields,
    droppedValues,
    isEmptyList,
    getWarning,
    splitLiveDataConfiguration,
    withConvertedIdentifier
  };
});
