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
require.config({
  paths: {
    'filterDisplayer': new XWiki.Document(new XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT)).getURL
      ('jsx', 'resource=js/projectmanagement/filterDisplayer.js&minify=true'),
    // The same path as in filterParameter.js.
    'projectManagementQueryWriter': new XWiki.Document(new XWiki.Model.resolve('Main.WebHome',
      XWiki.EntityType.DOCUMENT)).getURL('jsx', 'resource=js/projectmanagement/macro/queryWriter.js&minify=true')
  }
});
// TODO: All the dependencies are defined by the livedata so we can just require them. However, we might want to define
// their urls to not depend on livedata.
/*
 * A filter builder edits the filters of a set of work items: one row per filter, holding a property, an operator and
 * the value(s) the property is compared to. The values of a row are combined with OR, the rows with AND. Its value is
 * a map of Live Data filters, by property:
 *   property: {
 *     property: name,
 *     constraints: [{operator: name, value: val}, ...]
 *   }
 * The values of a list row are stored as one constraint per value, so a filter can hold the constraints of several
 * rows of its property.
 */
define('project-management-filter-builder', ['jquery', 'filterDisplayer', 'projectManagementQueryWriter'],
    function ($, filterDisplayer, queryWriter) {
  // The operators that take no value, e.g. "is not set" or "is open" (or the generic "empty" of the Live Data, kept for
  // the properties that have no "is not set").
  const NO_VALUE_OPERATORS = ['*', '!*', 'o', 'c', 't', 'w', 'empty'];
  // The operators excluding the values of their row, whose values are highlighted.
  const EXCLUDING_OPERATORS = ['!', '!~'];
  // The operators of the conditions on whether a property is set (IS NOT EMPTY and IS EMPTY in a query), which a
  // property proposes when the tool can apply them on it: a filter using one on another property (e.g. written by hand
  // in a query) can't be applied, which its row tells (e.g. OpenProject rejects them on the description).
  const SET_OPERATORS = ['*', '!*'];
  // Numbers the warnings of the rows, by which their operator refers to them.
  let warningCount = 0;
  // Marks a builder while the filters it's given are being read, e.g. the query of a macro (see setLoading).
  const LOADING_CLASS = 'proj-manag-query-loading';
  // Marks a builder while its properties are loading (see setSourceParameters).
  const PROPERTIES_LOADING_CLASS = 'proj-manag-properties-loading';
  // The message telling that the properties couldn't be loaded.
  const PROPERTIES_ERROR_CLASS = 'proj-manag-properties-error';

  let builders = (window.FilterBuilder && window.FilterBuilder.instances) || new Map();
  let initBuilder = (window.FilterBuilder && window.FilterBuilder.inializeBuilder) || function (element) {
    if (window.FilterBuilder && window.FilterBuilder.instances.has(element[0])) {
      return;
    }
    // A copy of a builder, e.g. a new series, loads its own properties, and is given no filters to read.
    element.removeClass(PROPERTIES_LOADING_CLASS + ' ' + LOADING_CLASS);
    element.find('.' + PROPERTIES_ERROR_CLASS).remove();
    let builder = {
      constraintBuilder: '',
      addButton: '',
      template: '',
      addPoint: '',
      cfg: '',
      l10n: {},
      // The filters on properties that the builder doesn't know (yet), e.g. on the custom fields of an instance
      // whose properties are being loaded: they are kept as they are.
      pending: [],
      propertiesRequest: 0,
      // Whether the properties are those of the previous source (e.g. another OpenProject instance) while the ones of
      // the new source are loading: see setSourceParameters.
      sourceChanging: false,
      // Whether the builder doesn't show the filters it was given: see setUnreadable.
      unreadable: false,
      // The other names under which the filters on properties were given (see addFilter), by property id: the filters
      // of these properties are written under them, so that the filters given are written back as they were given.
      givenNames: new Map()
    };

    let getType = function (property) {
      return property.filter.id || 'text';
    };
    let getRows = function (propertyId) {
      return builder.addPoint.children('.proj-manag-constraint').filter(function () {
        return propertyId === undefined || $(this).find('.proj-manag-constraint-name').val() == propertyId;
      });
    };
    let getRowProperty = function (row) {
      let propertyId = row.find('.proj-manag-constraint-name').val();
      return row.data('property') || builder.cfg.find(i => i.id == propertyId);
    };
    let getRowOperator = function (row) {
      return row.find('select.proj-manag-constraint-operator').val();
    };
    let getValueElement = function (row) {
      return row.find('input.proj-manag-constraint-value');
    };
    let takesValue = function (operator) {
      return !NO_VALUE_OPERATORS.includes(operator);
    };
    let getPropertyName = function (property) {
      return property.name || property.id;
    };
    // A translated message about a property, e.g. the text alternative of the value of its filter.
    let formatMessage = function (key, property) {
      // A function, so that the name is inserted as it is, even when it holds replacement patterns such as $&.
      return (builder.l10n[key] || '{0}').replace('{0}', () => getPropertyName(property));
    };
    let getOperatorLabel = function (property, operator) {
      let label = builder.l10n[getType(property) + '.' + operator.id];
      return label || operator.name || operator.id;
    };
    // A list takes several values in a row, the other types a single value.
    let getRowValues = function (row) {
      let property = getRowProperty(row);
      let operator = getRowOperator(row);
      if (!takesValue(operator)) {
        return [''];
      }
      // The values of a list are kept in its input, separated by commas, while their labels are being loaded.
      let value = getValueElement(row).val() || '';
      if (getType(property) === 'list') {
        return value.split(',').filter(item => item !== '');
      }
      return value ? [value] : [];
    };
    /*
     * Only a number whose filter takes a range (the "range" parameter of its filter, e.g. the progress) can be
     * filtered by two rows, a lower and an upper bound. The project management tools keep a single condition per
     * property (OpenProject keeps the last one), so that the work items having one tag and not another one, or a text
     * containing two words, can't be filtered.
     */
    let getMaxRows = function (property) {
      return getType(property) === 'number' && property.filter.range ? 2 : 1;
    };
    // The bound completing the bound of another row into a range.
    const OTHER_BOUNDS = { '>=': '<=', '<=': '>=' };
    // The rows of a property can't share an operator, and an operator taking no value is only for a single row.
    let updateOperators = function (propertyId) {
      let rows = getRows(propertyId);
      rows.each(function () {
        let row = $(this);
        let otherOperators = rows.not(row).map((index, other) => getRowOperator($(other))).get();
        row.find('select.proj-manag-constraint-operator option').each(function () {
          this.disabled = otherOperators.includes(this.value) || (rows.length > 1 && !takesValue(this.value));
        });
      });
    };
    // A property is proposed until it has as many rows as it can get.
    let updateAddButton = function () {
      builder.addButton.find('option').each(function () {
        let property = builder.cfg.find(i => i.id == this.value);
        if (!property) {
          return;
        }
        let rows = getRows(property.id);
        let full = rows.length >= getMaxRows(property)
          || rows.filter((index, row) => !takesValue(getRowOperator($(row)))).length > 0;
        this.hidden = full;
        this.disabled = full;
        $(this).text(rows.length > 0 ? formatMessage('anotherCondition', property) : getPropertyName(property));
      });
      builder.addButton.val('');
    };
    // The names of the values loaded by the list fields, by property id, kept when a field is displayed again (e.g.
    // when the operator of its row changes) until it loads them again: see getValueName. They're cleared when the
    // properties are loaded from another source (e.g. another OpenProject instance), whose values differ.
    let knownNames = new Map();
    // The name of a value written in a query is the queryValue of its option, given by the source when the name
    // identifies the value (e.g. not the name of a version that several projects have).
    let getOptionName = function (option) {
      return option && option.queryValue;
    };
    let rememberName = function (property, value, option) {
      let name = getOptionName(option);
      if (name && String(name) !== String(value)) {
        if (!knownNames.has(property.id)) {
          knownNames.set(property.id, new Map());
        }
        knownNames.get(property.id).set(String(value), String(name));
      }
    };
    let getValueLabel = function (row, value) {
      let valElem = getValueElement(row);
      let selectize = valElem[0] && valElem[0].selectize;
      let option = selectize && selectize.options[value];
      return (option && (option.label || option.text)) || value;
    };
    let joinValues = function (values, conjunction) {
      if (values.length <= 1) {
        return values.join('');
      }
      return values.slice(0, -1).join(', ') + ' ' + conjunction + ' ' + values[values.length - 1];
    };
    let describeRow = function (row) {
      let property = getRowProperty(row);
      let operatorId = getRowOperator(row);
      let operator = property.filter.operators.find(o => o.id == operatorId) || { id: operatorId };
      let description = getPropertyName(property) + ' ' + getOperatorLabel(property, operator);
      if (!takesValue(operatorId)) {
        return description;
      }
      let values = getRowValues(row);
      if (values.length === 0) {
        return null;
      }
      let type = getType(property);
      if (type === 'list' || type === 'boolean') {
        let conjunction = operatorId === '&=' ? builder.l10n.and : builder.l10n.or;
        return description + ' ' + joinValues(values.map(value => getValueLabel(row, value)), conjunction);
      } else if (type === 'date') {
        // The date, or the range of dates, as displayed by the date picker.
        let displayedDate = row.find('.proj-manag-date-val').val();
        return description + ' ' + (displayedDate || values[0]);
      } else if (type === 'text') {
        return description + ' "' + values[0] + '"';
      }
      return description + ' ' + values[0];
    };
    // Whether the filters are being read (see setLoading), or the properties loading (see setSourceParameters).
    let isLoading = function () {
      return builder.constraintBuilder.hasClass(LOADING_CLASS)
        || builder.constraintBuilder.hasClass(PROPERTIES_LOADING_CLASS);
    };
    // What the filters select, in plain words, or that they're loading (see isLoading). Nothing while the builder
    // doesn't show the filters it was given (see setUnreadable).
    let updateSummary = function () {
      let summaryElement = builder.constraintBuilder.find('.proj-manag-filter-summary');
      if (isLoading()) {
        summaryElement.text(builder.l10n.summaryLoading || '');
        return;
      } else if (builder.unreadable) {
        summaryElement.text('');
        return;
      }
      let descriptions = getRows().map((index, row) => describeRow($(row))).get().filter(text => text !== null);
      let incomplete = getRows().length - descriptions.length;
      let kept = getKeptRows().length > 0;
      // The summary doesn't describe the conditions that the builder can't show: it doesn't claim to describe all the
      // selected work items when there are some.
      let summary = '';
      if (descriptions.length > 0) {
        summary = (builder.l10n.summary || '{0}').replace('{0}', descriptions.join(', ' + builder.l10n.and + ' '));
      } else if (!kept) {
        summary = builder.l10n.summaryAll || '';
      }
      if (incomplete > 0 && builder.l10n.summaryIncomplete) {
        summary += ' ' + builder.l10n.summaryIncomplete;
      }
      if (kept && builder.l10n.summaryKept) {
        summary += ' ' + builder.l10n.summaryKept;
      }
      summaryElement.text(summary.trim());
    };
    /*
     * The filters on the properties that the builder doesn't have (e.g. a name that the tool doesn't know, written by
     * hand in a query) can't be shown as rows, but are kept as they are: each one is listed as a row naming it, with
     * the condition as written and a remove action, so that no condition is hidden. They aren't listed while the
     * properties load, which may have them (e.g. a custom field of the chosen instance).
     */
    let getKeptRows = function () {
      return builder.addPoint.children('.proj-manag-constraint-kept');
    };
    let updateKeptRows = function () {
      let filters = builder.constraintBuilder.hasClass(PROPERTIES_LOADING_CLASS) ? [] : builder.pending;
      let shown = getKeptRows().map((index, row) => $(row).data('keptCondition')).get();
      let conditions = filters.map(filter => queryWriter.toQuery([filter]));
      if (JSON.stringify(shown) === JSON.stringify(conditions)) {
        return;
      }
      getKeptRows().remove();
      filters.forEach((filter, index) => {
        let row = builder.template.clone().removeAttr('id').removeClass('hidden').addClass('proj-manag-constraint-kept')
          .data('keptCondition', conditions[index]);
        row.children('.proj-manag-constraint-name, .proj-manag-constraint-operator-container,'
          + ' .proj-manag-constraint-value-container').remove();
        row.find('.proj-manag-constraint-title').text(filter.property).after($('<div></div>')
          .addClass('box warningmessage proj-manag-constraint-kept-message')
          .text((builder.l10n.keptCondition || '{0}').replace('{0}', () => conditions[index])));
        row.find('.proj-manag-delete-filter').on('click', function (event) {
          event.preventDefault();
          builder.pending = builder.pending.filter(pending => pending !== filter);
          onChange();
        });
        builder.addPoint.append(row);
      });
    };
    // No filter can be added while the filters or the properties are loading: the filters being read would be lost,
    // and the properties proposed would be incomplete, or those of another source.
    let updateLoading = function () {
      let loading = isLoading();
      builder.constraintBuilder.attr('aria-busy', loading ? 'true' : null);
      builder.addButton.prop('disabled', loading);
      updateKeptRows();
      updateSummary();
    };
    // The filters of the rows, in their order, followed by the filters kept on the properties that the builder doesn't
    // have, listed after the rows. The filters given under another name of their property keep it (see givenNames).
    let getJson = function () {
      let resultJson = {};
      getRows().each(function () {
        let row = $(this);
        let property = getRowProperty(row);
        if (!property) {
          return;
        }
        let name = builder.givenNames.get(property.id) || property.id;
        let operator = getRowOperator(row);
        let filter = resultJson[name] || { property: name, constraints: [] };
        getRowValues(row).forEach(value => filter.constraints.push({ operator: operator, value: value }));
        if (filter.constraints.length > 0) {
          resultJson[name] = filter;
        }
      });
      builder.pending.forEach(filter => {
        let jsonFilter = resultJson[filter.property] || { property: filter.property, constraints: [] };
        jsonFilter.constraints.push(...filter.constraints);
        resultJson[filter.property] = jsonFilter;
      });
      return resultJson;
    };
    let onChange = function () {
      updateAddButton();
      updateKeptRows();
      updateSummary();
      builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
    };
    // The options of the given values in their list field, e.g. with their labels, without the data of the field.
    let getValueOptions = function (valElem, values) {
      let selectize = valElem[0] && valElem[0].selectize;
      return values.map(value => selectize && selectize.options[value]).filter(option => option)
        .map(option => Object.fromEntries(Object.entries(option).filter(([key]) => !key.startsWith('$'))));
    };
    // The row of a condition on whether its property is set, which the property doesn't propose, tells that the tool
    // can't apply it: the filter is kept as it is, until the user changes it or removes it.
    let updateSetConditionWarning = function (row, property, operator) {
      let operatorElem = row.find('select.proj-manag-constraint-operator');
      let warning = row.children('.proj-manag-constraint-unsupported');
      if (!SET_OPERATORS.includes(operator) || property.filter.operators.some(item => item.id == operator)) {
        warning.remove();
        operatorElem.removeAttr('aria-invalid aria-describedby');
        return;
      }
      if (!warning.length) {
        warning = $('<div class="box warningmessage proj-manag-constraint-unsupported"></div>')
          .attr('id', 'proj-manag-constraint-unsupported-' + (++warningCount));
        row.children('.proj-manag-constraint-value-container').after(warning);
      }
      warning.text(formatMessage('setConditionUnsupported', property));
      operatorElem.attr({ 'aria-invalid': 'true', 'aria-describedby': warning.attr('id') });
    };
    let displayValue = function (row, values, focus) {
      let property = getRowProperty(row);
      let type = getType(property);
      let operator = getRowOperator(row);
      let valElem = getValueElement(row);
      let valueContainer = row.find('.proj-manag-constraint-value-container');
      // Read before the field is cleaned.
      let valueOptions = type === 'list' && builder.sourceChanging ? getValueOptions(valElem, values) : [];
      row.toggleClass('proj-manag-constraint-excluding', EXCLUDING_OPERATORS.includes(operator));
      updateSetConditionWarning(row, property, operator);
      filterDisplayer.clean(type, valElem, row.data('displayedOperator'));
      row.data('displayedOperator', operator);
      valElem.off('change.filterBuilder dateDisplayed.filterBuilder');
      if (!takesValue(operator)) {
        valElem.val('');
        valueContainer.addClass('hidden');
        return;
      }
      valueContainer.removeClass('hidden');
      valElem.val(values.join(','));
      let displayer = property.valueDisplayer || filterDisplayer;
      // The values of a list row are picked from a list allowing several values. The displayer gives the text
      // alternative to the field that it displays, the row having no visible label for it.
      let displayerParams = $.extend({}, property.filter, {
        multiple: type === 'list',
        label: formatMessage('valueLabel', property)
      });
      if (type === 'list' && builder.sourceChanging) {
        // The values of a list come from the source of the properties: while the properties of a new source are
        // loading, no value is suggested, rather than the values of the previous source. The labels of the values
        // already displayed are kept.
        delete displayerParams.searchURL;
        displayerParams.options = valueOptions;
      }
      displayer.display(type, valElem, operator, displayerParams);
      valElem.on('change.filterBuilder', onChange);
      // The summary describes a date as the date picker displays it, once it's loaded.
      valElem.on('dateDisplayed.filterBuilder', updateSummary);
      let selectize = valElem[0].selectize;
      if (selectize) {
        // The summary displays the labels of the values, loaded after the values are displayed. The label of a value
        // that already has an option, e.g. one created from the value alone, is loaded by updating that option, which
        // the Selectize version bundled with XWiki (0.13.3, from 15.10.x to 17.10.x) doesn't signal with an event.
        selectize.on('option_add', function (value, data) {
          rememberName(property, value, data);
          updateSummary();
        });
        let updateOption = selectize.updateOption;
        selectize.updateOption = function (...args) {
          let result = updateOption.apply(this, args);
          rememberName(property, args[0], args[1]);
          updateSummary();
          return result;
        };
      }
      if (focus) {
        if (selectize) {
          selectize.focus();
        } else {
          valueContainer.find('input:visible').first().trigger('focus');
        }
      }
    };
    let addRow = function (property, operatorId, values, focus) {
      let row = builder.template.clone();
      row.removeAttr('id');
      row.removeClass('hidden');
      row.addClass('proj-manag-constraint');
      row.find('.proj-manag-constraint-title').text(getPropertyName(property));
      row.find('.proj-manag-constraint-name').val(property.id);
      row.data('property', property);
      let operatorElem = row.find('select.proj-manag-constraint-operator')
        .attr('aria-label', formatMessage('operatorLabel', property));
      property.filter.operators.forEach((operator) => {
        operatorElem.append($('<option></option>').attr('value', operator.id)
          .text(getOperatorLabel(property, operator)));
      });
      // An operator that the property doesn't propose (e.g. of a filter written by hand) is kept, named by its id,
      // rather than lost.
      if (operatorId && !property.filter.operators.some(operator => operator.id == operatorId)) {
        operatorElem.append($('<option></option>').attr('value', operatorId)
          .text(getOperatorLabel(property, { id: operatorId })));
      }
      let existingRows = getRows(property.id);
      if (!operatorId) {
        // The first operator that the other rows of the property don't use, preferably the bound completing a range
        // for a further row of a number, else an excluding one.
        let usedOperators = existingRows.map((index, other) => getRowOperator($(other))).get();
        let candidates = property.filter.operators.filter(o => !usedOperators.includes(o.id)
          && (existingRows.length === 0 || takesValue(o.id)));
        let otherBounds = usedOperators.map(used => OTHER_BOUNDS[used]);
        let operator = (existingRows.length > 0 && (candidates.find(o => otherBounds.includes(o.id))
          || candidates.find(o => EXCLUDING_OPERATORS.includes(o.id)))) || candidates[0];
        operatorId = (operator && operator.id) || property.filter.defaultOperator;
      }
      operatorElem.val(operatorId);
      // The rows of a property are kept together.
      if (existingRows.length > 0) {
        existingRows.last().after(row);
      } else {
        builder.addPoint.append(row);
      }
      operatorElem.on('change', function () {
        updateOperators(property.id);
        // The values are kept when the new operator takes the same kind of value, e.g. from "is one of" to "is not
        // one of", but not from one date to a range of dates.
        let value = getValueElement(row).val() || '';
        let values = [];
        if (getType(property) === 'list') {
          values = value.split(',').filter(item => item !== '');
        } else if (getType(property) !== 'date' && value) {
          values = [value];
        }
        displayValue(row, values, true);
        onChange();
      });
      row.find('.proj-manag-delete-filter').on('click', function (e) {
        e.preventDefault();
        filterDisplayer.clean(getType(property), getValueElement(row), getRowOperator(row));
        row.remove();
        updateOperators(property.id);
        onChange();
      });
      updateOperators(property.id);
      displayValue(row, values || [], focus);
      return row;
    };
    /*
     * Adds the rows of a Live Data filter: one row per operator for a list, the values of an operator being in the
     * same row, and one row per constraint for the other types. A filter without constraints adds an empty row.
     *
     * The constraints of a filter flagged with dropUnsupported (e.g. read from the deprecated filters parameter) whose
     * generic Live Data operator has no equivalent for the property (see queryWriter.splitSupported) are dropped, and
     * reported by the unsupportedConstraints event (the name of the property, the dropped constraints) triggered on
     * the builder.
     *
     * The property of the filter is found by its id, or by one of the other names that a query can give it (the
     * "aliases" parameter of its filter, e.g. the name of its filter in the tool), under which its filters are then
     * written. The filters on the other properties are kept as they are (see updateKeptRows).
     */
    let addFilter = function (filter, focus) {
      let property = builder.cfg.find(i => i.id == filter.property) || builder.cfg.find(i => i.filter
        && Array.isArray(i.filter.aliases) && i.filter.aliases.some(alias => alias == filter.property));
      if (property && property.id != filter.property) {
        builder.givenNames.set(property.id, filter.property);
        filter = Object.assign({}, filter, { property: property.id });
      }
      if (property) {
        // The generic operators of the Live Data (e.g. contains, in the filters parameter written by an older version)
        // are shown with the operators of the property selecting the same work items (e.g. ~ for a text).
        let operators = property.filter.operators.map(operator => operator.id);
        if (filter.dropUnsupported) {
          let split = queryWriter.splitSupported(filter, getType(property), operators);
          if (split.unsupported.length > 0) {
            builder.constraintBuilder.trigger('unsupportedConstraints', [getPropertyName(property), split.unsupported]);
            if (split.filter.constraints.length === 0) {
              return;
            }
          }
          filter = split.filter;
        } else {
          filter = queryWriter.toBuilderFilter(filter, getType(property), operators);
        }
      }
      let constraints = filter.constraints || [];
      if (!property) {
        if (constraints.length > 0) {
          let pendingFilter = builder.pending.find(pending => pending.property == filter.property);
          if (pendingFilter) {
            pendingFilter.constraints.push(...constraints);
          } else {
            // Added once its property is known, e.g. a custom field of the instance whose properties are loading.
            builder.pending.push({ property: filter.property, constraints: constraints.slice(),
              dropUnsupported: filter.dropUnsupported });
          }
          onChange();
        }
        return;
      }
      if (constraints.length === 0) {
        addRow(property, null, [], focus);
      } else if (getType(property) === 'list') {
        let valuesByOperator = new Map();
        constraints.forEach(constraint => {
          let values = valuesByOperator.get(constraint.operator) || [];
          if (constraint.value !== undefined && constraint.value !== null && constraint.value !== '') {
            values.push(constraint.value);
          }
          valuesByOperator.set(constraint.operator, values);
        });
        valuesByOperator.forEach((values, operator) => addRow(property, operator, values, focus));
      } else {
        constraints.forEach(constraint => addRow(property, constraint.operator,
          constraint.value ? [constraint.value] : [], focus));
      }
      onChange();
    };
    let addDisplayer = function (property, displayer) {
      let index = builder.cfg.findIndex(i => i.id == property);
      if (index < 0) {
        console.error(`Property [${property}] is not part of the builder configuration. Could not set displayer.`);
        return;
      }
      builder.cfg[index].valueDisplayer = displayer;
    };
    let clean = function () {
      getRows().each(function () {
        let row = $(this);
        let property = getRowProperty(row);
        if (property) {
          filterDisplayer.clean(getType(property), getValueElement(row), getRowOperator(row));
        }
      });
    };
    /*
     * Removes all the filters, including those waiting for the properties of the chosen instance, e.g. before adding
     * the filters read from another place.
     */
    let clear = function () {
      clean();
      getRows().remove();
      builder.pending = [];
      builder.givenNames.clear();
      onChange();
    };
    /*
     * Replaces the properties that can be filtered on, e.g. by the properties of the instance of the client chosen in
     * the macro editor. The rows already added are kept, and the pending filters on the new properties are added.
     *
     * @param properties the new properties
     * @param redisplayRows whether the rows already added are displayed again, e.g. when the user chooses another
     *   instance: they take the new definition of their property, e.g. the values that their value field suggests,
     *   which come from the chosen instance. A row whose property isn't part of the new properties, e.g. a custom field
     *   of the previous instance, no longer suggests values. The values of the rows are kept.
     */
    let setProperties = function (properties, redisplayRows) {
      properties.forEach(property => {
        let previous = builder.cfg.find(i => i.id == property.id);
        if (previous && previous.valueDisplayer) {
          property.valueDisplayer = previous.valueDisplayer;
        }
      });
      builder.cfg = properties;
      // The series added later are copies of this builder.
      builder.constraintBuilder.attr('data-cfg', JSON.stringify(properties)).data('cfg', properties);
      let instance = builders.get(builder.constraintBuilder[0]);
      if (instance) {
        instance.cfg = properties;
      }
      builder.addButton.find('option').filter((index, option) => option.value !== '').remove();
      properties.forEach(property => {
        builder.addButton.append($('<option></option>').attr('value', property.id).text(getPropertyName(property)));
      });
      if (redisplayRows) {
        knownNames.clear();
        getRows().each(function () {
          let row = $(this);
          let values = getRowValues(row);
          let property = properties.find(i => i.id == row.find('.proj-manag-constraint-name').val());
          if (!property) {
            property = $.extend(true, {}, getRowProperty(row));
            delete property.filter.searchURL;
          }
          row.data('property', property);
          displayValue(row, values, false);
        });
      }
      let pending = builder.pending;
      builder.pending = [];
      pending.forEach(filter => addFilter(filter));
      onChange();
    };
    /*
     * Loads the properties that can be filtered on for the given parameters of the livedata source, e.g. the instance
     * of the client. While they load, the summary tells so and no filter can be added (see updateLoading). When they
     * can't be loaded, a message tells so, and the builder keeps the properties it has.
     *
     * @param sourceParameters the parameters of the livedata source
     * @param redisplayRows whether the rows already added are displayed again with the new properties, see
     *   setProperties: the parameters select another source (e.g. the user chose another instance), whose values
     *   differ, so that the list rows don't suggest the values of the previous source while the properties load
     */
    let setSourceParameters = function (sourceParameters, redisplayRows) {
      let request = ++builder.propertiesRequest;
      let url = new XWiki.Document(XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT)).getURL('get',
        $.param({
          xpage: 'filter_properties',
          outputSyntax: 'plain',
          client: builder.constraintBuilder.attr('data-client'),
          sourceParameters: JSON.stringify(sourceParameters || {})
        }));
      // Marks the builder while its properties are loading. The message of a previous failure, if any, is removed.
      builder.constraintBuilder.addClass(PROPERTIES_LOADING_CLASS).find('.' + PROPERTIES_ERROR_CLASS).remove();
      updateLoading();
      if (redisplayRows && !builder.sourceChanging) {
        // Until the properties of the new source are loaded, the list rows, those already added and those added
        // meanwhile, suggest no value (see displayValue), and they're displayed again once the properties are loaded.
        // When they can't be loaded, the rows keep suggesting no value, the properties being those of another source.
        builder.sourceChanging = true;
        getRows().filter((index, row) => getType(getRowProperty($(row))) === 'list').each(function () {
          displayValue($(this), getRowValues($(this)), false);
        });
      }
      return $.getJSON(url).then(function (properties) {
        // Only the properties of the last parameters are used.
        if (request !== builder.propertiesRequest) {
          return;
        }
        if (!Array.isArray(properties) || properties.length === 0) {
          // The configuration of the client couldn't be resolved for these parameters (see filter_properties.vm).
          throw new Error('No properties to filter on.');
        }
        let sourceChanged = builder.sourceChanging;
        builder.sourceChanging = false;
        setProperties(properties, redisplayRows || sourceChanged);
      }).catch(function () {
        console.error(`Failed to load the properties to filter on from [${url}].`);
        // The builder keeps the properties it has, which can lack some properties (e.g. the custom fields of the
        // instance): the user is told. The filters on the properties that it doesn't know are kept.
        if (request === builder.propertiesRequest && builder.l10n.propertiesNotLoaded) {
          builder.addPoint.before($('<div class="box warningmessage"></div>').addClass(PROPERTIES_ERROR_CLASS)
            .text(builder.l10n.propertiesNotLoaded));
        }
      }).then(function () {
        if (request === builder.propertiesRequest) {
          builder.constraintBuilder.removeClass(PROPERTIES_LOADING_CLASS);
          updateLoading();
        }
      });
    };
    let setTitle = function (newTitle) {
      builder.constraintBuilder.find('.proj-manag-header-title').text(newTitle);
    };
    /*
     * Marks the builder while the filters it's given are being read, e.g. the query of a macro, parsed by the server:
     * its summary tells so instead of describing the filters it has so far (none), and no filter can be added, since
     * the filters being read would then be lost.
     */
    let setLoading = function (loading) {
      builder.constraintBuilder.toggleClass(LOADING_CLASS, loading);
      updateLoading();
    };
    /*
     * Marks the builder while it doesn't show the filters it was given, e.g. a query that it can't show, kept until
     * the filters of the user replace it: its summary isn't displayed, since the filters that it shows don't select
     * the work items.
     */
    let setUnreadable = function (unreadable) {
      builder.unreadable = unreadable;
      updateSummary();
    };
    let init = function () {
      builder.constraintBuilder = element;
      builder.addButton = builder.constraintBuilder.find('.proj-manag-add-filter');
      builder.template = builder.constraintBuilder.find('#proj-manag-constraint-template');
      builder.addPoint = builder.constraintBuilder.find('.proj-manag-constraints');
      builder.cfg = builder.constraintBuilder.data('cfg');
      builder.l10n = builder.constraintBuilder.data('l10n') || {};
      // Choosing a property adds a row for it, the select going back to its placeholder.
      let addSelectedProperty = function () {
        let selectedVal = builder.addButton.val();
        if (selectedVal) {
          addFilter({ property: selectedVal }, true);
        }
      };
      /*
       * A property picked with the mouse is added at once. The keys moving through the properties of the closed select
       * (arrows, type-ahead) select each property they reach, which the browsers signal as a change before the key is
       * released: the property is added on Enter instead, and the select goes back to its placeholder when it's left
       * without adding it.
       */
      let keyDown = false;
      builder.addButton.off('.filterBuilder').on('change.filterBuilder', function (event) {
        event.preventDefault();
        if (!keyDown) {
          addSelectedProperty();
        }
      }).on('keydown.filterBuilder', function (event) {
        if (event.key === 'Enter') {
          addSelectedProperty();
        } else {
          keyDown = true;
        }
      }).on('keyup.filterBuilder mousedown.filterBuilder', function () {
        keyDown = false;
      }).on('blur.filterBuilder', function () {
        keyDown = false;
        $(this).val('');
      });
      // Enter on a list of the builder (the property to add a filter on, the operator of a filter) or on a date whose
      // date picker is open (Enter closing it) doesn't submit the form holding the builder, e.g. the macro editor:
      // these fields use it to pick their value. Enter on a text value, or on a date once picked, still submits the
      // form, as on any text field. Listened to in the capture phase, before the date picker closes on Enter.
      let builderElement = builder.constraintBuilder[0];
      if (builderElement.preventSubmitOnEnter) {
        builderElement.removeEventListener('keydown', builderElement.preventSubmitOnEnter, true);
      }
      builderElement.preventSubmitOnEnter = function (event) {
        let field = $(event.target);
        let datePicker = field.is('.proj-manag-date-val') && field.data('daterangepicker');
        if (event.key === 'Enter' && (field.is('select') || (datePicker && datePicker.isShowing))) {
          event.preventDefault();
        }
      };
      builderElement.addEventListener('keydown', builderElement.preventSubmitOnEnter, true);
      builder.constraintBuilder.find('.proj-manag-remove-filter').off('click.filterBuilder')
        .on('click.filterBuilder', function (event) {
          // A link, to be reachable with the keyboard, that doesn't navigate.
          event.preventDefault();
          builder.constraintBuilder.trigger('builderRemoved');
          if (window.FilterBuilder && window.FilterBuilder.instances) {
            window.FilterBuilder.instances.delete(builder.constraintBuilder[0]);
          }
          builder.constraintBuilder.remove();
        });
      updateAddButton();
      updateLoading();
      $(document).trigger('filterBuilderInitialized', [builder.constraintBuilder]);
    };

    init();
    builders.set(builder.constraintBuilder[0], {
      getConstraints: getJson,
      // The name of a value of a filter written in a query: the queryValue of its option (e.g. the identifier of a
      // project, the name of a status), or undefined when the value has none or its option isn't known (yet).
      getValueName: function (propertyId, value) {
        let name;
        getRows(propertyId).each(function () {
          let selectize = getValueElement($(this))[0]?.selectize;
          let optionName = getOptionName(selectize && selectize.options[value]);
          if (optionName && String(optionName) !== String(value)) {
            name = optionName;
            return false;
          }
        });
        return name || knownNames.get(propertyId)?.get(String(value));
      },
      element: builder.constraintBuilder,
      addFilter: addFilter,
      addDisplayer: addDisplayer,
      clean: clean,
      clear: clear,
      cfg: builder.cfg,
      init: init,
      setTitle: setTitle,
      setSourceParameters: setSourceParameters,
      setLoading: setLoading,
      setUnreadable: setUnreadable
    });
  };
  /*
   * Adds a builder, e.g. a new series of a chart: a copy, without filters, of the first builder of the given container
   * (of the first builder of the page when no container is given).
   *
   * @param container the element holding the builders, e.g. the series of a chart
   * @return the new builder, or nothing when there's no builder to copy
   */
  let newBuilder = function (container) {
    let existingBuilder = container && $(container).length
      ? $(container).find('.proj-manag-constraint-builder').first()
      : $(Array.from(builders.keys())[0] || []);
    if (!existingBuilder.length) {
      return;
    }
    let cloned = existingBuilder.clone();
    cloned.find('.proj-manag-constraints').empty();
    existingBuilder.parent().append(cloned);
    window.FilterBuilder.inializeBuilder(cloned);
    return window.FilterBuilder.instances.get(cloned[0]);
  };

  $('.proj-manag-constraint-builder').each(function () {
    initBuilder($(this))
  });
  window.FilterBuilder = window.FilterBuilder || {};
  window.FilterBuilder.inializeBuilder = initBuilder;
  window.FilterBuilder.instances = builders;
  window.FilterBuilder.newBuilder = newBuilder;
  return window.FilterBuilder;
});
