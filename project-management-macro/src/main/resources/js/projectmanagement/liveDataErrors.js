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
 * Tells the viewers of a Live Data table of work items why its entries couldn't be retrieved, e.g. when the tool rejects
 * a filter value typed in the table. Live Data itself only shows a transient "unknown reason" notification and keeps
 * the entries of the previous request, which look like the result of the rejected filter.
 *
 * Live Data (15.10 and later) asks the source of an instance for its entries right after firing
 * xwiki:livedata:beforeEntryFetch on it: the first time, the listener of this event wraps the getEntries method of the
 * source of the project management instances. A request that the server answered with an error (a 400 when the tool
 * rejected it, another status when the tool failed) then gives no entry and shows a message above the table, until a
 * request succeeds. The requests that didn't complete (e.g. aborted by a newer request) fail as before, which Live Data
 * ignores.
 */
define('projectmanagement-live-data-errors-l10n', {
  prefix: 'projectmanagement.liveData.error.',
  keys: ['rejected', 'failed']
});

define('projectmanagement-live-data-errors', [], function () {
  'use strict';

  const SOURCE_ID = 'projectmanagement';
  const MESSAGE_CLASS = 'proj-manag-livedata-error';
  // The XMLHttpRequest readyState of a request that completed, see
  // https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest/readyState
  const DONE = 4;
  const BAD_REQUEST = 400;

  const watchedSources = new WeakSet();
  const messages = new WeakMap();

  /**
   * @param {object} error the reason why the source couldn't get the entries: the jQuery request of the XWiki source
   * @return {string|undefined} the failure to show: 'rejected' when the tool rejected the request, 'failed' when the
   *   server answered with another error, undefined when the request didn't complete
   */
  const getFailure = function (error) {
    if (error?.readyState !== DONE) {
      return undefined;
    }
    return error.status === BAD_REQUEST ? 'rejected' : 'failed';
  };

  /**
   * Makes the source of a Live Data instance of work items report the failures of the requests for its entries
   * instead of failing: the instance then has no entry, since the request has no result.
   *
   * @param {object} liveData the Live Data instance (its logic)
   * @param {function(string|undefined)} onResult called after each completed request for the entries, with the
   *   failure (see getFailure) or undefined when the request succeeded
   * @return {boolean} whether the source of the instance is watched by this call, false when it isn't a project
   *   management source or is already watched
   */
  const watch = function (liveData, onResult) {
    const source = liveData?.liveDataSource;
    if (liveData?.data?.query?.source?.id !== SOURCE_ID || typeof source?.getEntries !== 'function'
        || watchedSources.has(source)) {
      return false;
    }
    watchedSources.add(source);
    const getEntries = source.getEntries;
    source.getEntries = function (...args) {
      return Promise.resolve(getEntries.apply(this, args)).then(entries => {
        onResult(undefined);
        return entries;
      }, error => {
        const failure = getFailure(error);
        if (!failure) {
          throw error;
        }
        onResult(failure);
        return {count: 0, entries: []};
      });
    };
    return true;
  };

  const translate = function (failure) {
    return new Promise(resolve => {
      require(['xwiki-l10n!projectmanagement-live-data-errors-l10n'], l10n => resolve(l10n.get(failure)),
        () => resolve(failure));
    });
  };

  /**
   * Shows the failure above the Live Data instance, or removes the message shown for the previous failure.
   */
  const showFailure = function (liveData, failure) {
    const element = liveData.element;
    // The element of the instance is the one of the Live Data macro on XWiki 15.10, the root of the Vue application
    // mounted in it on more recent versions: the message is put next to the macro, outside of the application.
    const container = element?.closest('.liveData') || element;
    let message = messages.get(liveData);
    if (!failure) {
      message?.remove();
      return;
    }
    if (!container?.parentNode) {
      return;
    }
    if (!message) {
      message = document.createElement('div');
      message.className = 'box errormessage ' + MESSAGE_CLASS;
      message.setAttribute('role', 'alert');
      messages.set(liveData, message);
    }
    message.dataset.failure = failure;
    if (message.nextSibling !== container) {
      container.parentNode.insertBefore(message, container);
    }
    translate(failure).then(text => {
      if (message.dataset.failure === failure) {
        message.textContent = text;
      }
    });
  };

  const init = function (root) {
    root.addEventListener('xwiki:livedata:beforeEntryFetch', event => {
      const liveData = event.detail?.livedata;
      watch(liveData, failure => showFailure(liveData, failure));
    });
  };

  return {getFailure, watch, init};
});

require(['projectmanagement-live-data-errors'], function (liveDataErrors) {
  liveDataErrors.init(document);
});
