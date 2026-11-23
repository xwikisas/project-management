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
 * The OpenProject connection used by the macro edited in the macro editor, read from its instance parameter.
 */
define(['jquery'], function ($) {
  'use strict';

  // The value of the instance option making the macro use the connection selected on the OpenProject dashboard.
  const DASHBOARD_CONNECTION = 'use_selected_dashboard_connection';

  const getInstanceInput = function () {
    return $('.macro-editor-modal :input[name="instance"]');
  };

  /**
   * @return {string} the name of the connection used by the edited macro: the chosen one, otherwise the default one
   *     (held by the instance picker), or the one selected on the OpenProject dashboard; empty when there's none
   */
  const getInstance = function () {
    const input = getInstanceInput();
    let instance = input.val() || input.attr('data-default-connection') || '';
    if (instance === DASHBOARD_CONNECTION) {
      instance = $('#openproject-dashboard-connection-select').val() || '';
    }
    return instance;
  };

  /**
   * @param {jQuery} input the instance picker
   * @return {{connectionCount: number, defaultConnection: string}} the connections it offers
   */
  const getConnections = function (input) {
    return {
      connectionCount: parseInt(input.attr('data-connection-count'), 10) || 0,
      defaultConnection: input.attr('data-default-connection') || ''
    };
  };

  /**
   * @param {{connectionCount: number, defaultConnection: string}} connections the connections offered by the
   *     instance picker
   * @return {boolean} whether the user has to choose the connection, i.e. whether there's no default connection and
   *     not exactly one connection
   */
  const isConnectionChoiceNeeded = function (connections) {
    return !connections.defaultConnection && connections.connectionCount !== 1;
  };

  return {getInstanceInput, getInstance, getConnections, isConnectionChoiceNeeded};
});
