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
 * Shows why the events of a project management calendar can't be loaded. The calendar macro of macro-fullcalendar
 * requests the events without telling anyone about a failure, so the requests are watched through the global Ajax
 * events of jQuery, which it uses. The messages are rendered, hidden, by the calendar macro, one per status of the
 * calendar REST resource: the one matching the status of the failed request is shown above the calendar, until the
 * events are loaded again.
 */
define('projectmanagement-calendar-load-error', [], function() {
  'use strict';

  // The status of the message displayed for the failures that have no message of their own.
  const OTHER = 'other';

  /**
   * The calendar macro requests its events from the URL it is given followed by its own parameters, starting with
   * xpage. Checking that they follow tells apart two calendars whose URLs only differ by their last parameters.
   *
   * @param eventsURL the URL of the events given to the calendar macro
   * @param requestURL the URL of an Ajax request
   * @return true if the request is a request of the events of the calendar
   */
  const isEventsRequest = function(eventsURL, requestURL) {
    return typeof eventsURL === 'string' && eventsURL !== '' && typeof requestURL === 'string'
      && requestURL.startsWith(eventsURL) && /^[?&]xpage=/.test(requestURL.substring(eventsURL.length));
  };

  /**
   * @param statuses the statuses of the messages rendered for the calendar
   * @param status the status of the response of the failed request
   * @return the status of the message to display
   */
  const getMessageStatus = function(statuses, status) {
    const value = String(status);
    return statuses.includes(value) ? value : OTHER;
  };

  /**
   * @return the calendars of the document whose events are requested by the given request
   */
  const getCalendars = function(document, requestURL) {
    return Array.from(document.querySelectorAll('.projectmanagement-calendar[data-events-url]')).filter(
      calendar => isEventsRequest(calendar.getAttribute('data-events-url'), requestURL));
  };

  /**
   * @return the messages of the calendar, not those of a calendar nested in its events
   */
  const getMessages = function(calendar) {
    return Array.from(calendar.children).filter(
      child => child.classList.contains('projectmanagement-calendar-load-error'));
  };

  return {
    isEventsRequest,
    getMessageStatus,

    /**
     * Show the message explaining why the request failed above the calendars whose events it requested.
     *
     * @param document the document holding the calendars
     * @param xhr the failed request
     * @param requestURL the URL of the request
     */
    onError: function(document, xhr, requestURL) {
      // No response: the request was canceled, e.g. when leaving the page (the browsers don't all report it as
      // aborted). The failures of the project management tool are reported by the calendar resource, with a status.
      if (!xhr || !xhr.status) {
        return;
      }
      getCalendars(document, requestURL).forEach(calendar => {
        const messages = getMessages(calendar);
        const status = getMessageStatus(messages.map(message => message.getAttribute('data-status')), xhr.status);
        messages.forEach(message => {
          message.hidden = message.getAttribute('data-status') !== status;
        });
      });
    },

    /**
     * Hide the messages of the calendars whose events were loaded by the given request.
     *
     * @param document the document holding the calendars
     * @param requestURL the URL of the request
     */
    onSuccess: function(document, requestURL) {
      getCalendars(document, requestURL).forEach(calendar => {
        getMessages(calendar).forEach(message => {
          message.hidden = true;
        });
      });
    }
  };
});

require(['jquery', 'projectmanagement-calendar-load-error'], function($, loadError) {
  'use strict';

  $(document).on('ajaxError', function(event, xhr, settings) {
    loadError.onError(document, xhr, settings && settings.url);
  }).on('ajaxSuccess', function(event, xhr, settings) {
    loadError.onSuccess(document, settings && settings.url);
  });
});
