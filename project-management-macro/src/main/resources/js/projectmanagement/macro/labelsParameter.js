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
  let updateInput = function () {
    let vals = $('.proj-manag-chart-label input').map(function () { return $(this).val(); }).get();
    $('#proj-manag-chart-parameter-labels').val(JSON.stringify(vals));
  };
  let createNewLabel = function (title, value) {
    var template = document.getElementById("proj-manag-chart-label-template").content;
    var clone = $(template).clone();
    clone.find("label").text(title);
    clone.find("input").val(value).on('change', updateInput);
    clone.find(".proj-manag-chart-label-remove").on('click', (e) => {
      $(e.target).closest('.proj-manag-chart-label').remove();
      updateInput();
    });
    $(".proj-manag-chart-labels").append(clone);

  };
  let init = function () {
    let labelsInput = $('#proj-manag-chart-parameter-labels');
    if (labelsInput.length <= 0) {
      return;
    }
    let labels = JSON.parse(labelsInput.val());
    labels.forEach((element, i) => {
      createNewLabel("Dataset #" + (i + 1) + " label", element);
    });
  };
  init();
  $(document).on('click', '.proj-manag-chart-label-new', function () {
    createNewLabel("Dataset #" + ($('.proj-manag-chart-label').length + 1) + " label", "");
  });
  $(document).on('shown.bs.modal', '.modal', function () {
    init();
  });
});

