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
  keys: ['placeholder', 'add', 'remove', 'notFound', 'notRetrieved', 'invalid', 'editAsText', 'editWithPicker',
    'pasteLabel', 'pastePlaceholder', 'onlyFirstShown']
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
 *
 * The picker tells whether the work items can be retrieved, so that the client can explain why they can't (e.g. the
 * user isn't authenticated to the tool): it triggers suggestionsLoaded on its element once a search or a lookup of a
 * work item answers, and suggestionsFailed, with the HTTP status, when one fails (a work item not found isn't a
 * failure). The client can display a message in the element of the picker (e.g. above its field): the suggestions,
 * displayed in the page body under the field, follow the field when the message moves it.
 */
define(['jquery', 'projectManagementWorkItemList', 'xwiki-l10n!projectmanagement.workItemPicker.messages',
    'xwiki-selectize'], function ($, workItemList, l10n) {
  const CLASS = 'pm-workitem-picker';

  let createDOM = function (message, labelId) {
    let root = $('<div></div>').addClass(CLASS);
    let input = $('<input type="text"/>').addClass(CLASS + '-input').appendTo(root);
    if (labelId) {
      // The field is named by the label displayed above it (WCAG 2.5.3, Label in Name), and described by the search
      // hint that its placeholder shows (the hidden hint is only read as the description of the field).
      let hintId = 'pm-workitem-picker-hint-' + Math.random().toString(36).substring(2);
      $('<span hidden></span>').attr('id', hintId).text(l10n.get('placeholder')).appendTo(root);
      input.attr({'aria-labelledby': labelId, 'aria-describedby': hintId});
    } else {
      input.attr('aria-label', l10n.get('placeholder'));
    }
    $('<p class="xHint"></p>').addClass(CLASS + '-onlyFirst').text(l10n.get('onlyFirstShown')).hide()
      .appendTo(root);
    let paste = $('<div></div>').addClass(CLASS + '-paste').hide().appendTo(root);
    let textAreaId = 'pm-workitem-picker-paste-' + Math.random().toString(36).substring(2);
    $('<label class="sr-only"></label>').attr('for', textAreaId).text(message('pasteLabel')).appendTo(paste);
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
  let renderItem = function (message, option) {
    let selectize = this;
    let item = $('<div class="item"></div>').addClass(CLASS + '-item').attr('data-value', option.value);
    $('<span></span>').addClass(CLASS + '-label').text(option.label || option.value).appendTo(item);
    if (['notFound', 'notRetrieved', 'invalid'].includes(option.state)) {
      item.addClass(CLASS + '-missing').attr('title', message(option.state, option.value));
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
    let label = option.label || option.value;
    // The label wraps (see workItemPicker.css) and is the tooltip, so that a long summary can be read.
    let output = $('<div class="option xwiki-selectize-option"></div>').addClass(CLASS + '-option')
      .attr({'data-value': option.value, title: label});
    $('<span class="xwiki-selectize-option-label"></span>').text(label).appendTo(output);
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
   *     initialIds: the ids picked initially, onChange(ids): called with the picked ids when the user changes them,
   *     waitForParameters: whether the picked work items are only resolved (labelled) once refresh() is called, e.g.
   *     once the client parameters of the URLs are known, messages: the messages worded by the client, by key, e.g.
   *     {notFound: 'Work package {0} not found'}, see workItemList.clientMessages, labelId: the id of the element
   *     displaying the label of the picker, which names its field, if any}
   * @return the picker: {getIds(), setIds(ids), setSingle(single): shows that only the first work item is shown,
   *     refresh(): searches the work items again and resolves the picked ones again, e.g. once the URLs change,
   *     destroy()}; in text mode, the ids are the ones typed in the text area
   */
  let mount = function (container, options) {
    let settings = options || {};
    let message = function (key, parameter) {
      let clientMessage = settings.messages?.[key];
      return typeof clientMessage === 'string' ? clientMessage.replace('{0}', () => parameter)
        : l10n.get(key, parameter);
    };
    let root = createDOM(message, settings.labelId).appendTo($(container).first());
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

    // Tells whether a request answered, or failed with the given HTTP status (see the description of the module).
    let report = function (status) {
      if (status !== undefined && workItemList.failureState(status) === 'notRetrieved') {
        root.trigger('suggestionsFailed', [status]);
      } else {
        root.trigger('suggestionsLoaded');
      }
    };
    // The HTTP status of a failed request, 0 when the server didn't answer.
    let getStatus = xhr => xhr?.status || 0;

    // Declared before the suggest widget is created, since the work items typed as ids are resolved once created.
    let resolve;
    // The number of work items picked so far: the suggestions opened by the results of a search sent before the last
    // pick (e.g. the search of the id typed and picked with Enter) are closed.
    let pickCount = 0;
    // The ids typed by the user: once found, they're dropped if they're the work item of another pick, and replaced by
    // the key of their work item otherwise. This is only done by the lookup on the connection on which they were
    // typed: they're forgotten when the parameters of the URLs change.
    let typedIds = new Set();
    // Incremented when the parameters of the URLs change (e.g. the connection): the answers of the searches and of the
    // lookups sent before are ignored.
    let requestParameters = 0;
    input.xwikiSelectize({
      // A typed id (e.g. 236 or PROJ-42) is picked as typed, and replaced by the key of its work item, if it has one,
      // once the work item is found (e.g. AC-1 for 157 or ac-1), or kept as typed when it can't be. A key typed in
      // another case (e.g. ac-9) picks the work item found by the search (AC-9), and a work item already picked by its
      // id or its key isn't picked again.
      create: function (text) {
        let id = text.trim();
        let picked = workItemList.findOption(getPickedOptions(), id);
        let found = picked || workItemList.findOption(Object.values(selectize.options)
          .filter(option => !option.state && !selectize.items.includes(option.value)), id);
        if (found) {
          if (picked) {
            // Nothing is picked: the suggestions of the cleared text are closed.
            setTimeout(closeIfNothingTyped, 0);
          }
          return {...found};
        }
        typedIds.add(id);
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
      // The typed text is cleared once a work item is picked, so that the next one can be searched right away: by Tom
      // Select (XWiki 18.x), and by the item_add listener below for Selectize (XWiki 15.10), which ignores it.
      clearAfterSelect: true,
      placeholder: l10n.get('placeholder'),
      dropdownParent: 'body',
      // The suggest widget filters the found work items again: they're found by their key and their id too.
      searchField: ['value', 'label', 'hint', 'key', 'id'],
      render: {
        item: function (option) {
          return renderItem.call(this, message, option);
        },
        option: renderOption,
        option_create: renderCreate
      },
      load: function (text, callback) {
        if (!text || !text.trim() || typeof settings.searchUrl !== 'function') {
          callback();
          return;
        }
        let typed = text.trim();
        let searchPickCount = pickCount;
        let sentParameters = requestParameters;
        let current = () => sentParameters === requestParameters;
        // Whether a request failed (e.g. the tool is unreachable, or the user isn't authenticated to it yet), a work
        // item not found being no failure.
        let failed = false;
        // A failed request finds nothing, so that the loading ends: otherwise the picker stays in its loading state
        // and makes no more request.
        let get = url => Promise.resolve($.getJSON(url)).then(data => {
          if (current()) {
            report();
          }
          return data;
        }, xhr => {
          failed = failed || workItemList.failureState(getStatus(xhr)) === 'notRetrieved';
          if (current()) {
            report(getStatus(xhr));
          }
          return undefined;
        });
        // The search of the summaries doesn't match the ids: the work item whose id or key is typed is looked up too.
        let lookup = workItemList.isIdOrKey(typed) && typeof settings.itemUrl === 'function'
          ? get(settings.itemUrl(typed)) : Promise.resolve();
        Promise.all([lookup, get(settings.searchUrl(typed))]).then(function ([found, results]) {
          if (!current()) {
            callback();
            return;
          }
          // The suggest widget doesn't search again a text that it searched: a failed search is forgotten, so that
          // the same text is searched again, e.g. once the tool answers again.
          if (failed) {
            delete selectize.loadedSearches[text];
          }
          // A found work item is picked by its key when it has one, as the # inserter writes it. The work items
          // already picked (by their id or their key) aren't suggested again.
          callback(workItemList.withItemFound(found, results?.items).filter(item => !isPicked(item))
            .map(item => workItemList.toOption(item, item?.key)).filter(option => option));
          // The suggest widget keeps the results of each search, so they're given even when a work item was picked
          // since the search was sent (e.g. the id typed and picked with Enter); the suggestions that they open
          // are closed when nothing is typed.
          if (searchPickCount !== pickCount) {
            closeIfNothingTyped();
          }
        });
      }
    });
    let selectize = input[0].selectize;
    // The suggest widget gives its own field only the aria-label of the input (Selectize, XWiki 15.10, and Tom Select,
    // XWiki 18.x).
    let field = $(selectize.$control_input || selectize.control_input);
    ['aria-labelledby', 'aria-describedby'].filter(name => input.attr(name))
      .forEach(name => field.attr(name, input.attr(name)));

    // The suggestions are displayed in the page body, under the field, where the suggest widget placed them when they
    // opened: they're moved with the field when the client displays a message above it, or removes it.
    let observer = new MutationObserver(function () {
      if (selectize.isOpen) {
        selectize.positionDropdown();
      }
    });
    observer.observe(root[0], {childList: true});

    // Forgets the work items found by the searches made so far, e.g. on another connection: the next searches are
    // sent again rather than answered from the cache of the suggest widget. The picked work items are kept.
    let forgetSearches = function () {
      requestParameters++;
      Object.keys(selectize.options).filter(value => !selectize.items.includes(value))
        .forEach(value => selectize.removeOption(value, true));
      selectize.loadedSearches = {};
      selectize.lastQuery = null;
    };

    let getPickedOptions = function () {
      return selectize.items.map(id => selectize.options[id]).filter(option => option);
    };

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

    let closeIfNothingTyped = function () {
      if (!workItemList.canShowSuggestions(getTypedText())) {
        selectize.close();
      }
    };

    // The suggestions are only shown for a typed text: the suggest widget opens them once a work item is picked with
    // Enter (Selectize, XWiki 15.10, after the item_add event), as does a late answer of a search, listing the work
    // items found by the previous searches.
    selectize.on('dropdown_open', closeIfNothingTyped);

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

    // Shows the label of a work item listed by its id, or a warning when the work item can't be found or retrieved.
    let resolveNow = function (id) {
      if (!workItemList.isIdOrKey(id)) {
        updateOption(id, {value: id, label: id, state: 'invalid'});
        return;
      }
      if (typeof settings.itemUrl !== 'function') {
        return;
      }
      let show = function (item, status) {
        // The work item is listed by the id it was written with, which can be its key, unless the user typed it: it's
        // then picked by its key.
        let typed = typedIds.has(id) && selectize.items.includes(id);
        let option = workItemList.pickedOption(item, id, typed, status);
        // An id or key typed by the user for a work item already picked otherwise (e.g. 157 when AC-1 is picked) is
        // dropped.
        let otherPicks = getPickedOptions().filter(picked => picked.value !== id);
        if (!option.state && typed && [option.id || option.value, option.key]
            .some(value => value && workItemList.findOption(otherPicks, value))) {
          selectize.removeItem(id);
          return;
        }
        // The suggest widget replaces the typed id by the key in the picked ones, without telling.
        updateOption(id, option);
        if (option.value !== id) {
          typedIds.delete(id);
          notify();
        }
      };
      // The work item is looked up again once the parameters change: the answer of this lookup is then ignored, so
      // that the work item isn't shown as found on the previous connection.
      let sentParameters = requestParameters;
      $.getJSON(settings.itemUrl(id)).then(function (item) {
        if (sentParameters === requestParameters) {
          show(item);
          report();
        }
      }, function (xhr) {
        if (sentParameters === requestParameters) {
          show(undefined, getStatus(xhr));
          report(getStatus(xhr));
        }
      });
    };
    // The work items are resolved once the client parameters of the URLs (e.g. the connection) are known.
    let resolution = workItemList.resolution(resolveNow, settings.waitForParameters);
    resolve = id => resolution.request(id);

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
      refresh: function () {
        forgetSearches();
        // The picked work items keep their ids: an id typed on another connection isn't replaced by the key that its
        // work item has on this one, which would select another work item, or none, on the other connection.
        typedIds.clear();
        resolution.ready(selectize.items);
      },
      setSingle: function (value) {
        single = !!value;
        root.find('.' + CLASS + '-onlyFirst').toggle(single && getIds().length > 1);
      },
      destroy: function () {
        observer.disconnect();
        selectize.destroy();
        root.remove();
      }
    };
  };

  return {mount};
});
