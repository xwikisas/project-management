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
define(['jquery', 'moment', 'moment-jdateformatparser', 'xwiki-selectize', 'daterangepicker'], function ($) {
  let clean = function (type, inputElem, operator) {
    let parent = inputElem.parent();
    switch (type) {
      case "boolean":
      case "list":
        if (inputElem[0] && inputElem[0].selectize) {
          inputElem[0].selectize.destroy();
        }
        break;
      case "date":
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
    switch (type) {
      case "number":
        inputElem.attr('type', 'number');
        break;
      case "date":
        let dateFormat = params.dateFormat ? moment.toMomentFormatString(params.dateFormat) : 'YYYY/MM/DD HH:mm';
        let dateInput = $('<input />').attr('type', 'text').addClass('proj-manag-date-val').on('change', function() {
          let valToSet = '';
          const daterangepicker = $(this).data("daterangepicker");
          if (!daterangepicker) {
            return;
          }
          if (this.operator === "between") {
            // Serialize the date range as a ISO 8601 time interval, without fractional seconds.
            // See https://en.wikipedia.org/wiki/ISO_8601#Time_intervals
            valToSet = `${daterangepicker.startDate.format()}/${daterangepicker.endDate.add(59, "seconds").format()}`;
          } else if (this.operator === "before" || this.operator === "after") {
            // Use the ISO 8601 representation, without fractional seconds.
            valToSet = daterangepicker.startDate.format();
          } else {
            // Use the formatted date.
            valToSet = daterangepicker.startDate.format(this.format);
          }
          inputElem.val(valToSet);
          inputElem.trigger('change');
        }).appendTo(parent).daterangepicker({
          drops: "down",
          opens: "right",
          timePicker: /[Hhkms]/.test(dateFormat),
          singleDatePicker: 'between' != operator,
          timePicker24Hour: true,
          locale: {
            format: dateFormat,
            cancelLabel: "Clear",
          },
        }).on("hide.daterangepicker", function (e) {
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
        inputElem.attr('type', 'text').addClass('hidden');
        break;
      case "list":
        let options = params.options || [];
        let selectizeCfg = {
          create: true,
          options,
          maxItems: 1
        };
        if (params.searchURL) {
          selectizeCfg.load = function (text, callback) {
            const searchURL = params.searchURL.replace("{encodedQuery}", encodeURIComponent(text));
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