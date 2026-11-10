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
  // Edit a macro using the deprecated count parameter with the limit one, so that the limit chosen in the editor, the
  // default one included, isn't overridden by the deprecated parameter left in the macro. The limit wins over the count
  // when both are set, so it is kept when it doesn't have its default value. This script is loaded by the instance
  // parameter, which all the macros having a count parameter have.
  let migrateCount = function () {
    let macroEditor = $('.macro-editor');
    let countInput = macroEditor.find(':input[name="count"]');
    let limitInput = macroEditor.find(':input[name="limit"]');
    let count = (countInput.val() || '').trim();
    if (count === '' || limitInput.length === 0) {
      return;
    }
    let parameterDescriptors = (macroEditor.data('macroDescriptor') || {}).parameterDescriptorMap || {};
    let defaultLimit = (parameterDescriptors.limit || {}).defaultValue;
    let limit = (limitInput.val() || '').trim();
    if (limit === '' || defaultLimit === undefined || defaultLimit === null || limit === String(defaultLimit)) {
      limitInput.val(count);
    }
    countInput.val('');
  };
  $(document).on('show.bs.modal', '.modal', function () {
    let modal = $(this);
    setTimeout(function () {
      const content = modal.find('.macro-editor');
      if (content.length === 0) {
        return;
      }
      if (!content[0].classList.contains('loading')) {
        migrateCount();
        return;
      }
      const observer = new MutationObserver(() => {
        if (!content[0].classList.contains('loading')) {
          observer.disconnect();
          migrateCount();
        }
      });
      observer.observe(content[0], {
        attributes: true,
        attributeFilter: ['class'],
        subtree: true
      });
    });
  });
  // The script is loaded by the instance field, i.e. once the macro editor is already displayed.
  migrateCount();
});
