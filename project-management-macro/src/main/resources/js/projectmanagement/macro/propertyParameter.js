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
    $('#proj-manag-property-picker').xwikiSelectize({
      plugins: {
        remove_button: {
          title: $('#proj-manag-property-picker').data('remove-title') || 'Remove',
          className: 'proj-manag-property-remove'
        }
      }
    });
    //delete $.fn.search;
    //delete $.fn.substring;
  };
  $(document).on('show.bs.modal', '.modal', function () {
    setTimeout(selectizeWithCustomizations);
  });
  $(document).on('hide.bs.modal', '.modal', function () {
    delete $.fn.search;
    delete $.fn.substring;
  });
  selectizeWithCustomizations();
});
