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
/**
 * The platform inserts the result of an asynchronous rendering in the page without notifying the page scripts, so the
 * errors it displays don't get the click listener that shows their details. Notify them for the errors displayed by
 * the project management macros, unless the page scripts already did it.
 */
require(['jquery'], function($) {
  'use strict';
  const processedAttribute = 'data-proj-manag-async-error-processed';

  const notify = function(root) {
    $(root).find('.proj-manag-async-error').addBack('.proj-manag-async-error').not('[' + processedAttribute + ']')
      .each(function() {
        this.setAttribute(processedAttribute, 'true');
        // The errors already made expandable by the page scripts have a pointer cursor.
        const error = $(this).find('.xwikirenderingerror');
        if (error.length && error[0].style.cursor !== 'pointer') {
          document.fire('xwiki:dom:updated', {elements: [this]});
        }
      });
  };

  const init = function() {
    notify(document.body);
    new MutationObserver(function(mutations) {
      mutations.forEach(function(mutation) {
        mutation.addedNodes.forEach(function(node) {
          if (node.nodeType === Node.ELEMENT_NODE) {
            notify(node);
          }
        });
      });
    }).observe(document.body, {childList: true, subtree: true});
  };

  if (window.XWiki && XWiki.domIsLoaded) {
    init();
  } else {
    document.observe('xwiki:dom:loaded', init);
  }
});
