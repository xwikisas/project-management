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
require(['jquery'], function ($) {
  // The macros having the deprecated count parameter.
  const macrosWithCount = ['openproject-news', 'openproject-projects', 'openproject-subprojects',
    'openproject-project-members', 'openproject-spent-time'];

  // Returns the value of a parameter in the edited macro call, or undefined when the call doesn't set it. The macro
  // editor fills the inputs of the parameters that the call doesn't set with their default value (the default value of
  // count being the one of limit), so the call is the only way to know which parameters are really set.
  let getParameterValue = function (macroCall, name) {
    let parameters = (macroCall && macroCall.parameters) || {};
    let key = Object.keys(parameters).find(key => key.toLowerCase() === name);
    let value = key === undefined ? undefined : parameters[key];
    if (value && typeof value === 'object' && typeof value.name === 'string') {
      value = value.value;
    }
    return value === undefined || value === null ? undefined : String(value);
  };

  // Hide the field of a deprecated parameter whose value was migrated, so that the macro editor doesn't display it empty,
  // and its tab when it holds no other visible field, activating the first visible tab if it was the active one (the
  // macro editor displays the deprecated parameters set by the macro call, on the tab of their group).
  let hideParameter = function (input) {
    let tabPanel = input.closest('.macro-parameter').addClass('hidden').closest('.tab-pane');
    while (tabPanel.length > 0 && tabPanel.find('.macro-parameter').not('.hidden').length === 0) {
      let tabPanels = tabPanel.parent().children();
      let tabs = tabPanel.parent().siblings('[role="tablist"]').children();
      tabs.eq(tabPanels.index(tabPanel)).add(tabPanel).addClass('hidden');
      if (tabPanel.hasClass('active')) {
        tabs.add(tabPanels).removeClass('active');
        tabs.not('.hidden').first().add(tabPanels.not('.hidden').first()).addClass('active');
      }
      tabPanel = tabPanel.parent().closest('.tab-pane');
    }
  };

  // Edit a macro using the deprecated count parameter with the limit one, so that the limit chosen in the editor, the
  // default one included, isn't overridden by the deprecated parameter left in the macro. The limit wins over the count
  // when the call sets both. This script is loaded by the instance parameter, which all the macros having a count
  // parameter have.
  let migrateCount = function (macroEditor) {
    let macroId = (macroEditor.attr('data-macroId') || '').split('/')[0];
    let countInput = macroEditor.find(':input[name="count"]');
    let limitInput = macroEditor.find(':input[name="limit"]');
    // Migrate once per display of the parameters, which are displayed again for each edited macro.
    if (macrosWithCount.indexOf(macroId) < 0 || countInput.length === 0 || limitInput.length === 0
        || countInput.data('countMigrated')) {
      return;
    }
    countInput.data('countMigrated', true);
    let macroCall = (macroEditor.closest('.modal').data('input') || {}).macroCall;
    let count = getParameterValue(macroCall, 'count');
    if (count !== undefined && count.trim() !== '' && getParameterValue(macroCall, 'limit') === undefined) {
      limitInput.val(count.trim());
    }
    // Always clear the count, whose input holds at least its default value, so that it isn't saved.
    countInput.val('');
    hideParameter(countInput);
  };
  // The macro editor sends this event once it displays the parameters of the edited macro.
  $(document).on('xwiki:dom:updated', function (event, data) {
    $((data && data.elements) || []).filter('.macro-editor').each(function () {
      migrateCount($(this));
    });
  });
  // The script is loaded by the instance field, i.e. once the macro editor is already displayed, after the event.
  $('.macro-editor').each(function () {
    migrateCount($(this));
  });
});
