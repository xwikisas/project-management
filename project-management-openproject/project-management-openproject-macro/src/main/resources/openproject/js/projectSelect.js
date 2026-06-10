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
  function initProjectSelect(input) {
    if (input[0].selectize) {
      return;
    }
    let instance = $('.macro-editor-modal :input[name="instance"]').val();
    if (!instance) {
      return;
    }
    let projectsREST = `${XWiki.contextPath}/rest/wikis/${XWiki
    .currentWiki}/openproject/instance/${instance}/suggest/projects`;

    let selectizeCfg = {
      create: false,
      maxItems: 1,
    };

    selectizeCfg.load = function (text, callback) {
      $.getJSON(projectsREST, {search: text})
        .then(function (results) {
        input[0].selectize.clearOptions();
          callback(results);
        })
        .catch(function () {
          callback([]);
        });
    };

    input.xwikiSelectize(selectizeCfg);
  }

  $(document).on('focus', '.macro-editor-modal .openproject-project-select', function () {
    initProjectSelect($(this));
  });
});