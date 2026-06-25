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
require(['jquery'], function ($) {
  // Returns the warning shown when the OpenProject OAuth connection is not established for the instance.
  function getTokenWarning(input) {
    return input.closest('.openproject-project-select-container').find('.openproject-project-incorrect-token');
  }

  function showTokenWarning(input) {
    getTokenWarning(input).removeClass('hidden');
  }

  function hideTokenWarning(input) {
    getTokenWarning(input).addClass('hidden');
  }

  // Reads the currently selected OpenProject instance from the macro parameters.
  function getSelectedInstance() {
    let instance = $('.macro-editor-modal :input[name="instance"]').val();
    if (instance === 'use_selected_dashboard_connection') {
      instance = $('#openproject-dashboard-connection-select').val();
    }
    return instance;
  }

  // On the dashboard, the project picker offers a "use the dashboard-selected project" option, mirroring the
  // dashboard connection option.
  function getDashboardProjectOption(input) {
    let value = input.attr('data-dashboard-project-value');
    if (!value) {
      return null;
    }
    return {value: value, label: input.attr('data-dashboard-project-label')};
  }

  function initProjectSelect(input) {
    if (input[0].selectize) {
      return;
    }

    let dashboardOption = getDashboardProjectOption(input);

    let selectizeCfg = {
      create: false,
      maxItems: 1,
      preload: 'focus',
    };

    selectizeCfg.load = function (text, callback) {
      let dashboardResults = dashboardOption ? [dashboardOption] : [];
      let instance = getSelectedInstance();

      if (!instance) {
        callback(dashboardResults);
        return;
      }

      let projectsREST = `${XWiki.contextPath}/rest/wikis/${XWiki.currentWiki}/openproject/instance/${instance}/suggest/projects`;
      $.getJSON(projectsREST, {search: text})
        .then(function (results) {
          hideTokenWarning(input);
          callback(dashboardResults.concat(results));
        })
        .catch(function (err) {
          if (err && err.status === 404) {
            showTokenWarning(input);
          }
          callback(dashboardResults);
        });
    };

    input.xwikiSelectize(selectizeCfg);

    if (dashboardOption) {
      input[0].selectize.addOption(dashboardOption);
      if (input.val() === dashboardOption.value) {
        input[0].selectize.setValue(dashboardOption.value, true);
      }
    }
  }

  function resetForInstanceChange(input) {
    let instance = getSelectedInstance();
    if (input.data('openprojectInstance') === instance) {
      return;
    }
    input.data('openprojectInstance', instance);
    let selectize = input[0].selectize;
    if (selectize) {
      selectize.clear(true);
      selectize.clearOptions();
      selectize.loadedSearches = {};
      hideTokenWarning(input);
    }
  }

  $(document).on('focusin', '.macro-editor-modal .openproject-project-select-container', function () {
    let input = $(this).find('.openproject-project-select').first();

    if (!input.length) {
      return;
    }

    initProjectSelect(input);
    resetForInstanceChange(input);
  });
});