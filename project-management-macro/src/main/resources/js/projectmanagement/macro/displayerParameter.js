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
  // The keys are the values of the displayer parameter. The single and inline displayers display the first work item
  // only, with properties of their own.
  const unneededParams = {
    single: ["sort", "offset", "limit", "properties"],
    inline: ["sort", "offset", "limit", "properties"],
    list: ["properties"],
    enum: ["properties"]
  };
  // The values of the deprecated workItemsDisplayer parameter, and the matching values of the displayer parameter.
  const legacyDisplayers = {
    liveData: 'table',
    liveDataCards: 'cards',
    workItemsSingle: 'single',
    workItemInline: 'inline',
    workItemsList: 'list',
    workItemsEnum: 'enum'
  };
  // Whether the edited macro call sets the displayer parameter. The displayer select can't tell, since it always has a
  // value (the default one when the parameter is missing), so look at the macro call the macro editor was opened with.
  let hasDisplayerParameter = function (displayerSelect) {
    let input = displayerSelect.closest('.modal').data('input') || {};
    let parameters = (input.macroCall && input.macroCall.parameters) || {};
    return Object.keys(parameters).some(key => key.toLowerCase() === 'displayer');
  };
  // Hide the field of a deprecated parameter whose value was migrated, so that the macro editor doesn't display it
  // empty, and its tab when it holds no other visible field, activating the first visible tab if it was the active one
  // (the macro editor displays the deprecated parameters set by the macro call, on the tab of their group).
  // Keep in sync with the copy in project-management-openproject-macro's openproject/js/countParameter.js.
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
  // Edit a macro using the deprecated parameter with the displayer one, so that the displayer chosen in the editor,
  // the default one included, isn't overridden by the deprecated parameter left in the macro. The displayer parameter
  // takes precedence over the deprecated one, so the deprecated value is used only when the macro call has no displayer
  // parameter.
  let migrateLegacyDisplayer = function () {
    let legacyInput = $('.macro-editor :input[name="workItemsDisplayer"]');
    let displayerSelect = $('.macro-editor select[name="displayer"]');
    let legacyValue = Object.keys(legacyDisplayers).find(key => key.toLowerCase() ===
      (legacyInput.val() || '').toLowerCase());
    if (legacyValue && displayerSelect.length > 0) {
      if (!hasDisplayerParameter(displayerSelect)) {
        displayerSelect.val(legacyDisplayers[legacyValue]);
      }
      legacyInput.val('');
      hideParameter(legacyInput);
    }
  };
  let initParams = function () {
    migrateLegacyDisplayer();
    hideShowParams();
  };
  let hideShowParams = function () {
    let displayerVal = ($('.macro-editor select[name="displayer"]').val() || '').toLowerCase();
    $('.macro-editor').find('.macro-parameter').each(function () {
      let element = $(this);
      let foundInput = element.find('.macro-parameter-field :input');
      if (foundInput.length > 0 && (unneededParams[displayerVal] || []).indexOf(foundInput.attr('name')) > -1) {
        element.hide();
      } else {
        element.show();
      }
    });
  };
  $(document).on('change', '.macro-editor select[name="displayer"]', hideShowParams);
  $(document).on('show.bs.modal', '.modal', function () {
    let modal = $(this);
    setTimeout(function () {
      const content = modal.find('.macro-editor');
      if (!content || content.length <= 0) {
        console.log('Modal content not found');
        return;
      }
      if (!content[0].classList.contains('loading')) {
        initParams();
        return;
      }
      const observer = new MutationObserver(() => {
        if (!content[0].classList.contains('loading')) {
          observer.disconnect();
          initParams();
        }
      });

      observer.observe(content[0], {
        attributes: true,
        attributeFilter: ['class'],
        subtree: true
      });
    });
    setTimeout(initParams);
  });
  // The script is loaded by the displayer field, i.e. once the macro editor is already displayed.
  initParams();
});