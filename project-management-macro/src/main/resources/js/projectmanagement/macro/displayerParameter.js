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
  const unneededParams = {
    liveData: [],
    liveDataCards: [],
    workItemsSingle: ["sort", "offset", "limit", "properties"]
  };
  let hideShowParams = function () {
    let displayerVal = $('.macro-editor select[name="workItemsDisplayer"]').val();
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
  $(document).on('change', '.macro-editor select[name="workItemsDisplayer"]', hideShowParams);
  $(document).on('show.bs.modal', '.modal', function () {
    setTimeout(hideShowParams);
  });
  hideShowParams();
});