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
 * Remove this script and the wrapper once the minimal supported XWiki version sends the event for the rendered content
 * (XWIKI-25675, https://jira.xwiki.org/browse/XWIKI-25675).
 */
require(['jquery'], function ($) {
  const WRAPPER = 'proj-manag-async-wrapper';

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
      $(document).trigger('xwiki:dom:updated', {elements: [wrapper]});
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
});
