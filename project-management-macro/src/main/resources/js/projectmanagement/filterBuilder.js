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
define('project-management-filter-builder', ['jquery', 'xwiki-selectize'], function($) {
  let constraintBuilder = $('.projManagementConstraintBuilder');
  let addButton = $('.projManagementConstraintBuilder #addConstraint');
  let template = $('#projManagConstraintTemplate');
  let addPoint = $('.projManagConstraints');
  let getJson = function() {
    let resultJson = {};
    constraintBuilder.find('.projManagConstraint').each(function (index, constraint) {
      let root = $(constraint);
      let key = $(root.find('select.projManagConstraintName'));
      let val = $(root.find('input.projManagConstraintValue'));
      if (!val.val()) {
        return;
      }
      if (resultJson[key.val()] && resultJson[key.val()].push) {
        resultJson[key.val()].push(val.val())
      } else {
        resultJson[key.val()] = [val.val()];
      }
    });
    return resultJson;
  };
  let addFilter = function (key, value) {
    // Clone template and add it to dom.
    let newConstraint = template.clone();
    newConstraint.removeAttr('id');
    newConstraint.removeClass('hidden');
    addPoint.append(newConstraint);
    if (key && value) {
      newConstraint.find('select.projManagConstraintName').val(key);
      newConstraint.find('.projManagConstraintValue').val(value);
    }
    newConstraint.find('select').xwikiSelectize({});
    newConstraint.find('input.projManagConstraintValue').on('change', function(e) {
      constraintBuilder.trigger('constraintsUpdated', [getJson()]);
    });
    // Clear the value input when the field is changed.
    newConstraint.find('select.projManagConstraintName').on('change', function(e) {
      let keyElem = $(e.target);
      let parent = keyElem.closest('.projManagConstraint');
      let valElem = parent.find('.projManagConstraintValue');
      valElem.clear();
    });
  };
  addButton.on('click', function (event) {
    event.preventDefault();
    addFilter();
  });
  return {
    getConstraints: getJson,
    element: constraintBuilder,
    addFilter: addFilter
  };
});