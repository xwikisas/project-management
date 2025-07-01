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
    ('jsx', 'resource=js/projectmanagement/filterDisplayer.js&minify=false')
  }
});
// TODO: All the dependencies are defined by the livedata so we can just require them. However, we might want to define
// their urls to not depend on livedata.
define('project-management-filter-builder', ['jquery', 'filterDisplayer'], function ($, filterDisplayer) {
  let builder = {
    constraintBuilder: '',
    addButton: '',
    template: '',
    addPoint: '',
    cfg: ''
  };
  let clean = function () {
    builder.constraintBuilder.find('.projManagConstraint').each(function (index, constraint) {
      let root = $(constraint);
      let key = $(root.find('.projManagConstraintName'));
      let operators = $(root.find('select.proj-manag-constraint-operator'))
      let vals = $(root.find('input.proj-manag-constraint-value'));
      let property = builder.cfg.find(i => i.id == key.val());
      if (!property) {
        return;
      }
      for (i = 0; i > operators.length; i++) {
        filterDisplayer.clean();
      }

      filterDisplayer.clean(property.filter.id || "text", $(vals[i]), $(operators[i]).val());
    });
  };
  let getJson = function () {
    let resultJson = {};
    builder.constraintBuilder.find('.projManagConstraint').each(function (index, constraint) {
      let root = $(constraint);
      let key = $(root.find('.projManagConstraintName'));
      let operators = $(root.find('.proj-manag-constraint-operator'));
      let vals = $(root.find('input.proj-manag-constraint-value'));
      if (vals.length <= 0) {
        return;
      }
      let filter = resultJson[key.val()] || { property: key.val(), constraints: []};
      let property = builder.cfg.find(i => i.id == key.val());
      for (i = 0; i < operators.length; i++) {
        let opName = $(operators[i]).val();
        let val = $(vals[i]).val();
        if (val == '' && (!property.emptyOperators || !property.emptyOperators.filter(o => o.id == opName))) {
          continue;
        }
        filter.constraints.push( { operator: opName, value: val });
      }
      if (filter.constraints.length > 0) {
        resultJson[key.val()] = filter;
      }
    });
    return resultJson;
  };
  /*
    filter:
      property: name
      constraints: [
        {
          operator: name
          value: val
        }
      ]
  */
  let addFilter = function (filter) {
    // Clone template and add it to dom or find the already existing one.
    let constraint = builder.constraintBuilder
      .find('.proj-manag-constraints')
      .find('.projManagConstraintName')
      .filter((i, j) => $(j).val() == filter.property)
      .closest('.projManagConstraint');
    let operatorValueContainer = null;
    if (constraint.length <= 0) {
      constraint = builder.template.clone();
      builder.addPoint.append(constraint);
      constraint.removeAttr('id');
      constraint.removeClass('hidden');
      constraint.addClass('projManagConstraint');
      constraint.find('.proj-manag-constraint-title').text(filter.property);
      operatorValueContainer = constraint.find('.proj-manag-filter-container');
      constraint.find('.projManagConstraintName').val(filter.property);
    } else {
      operatorValueContainer = builder.template.find('.proj-manag-filter-container').clone();
      constraint.find('.proj-manag-add-constraint').before(operatorValueContainer);
    }
    // Handle the add constraint link.
    let addConstraintElem = constraint.find('.proj-manag-add-constraint');
    if (!addConstraintElem.hasClass('listenerConstraintAdded')) {
      addConstraintElem.on('click', function(e) {
        e.preventDefault();
        addFilter({ property: filter.property});
      }).addClass('listenerConstraintAdded');
    }
    // Handle the operator field. It should change the value field depending on the type.
    let operatorElem = operatorValueContainer.find('.proj-manag-constraint-operator');
    if (filter.constraints && filter.constraints.length > 0) {
      operatorElem.val(filter.constraints[0].operator)
    }
    let property = builder.cfg.find(i => i.id == filter.property);
    operatorElem.on('change', function (e, initFilter) {
      let operatorElem = $(e.target);
      let parent = operatorElem.closest('.proj-manag-filter-container');
      let valElem = parent.find('.proj-manag-constraint-value');
      if (initFilter && initFilter.constraints) {
        operatorElem.val(initFilter.constraints[0].operator)
      }
      // If the operator does not have a value defined, it means it doesnt need a value.
      if (property.emptyOperators && property.emptyOperators.filter(o => o.id == operatorElem.val())) {
        let parent = valElem.parent();
        parent.children().remove();
        parent.append(valElem);
        valElem.hide();
        return;
      } else {
        valElem.show();
      }

      let valueType = property.filter.id || "text";
      let displayer = property.valueDisplayer || filterDisplayer;
      let displayerParams = property.filter || {};
      valElem.val('');
      if (initFilter && initFilter.constraints) {
        valElem.val(initFilter.constraints[0].value);
      }
      displayer.display(valueType, valElem, operatorElem.val(), displayerParams);
      valElem.on('change', function (e) {
        builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
      });
      builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
    });
    // Init the operator element with the possible operators.
    if (!property) {
      return;
    }
    property.filter.operators.forEach((operator) => {
      let option = $('<option></option>').attr('value', operator.id).text(operator.name);
      operatorElem.append(option);
    });
    operatorElem.trigger('change', filter);

    builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
  };
  let addDisplayer = function(property, displayer) {
    let index = builder.cfg.findIndex(i => i.id == property);
    if (index < 0) {
      console.error(`Property [${property}] is not part of the builder configuration. Could not set displayer.`);
      return;
    }
    builder.cfg[index].valueDisplayer = displayer;
  };
  let init = function () {
      builder.constraintBuilder = $('.proj-manag-constraint-builder');
      builder.addButton = $('.proj-manag-constraint-builder #proj-manag-add-constraint');
      builder.template = $('#proj-manag-constraint-template');
      builder.addPoint = $('.proj-manag-constraints');
      builder.cfg = builder.constraintBuilder.data('cfg');
      builder.addButton.on('change', function (event) {
        event.preventDefault();
        let selectedVal = $(this).val();
        $(this).val('');
        $(this).find(`option[value='${selectedVal}']`).remove();
        addFilter( { property: selectedVal });
      });
  };
  init();
  let builderExport = {
    getConstraints: getJson,
    element: builder.constraintBuilder,
    addFilter: addFilter,
    addDisplayer: addDisplayer,
    clean: clean,
    cfg: builder.cfg,
    init: init
  };
  window.FilterBuilder = builderExport;
  return builderExport;
});