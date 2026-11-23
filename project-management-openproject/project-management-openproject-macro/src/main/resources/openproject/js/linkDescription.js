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
 * Describes the OpenProject link typed in the macro editor, with the OpenProject link REST resource
 * (/wikis/{wiki}/openproject/link?instance=&url=), which answers {kind, level, message, name?, filterCount?}.
 * The requests are debounced while the link is typed, and only the reply to the latest call is returned: the promise
 * of a call replaced by a newer one resolves with null.
 */
define('openproject-link-description', [], function () {
  'use strict';

  const DEFAULT_DELAY = 300;

  /**
   * @param restUrl the URL of the link REST resource, e.g. /xwiki/rest/wikis/xwiki/openproject/link
   * @param instance the name of the OpenProject connection, empty for the default one
   * @param url the link to describe
   * @return the URL of the request describing the link
   */
  const getRequestUrl = function (restUrl, instance, url) {
    const parameters = new URLSearchParams();
    parameters.set('instance', instance || '');
    parameters.set('url', url);
    return restUrl + (restUrl.includes('?') ? '&' : '?') + parameters.toString();
  };

  /**
   * @param fetchDescription requests the description of a link: (requestUrl) => promise of the JSON description
   * @param delay the time to wait, in milliseconds, for the link to stop changing before requesting its description
   * @return a describeLink(restUrl, instance, url) function returning the promise of the description of the link, or of
   *     null when the link is empty, or when a newer call replaced this one
   */
  const createDescriber = function (fetchDescription, delay) {
    const wait = delay === undefined ? DEFAULT_DELAY : delay;
    let latestCall = 0;
    let timeout;
    // Resolves the call whose request isn't sent yet.
    let resolveWaitingCall;
    return function (restUrl, instance, url) {
      const call = ++latestCall;
      clearTimeout(timeout);
      if (resolveWaitingCall) {
        resolveWaitingCall(null);
        resolveWaitingCall = undefined;
      }
      const link = (url || '').trim();
      if (!link) {
        return Promise.resolve(null);
      }
      return new Promise(function (resolve, reject) {
        resolveWaitingCall = resolve;
        timeout = setTimeout(function () {
          resolveWaitingCall = undefined;
          fetchDescription(getRequestUrl(restUrl, instance, link)).then(function (description) {
            resolve(call === latestCall ? description : null);
          }, function (error) {
            if (call === latestCall) {
              reject(error);
            } else {
              resolve(null);
            }
          });
        }, wait);
      });
    };
  };

  const fetchJson = function (requestUrl) {
    return fetch(requestUrl, {headers: {'Accept': 'application/json'}, credentials: 'same-origin'})
      .then(function (response) {
        if (!response.ok) {
          const error = new Error('Failed to describe the OpenProject link: ' + response.status);
          error.status = response.status;
          throw error;
        }
        return response.json();
      });
  };

  /**
   * @param level the level of a description: info, or warning when the macro wouldn't display the work packages of the
   *     link as the user expects
   * @return the classes of the message box displaying the description, as the XWiki messages
   */
  const getMessageClass = function (level) {
    return level === 'info' ? 'box infomessage' : 'box warningmessage';
  };

  return {
    getRequestUrl,
    getMessageClass,
    createDescriber,
    describeLink: createDescriber(fetchJson)
  };
});
