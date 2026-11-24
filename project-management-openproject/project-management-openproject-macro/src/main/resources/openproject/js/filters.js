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
 require.config({
   paths: {
     'openproject-connection': new XWiki.Document(XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT))
       .getURL('jsx', 'resource=openproject/js/connection.js'),
     'openproject-version-suggestions': new XWiki.Document(XWiki.Model.resolve('Main.WebHome',
       XWiki.EntityType.DOCUMENT)).getURL('jsx', 'resource=openproject/js/versionSuggestions.js')
   }
 });
 require(['jquery', 'openproject-connection', 'openproject-version-suggestions'],
   function ($, connection, versionSuggestions) {
   let getInstance = connection.getInstance;
   // The work items picked in the macro editor are searched on the connection used by the macro.
   $(document).on('pm:collectSourceParameters', '.macro-editor-modal .pm-work-item-source', function (event, params) {
     let instance = getInstance();
     if (instance) {
       params.instance = instance;
     }
   });
   // Without a connection, the values of the filters can't be suggested: the user is told to choose one.
   let updateNoConnectionHint = function () {
     $('.macro-editor-modal .op-filters-no-connection').toggleClass('hidden', getInstance() !== '');
   };
   /*
    * The properties of the chosen instance, e.g. its custom fields, can be filtered on, and the values of the filters
    * are suggested from it. Without an instance, the generic properties are loaded. When the user changes the
    * connection, the filters already added are displayed again, so that they suggest the values of the new instance
    * instead of the ones of the instance chosen before.
    */
   let loadInstanceProperties = function (builderElement, connectionChanged) {
     let instance = getInstance();
     let builder = window.FilterBuilder && window.FilterBuilder.instances.get(builderElement[0]);
     // The builder starts with the generic properties, the ones loaded without an instance.
     if (builder && builder.setSourceParameters && (builderElement.data('instance') || '') !== instance) {
       builderElement.data('instance', instance);
       builder.setSourceParameters(instance ? {instance: instance} : {}, connectionChanged);
     }
   };
   /*
    * The versions suggested for a filter are the ones available in the projects that the builder filters on ("is"),
    * if any. The projects are read each time the suggestions are requested, since the filter on the projects can
    * change after the filter on the versions is displayed.
    */
   let restrictVersionsToFilteredProjects = function (builderElement, params) {
     let descriptor = Object.getOwnPropertyDescriptor(params, 'searchURL');
     if (!descriptor || descriptor.get || !versionSuggestions.isVersionSearch(params.searchURL)) {
       return;
     }
     let searchURL = params.searchURL;
     Object.defineProperty(params, 'searchURL', {
       configurable: true,
       enumerable: true,
       get: function () {
         let builder = window.FilterBuilder && window.FilterBuilder.instances.get(builderElement);
         return versionSuggestions.getSearchURL(searchURL, builder && builder.getConstraints().project);
       }
     });
   };
   let init = function (event, builderElement) {
     let element = builderElement || (window.FilterBuilder && window.FilterBuilder.instances.size > 0
       && window.FilterBuilder.instances.values().next().value.element);
     if (element) {
       element.on('displayingFilter', function (e, a, b, c, d) {
         if (d.searchURL && d.searchURL.includes('{instance}')) {
           let instance = getInstance();
           if (instance) {
             d.searchURL = d.searchURL.replace('{instance}', encodeURIComponent(instance));
           } else {
             // The suggestions can't be requested without an instance: only the typed values are proposed.
             delete d.searchURL;
           }
         }
         restrictVersionsToFilteredProjects(e.currentTarget, d);
       });
       // The builder is registered right after it's initialized.
       setTimeout(() => {
         loadInstanceProperties(element, false);
         updateNoConnectionHint();
       }, 0);
     }
   };
   init();
   $(document).on('filterBuilderInitialized', init);
   $(document).on('change', '.macro-editor-modal :input[name="instance"]', function () {
     updateNoConnectionHint();
     if (window.FilterBuilder) {
       window.FilterBuilder.instances.forEach(builder => loadInstanceProperties(builder.element, true));
     }
   });
 });
