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
 * Binds the widget of the source parameter of the work item macros (projectmanagementworkitemsource/edit.vm) to the
 * macro parameters: reads the values that the macro editor put in the source, $content, filters, identifier and
 * presetId fields into the state of workItemSelection.js, shows the editor of the selected way, and writes the fields
 * of the selected way on every change. The content field is hidden: the widget edits it.
 *
 * The filter builder of the matching filters is bound by filterParameter.js, through events on its container: the
 * query (or the deprecated filters parameter) is given to it, and it reports the query once the user edits the filters.
 *
 * The client can describe the link of the url way: the pm:urlChanged event is triggered on the widget with the link,
 * when it's displayed and when the user changes it.
 *
 * A warning in the footer of the macro editor lists the values that Submit drops, e.g. the work items picked before
 * the user chose the matching filters, and tells when the specific work items are selected without any work item
 * picked, all the work items being then displayed.
 */
(function () {
  const getResourceURL = function (resource) {
    return new XWiki.Document(XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT))
      .getURL('jsx', 'resource=' + resource);
  };
  require.config({
    paths: {
      'projectmanagement-macro-editor-layout': getResourceURL('js/projectmanagement/macro/macroEditorLayout.js'),
      'projectmanagement-work-item-selection': getResourceURL('js/projectmanagement/macro/workItemSelection.js'),
      projectManagementWorkItemList: getResourceURL('js/projectmanagement/macro/workItemList.js'),
      projectManagementWorkItemPicker: getResourceURL('js/projectmanagement/macro/workItemPicker.js')
    }
  });
})();
require(['jquery', 'projectmanagement-macro-editor-layout', 'projectmanagement-work-item-selection',
    'projectManagementWorkItemList', 'projectManagementWorkItemPicker'], function ($, layout, model, workItemList,
    workItemPicker) {
  'use strict';

  const STYLE_ID = 'pm-work-item-source-style';
  const DROPPED_CLASS = 'pm-dropped-values';

  const ensureStyle = function () {
    if (!document.getElementById(STYLE_ID)) {
      const style = document.createElement('style');
      style.id = STYLE_ID;
      style.textContent = '.pm-work-item-source .pm-source-ways { margin-bottom: 10px; }'
        + ' .pm-work-item-source .pm-source-way-select { width: auto; max-width: 100%; }'
        + ' .pm-work-item-source .pm-source-field-label { display: block; font-weight: normal; }'
        + ' .pm-work-item-source .pm-source-toggle-text { padding-left: 0; }'
        + ' .modal-footer .' + DROPPED_CLASS + ' { text-align: left; }';
      document.head.appendChild(style);
    }
  };

  const getValue = function (input) {
    return input.length ? String(input.val() || '') : '';
  };

  // Writes a macro parameter, cleared rather than disabled when unused: the macro editor doesn't write the empty
  // parameters, and writes the disabled ones badly on some versions.
  const setValue = function (input, value) {
    if (!input.length || getValue(input) === value) {
      return;
    }
    const selectize = input[0].selectize;
    if (selectize) {
      selectize.setValue(value, true);
    }
    input.val(value).trigger('input');
  };

  // Writes a parameter that may be a list of values separated by commas (e.g. the properties, picked in a list
  // allowing several values), keeping the values that its list doesn't propose.
  const setParameterValue = function (input, value) {
    const selectize = input[0] && input[0].selectize;
    if (!selectize || selectize.settings.maxItems === 1) {
      setValue(input, value);
      return;
    }
    const items = value.split(',');
    items.filter(item => !selectize.options[item]).forEach(item => {
      selectize.addOption({[selectize.settings.valueField]: item, [selectize.settings.labelField]: item});
    });
    selectize.setValue(items);
  };

  // The parameters that can hold the settings of the advanced Live Data configuration of a macro, see
  // workItemSelection.js#splitLiveDataConfiguration.
  const LIVE_DATA_PARAMETERS = ['properties', 'limit'];

  // The displayers showing only one work item.
  const SINGLE_DISPLAYERS = ['single', 'inline'];

  /**
   * Mounts the work item picker of the specific work items. The client specific parameters of its REST requests
   * (e.g. the connection of OpenProject) are collected, on each request, from the handlers of the
   * pm:collectSourceParameters event triggered on the widget.
   */
  const mountPicker = function (widget, initialIds, onChange) {
    const urls = function () {
      const parameters = {};
      widget.trigger('pm:collectSourceParameters', [parameters]);
      return workItemList.restUrls(widget.attr('data-rest-url'), widget.attr('data-wiki'), widget.attr('data-client'),
        parameters);
    };
    return workItemPicker.mount(widget.find('.pm-source-list-picker'), {
      searchUrl: text => urls().searchUrl(text),
      itemUrl: id => urls().itemUrl(id),
      initialIds,
      onChange
    });
  };

  // The picker of the last bound widget: its suggestions are displayed in the page body, outside of the macro editor.
  let mountedPicker;

  const removeDroppedWarning = function (editor) {
    editor.closest('.modal').find('.modal-footer .' + DROPPED_CLASS).remove();
  };

  const bind = function (editor) {
    const widget = editor.find('.pm-work-item-source').first();
    if (widget.data('pmBound')) {
      return;
    }
    widget.data('pmBound', true);
    ensureStyle();
    const l10n = widget.data('l10n') || {};
    const format = function (key, parameter) {
      return String(l10n[key] || '').replace('{0}', () => parameter);
    };
    const fields = {
      source: widget.find('input[type="hidden"][name]').first(),
      content: editor.find(':input[name="$content"]'),
      filters: editor.find(':input[name="filters"]'),
      identifier: editor.find(':input[name="identifier"]'),
      presetId: editor.find(':input[name="presetId"]')
    };
    // The filters parameter of a chart holds its series, edited by their own filter builders (role series), not
    // the deprecated filters selecting the work items.
    const isChart = editor.find('[data-builder-role="series"]').length > 0;
    let state = model.readInitial({
      source: getValue(fields.source),
      content: getValue(fields.content),
      identifier: getValue(fields.identifier),
      filters: getValue(fields.filters),
      presetId: getValue(fields.presetId),
      isChart
    });
    // Whether the user changed the selection: the fields are only written, and the dropped values only reported, then.
    let touched = false;
    let textMode = false;
    const queryContainer = widget.find('[data-builder-role="query"]');
    const waySelect = widget.find('.pm-source-way-select');
    // The advanced way is only offered to the macros using it.
    if (!model.hasAdvancedWay(state)) {
      waySelect.find('option[value="advanced"]').remove();
    }
    const queryText = widget.find('.pm-source-query-input');

    // The layout: the widget edits the content, the preset is displayed under the filters, the deprecated identifier
    // is displayed by the widget, which converts it, and the deprecated filters by the filter builder.
    model.getEditedParameters(state).forEach(name => layout.hideParameter(editor, name));
    layout.moveParameter(editor, 'presetId', widget.find('.pm-source-preset-slot'));
    layout.activateGroup(editor, 'filters');

    const renderDropped = function () {
      removeDroppedWarning(editor);
      if (!touched) {
        return;
      }
      const messages = [];
      const liveData = state.values.liveData;
      if (liveData && state.way !== model.ADVANCED && Object.keys(liveData.parameters).length) {
        messages.push(l10n['liveData.moved']);
      }
      if (model.isEmptyList(state)) {
        messages.push(l10n.emptyList);
      }
      const dropped = model.droppedValues(state);
      if (dropped.length) {
        const items = dropped.map(item => {
          if (item.type === 'preset') {
            return l10n['dropped.preset'];
          }
          return format('dropped.' + item.type, item.value);
        });
        messages.push(format('dropped', items.join(l10n['dropped.separator'] || '; ')));
      }
      if (messages.length) {
        const warning = $('<div role="status" class="alert alert-warning"></div>').addClass(DROPPED_CLASS)
          .text(messages.join(' '));
        editor.closest('.modal').find('.modal-footer').prepend(warning);
      }
    };

    const write = function () {
      touched = true;
      const values = model.toFields(state);
      setValue(fields.source, values.source);
      setValue(fields.content, values.content);
      setValue(fields.presetId, values.presetId);
      if (!isChart) {
        setValue(fields.filters, values.filters);
      }
      setValue(fields.identifier, values.identifier);
      renderDropped();
    };

    const render = function () {
      const values = state.values;
      waySelect.val(state.way).prop('disabled', state.locked);
      widget.find('.pm-source-editor').each(function () {
        $(this).toggleClass('pm-hidden', $(this).attr('data-way') !== state.way);
      });
      widget.find('.pm-source-locked').toggleClass('pm-hidden', !state.locked);
      widget.find('.pm-source-advanced-input').prop('readonly', state.locked);
      const identifier = widget.find('.pm-source-identifier');
      identifier.toggleClass('pm-hidden', values.identifier === '');
      identifier.find('.pm-source-identifier-message').text(format('identifier.deprecated', values.identifier));
      queryContainer.toggleClass('pm-hidden', textMode);
      widget.find('.pm-source-query-text').toggleClass('pm-hidden', !textMode);
      widget.find('.pm-source-toggle-text').text(l10n[textMode ? 'editAsFilters' : 'editAsText']);
    };

    // Gives the filter builder what to show; filterParameter.js keeps it until the builder is ready.
    const showInBuilder = function () {
      const input = model.showsLegacyFilters(state)
        ? {type: 'filters', value: state.values.legacyFilters} : {type: 'query', value: state.values.query};
      queryContainer.data('pmPendingInput', input);
      queryContainer.trigger(input.type === 'filters' ? 'pm:importFilters' : 'pm:setQuery', [input.value]);
    };

    const showValues = function () {
      const values = state.values;
      queryText.val(values.query);
      picker.setIds(values.list);
      widget.find('.pm-source-url-input').val(values.url);
      widget.trigger('pm:urlChanged', [values.url]);
      widget.find('.pm-source-advanced-input').val(values.advanced.content);
    };

    waySelect.on('change', function () {
      const way = String($(this).val() || '');
      if (way && way !== state.way && !state.locked) {
        state.way = way;
        render();
        write();
      }
    });
    const picker = mountPicker(widget, state.values.list, function (ids) {
      state.values.list = ids;
      write();
    });
    mountedPicker = picker;
    // Only the first work item is shown by the displayers of a single work item.
    const displayerFields = editor.find(':input[name="displayer"], :input[name="workItemsDisplayer"]');
    const updateSingle = function () {
      picker.setSingle(displayerFields.toArray().some(field => SINGLE_DISPLAYERS.includes(getValue($(field)))));
    };
    displayerFields.on('change', updateSingle);
    updateSingle();
    widget.on('input', '.pm-source-url-input', function () {
      state.values.url = String($(this).val() || '');
      widget.trigger('pm:urlChanged', [state.values.url]);
      write();
    });
    widget.on('input', '.pm-source-advanced-input', function () {
      if (!state.locked) {
        state.values.advanced = {source: state.values.advanced.source, content: String($(this).val() || '')};
        write();
      }
    });
    widget.on('input', '.pm-source-query-input', function () {
      state.values.query = String($(this).val() || '');
      render();
      write();
    });
    widget.on('click', '.pm-source-toggle-text', function () {
      textMode = !textMode;
      if (textMode) {
        // The deprecated filters are shown as the query of the filter builder importing them, which is only written
        // once the user changes it.
        const holder = {query: state.values.query};
        if (model.showsLegacyFilters(state)) {
          queryContainer.trigger('pm:collectQuery', [holder]);
        }
        queryText.val(holder.query);
      } else {
        showInBuilder();
      }
      render();
    });
    widget.on('click', '.pm-source-convert', function () {
      // The settings of the advanced Live Data configuration of the macro, which the content no longer holds, are
      // moved to the parameters holding them, when the macro has them.
      const parameters = LIVE_DATA_PARAMETERS.filter(name => editor.find(`:input[name="${name}"]`).length);
      state = model.withConvertedIdentifier(state, parameters);
      const liveData = state.values.liveData;
      Object.entries((liveData && liveData.parameters) || {}).forEach(([name, value]) => {
        setParameterValue(editor.find(`:input[name="${name}"]`), value);
      });
      showValues();
      render();
      write();
    });
    queryContainer.on('pm:queryChanged', function (event, query) {
      if (event.target === this) {
        state.values.query = String(query || '');
        render();
        write();
      }
    });
    queryContainer.on('pm:unsupportedFilters', function (event, conditions) {
      if (event.target === this) {
        state.values.unsupportedFilters = conditions || [];
        renderDropped();
      }
    });
    fields.presetId.on('change', function () {
      const presetId = getValue(fields.presetId);
      // The preset field is cleared when the way doesn't use it: only the choices of the user are kept.
      if (state.way === model.QUERY && presetId !== state.values.presetId) {
        state.values.presetId = presetId;
        write();
      }
    });

    showValues();
    render();
    showInBuilder();
  };

  // The macro editor is shared by the macros: the warning of the previous macro, if any, is removed.
  layout.onMacroEditor(() => true, function (editor) {
    const widget = editor.find('.pm-work-item-source').first();
    if (widget.data('pmBound')) {
      return;
    }
    removeDroppedWarning(editor);
    if (mountedPicker) {
      mountedPicker.destroy();
      mountedPicker = undefined;
    }
    if (widget.length && editor.closest('.macro-editor-modal').length) {
      bind(editor);
    }
  });
  $(document).on('hide.bs.modal', '.macro-editor-modal', function () {
    $(this).find('.modal-footer .' + DROPPED_CLASS).remove();
  });
});
