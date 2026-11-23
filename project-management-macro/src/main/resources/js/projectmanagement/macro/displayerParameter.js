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
    'projectmanagement-macro-editor-layout': new XWiki.Document(XWiki.Model.resolve('Main.WebHome',
      XWiki.EntityType.DOCUMENT)).getURL('jsx', 'resource=js/projectmanagement/macro/macroEditorLayout.js')
  }
});
require(['jquery', 'projectmanagement-macro-editor-layout'], function ($, layout) {
  // The keys are the values of the displayer parameter. The single and inline displayers display the first work item
  // only, with properties of their own: the sort and the offset still choose which work item comes first. The page
  // sizes and the id only matter for the table and cards displayers.
  const unneededParams = {
    single: ["limit", "properties", "pageSizes", "id"],
    inline: ["limit", "properties", "pageSizes", "id"],
    list: ["properties", "pageSizes", "id"],
    enum: ["properties", "pageSizes", "id"]
  };
  // The advanced parameters of the Display group, grouped under a "More options" toggle.
  const moreOptions = ['pageSizes', 'id'];
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
  // Edit a macro using the deprecated parameter with the displayer one, so that the displayer chosen in the editor,
  // the default one included, isn't overridden by the deprecated parameter left in the macro. The displayer parameter
  // takes precedence over the deprecated one, so the deprecated value is used only when the macro call has no displayer
  // parameter. The field of the deprecated parameter is hidden, so that the macro editor doesn't display it empty.
  let migrateLegacyDisplayer = function (editor) {
    let legacyInput = editor.find(':input[name="workItemsDisplayer"]');
    let displayerSelect = editor.find('select[name="displayer"]');
    let legacyValue = Object.keys(legacyDisplayers).find(key => key.toLowerCase() ===
      (legacyInput.val() || '').toLowerCase());
    if (legacyValue && displayerSelect.length > 0) {
      if (!hasDisplayerParameter(displayerSelect)) {
        displayerSelect.val(legacyDisplayers[legacyValue]);
      }
      legacyInput.val('');
      layout.hideParameter(editor, 'workItemsDisplayer');
    }
  };
  let hideShowParams = function (editor) {
    let displayerVal = (editor.find('select[name="displayer"]').val() || '').toLowerCase();
    editor.find('.macro-parameter').each(function () {
      let element = $(this);
      let foundInput = element.find('.macro-parameter-field :input');
      if (foundInput.length > 0 && (unneededParams[displayerVal] || []).indexOf(foundInput.attr('name')) > -1) {
        element.hide();
      } else {
        element.show();
      }
    });
    layout.refreshMoreOptions(editor);
  };
  let initParams = function (editor) {
    let displayerSelect = editor.find('select[name="displayer"]');
    layout.mergeGroupTabs(editor, displayerSelect.attr('data-other-parameters-label'));
    layout.addMoreOptions(editor, 'display', moreOptions, displayerSelect.attr('data-more-options-label'),
      displayerSelect.attr('data-fewer-options-label'));
    migrateLegacyDisplayer(editor);
    hideShowParams(editor);
  };
  $(document).on('change', '.macro-editor select[name="displayer"]', function () {
    hideShowParams($(this).closest('.macro-editor'));
  });
  // The script is loaded by the displayer field: lay out the editors of the macros having one.
  layout.onMacroEditor(editor => editor.find('select[name="displayer"]').length > 0, initParams);
});
