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
 * Tracks, for each filter builder, whether the user edited its filters, so that the query of a builder is only
 * reported as changed once the user edits that builder: the builders update their filters by themselves too, e.g.
 * when the properties of the chosen instance are loaded, and a chart has other builders (its series) next to the one
 * of the matching filters.
 *
 * The builders are known by their element; the user actions are recognized from the elements they target, through
 * Element#closest, so that this module has no dependency and can be run outside of a browser.
 */
define([], function () {
  'use strict';

  const BUILDER = '.proj-manag-constraint-builder';
  const DATE_PICKER = '.daterangepicker';
  const FILTER_FIELD = '.proj-manag-constraint-operator, .proj-manag-constraint-value-container';

  /**
   * @param {Array<{property, constraints}>} filters the filters of a builder
   * @return {string} a description of the filters, the same whatever their order
   */
  const describeFilters = function (filters) {
    return JSON.stringify((filters || []).map(filter => [String(filter.property), (filter.constraints || [])
      .map(constraint => constraint.operator + ':' + constraint.value).sort()]).sort());
  };

  /**
   * @return {Object} a tracker of the edits of the builders, by builder element
   */
  const createTracker = function () {
    // By builder element: whether the user edited it, the property of the filter whose operator or value the user
    // last changed, and the description of the filters read from the macro (null while they're being read).
    const states = new Map();
    const getState = function (element) {
      let state = states.get(element);
      if (!state) {
        state = {edited: false, editedProperty: null, readFilters: null};
        states.set(element, state);
      }
      return state;
    };
    const isChanged = function (element, filters) {
      const readFilters = getState(element).readFilters;
      return readFilters !== null && describeFilters(filters) !== readFilters;
    };
    return {
      /**
       * Records a user action (a click, a key or a change) on the given element: when it's inside a builder, or inside
       * the date picker of one of its filters (displayed outside of the builders: the one of the filter whose field
       * has the focus), that builder is edited, and no other.
       *
       * @param {Element} target the element targeted by the action
       * @param {Element} activeElement the element having the focus
       * @return {Element} the element of the edited builder, or null
       */
      recordAction: function (target, activeElement) {
        const field = target.closest(DATE_PICKER) ? activeElement : target;
        const element = field && field.closest(BUILDER);
        if (!element) {
          return null;
        }
        const state = getState(element);
        state.edited = true;
        const row = field.closest('.proj-manag-constraint');
        const onFilter = row && field.closest(FILTER_FIELD);
        const nameField = onFilter && row.querySelector('.proj-manag-constraint-name');
        state.editedProperty = nameField ? nameField.value : null;
        return element;
      },
      /**
       * @param {Element} element the element of a builder
       * @param {Array} filters the filters read from the macro, that the builder shows
       */
      setReadFilters: function (element, filters) {
        getState(element).readFilters = describeFilters(filters);
      },
      isChanged,
      /**
       * @param {Element} element the element of a builder
       * @param {Array} filters the filters of the builder
       * @return {boolean} whether the user edited the builder, or its filters differ from the ones read from the
       *     macro (the events of some tools, e.g. a script or an assistive technology, being untrusted); once edited,
       *     the builder stays edited
       */
      isEdited: function (element, filters) {
        const state = getState(element);
        state.edited = state.edited || isChanged(element, filters);
        return state.edited;
      },
      /**
       * @param {Element} element the element of a builder
       * @return {string} the property of the filter whose operator or value the user last changed, or null when the
       *     last action of the user on the builder was another one, e.g. adding or removing a filter
       */
      getEditedProperty: function (element) {
        return getState(element).editedProperty;
      },
      /**
       * @param {Element} element the element of a builder
       */
      clearEditedProperty: function (element) {
        getState(element).editedProperty = null;
      },
      /**
       * Forgets the edits of the given builder, e.g. when it shows another query.
       *
       * @param {Element} element the element of a builder
       */
      reset: function (element) {
        states.delete(element);
      },
      /**
       * Forgets the edits of all the builders, e.g. when the macro editor is closed.
       */
      clear: function () {
        states.clear();
      }
    };
  };

  return {describeFilters, createTracker};
});
