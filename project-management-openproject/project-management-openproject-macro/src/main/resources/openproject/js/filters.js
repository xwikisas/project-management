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
     'openproject-not-authorized-message': new XWiki.Document(XWiki.Model.resolve('Main.WebHome',
       XWiki.EntityType.DOCUMENT)).getURL('jsx', 'resource=openproject/js/notAuthorizedMessage.js')
   }
 });
 require(['jquery', 'openproject-connection', 'openproject-not-authorized-message'],
   function ($, connection, notAuthorizedMessage) {
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
    * When the values of a filter can't be suggested because the current user can't read the data of the connection,
    * the message of the macros is displayed above the filters. Its link to connect the OpenProject account opens in a
    * new tab, so that the edited macro isn't lost, and comes back to the edited page.
    */
   let notAuthorizedNotifier = notAuthorizedMessage.createNotifier({
     getConnection: getInstance,
     getMessage: notAuthorizedMessage.createMessageLoader(function (instance) {
       let pluginReference = XWiki.Model.resolve('OpenProject.Code.CKEditorPlugin', XWiki.EntityType.DOCUMENT,
         XWiki.currentDocument.documentReference);
       let url = new XWiki.Document(pluginReference).getURL('get',
         $.param({outputSyntax: 'plain', connection: instance}));
       return Promise.resolve($.getJSON(url)).then(response => response && response.notAuthorizedMessage);
     }),
     show: function (message, builderElement) {
       if ($('.macro-editor-modal .op-filters-not-authorized').length) {
         return;
       }
       let container = $('<div></div>').addClass('op-filters-not-authorized').html(message);
       container.find('a[href]').each(function () {
         $(this).attr({
           href: notAuthorizedMessage.getLinkURL(this.href, window.location.href,
             XWiki.currentDocument.getURL('view')),
           target: '_blank',
           rel: 'noopener'
         });
       });
       builderElement.before(container);
     },
     remove: function () {
       $('.macro-editor-modal .op-filters-not-authorized').remove();
     }
   });
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
       });
       element.off('suggestionsFailed.openproject').on('suggestionsFailed.openproject', function () {
         notAuthorizedNotifier.suggestionsFailed(element);
       });
       // The message goes away once the suggestions work, e.g. after the user connected their account in another tab.
       element.off('suggestionsLoaded.openproject').on('suggestionsLoaded.openproject',
         notAuthorizedNotifier.suggestionsLoaded);
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
     notAuthorizedNotifier.connectionChanged();
     if (window.FilterBuilder) {
       window.FilterBuilder.instances.forEach(builder => loadInstanceProperties(builder.element, true));
     }
   });
 });
