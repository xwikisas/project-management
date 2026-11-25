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
 * Tells the user of the macro editor why the values of the filters can't be suggested when they can't read the data of
 * the OpenProject connection (e.g. they must connect their OpenProject account): the message of the macros, rendered by
 * OpenProject.Code.CKEditorPlugin, is displayed once a suggestion request fails, and removed once the suggestions are
 * retrieved again or when the connection changes. The message is kept until a request on its connection succeeds
 * (e.g. once the user connected their account in another tab), the next failure checking again whether the user can
 * read the data; the absence of message isn't kept, so that a user whose access expired gets the message on the next
 * failure.
 */
define('openproject-not-authorized-message', [], function () {
  'use strict';

  /**
   * @param fetchMessage requests the message of a connection: (connection) => promise of the HTML of the message,
   *     empty when the current user can read the data of the connection
   * @return {get(connection): promise of the message of the connection, a failure giving an empty message,
   *     forget(connection): requests the message of the connection again on the next get, e.g. once a request on the
   *     connection succeeded}; the requests made at once share their answer, and only a message telling that the user
   *     can't read the data of the connection is kept, until forgotten: the user can lose their access at any time
   */
  const createMessageLoader = function (fetchMessage) {
    const messages = {};
    const get = function (connection) {
      if (!messages[connection]) {
        const message = Promise.resolve(fetchMessage(connection))
          .then(answer => typeof answer === 'string' ? answer : '', () => '')
          .then(answer => {
            if (!answer && messages[connection] === message) {
              delete messages[connection];
            }
            return answer;
          });
        messages[connection] = message;
      }
      return messages[connection];
    };
    const forget = function (connection) {
      delete messages[connection];
    };
    return {get, forget};
  };

  /**
   * @param options the environment of the message:
   *     - getConnection: () => the connection used by the edited macro, empty when there's none
   *     - getMessage: (connection) => promise of the message of the connection (see createMessageLoader#get)
   *     - forgetMessage: (connection) => forgets the message of the connection, called once a request on it
   *       succeeded: the user can read its data now (see createMessageLoader#forget)
   *     - show: (html, context) => displays the message, unless it's already displayed, the context being the one of
   *       the failure, e.g. the filters whose suggestions failed
   *     - remove: () => removes the message, if displayed
   * @return the handlers of the events changing the message: suggestionsFailed(context), suggestionsLoaded,
   *     connectionChanged
   */
  const createNotifier = function (options) {
    return {
      suggestionsFailed: function (context) {
        const connection = options.getConnection();
        if (!connection) {
          return Promise.resolve();
        }
        return options.getMessage(connection).then(message => {
          // The connection may have changed while the message was loading.
          if (message && options.getConnection() === connection) {
            options.show(message, context);
          }
        });
      },
      suggestionsLoaded: function () {
        options.remove();
        const connection = options.getConnection();
        if (connection) {
          options.forgetMessage(connection);
        }
      },
      connectionChanged: function () {
        options.remove();
      }
    };
  };

  /**
   * @param href the URL of a link of the message, e.g. the link connecting the OpenProject account, which may come
   *     back to a page (its redirectUrl parameter)
   * @param baseURL the URL against which a relative link is resolved, e.g. the URL of the current page
   * @param redirectURL the page to come back to, e.g. the edited page
   * @return the URL of the link, coming back to the given page
   */
  const getLinkURL = function (href, baseURL, redirectURL) {
    const url = new URL(href, baseURL);
    if (url.searchParams.has('redirectUrl')) {
      url.searchParams.set('redirectUrl', redirectURL);
    }
    return url.toString();
  };

  return {
    createMessageLoader,
    createNotifier,
    getLinkURL
  };
});
