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
 * The OpenProject part of the widget choosing how an OpenProject macro selects its work packages
 * (openprojectworkitemsource/edit.vm): describes the OpenProject link of the url way under its field, with the link
 * REST resource, on the connection used by the macro. The description is displayed as an XWiki information or warning
 * message, depending on its level; an empty link has no description.
 */
(function () {
  const getResourceURL = function (resource) {
    return new XWiki.Document(XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT))
      .getURL('jsx', 'resource=' + resource);
  };
  require.config({
    paths: {
      'openproject-link-description': getResourceURL('openproject/js/linkDescription.js')
    }
  });
})();
require(['jquery', 'openproject-link-description'], function ($, linkDescription) {
  'use strict';

  const WIDGET = '.macro-editor-modal .pm-work-item-source';

  // The resource only describes the link to the users who can edit the page of the macro.
  const getRestUrl = function (widget) {
    const page = XWiki.Model.serialize(XWiki.currentDocument.documentReference);
    return widget.attr('data-rest-url') + '/wikis/' + encodeURIComponent(widget.attr('data-wiki') || '')
      + '/openproject/link?document=' + encodeURIComponent(page);
  };

  // The connection used by the macro, given by the handlers of pm:collectSourceParameters (see filters.js).
  const getInstance = function (widget) {
    const parameters = {};
    widget.trigger('pm:collectSourceParameters', [parameters]);
    return parameters.instance || '';
  };

  const show = function (widget, description) {
    const container = widget.find('.pm-source-url-description').empty();
    if (description && description.message) {
      container.append($('<div></div>').addClass(linkDescription.getMessageClass(description.level))
        .text(description.message));
    }
  };

  const describe = function (widget, url) {
    if (!widget.length) {
      return;
    }
    // An empty link cancels the request of the previous one, if any.
    linkDescription.describeLink(getRestUrl(widget), getInstance(widget), url).then(function (description) {
      // Null for an empty link, and when a newer link replaced this one.
      if (description || String(url || '').trim() === '') {
        show(widget, description);
      }
    }, function () {
      // The link can't be described (e.g. no connection): the macro shows its error when it's rendered.
      show(widget, null);
    });
  };

  const describeCurrent = function (widget) {
    describe(widget, widget.find('.pm-source-url-input').val());
  };

  $(document).on('pm:urlChanged', WIDGET, function (event, url) {
    describe($(this), url);
  });
  // The same link can mean something else on another connection.
  $(document).on('change', '.macro-editor-modal :input[name="instance"]', function () {
    describeCurrent($(this).closest('.macro-editor-modal').find('.pm-work-item-source').first());
  });
  // The widget may have been bound before this script was loaded.
  describeCurrent($(WIDGET).first());
});
