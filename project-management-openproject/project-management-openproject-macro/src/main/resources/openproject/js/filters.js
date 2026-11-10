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
   let getInstance = function () {
     let input = $('.macro-editor-modal :input[name="instance"]');
     // An empty instance means the default connection, whose name is held by the instance picker.
     return input.val() || input.attr('data-default-connection');
   };
   // The properties of the chosen instance, e.g. its custom fields, can be filtered on.
   let loadInstanceProperties = function (builderElement) {
     let instance = getInstance();
     let builder = instance && window.FilterBuilder && window.FilterBuilder.instances.get(builderElement[0]);
     if (builder && builder.setSourceParameters && builderElement.data('instance') !== instance) {
       builderElement.data('instance', instance);
       builder.setSourceParameters({instance: instance});
     }
   };
   let init = function (event, builderElement) {
     let element = builderElement || (window.FilterBuilder && window.FilterBuilder.instances.size > 0
       && window.FilterBuilder.instances.values().next().value.element);
     if (element) {
       element.on('displayingFilter', function (e, a, b, c, d) {
         let instance = getInstance();
         if (d.searchURL && instance) {
           d.searchURL = d.searchURL.replace('{instance}', instance);
         }
       });
       // The builder is registered right after it's initialized.
       setTimeout(() => loadInstanceProperties(element), 0);
     }
   };
   init();
   $(document).on('filterBuilderInitialized', init);
   $(document).on('change', '.macro-editor-modal :input[name="instance"]', function () {
     if (window.FilterBuilder) {
       window.FilterBuilder.instances.forEach(builder => loadInstanceProperties(builder.element));
     }
   });
 });
