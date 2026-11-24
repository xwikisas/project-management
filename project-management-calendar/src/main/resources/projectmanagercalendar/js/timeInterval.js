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
 * The editor of the Visible Hours (timeInterval parameter) of the calendar macros: two time inputs, the start and the
 * end, backed by the hidden input holding the value of the parameter, e.g. 06:00-20:00. A time input can't hold 24:00,
 * the end of the day: an end of 00:00 stands for it. An empty start or end is written as the start or the end of the
 * day, so that the parameter always holds both times: the calendar has other defaults for a missing one (e.g. its
 * last visible hour ends at 23:00).
 */
define('projectmanagement-calendar-time-interval', [], function() {
  'use strict';

  const START_OF_DAY = '00:00';

  const END_OF_DAY = '24:00';

  /**
   * @param time a time of the timeInterval parameter, e.g. 6:00
   * @param isEnd whether the time is the end of the visible hours
   * @return the time to display in a time input, e.g. 06:00
   */
  const toInputTime = function(time, isEnd) {
    // A time input only holds two-digit hours.
    const value = time.trim().replace(/^(\d):/, '0$1:');
    return isEnd && /^24:00(:00)?$/.test(value) ? START_OF_DAY : value;
  };

  /**
   * @param value the value of the timeInterval parameter, e.g. 06:00-24:00
   * @return the start and the end to display in the time inputs, e.g. ['06:00', '00:00']; a time that a time input
   *     can't hold is displayed empty
   */
  const toInputs = function(value) {
    const separator = (value || '').indexOf('-');
    if (separator < 0) {
      return [toInputTime(value || '', false), ''];
    }
    return [toInputTime(value.substring(0, separator), false), toInputTime(value.substring(separator + 1), true)];
  };

  /**
   * @param start the start displayed in the time input, e.g. 07:00, or an empty string
   * @param end the end displayed in the time input, e.g. 00:00, or an empty string
   * @return the value of the timeInterval parameter, e.g. 07:00-24:00, or an empty string when both are empty (the
   *     macro editor then writes no parameter)
   */
  const toParameter = function(start, end) {
    if (!start && !end) {
      return '';
    }
    const isEndOfDay = !end || /^00:00(:00(\.0+)?)?$/.test(end);
    return (start || START_OF_DAY) + '-' + (isEndOfDay ? END_OF_DAY : end);
  };

  // The fields already bound to their hidden input.
  const boundFields = new WeakSet();

  return {
    toInputs,
    toParameter,

    /**
     * Display the value of the hidden input in the time inputs, and write the times back into it when they change.
     * The value is written only when a time changes, so that the value of the macro is kept as it is otherwise,
     * including the times that the time inputs can't hold.
     *
     * @param field the element holding the hidden input and the two time inputs
     */
    bind: function(field) {
      const hiddenInput = field.querySelector('.time-interval-hidden');
      const startInput = field.querySelector('.time-interval-min');
      const endInput = field.querySelector('.time-interval-max');
      if (!hiddenInput || !startInput || !endInput || boundFields.has(field)) {
        return;
      }
      boundFields.add(field);
      [startInput.value, endInput.value] = toInputs(hiddenInput.value);
      const write = function() {
        hiddenInput.value = toParameter(startInput.value, endInput.value);
      };
      [startInput, endInput].forEach(input => {
        input.addEventListener('input', write);
        input.addEventListener('change', write);
      });
    }
  };
});

require(['jquery', 'projectmanagement-calendar-time-interval'], function($, timeInterval) {
  'use strict';

  // The macro editor sets the value of the hidden input before adding the field to the page. This script is loaded by
  // the first macro editor displaying the field, possibly after the editor is displayed.
  const bindFields = function(elements) {
    $(elements).find('.time-interval-hidden').addBack('.time-interval-hidden').each(function() {
      timeInterval.bind(this.parentElement);
    });
  };
  $(document).on('xwiki:dom:updated', function(event, data) {
    bindFields(data && data.elements);
  });
  bindFields(document.body);
});
