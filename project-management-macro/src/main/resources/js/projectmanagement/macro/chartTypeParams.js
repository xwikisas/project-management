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
define('projectmanagement.chart.typeParams', {
  prefix: 'projectmanagement.macro.chart.typeParams.',
  keys: ['failed']
});

require(['jquery', 'xwiki-l10n!projectmanagement.chart.typeParams', 'xwiki-selectize'], function ($, l10n) {
  // The text of a notification is HTML on some XWiki versions: display the translations as text.
  let createNotification = function (text, type) {
    let notification = new XWiki.widgets.Notification('', type, {inactive: true});
    // The notification element wraps the styled one, which holds the text.
    notification.element.down('.xnotification').textContent = text;
    return notification;
  };
  const SEPARATOR = ",";
  // The fields of the options get new ids each time they're displayed: the pickers of the previous ones can still be in
  // the page (their lists of values are appended to the body).
  let fieldCount = 0;
  let updateInput = function () {
    let param = {};
    $('.proj-manag-chart-type-param input').each(function () {
      let elem = $(this);
      let key = elem.attr('name');
      if (!key) {
        return;
      }
      let isArray = 'array' === elem.data('type');
      let val = !isArray ? elem.val() : elem.val().split(SEPARATOR);
      param[key] = val;
    });
    $('#proj-manag-chart-type-params').val(JSON.stringify(param));
  };
  let init = function () {
    let paramsInput = $('#proj-manag-chart-type-params');
    if (paramsInput.length <= 0) {
      return;
    }
    let params = paramsInput.val() ? JSON.parse(paramsInput.val()) : {};

    // The macros drawing a single chart type don't have a type parameter (e.g. the completion rate one draws lines):
    // the chart types have the same options anyway.
    let chartType = $('.macro-editor-modal .macro-parameter :input[name=type]').val() || 'line';
    let getChartTypeParamsURL = XWiki.contextPath + '/rest/wikis/' + XWiki.currentWiki + '/projectmanagement/chart/displayers/' + chartType;
    // Displaying the options of the chart type is a refresh of the editor, which only tells when it fails.
    $.getJSON(getChartTypeParamsURL)
      .done(function (paramTemplates) {
        var htmlTemplate = document.getElementById("proj-manag-chart-type-param-template").content;

        paramTemplates.forEach(template => {
          var clone = $(htmlTemplate).clone();
          // The name of the option labels its field. The picker moves this label to its own text input, the field
          // keeping its name through aria-labelledby (Tom Select, on XWiki 18+, hides it but the screen readers see it).
          let fieldId = 'proj-manag-chart-type-param-' + (++fieldCount);
          let labelId = fieldId + '-label';
          clone.find('.proj-manag-chart-type-param-name').text(template.label || template.id)
            .attr({id: labelId, for: fieldId});
          var val = template.multiple && params[template.id] instanceof Array ?
            params[template.id].join(SEPARATOR) : params[template.id];
          if (template.description) {
            clone.find('.proj-manag-chart-type-param-description').text(template.description);
          }
          clone.find('input')
            .data('type', template.multiple ? 'array' : 'string')
            .attr({id: fieldId, name: template.id, 'aria-labelledby': labelId})
            .val(val)
            .on('change', updateInput);

          $('.proj-manag-chart-type-params').append(clone);

          clone = $('.proj-manag-chart-type-params').find(".proj-manag-chart-type-param").last();


          // Display the translated labels of the values, the parameter keeping the values themselves.
          let valueLabels = template.valueLabels || {};
          let selectizeOptions = template.values ? template.values.map(val => {
            return { value: val, label: valueLabels[val] || val };
          }) : [];
          clone.find('input').xwikiSelectize({
            delimiter: SEPARATOR,
            create: template.values ? false : true,
            maxItems: template.multiple ? -1 : 1,
            options: selectizeOptions
          });
        });
      })
      .fail(function () {
        createNotification(l10n.get('failed'), 'error').show();
        console.error("Failed to retrieve the chart type parameter.");
        paramsInput.toggleClass('hidden');
      });
  };
  $(document).on('change', '.macro-editor-modal .macro-parameter select[name=type]', function () {
    $('#proj-manag-chart-type-params').val('');
    $('.proj-manag-chart-type-params').empty();
    init();
  })
  init();
  $(document).on('shown.bs.modal', '.modal', function () {
    let modal = $(this);
    setTimeout(function () {
      const content = modal.find('.macro-editor');
      if (!content || content.length <= 0) {
        console.log('Modal content not found');
        return;
      }
      if (!content[0].classList.contains('loading')) {
        init();
        return;
      }
      const observer = new MutationObserver(() => {
        if (!content[0].classList.contains('loading')) {
          observer.disconnect();
          init();
        }
      });

      observer.observe(content[0], {
        attributes: true,
        attributeFilter: ['class'],
        subtree: true
      });
    });
  });
});