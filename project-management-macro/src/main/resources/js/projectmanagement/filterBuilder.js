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

  let builders = (window.FilterBuilder && window.FilterBuilder.instances) || new Map();
  let initBuilder = (window.FilterBuilder && window.FilterBuilder.inializeBuilder) || function (element) {
    if (window.FilterBuilder && window.FilterBuilder.instances.has(element[0])) {
      return;
    }
    // A copy of a builder, e.g. a new series, loads its own properties.
    element.removeClass('proj-manag-properties-loading');
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
      propertiesRequest: 0
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
    // What the filters select, in plain words.
    let updateSummary = function () {
      let descriptions = getRows().map((index, row) => describeRow($(row))).get().filter(text => text !== null);
      let incomplete = getRows().length - descriptions.length;
      let summary = descriptions.length > 0
        ? (builder.l10n.summary || '{0}').replace('{0}', descriptions.join(', ' + builder.l10n.and + ' '))
        : builder.l10n.summaryAll || '';
      if (incomplete > 0 && builder.l10n.summaryIncomplete) {
        summary += ' ' + builder.l10n.summaryIncomplete;
      }
      builder.constraintBuilder.find('.proj-manag-filter-summary').text(summary);
    };
    let getJson = function () {
      let resultJson = {};
      builder.pending.forEach(filter => {
        resultJson[filter.property] = { property: filter.property, constraints: filter.constraints.slice() };
      });
      getRows().each(function () {
        let row = $(this);
        let property = getRowProperty(row);
        if (!property) {
          return;
        }
        let operator = getRowOperator(row);
        let filter = resultJson[property.id] || { property: property.id, constraints: [] };
        getRowValues(row).forEach(value => filter.constraints.push({ operator: operator, value: value }));
        if (filter.constraints.length > 0) {
          resultJson[property.id] = filter;
        }
      });
      return resultJson;
    };
    let onChange = function () {
      updateAddButton();
      updateSummary();
      builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
    };
    let displayValue = function (row, values, focus) {
      let property = getRowProperty(row);
      let type = getType(property);
      let operator = getRowOperator(row);
      let valElem = getValueElement(row);
      let valueContainer = row.find('.proj-manag-constraint-value-container');
      row.toggleClass('proj-manag-constraint-excluding', EXCLUDING_OPERATORS.includes(operator));
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
     */
    let addFilter = function (filter, focus) {
      let property = builder.cfg.find(i => i.id == filter.property);
      if (property) {
        // The generic operators of the Live Data (e.g. contains, in the filters parameter written by an older version)
        // are shown with the operators of the property selecting the same work items (e.g. ~ for a text).
        filter = queryWriter.toBuilderFilter(filter, getType(property),
          property.filter.operators.map(operator => operator.id));
      }
      let constraints = filter.constraints || [];
      if (!property) {
        if (constraints.length > 0) {
          let pendingFilter = builder.pending.find(pending => pending.property == filter.property);
          if (pendingFilter) {
            pendingFilter.constraints.push(...constraints);
          } else {
            builder.pending.push({ property: filter.property, constraints: constraints.slice() });
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
     * of the client.
     *
     * @param sourceParameters the parameters of the livedata source
     * @param redisplayRows whether the rows already added are displayed again with the new properties, see
     *   setProperties
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
      // Marks the builder while its properties are loading.
      builder.constraintBuilder.addClass('proj-manag-properties-loading');
      return $.getJSON(url).then(function (properties) {
        // Only the properties of the last parameters are used.
        if (request === builder.propertiesRequest && Array.isArray(properties) && properties.length > 0) {
          setProperties(properties, redisplayRows);
        }
      }).catch(function () {
        console.error(`Failed to load the properties to filter on from [${url}].`);
      }).then(function () {
        if (request === builder.propertiesRequest) {
          builder.constraintBuilder.removeClass('proj-manag-properties-loading');
        }
      });
    };
    let setTitle = function (newTitle) {
      builder.constraintBuilder.find('.proj-manag-header-title').text(newTitle);
    };
    let init = function () {
      builder.constraintBuilder = element;
      builder.addButton = builder.constraintBuilder.find('.proj-manag-add-filter');
      builder.template = builder.constraintBuilder.find('#proj-manag-constraint-template');
      builder.addPoint = builder.constraintBuilder.find('.proj-manag-constraints');
      builder.cfg = builder.constraintBuilder.data('cfg');
      builder.l10n = builder.constraintBuilder.data('l10n') || {};
      // Choosing a property adds a row for it, the select going back to its placeholder.
      builder.addButton.off('change.filterBuilder').on('change.filterBuilder', function (event) {
        event.preventDefault();
        let selectedVal = $(this).val();
        if (selectedVal) {
          addFilter({ property: selectedVal }, true);
        }
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
        .on('click.filterBuilder', function () {
          builder.constraintBuilder.trigger('builderRemoved');
          if (window.FilterBuilder && window.FilterBuilder.instances) {
            window.FilterBuilder.instances.delete(builder.constraintBuilder[0]);
          }
          builder.constraintBuilder.remove();
        });
      updateAddButton();
      updateSummary();
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
      setSourceParameters: setSourceParameters
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
