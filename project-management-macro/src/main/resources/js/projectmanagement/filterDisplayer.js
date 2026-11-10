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
define(['jquery', 'moment', 'moment-jdateformatparser', 'xwiki-selectize'], function ($) {
  /*
   * The date picker is loaded when a date is displayed, from the path given by the filter field: in the macro editor,
   * this script can be loaded before the field is added to the page.
   */
  let loadDatePicker = function () {
    let deps = JSON.parse($('.proj-manag-filter[data-deps]').first().attr('data-deps') || '{}');
    if (deps.daterangepicker) {
      require.config({ paths: { daterangepicker: deps.daterangepicker } });
    }
    return new Promise((resolve, reject) => require(['daterangepicker'], resolve, reject));
  };
  /*
   * The number of seconds added to the end of a range of dates when it's saved, so that the range includes its last
   * minute.
   */
  const RANGE_END_SECONDS = 59;
  /*
   * The dates of a saved value, to select in the date picker: the bounds of an ISO 8601 time interval for the "between"
   * operator, and a single date otherwise, in the ISO 8601 format or the date format of the filter. Empty when there's
   * no saved value or when it can't be parsed, the date picker then selecting the current day.
   */
  let getPickerDates = function (value, operator, dateFormat) {
    let parse = text => {
      // Keep the time zone of the saved date, so that the dates are displayed as they were saved, whatever the time
      // zone of the browser.
      let date = moment.parseZone(text, moment.ISO_8601, true);
      return date.isValid() ? date : moment(text, dateFormat, true);
    };
    if (!value) {
      return {};
    } else if (operator === 'between') {
      let bounds = value.split('/');
      if (bounds.length === 1) {
        // A single date (e.g. saved by an older version) is both the start and the end of the range.
        let date = parse(value);
        return date.isValid() ? { startDate: date, endDate: date.clone() } : {};
      }
      let startDate = parse(bounds[0]);
      let endDate = bounds.length === 2 ? parse(bounds[1]) : moment.invalid();
      if (!startDate.isValid() || !endDate.isValid()) {
        return {};
      }
      // Remove the seconds added when the range was saved, which can move its end to the next day.
      if (endDate.seconds() !== 0) {
        endDate.subtract(RANGE_END_SECONDS, 'seconds');
      }
      return { startDate: startDate, endDate: endDate };
    }
    let date = parse(value);
    return date.isValid() ? { startDate: date, endDate: date.clone() } : {};
  };
  let clean = function (type, inputElem, operator) {
    let parent = inputElem.parent();
    switch (type) {
      case "boolean":
      case "list":
        if (!inputElem[0]) {
          return;
        }
        if (inputElem[0].selectize) {
          inputElem[0].selectize.destroy();
        } else if (inputElem[0].tomselect) {
          inputElem[0].tomselect.destroy();
        }
        break;
      case "date":
        // A date picker still loading isn't displayed.
        inputElem.removeData('dateDisplay').removeClass('loading');
        parent.find('.proj-manag-date-val').data("daterangepicker")?.remove();
      default:
        parent.children().remove();
        parent.append(inputElem);
    }
  };
  let display = function (type, inputElem, operator, params) {
    clean(type, inputElem, operator);
    let parent = inputElem.parent();
    inputElem.removeClass('hidden');
    let builder = $(parent).closest('.proj-manag-constraint-builder')[0];
    window.FilterBuilder.instances.get(builder).element.trigger('displayingFilter', [type, inputElem, operator, params]);
    switch (type) {
      case "number":
        inputElem.attr('type', 'number');
        break;
      case "date":
        if (inputElem.prop('disabled')) {
          inputElem.attr('type', 'text');
          return;
        }
        // The value is marked as loading until the date picker displays it.
        inputElem.attr('type', 'text').addClass('hidden loading');
        let dateDisplay = {};
        inputElem.data('dateDisplay', dateDisplay);
        loadDatePicker().then(() => {
          // The value may have been displayed again, or cleaned, while the date picker was loading.
          if (inputElem.data('dateDisplay') !== dateDisplay) {
            return;
          }
          let dateFormat = params.dateFormat ? moment.toMomentFormatString(params.dateFormat) : 'YYYY/MM/DD HH:mm';
          let dateInput = $('<input />').attr('type', 'text').addClass('proj-manag-date-val').on('change', function() {
            let valToSet = '';
            const daterangepicker = $(this).data("daterangepicker");
            if (!daterangepicker) {
              return;
            }
            if (operator === "between") {
              // Serialize the date range as a ISO 8601 time interval, without fractional seconds.
              // See https://en.wikipedia.org/wiki/ISO_8601#Time_intervals
              let endDate = daterangepicker.endDate.clone().add(RANGE_END_SECONDS, 'seconds');
              valToSet = `${daterangepicker.startDate.format()}/${endDate.format()}`;
            } else if (operator === "before" || operator === "after") {
              // Use the ISO 8601 representation, without fractional seconds.
              valToSet = daterangepicker.startDate.format();
            } else {
              // Use the formatted date.
              valToSet = daterangepicker.startDate.format(dateFormat);
            }
            inputElem.val(valToSet);
            inputElem.trigger('change');
          }).appendTo(parent).daterangepicker($.extend({
            drops: "down",
            opens: "right",
            timePicker: /[Hhkms]/.test(dateFormat),
            singleDatePicker: 'between' != operator,
            timePicker24Hour: true,
            locale: {
              format: dateFormat,
              cancelLabel: "Clear",
            },
          // The date picker selects the saved dates, instead of the current day.
          }, getPickerDates(inputElem.val(), operator, dateFormat))).on("hide.daterangepicker", function (e) {
            // Overwrite at instance level the 'hide' function added by Prototype.js to the Element prototype.
            // This removes the 'hide' function only for the event target.
            e.target.hide = undefined;
            // Restore the 'hide' function after the event is handled (i.e. after all the listeners have been called).
            setTimeout(function() {
              // This deletes the local 'hide' key from the instance, making the 'hide' inherited from the prototype
              // visible again (the next calls to 'hide' won't find the key on the instance and thus it will go up
              // the prototype chain).
              delete e.target["hide"];
            }, 0);
          });
          dateInput.data("daterangepicker").container.addClass('proj-manag-date-picker');
          // The saved value is now displayed as the date picker formats it (e.g. in the summary of the filters).
          inputElem.removeClass('loading').trigger('dateDisplayed');
        }).catch(error => {
          inputElem.removeClass('loading');
          console.error('Failed to load the date picker.', error);
        });
        break;
      case "list":
        let options = params.options || [];
        let selectizeCfg = {
          create: true,
          // A list filter can match several values (the value of the input holding them separated by commas).
          maxItems: params.multiple ? null : 1
        };
        if (options.size() > 0) {
          selectizeCfg.options = options;
        }
        if (params.searchURL) {
          let search = function (text, selectedItem, callback) {
            let searchURL = params.searchURL.replace("{encodedQuery}", encodeURIComponent(text));
            if (selectedItem) {
              let selectedItemParam = 'selectedItem=' + encodeURIComponent(selectedItem);
              searchURL += searchURL.indexOf('?') >= 0 ? '&' + selectedItemParam : '?' + selectedItemParam;
            }
            $.getJSON(searchURL)
              .then(function (results) {
                if (Array.isArray(results)) {
                  callback(results);
                  return;
                }
                callback([]);
            })
            .catch(function (e) {
              console.log(`error trying to retrieve selectize searchUrl ${searchURL} elements.` + e);
              callback([]);
            });
          };
          selectizeCfg.load = function (text, callback) {
            search(text, params.multiple ? '' : inputElem.val(), callback);
          };
          // Each selected value is loaded on its own, to get its label.
          selectizeCfg.loadSelected = function (value, callback) {
            search('', value, callback);
          };
        }
        inputElem.xwikiSelectize(selectizeCfg);
        break;
      case "boolean":
        inputElem.attr('type', 'text');
        inputElem.xwikiSelectize({
          create: false,
          options: [
            {
              value: params.trueValue || 'true',
              label: params.trueValue || 'true'
            },
            {
              value: params.falseValue || 'false',
              label: params.falseValue || 'false'
            }
          ],
          maxItems: 1
        });
        break;
      default: // text
        inputElem.attr('type', 'text');
        break;
    }
  };
  return {
    display: display,
    clean: clean
  };
});
