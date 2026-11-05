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
 * The charts are drawn by the Chart.js application on page load and on the xwiki:dom:updated event, in the updated
 * elements. When a chart is rendered asynchronously, the event sent by XWiki targets the placeholder that the chart
 * replaced, which isn't in the page anymore, so the chart isn't drawn. The chart macro wraps its output, so that the
 * event can be sent again for the wrapper, once its placeholder is replaced. The wrappers can be added after the page
 * load, e.g. when saving the in-place editor. The event isn't sent again when XWiki sent it for the inserted chart,
 * which would draw it twice.
 *
 * This works around XWIKI-25675 (The xwiki:dom:updated event sent after an asynchronous rendering targets the removed
 * placeholder instead of the rendered content, https://jira.xwiki.org/browse/XWIKI-25675): remove this script and the
 * chart wrapper once the minimal supported XWiki version includes its fix.
 */
require(['jquery'], function ($) {
  let observeWrapper = function (wrapper) {
    if ($(wrapper).data('projManagObserved')) {
      return;
    }
    $(wrapper).data('projManagObserved', true);
    const observer = new MutationObserver(() => {
      observer.disconnect();
      // XWiki sends its event right after replacing the placeholder, before the mutations are reported.
      if (!$(wrapper).data('projManagUpdated')) {
        $(document).trigger('xwiki:dom:updated', {elements: [wrapper]});
      }
    });
    // Only the replacement of the placeholder, not the changes inside it (e.g. its loading animation).
    observer.observe(wrapper, {childList: true});
  };

  let observeWrappers = function (root) {
    $(root).find('.proj-manag-chart-wrapper').addBack('.proj-manag-chart-wrapper').each(function () {
      observeWrapper(this);
    });
  };

  observeWrappers(document);
  $(document).on('xwiki:dom:updated', function (event, data) {
    $(data?.elements || document).each(function () {
      observeWrappers(this);
      // Only the elements inserted in a wrapper let the Chart.js application draw its chart, not the elements removed
      // from the page, e.g. a replaced placeholder.
      if (this.isConnected) {
        $(this).parent().closest('.proj-manag-chart-wrapper').data('projManagUpdated', true);
      }
    });
  });
});
