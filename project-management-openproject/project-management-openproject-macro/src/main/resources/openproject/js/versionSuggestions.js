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
 * The versions suggested for a filter of the macro editor are the ones available in the projects that the filters keep
 * (the values of the "is" condition of the Project filter), if any: the suggest REST resource of the versions takes
 * these projects (ids or identifiers) as its projects parameter.
 */
define('openproject-version-suggestions', [], function () {
  'use strict';

  /**
   * @param searchURL the URL from which the values of a filter are suggested
   * @return whether the filter suggests versions
   */
  const isVersionSearch = function (searchURL) {
    return typeof searchURL === 'string' && searchURL.includes('/suggest/versions');
  };

  /**
   * @param searchURL the URL from which the versions are suggested, holding a query string
   * @param projectFilter the filter on the projects, as held by the filter builder ({constraints: [{operator,
   *     value}]}), if any
   * @return the URL suggesting the versions of the projects that the filter keeps
   */
  const getSearchURL = function (searchURL, projectFilter) {
    const projects = ((projectFilter && projectFilter.constraints) || [])
      .filter(constraint => constraint.operator === '=' && constraint.value)
      .map(constraint => '&projects=' + encodeURIComponent(constraint.value));
    return searchURL + projects.join('');
  };

  return {
    isVersionSearch,
    getSearchURL
  };
});
