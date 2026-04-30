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
console.log('Hello there');
define('project-management-filter-builder', ['jquery', 'filterDisplayer'], function ($, filterDisplayer) {
  let builders = (window.FilterBuilder && window.FilterBuilder.instances) || new Map();
  let initBuilder = (window.FilterBuilder && window.FilterBuilder.inializeBuilder) || function (element) {
    if (window.FilterBuilder && window.FilterBuilder.instances.has(element[0])) {
      return;
    }
    let builder = {
      constraintBuilder: '',
      addButton: '',
      template: '',
      addPoint: '',
      cfg: ''
    };
    let clean = function () {
      builder.constraintBuilder.find('.proj-manag-constraint').each(function (index, constraint) {
        let root = $(constraint);
        let key = $(root.find('.proj-manag-constraint-name'));
        let operators = $(root.find('select.proj-manag-constraint-operator'))
        let vals = $(root.find('input.proj-manag-constraint-value'));
        let property = builder.cfg.find(i => i.id == key.val());
        if (!property) {
          return;
        }
        for (i = 0; i > operators.length; i++) {
          filterDisplayer.clean(property.filter.id || "text", $(vals[i]), $(operators[i]).val());
        }
      });
    };
    let getJson = function () {
      let resultJson = {};
      builder.constraintBuilder.find('.proj-manag-constraint').each(function (index, constraint) {
        let root = $(constraint);
        let key = $(root.find('.proj-manag-constraint-name'));
        let operators = $(root.find('.proj-manag-constraint-operator'));
        let vals = $(root.find('input.proj-manag-constraint-value'));
        if (vals.length <= 0) {
          return;
        }
        let filter = resultJson[key.val()] || { property: key.val(), constraints: [] };
        let property = builder.cfg.find(i => i.id == key.val());
        for (i = 0; i < operators.length; i++) {
          let opName = $(operators[i]).val();
          let val = $(vals[i]).val();
          filter.constraints.push({ operator: opName, value: val });
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
      let property = builder.cfg.find(i => i.id == filter.property);
      if (!property) {
        return;
      }
      // Clone template and add it to dom or find the already existing one.
      let constraint = builder.constraintBuilder
        .find('.proj-manag-constraints')
        .find('.proj-manag-constraint-name')
        .filter((i, j) => $(j).val() == filter.property)
        .closest('.proj-manag-constraint');
      let operatorValueContainer = null;
      if (constraint.length <= 0) {
        constraint = builder.template.clone();
        builder.addPoint.append(constraint);
        constraint.removeAttr('id');
        constraint.removeClass('hidden');
        constraint.addClass('proj-manag-constraint');
        constraint.find('.proj-manag-constraint-title').text(property.name || property.id);
        operatorValueContainer = constraint.find('.proj-manag-filter-container');
        constraint.find('.proj-manag-constraint-name').val(filter.property);
      } else {
        operatorValueContainer = builder.template.find('.proj-manag-filter-container').clone();
        constraint.find('.proj-manag-add-constraint').before(operatorValueContainer);
      }
      // Handle the add constraint link.
      let addConstraintElem = constraint.find('.proj-manag-add-constraint');
      if (!addConstraintElem.hasClass('listenerConstraintAdded')) {
        addConstraintElem.on('click', function (e) {
          e.preventDefault();
          addFilter({ property: filter.property });
        }).addClass('listenerConstraintAdded');
      }
      // Handle the operator field. It should change the value field depending on the type.
      let operatorElem = operatorValueContainer.find('.proj-manag-constraint-operator');
      if (filter.constraints && filter.constraints.length > 0) {
        operatorElem.val(filter.constraints[0].operator)
      }
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
      let valElem = operatorValueContainer.find('.proj-manag-constraint-value');
      constraint.find('.proj-manag-delete-filter').on('click', function (e) {
        e.preventDefault();
        filterDisplayer.clean(property.filter.id || "text", valElem, operatorElem.val());
        if ($(this).closest('.proj-manag-constraint').find('.proj-manag-filter-container').length > 1) {
          $(this).closest('.proj-manag-filter-container').remove();
        } else {
          constraint.remove();
        }
        builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
      });
      // Init the operator element with the possible operators.
      property.filter.operators.forEach((operator) => {
        let option = $('<option></option>').attr('value', operator.id).text(operator.name);
        operatorElem.append(option);
      });
      operatorElem.trigger('change', filter);

      builder.constraintBuilder.trigger('constraintsUpdated', [getJson()]);
    };
    let addDisplayer = function (property, displayer) {
      let index = builder.cfg.findIndex(i => i.id == property);
      if (index < 0) {
        console.error(`Property [${property}] is not part of the builder configuration. Could not set displayer.`);
        return;
      }
      builder.cfg[index].valueDisplayer = displayer;
    };
    let setTitle = function(newTitle) {
      debugger;
      builder.constraintBuilder.find('.proj-manag-header-title').text(newTitle);
    }
    let init = function () {
      builder.constraintBuilder = element;
      builder.addButton = builder.constraintBuilder.find('#proj-manag-add-constraint');
      builder.template = builder.constraintBuilder.find('#proj-manag-constraint-template');
      builder.addPoint = builder.constraintBuilder.find('.proj-manag-constraints');
      builder.cfg = builder.constraintBuilder.data('cfg');
      builder.addButton.on('change', function (event) {
        event.preventDefault();
        let selectedVal = $(this).val();
        $(this).val('');
        $(this).find(`option[value='${selectedVal}']`).remove();
        addFilter({ property: selectedVal });
      });
      builder.constraintBuilder.find('.proj-manag-remove-filter').on('click', function() {
        builder.constraintBuilder.trigger('builderRemoved');
        if (window.FilterBuilder && window.FilterBuilder.instances) {
          window.FilterBuilder.instances.delete(builder.constraintBuilder[0]);
        }
        builder.constraintBuilder.remove();
      });
      $(document).trigger('filterBuilderInitialized', [builder.constraintBuilder]);
      // if (window.FilterBuilder) {
      //   window.FilterBuilder.instances[builder.constraintBuilder] = builder.constraintBuilder;
      // }
    };

    init();
    builders.set(builder.constraintBuilder[0], {
      getConstraints: getJson,
      element: builder.constraintBuilder,
      addFilter: addFilter,
      addDisplayer: addDisplayer,
      clean: clean,
      cfg: builder.cfg,
      init: init,
      setTitle: setTitle
    });
  };
  let newBuilder = (window.FilterBuilder && window.FilterBuilder.newBuilder()) || function () {
    if (!window.FilterBuilder || window.FilterBuilder.instances.size <= 0) {
      return;
    }
    let existingBuilder = window.FilterBuilder.instances.values().next().value.element;
    let cloned = existingBuilder.clone();
    cloned.find('.proj-manag-constraints').empty();
    existingBuilder.parent().append(cloned);
    window.FilterBuilder.inializeBuilder(cloned);
    return window.FilterBuilder.instances[cloned[0]];
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
