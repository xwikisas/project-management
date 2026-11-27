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
 * Some results of the project management macros are initialized by JavaScript code on page load and on the
 * xwiki:dom:updated event, in the updated elements: e.g. the charts, drawn by the Chart.js application, and the Live
 * Data tables. When a macro is rendered asynchronously, XWiki sends no event once the placeholder is replaced by the
 * result (before XWiki 18.6), or sends it for the removed placeholder (XWIKI-25675, since XWiki 18.6), so a result
 * inserted after the page load (e.g. when saving the in-place editor, or inside another asynchronous rendering) isn't
 * initialized: the Live Data keeps loading. The macros wrap their asynchronous result, and this script sends the event
 * for the wrapper once its placeholder is replaced, except when XWiki already sent it, after the replacement, for the
 * wrapper or one of its ancestors, which would initialize the result twice (e.g. draw a chart twice). An event sent
 * while the placeholder was still there doesn't count, whenever the result arrives: e.g. the in-place editor sends it
 * for the whole content, which still holds the placeholder, and the result can arrive before or after the browser runs
 * the next task. An event sent for the result itself doesn't count either: the Live Data only initializes the elements
 * inside the updated ones.
 *
 * The page loads the script when it displays a work item macro, and each asynchronous result brings it too, once it is
 * inserted, to a page that had none (e.g. when saving the in-place editor after inserting the first macro): when it
 * starts, the script also sends the event for the wrappers of a placeholder (marked by the macros) whose result is
 * already inserted, since XWiki doesn't initialize them and the script can't know about the events sent before it
 * started. Only asyncError.js may have sent one, for the error displayed instead of the result: the errors it processed
 * are marked, the script leaves their wrapper alone, and it marks the errors of the wrappers it sends the event for, so
 * that asyncError.js leaves them alone, whichever script runs first. The script runs once, even when the page loads it
 * twice (e.g. by the page and by an asynchronous result, or by two results), so the second copy costs only its
 * download, cached by the browser.
 *
 * Remove this script and the wrapper once the minimal supported XWiki version sends the event for the rendered content
 * (XWIKI-25675, https://jira.xwiki.org/browse/XWIKI-25675).
 */
require(['jquery'], function ($) {
  if (window.projectManagementAsyncFix) {
    return;
  }
  window.projectManagementAsyncFix = true;

  const WRAPPER = 'proj-manag-async-wrapper';
  // The marker of the errors notified by asyncError.js.
  const PROCESSED_ERROR = 'data-proj-manag-async-error-processed';

  // The wrappers whose result was inserted and initialized: the placeholder of a wrapper is replaced once.
  const initializedWrappers = new WeakSet();

  // An event sent for a wrapper or one of its ancestors initializes the wrapper's result, if it is already there.
  $(document).on('xwiki:dom:updated', function (event, data) {
    $(data?.elements || []).filter((index, element) => element.isConnected).each(function () {
      $(this).find('.' + WRAPPER).addBack('.' + WRAPPER).each(function () {
        if (!$(this).children('.xwiki-async').length) {
          initializedWrappers.add(this);
        }
      });
    });
  });

  // XWiki sends its event, if any, right after replacing the placeholder, before the mutations are reported.
  const onResultInserted = function (wrapper) {
    if (!initializedWrappers.has(wrapper)) {
      initializedWrappers.add(wrapper);
      if (!$(wrapper).find('[' + PROCESSED_ERROR + ']').length) {
        $(wrapper).find('.proj-manag-async-error').attr(PROCESSED_ERROR, 'true');
        $(document).trigger('xwiki:dom:updated', {elements: [wrapper]});
      }
    }
  };

  // A single observer finds the wrappers wherever and whenever they are added, without depending on the events.
  new MutationObserver(mutations => {
    mutations.forEach(mutation => {
      if (mutation.addedNodes.length && mutation.target.classList?.contains(WRAPPER)) {
        onResultInserted(mutation.target);
      }
    });
  }).observe(document.documentElement, {childList: true, subtree: true});

  // The results that replaced their placeholder before the script started.
  $('.' + WRAPPER + '[data-proj-manag-async-placeholder]')
    .filter((index, wrapper) => !$(wrapper).children('.xwiki-async').length)
    .each((index, wrapper) => onResultInserted(wrapper));
});
