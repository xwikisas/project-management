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
  // A picker whose value is held by a hidden input (the picker itself having no name, see
  // openprojectprojectdetailsproperty/edit.vm) is initialized from that input, which the macro editor fills with the
  // parameter value, and updates it when the selection changes.
  let syncWithValueInput = function (picker) {
    let valueInput = picker.closest('.proj-manag-property-picker-field').find('input.proj-manag-property-picker-value');
    if (valueInput.length === 0) {
      return;
    }
    let values = (valueInput.val() || '').split(',').map(value => value.trim()).filter(value => value !== '');
    // Like the macro editor does for the select inputs: use the value of the option matching each value regardless of
    // the case (the parameter values are case insensitive), and add an option for the values matching none, so that
    // they are kept.
    values = values.map(value => {
      let option = picker.find('option').filter(function () {
        return $(this).val().toUpperCase() === value.toUpperCase();
      });
      if (option.length > 0) {
        return option.first().val();
      }
      $('<option></option>').val(value).text(value).appendTo(picker);
      return value;
    });
    picker.val(values);
    picker.off('change.valueInput').on('change.valueInput', function () {
      let selected = picker.val() || [];
      valueInput.val(Array.isArray(selected) ? selected.join(',') : selected);
    });
  };

  let selectizeWithCustomizations = function () {

    // The remove_button plugin fails at the following lines 
    // https://github.com/selectize/selectize.js/blob/c635f80a35d0b02cc48982ac7ace630fb5bde9bf/src/plugins/remove_button/plugin.js#L49-L50
    // because the html_container is passed as a jquery object, due to the xwiki customizations, while the plugin expects a string.
    // As a patch, we define the used and use them over the element html.
    $.fn.search = function (substr) {
      return this.prop('outerHTML').search(substr);
    };
    $.fn.substring = function (startIndex, endIndex) {
      return this.prop('outerHTML').substring(startIndex, endIndex);
    };

    let options = {
      plugins: {
        remove_button: {
          title: $('.proj-manag-property-picker').data('remove-title') || 'Remove',
          className: 'proj-manag-property-remove'
        }
      }
    };
    let element = $('.proj-manag-property-picker');
    if (!element.attr('multiple')) {
      delete options.plugins;
    }
    element.not('.selectized').each(function () {
      syncWithValueInput($(this));
    });
    element.xwikiSelectize(options);
    //delete $.fn.search;
    //delete $.fn.substring;
  };
  $(document).on('show.bs.modal', '.modal', function () {
    let modal = $(this);
    setTimeout(() => {
      const content = $(this).find('.macro-editor'); // adjust selector
      if (!content || content.length <= 0) {
        console.log('Modal content not found');
        return;
      }
      if (!content[0].classList.contains('loading')) {
        selectizeWithCustomizations();
        return;
      }
      const observer = new MutationObserver(() => {
        if (!content[0].classList.contains('loading')) {
          observer.disconnect();
          selectizeWithCustomizations();
        }
      });

      observer.observe(content[0], {
        attributes: true,
        attributeFilter: ['class'],
        subtree: true
      });
    });

  });
  $(document).on('hide.bs.modal', '.modal', function () {
    delete $.fn.search;
    delete $.fn.substring;
  });
  selectizeWithCustomizations();
});
