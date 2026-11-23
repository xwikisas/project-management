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
define('projectmanagement.workItemPicker.messages', {
  prefix: 'projectmanagement.macro.workItemPicker.',
  keys: ['placeholder', 'add', 'remove', 'notFound', 'invalid', 'editAsText', 'editWithPicker', 'pasteLabel',
    'pastePlaceholder', 'onlyFirstShown']
});

/**
 * The picker of the work items listed by the "list" source of the work item macros: the XWiki suggest widget
 * (xwiki-selectize), whose chips are the picked work items in the order they're listed, which the user can edit as
 * text instead (a list of ids, e.g. pasted), as the filters of the matching filters. The styles are in
 * css/projectmanagement/workItemPicker.css.
 *
 * The module is loaded by RequireJS, with the path projectManagementWorkItemList configured for workItemList.js, e.g.
 * require.config({paths: {projectManagementWorkItemList: ..., projectManagementWorkItemPicker: ...}}).
 *
 * The inputs of the picker have no name, so that the macro editor doesn't write them as macro parameters: the caller
 * writes the picked ids where they belong, from onChange.
 */
define(['jquery', 'projectManagementWorkItemList', 'xwiki-l10n!projectmanagement.workItemPicker.messages',
    'xwiki-selectize'], function ($, workItemList, l10n) {
  const CLASS = 'pm-workitem-picker';

  let createDOM = function () {
    let root = $('<div></div>').addClass(CLASS);
    $('<input type="text"/>').addClass(CLASS + '-input').attr('aria-label', l10n.get('placeholder'))
      .appendTo(root);
    $('<p class="xHint"></p>').addClass(CLASS + '-onlyFirst').text(l10n.get('onlyFirstShown')).hide()
      .appendTo(root);
    let paste = $('<div></div>').addClass(CLASS + '-paste').hide().appendTo(root);
    let textAreaId = 'pm-workitem-picker-paste-' + Math.random().toString(36).substring(2);
    $('<label class="sr-only"></label>').attr('for', textAreaId).text(l10n.get('pasteLabel')).appendTo(paste);
    $('<textarea rows="3" class="form-control"></textarea>').attr({
      id: textAreaId,
      placeholder: l10n.get('pastePlaceholder')
    }).appendTo(paste);
    $('<button type="button" class="btn btn-link"></button>').addClass(CLASS + '-pasteToggle')
      .text(l10n.get('editAsText')).appendTo(root);
    return root;
  };

  /**
   * Renders a picked work item: its label, a warning when it can't be found, and a button to remove it (the
   * remove_button plugin of the suggest widget doesn't work with the XWiki suggest widget of all the supported
   * versions). The suggest widget (this) handles the mouse events of its chips without letting them bubble, so the
   * button handles its own.
   */
  let renderItem = function (option) {
    let selectize = this;
    let item = $('<div class="item"></div>').addClass(CLASS + '-item').attr('data-value', option.value);
    $('<span></span>').addClass(CLASS + '-label').text(option.label || option.value).appendTo(item);
    if (option.state === 'notFound' || option.state === 'invalid') {
      item.addClass(CLASS + '-missing').attr('title', l10n.get(option.state, option.value));
    } else if (option.hint) {
      item.attr('title', option.hint);
    }
    $('<button type="button" tabindex="-1"></button>').addClass(CLASS + '-remove')
      .attr({title: l10n.get('remove'), 'aria-label': l10n.get('remove')}).text('×').appendTo(item)
      .on('mousedown', function (event) {
        // Don't select the chip.
        event.preventDefault();
        event.stopPropagation();
      }).on('click', function (event) {
        event.preventDefault();
        event.stopPropagation();
        selectize.removeItem(option.value);
      });
    return item;
  };

  let renderOption = function (option) {
    let output = $('<div class="option xwiki-selectize-option"></div>').attr('data-value', option.value);
    $('<span class="xwiki-selectize-option-label"></span>').text(option.label || option.value).appendTo(output);
    if (option.hint) {
      $('<div class="xwiki-selectize-option-hint"></div>').text(option.hint).appendTo(output);
    }
    return output;
  };

  let renderCreate = function (data) {
    return $('<div class="create option"></div>').text(l10n.get('add', data.input));
  };

  /**
   * Mounts a work item picker.
   *
   * @param container the element (or jQuery collection) receiving the picker
   * @param options {searchUrl(text): the URL of the work items matching a text, itemUrl(id): the URL of a work item,
   *     initialIds: the ids picked initially, onChange(ids): called with the picked ids when the user changes them}
   * @return the picker: {getIds(), setIds(ids), setSingle(single): shows that only the first work item is shown,
   *     destroy()}; in text mode, the ids are the ones typed in the text area
   */
  let mount = function (container, options) {
    let settings = options || {};
    let root = createDOM().appendTo($(container).first());
    let input = root.find('.' + CLASS + '-input');
    let paste = root.find('.' + CLASS + '-paste');
    let textArea = paste.find('textarea');
    let pasteToggle = root.find('.' + CLASS + '-pasteToggle');
    let single = false;
    // Whether the work items are edited as text, in the text area replacing the suggest widget.
    let textMode = false;

    let notify = function () {
      let ids = getIds();
      root.find('.' + CLASS + '-onlyFirst').toggle(single && ids.length > 1);
      if (typeof settings.onChange === 'function') {
        settings.onChange(ids);
      }
    };

    // Declared before the suggest widget is created, since the work items typed as ids are resolved once created.
    let resolve;
    // The number of work items picked so far: the suggestions opened by the results of a search sent before the last
    // pick (e.g. the search of the id typed and picked with Enter) are closed.
    let pickCount = 0;
    input.xwikiSelectize({
      // A typed id (e.g. 236 or PROJ-42) is picked as is, and its label is shown once the work item is found.
      create: function (text) {
        let id = text.trim();
        setTimeout(() => resolve(id), 0);
        return {value: id, label: id};
      },
      createFilter: text => workItemList.isIdOrKey(text.trim()),
      // Enter picks the typed id (the "Add" option listed first) rather than the first work item found by the search
      // (e.g. #44 "Bug 2" when typing 2). Supported by Selectize (XWiki 15.10) and Tom Select (XWiki 18.x).
      addPrecedence: true,
      // Keep the order in which the work items are picked or listed.
      maxItems: null,
      // The work items are searched once the user types: an empty search would list any work item.
      preload: false,
      placeholder: l10n.get('placeholder'),
      dropdownParent: 'body',
      // The suggest widget filters the found work items again: they're found by their key too.
      searchField: ['value', 'label', 'hint', 'key'],
      render: {
        item: renderItem,
        option: renderOption,
        option_create: renderCreate
      },
      load: function (text, callback) {
        if (!text || !text.trim() || typeof settings.searchUrl !== 'function') {
          callback();
          return;
        }
        let searchPickCount = pickCount;
        $.getJSON(settings.searchUrl(text.trim())).then(function (results) {
          // A found work item is picked by its key when it has one, as the # inserter writes it. The work items
          // already picked (by their id or their key) aren't suggested again.
          callback((Array.isArray(results?.items) ? results.items : []).filter(item => !isPicked(item))
            .map(item => workItemList.toOption(item, item?.key)).filter(option => option));
          // The suggest widget keeps the results of each search, so they're given even when a work item was picked
          // since the search was sent (e.g. the id typed and picked with Enter); the suggestions that they open
          // are closed when nothing is typed.
          if (searchPickCount !== pickCount && getTypedText() === '') {
            selectize.close();
          }
        }, function () {
          // End the loading, otherwise the picker stays in its loading state and makes no more request.
          callback();
        });
      }
    });
    let selectize = input[0].selectize;

    // Whether a found work item is already picked, by its id or by its key.
    let isPicked = function (item) {
      let key = typeof item?.key === 'string' && item.key !== '' ? item.key.toUpperCase() : null;
      return selectize.items.some(pickedId => {
        let picked = String(pickedId).toUpperCase();
        let pickedKey = selectize.options[pickedId]?.key;
        return picked === String(item?.identifier?.value) || (key && (picked === key
          || (typeof pickedKey === 'string' && pickedKey.toUpperCase() === key)));
      });
    };

    // The text typed in the suggest widget: Selectize (XWiki 15.10) or Tom Select (XWiki 18.x).
    let getTypedText = function () {
      let typed = selectize.$control_input ? selectize.$control_input.val() : selectize.control_input?.value;
      return String(typed || '');
    };

    let getIds = function () {
      return textMode ? workItemList.parseList(textArea.val()) : selectize.items.slice();
    };

    let updateOption = function (id, option) {
      if (selectize.options.hasOwnProperty(id)) {
        selectize.updateOption(id, option);
        // Selectize (XWiki 15.10) opens the suggestions when an option is updated, e.g. once the work item picked by
        // its id is found, listing the work items found by the previous search.
        if (getTypedText() === '') {
          selectize.close();
        }
      }
    };

    // Shows the label of a work item listed by its id, or a warning when the work item can't be found.
    resolve = function (id) {
      if (!workItemList.isIdOrKey(id)) {
        updateOption(id, {value: id, label: id, state: 'invalid'});
        return;
      }
      if (typeof settings.itemUrl !== 'function') {
        return;
      }
      let show = function (item) {
        // The work item is listed by the id it was written with, which can be its key.
        updateOption(id, workItemList.toOption(item, id) || {value: id, label: id, state: 'notFound'});
      };
      $.getJSON(settings.itemUrl(id)).then(show, () => show());
    };

    let setIds = function (ids, silent) {
      if (textMode) {
        textArea.val(workItemList.formatList(ids));
      }
      selectize.clear(true);
      workItemList.extend([], ids).forEach(id => {
        if (!selectize.options.hasOwnProperty(id)) {
          selectize.addOption({value: id, label: id});
          resolve(id);
        }
        selectize.addItem(id, true);
      });
      if (!silent) {
        notify();
      }
    };

    selectize.on('change', notify);
    // The searched text is cleared once a work item is picked, so that the next one can be searched right away. The
    // suggestions of the cleared text (none, since nothing is searched without a text) are closed, once the suggest
    // widget is done adding the item: Selectize (XWiki 15.10) opens them again after a work item is picked with Enter,
    // listing the work items found by the previous search.
    selectize.on('item_add', function () {
      pickCount++;
      selectize.setTextboxValue('');
      selectize.close();
      setTimeout(function () {
        // Unless the user started typing the next search.
        if (getTypedText() === '') {
          selectize.close();
        }
      }, 0);
    });

    // The text area shows the picked ids, and the ids typed in it are the picked ones until the suggest widget is
    // shown again, with their chips.
    pasteToggle.on('click', function () {
      if (textMode) {
        let ids = getIds();
        textMode = false;
        setIds(ids, true);
      } else {
        textArea.val(workItemList.formatList(getIds()));
        textMode = true;
      }
      paste.toggle(textMode);
      // The suggest widget: Selectize (XWiki 15.10) or Tom Select (XWiki 18.x).
      root.children('.selectize-control, .ts-wrapper').toggle(!textMode);
      pasteToggle.text(l10n.get(textMode ? 'editWithPicker' : 'editAsText'));
      if (textMode) {
        textArea.trigger('focus');
      }
    });
    textArea.on('input', notify);

    setIds(settings.initialIds || [], true);

    return {
      getIds,
      setIds: ids => setIds(ids, true),
      setSingle: function (value) {
        single = !!value;
        root.find('.' + CLASS + '-onlyFirst').toggle(single && getIds().length > 1);
      },
      destroy: function () {
        selectize.destroy();
        root.remove();
      }
    };
  };

  return {mount};
});
