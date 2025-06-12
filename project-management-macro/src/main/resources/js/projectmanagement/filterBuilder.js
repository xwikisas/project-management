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
      let key = $(root.find('select.projManagConstraintName'));
      let operator = $(root.find('select.projManagConstraintOperator'))
      let val = $(root.find('input.projManagConstraintValue'));
      if (!val.val()) {
        return;
      }
      let property = builder.cfg.find(i => i.id == key.val());
      if (!property) {
        return;
      }
      filterDisplayer.clean(property.filter.id || "text", val, operator.val());
    });
  };
  let getJson = function () {
    let resultJson = {};
    builder.constraintBuilder.find('.projManagConstraint').each(function (index, constraint) {
      let root = $(constraint);
      let key = $(root.find('select.projManagConstraintName'));
      let operator = $(root.find('select.projManagConstraintOperator'))
      let val = $(root.find('input.projManagConstraintValue'));
      if (!val.val()) {
        return;
      }
      let property = builder.cfg.find(i => i.id == key.val());
      let filter = resultJson[key.val()] || { property: key.val(), constraints: []};
      if (property.filter.id == 'list') {
        val.val().split('|').each(e => filter.constraints.push({operator: operator.val(), value: e}));
      } else {
        filter.constraints.push({operator: operator.val(), value: val.val()});
      }
      resultJson[key.val()] = filter;
    });
    return resultJson;
  };
  let addFilter = function (filter) {
    // Clone template and add it to dom.
    let newConstraint = builder.template.clone();
    newConstraint.removeAttr('id');
    newConstraint.removeClass('hidden');
    builder.addPoint.append(newConstraint);
    // Handle the operator field. It should change the value field depending on the type.
    newConstraint.find('select.projManagConstraintOperator').on('change', function (e, initFilter) {
      let operatorElem = $(e.target);
      let parent = operatorElem.closest('.projManagConstraint');
      let nameElem = parent.find('.projManagConstraintName');
      let valElem = parent.find('.projManagConstraintValue');
      let property = builder.cfg.find(i => i.id == nameElem.val());
      if (initFilter) {
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
      if (initFilter) {
        valElem.val(initFilter.constraints[0].value);
      }
      displayer.display(valueType, valElem, operatorElem.val(), displayerParams);
      valElem.on('change', function (e) {
        builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
      });
    });
    // Handle property element change. It should fill the operator element.
    newConstraint.find('select.projManagConstraintName').on('change', function (e, initFilter) {
      let keyElem = $(e.target);
      let parent = keyElem.closest('.projManagConstraint');
      let valElem = parent.find('.projManagConstraintValue');
      let operatorElem = parent.find('.projManagConstraintOperator');
      valElem.val('');
      if (initFilter) {
      keyElem.val(initFilter.property)}
      if (!keyElem.val()) {
        return;
      }
      let property = builder.cfg.find(i => i.id == keyElem.val());
      if (!property) {
        return;
      }
      operatorElem.find('option').remove();
      property.filter.operators.forEach((operator) => {
        let option = $('<option></option>').attr('value', operator.id).text(operator.name);
        operatorElem.append(option);
      });
      operatorElem.trigger('change', filter);
    }).trigger('change', [filter]);

    if (filter) {
      newConstraint.find('select.projManagConstraintName').val(filter.property).trigger('change');
      newConstraint.find('.projManagConstraintOperator').val(filter.constraints[0].operator);
    }
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
      builder.constraintBuilder = $('.projManagementConstraintBuilder');
      builder.addButton = $('.projManagementConstraintBuilder #addConstraint');
      builder.template = $('#projManagConstraintTemplate');
      builder.addPoint = $('.projManagConstraints');
      builder.cfg = builder.constraintBuilder.data('cfg');
      builder.addButton.on('click', function (event) {
        event.preventDefault();
        addFilter();
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