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
 * Lays out the macro editor of the work item macros: hides parameters, orders and activates the tabs of the parameter
 * groups, moves parameters, groups the advanced parameters under a "More options" toggle, hides from the assistive
 * technologies the fields that the pickers enhance.
 *
 * The macro editor differs between the XWiki versions:
 * - XWiki 17.10+ builds one tab strip (ul.macro-tabs) with one pane per parameter group, and submits all the inputs;
 * - XWiki 15.10 displays each group in its own box with a one-tab header and submits only the inputs of the active
 *   panes: mergeGroupTabs() turns these boxes into one tab strip whose panes all stay active, the panes of the
 *   unselected tabs being hidden with the pm-tab-hidden class.
 * The version is detected from the DOM (the tab strip), never from the XWiki version.
 *
 * Rules:
 * - the 'hidden' class belongs to the macro editor (e.g. unset deprecated parameters, the 15.10 "More" toggle): the
 *   hidden fields and empty groups use the pm-hidden class;
 * - the moved fields stay inside .macro-editor, which is what the macro editor submits;
 * - every function is idempotent: the macro editor is laid out again each time it fires xwiki:dom:updated, and once
 *   when the script is loaded, since the script is loaded after the first event.
 */
define(['jquery'], function ($) {
  'use strict';

  const HIDDEN = 'pm-hidden';
  const TAB_HIDDEN = 'pm-tab-hidden';
  const PANE_ID_PREFIX = 'macroParameterTreeNode-';
  const STYLE_ID = 'pm-macro-editor-layout-style';

  // ---------------------------------------------------------------------------------------------------------------
  // DOM independent logic.
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * @param {string} dataMacroId the data-macroId attribute of a macro editor, e.g. "openproject/xwiki/2.1"
   * @return {string} the id of the edited macro, e.g. "openproject"
   */
  const getMacroId = function (dataMacroId) {
    return String(dataMacroId || '').split('/')[0];
  };

  /**
   * @param {Array<{shown: boolean, active: boolean}>} tabs the tabs of a tab strip, in order
   * @return {number} the index of the tab to activate because the active tab is hidden or missing, the first shown
   *     one, or -1 when there's nothing to do (the active tab is shown, or no tab is shown)
   */
  const getTabToActivate = function (tabs) {
    if (tabs.some(tab => tab.active && tab.shown)) {
      return -1;
    }
    return tabs.findIndex(tab => tab.shown);
  };

  /**
   * @param {Array<string>} values the values of the parameters under a "More options" toggle
   * @return {boolean} whether the toggle is expanded when the editor is displayed, i.e. whether a parameter is set
   */
  const isMoreOptionsExpanded = function (values) {
    return values.some(value => String(value === undefined || value === null ? '' : value).trim() !== '');
  };

  // ---------------------------------------------------------------------------------------------------------------
  // DOM code.
  // ---------------------------------------------------------------------------------------------------------------

  const ensureStyle = function () {
    if (!document.getElementById(STYLE_ID)) {
      const style = document.createElement('style');
      style.id = STYLE_ID;
      style.textContent = '.macro-editor .pm-hidden, .macro-editor .pm-tab-hidden { display: none !important; }'
        + ' .pm-merged-groups { list-style: none; }'
        + ' .pm-merged-groups > .nav-tabs { margin-bottom: 10px; }'
        + ' .pm-more-options-toggle { padding-left: 0; padding-right: 0; }';
      document.head.appendChild(style);
    }
  };

  const isShown = function (element) {
    return !$(element).is('.' + HIDDEN + ', .hidden') && element.style.display !== 'none';
  };

  const isTabbed = function (editor) {
    return editor.find('ul.macro-tabs').length > 0;
  };

  const getParameter = function (editor, name) {
    return editor.find('.macro-parameter').filter(function () {
      return $(this).attr('data-id') === name;
    }).first();
  };

  const getGroupPane = function (editor, groupId) {
    return editor.find('.macro-parameter-group-members').filter(function () {
      return $(this).attr('id') === PANE_ID_PREFIX + groupId;
    }).first();
  };

  const getGroupTab = function (editor, groupId) {
    return editor.find('a[role="tab"]').filter(function () {
      return $(this).attr('aria-controls') === PANE_ID_PREFIX + groupId;
    }).first();
  };

  // Shows a tab of the merged 15.10 strip, without changing the active class of the panes, which are all submitted.
  const selectMergedTab = function (container, link) {
    const target = link.attr('aria-controls');
    container.children('.nav-tabs').children('li').removeClass('active');
    link.parent().addClass('active');
    container.children('.tab-content').children('.macro-parameter-group-members').each(function () {
      $(this).toggleClass(TAB_HIDDEN, $(this).attr('id') !== target);
    });
  };

  const showTab = function (link) {
    const merged = link.closest('.pm-merged-groups');
    if (merged.length) {
      selectMergedTab(merged, link);
    } else {
      link.tab('show');
    }
  };

  /**
   * Hides the tabs whose parameters are all hidden, and activates the first shown tab when the active one is hidden.
   */
  const hideEmptyGroups = function (editor) {
    editor.find('.macro-parameter-group-members').each(function () {
      const pane = $(this);
      const empty = pane.find('.macro-parameter').filter(function () {
        return isShown(this);
      }).length === 0;
      const tab = getGroupTab(editor, (pane.attr('id') || '').substring(PANE_ID_PREFIX.length)).parent();
      if (isTabbed(editor) || pane.closest('.pm-merged-groups').length) {
        tab.add(pane).toggleClass(HIDDEN, empty);
      } else {
        pane.closest('li.macro-parameter-group').toggleClass(HIDDEN, empty);
      }
    });
    editor.find('ul.nav-tabs').each(function () {
      const tabs = $(this).children('li');
      const index = getTabToActivate(tabs.toArray().map(tab => ({
        shown: isShown(tab),
        active: $(tab).hasClass('active')
      })));
      if (index >= 0) {
        showTab(tabs.eq(index).children('a'));
      }
    });
  };

  /**
   * Hides a parameter, e.g. a deprecated one whose value was migrated, and its tab when it holds no other parameter.
   */
  const hideParameter = function (editor, name) {
    ensureStyle();
    getParameter(editor, name).addClass(HIDDEN);
    hideEmptyGroups(editor);
  };

  // Shows the parameters that the 15.10 "More" toggle hides. Only what follows the toggle is shown: the editor also
  // hides the unset hidden or deprecated parameters with the 'hidden' class.
  const expandMore = function (list) {
    const more = list.children('li.more');
    more.nextAll().removeClass('hidden');
    more.remove();
  };

  /**
   * XWiki 15.10: displays the group boxes as one tab strip, like XWiki 17.10+. The panes keep their active class, so
   * that their inputs are submitted. The parameters without group get a tab of their own.
   */
  const mergeGroupTabs = function (editor, otherLabel) {
    ensureStyle();
    if (isTabbed(editor) || editor.find('.pm-merged-groups').length) {
      return;
    }
    const root = editor.find('ul.macro-parameters').first();
    // The feature groups (single choice) are left alone.
    const groups = root.children('li.macro-parameter-group.multiple-choice');
    if (groups.length < 2) {
      return;
    }
    const container = $('<li class="pm-merged-groups"><ul class="nav nav-tabs" role="tablist"></ul>'
      + '<div class="tab-content"></div></li>');
    const strip = container.children('.nav-tabs');
    const panes = container.children('.tab-content');
    const addTab = function (label, paneId) {
      $('<li role="presentation"><a role="tab" href="#"></a></li>').appendTo(strip).children('a')
        .text(label).attr('aria-controls', paneId);
    };
    groups.each(function () {
      const group = $(this);
      const link = group.find('> .nav-tabs a[role="tab"]').first();
      const pane = group.find('> .tab-content > .macro-parameter-group-members').first();
      addTab(link.text(), pane.attr('id'));
      expandMore(pane);
      panes.append(pane);
      group.remove();
    });
    expandMore(root);
    const loose = root.children('li.macro-parameter');
    if (loose.length) {
      const otherPane = $('<ul class="macro-parameter-group-members tab-pane active" role="tabpanel"></ul>')
        .attr('id', PANE_ID_PREFIX + 'pmOther').append(loose);
      addTab(otherLabel || 'Other', PANE_ID_PREFIX + 'pmOther');
      panes.append(otherPane);
    }
    root.prepend(container);
    strip.on('click', 'a[role="tab"]', function (event) {
      event.preventDefault();
      selectMergedTab(container, $(this));
    });
    selectMergedTab(container, strip.find('a').first());
    hideEmptyGroups(editor);
  };

  /**
   * Puts the tab of the group first and shows it.
   */
  const activateGroup = function (editor, groupId) {
    const link = getGroupTab(editor, groupId);
    if (!link.length) {
      return;
    }
    if (link.closest('.pm-merged-groups').length || isTabbed(editor)) {
      link.parent().prependTo(link.closest('ul'));
      if (isShown(link.parent()[0])) {
        showTab(link);
      }
    } else {
      // 15.10 group boxes that were not merged.
      const group = link.closest('li.macro-parameter-group');
      group.removeClass('hidden').prependTo(group.parent());
    }
  };

  /**
   * Moves the parameter on top of the pane of the group, and hides the tab it leaves when it's now empty.
   */
  const moveParameterToTop = function (editor, name, groupId) {
    const parameter = getParameter(editor, name);
    const pane = getGroupPane(editor, groupId);
    if (parameter.length && pane.length && !parameter.parent().is(pane)) {
      pane.prepend(parameter);
      hideEmptyGroups(editor);
    }
    return parameter;
  };

  /**
   * Moves the parameter into the given element of the macro editor, e.g. a slot of a widget, and hides the tab it
   * leaves when it's now empty.
   */
  const moveParameter = function (editor, name, target) {
    const parameter = getParameter(editor, name);
    if (parameter.length && $(target).length && !parameter.parent().is(target)) {
      $(target).first().append(parameter);
      hideEmptyGroups(editor);
    }
    return parameter;
  };

  // Hides the toggle when the parameters under it are all hidden, e.g. by the chosen displayer.
  const refreshMoreOptions = function (editor) {
    editor.find('.pm-more-options').each(function () {
      const section = $(this);
      const shown = section.find('.macro-parameter').filter(function () {
        return isShown(this);
      }).length > 0;
      section.prev('.pm-more-options-toggle-container').toggleClass(HIDDEN, !shown);
    });
  };

  /**
   * Groups the given parameters of a group at the end of its pane, under a "More options" toggle, collapsed unless one
   * of them is set. The toggle is labelled "Fewer options" (fewerLabel, if given) when expanded.
   */
  const addMoreOptions = function (editor, groupId, names, label, fewerLabel) {
    ensureStyle();
    const pane = getGroupPane(editor, groupId);
    if (!pane.length || pane.children('.pm-more-options').length) {
      return;
    }
    const parameters = names.map(name => getParameter(editor, name)).filter(parameter => parameter.length > 0);
    if (!parameters.length) {
      return;
    }
    // The 15.10 panes are lists.
    const tag = pane.is('ul') ? 'li' : 'div';
    const toggleContainer = $('<' + tag + ' class="pm-more-options-toggle-container"></' + tag + '>');
    const toggle = $('<button type="button" class="btn btn-link pm-more-options-toggle"></button>')
      .appendTo(toggleContainer);
    const section = $('<' + tag + ' class="pm-more-options"></' + tag + '>');
    parameters.forEach(parameter => section.append(parameter));
    pane.append(toggleContainer, section);
    const expanded = isMoreOptionsExpanded(parameters.map(parameter => parameter.find(':input[name]').val()));
    const setExpanded = function (value) {
      section.toggleClass(HIDDEN, !value);
      toggle.attr('aria-expanded', String(value)).text(value && fewerLabel ? fewerLabel : label);
    };
    setExpanded(expanded);
    toggle.on('click', function () {
      setExpanded(toggle.attr('aria-expanded') !== 'true');
    });
    refreshMoreOptions(editor);
  };

  // The class that Tom Select, the picker of XWiki 18+, gives to the field it enhances: it hides the field visually
  // only, so that the field stays in the accessibility tree next to the input of the picker, with the same name, and,
  // for a select, with its options (plus one without text when nothing is selected). Selectize, the picker of the older
  // XWiki versions, doesn't use it: it hides the field with display: none.
  const ENHANCED_FIELD = 'ts-hidden-accessible';
  // Marks the fields hidden from the assistive technologies by hideEnhancedFields, which shows them again.
  const ENHANCED_FIELD_HIDDEN = 'data-pm-enhanced-hidden';

  const updateEnhancedField = function (element) {
    if (element.classList.contains(ENHANCED_FIELD)) {
      if (!element.hasAttribute('aria-hidden')) {
        element.setAttribute('aria-hidden', 'true');
        element.setAttribute(ENHANCED_FIELD_HIDDEN, '');
      }
    } else if (element.hasAttribute(ENHANCED_FIELD_HIDDEN)) {
      // The picker is destroyed: the field is displayed again.
      element.removeAttribute('aria-hidden');
      element.removeAttribute(ENHANCED_FIELD_HIDDEN);
    }
  };

  /**
   * Hides from the assistive technologies the fields of the given macro editor that the pickers enhance, now and once
   * they're enhanced, as long as the editor displays the same macro (the macro editor is shared by the macros), so
   * that each field is listed once, by the input of its picker. Such a field can't take the focus there: XWiki moves
   * its focus to the picker. Idempotent.
   *
   * @param {jQuery} editor the macro editor
   */
  const hideEnhancedFields = function (editor) {
    const element = editor[0];
    if (!element) {
      return;
    }
    element.pmEnhancedFieldsMacro = element.getAttribute('data-macroId');
    element.querySelectorAll('.' + ENHANCED_FIELD).forEach(updateEnhancedField);
    if (element.pmEnhancedFieldsObserver) {
      return;
    }
    element.pmEnhancedFieldsObserver = new MutationObserver(function (records) {
      if (element.getAttribute('data-macroId') !== element.pmEnhancedFieldsMacro) {
        return;
      }
      records.forEach(record => {
        if (record.type === 'attributes') {
          updateEnhancedField(record.target);
        } else {
          Array.from(record.addedNodes).filter(node => node.nodeType === Node.ELEMENT_NODE).forEach(node => {
            [node, ...node.querySelectorAll('.' + ENHANCED_FIELD)].forEach(updateEnhancedField);
          });
        }
      });
    });
    element.pmEnhancedFieldsObserver.observe(element, {subtree: true, childList: true, attributes: true,
      attributeFilter: ['class']});
  };

  /**
   * Calls the callback with each macro editor (modal or in-place) displayed now and each time the macro editor
   * displays the parameters of a macro, when the editor is accepted.
   *
   * @param {function(jQuery): boolean} accept whether to lay out the given editor
   * @param {function(jQuery)} callback lays out the given editor, idempotent
   */
  const onMacroEditor = function (accept, callback) {
    const handle = function (element) {
      const editor = $(element);
      if (accept(editor)) {
        callback(editor);
      }
    };
    $(document).on('xwiki:dom:updated', function (event, data) {
      $((data && data.elements) || []).filter('.macro-editor').each(function () {
        handle(this);
      });
    });
    $('.macro-editor').each(function () {
      handle(this);
    });
  };

  /**
   * @return {function(jQuery): boolean} accepts the editors of the given macros
   */
  const forMacros = function (macroIds) {
    return function (editor) {
      return macroIds.indexOf(getMacroId(editor.attr('data-macroId'))) >= 0;
    };
  };

  return {
    getMacroId,
    getTabToActivate,
    isMoreOptionsExpanded,
    isTabbed,
    getParameter,
    getGroupPane,
    hideParameter,
    hideEmptyGroups,
    mergeGroupTabs,
    activateGroup,
    moveParameterToTop,
    moveParameter,
    addMoreOptions,
    refreshMoreOptions,
    hideEnhancedFields,
    onMacroEditor,
    forMacros
  };
});
