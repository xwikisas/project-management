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
 * Reads and writes the list of work items selected by the "list" source of the work item macros, e.g.
 * 236, 237, PROJ-42, and builds the REST URLs used by the work item picker (workItemPicker.js).
 *
 * This module has no dependency, so that it can be run outside of a browser (tested by src/test/node, with Vitest).
 */
define([], function () {
  // The same separators as the ones of the "list" source (ListWorkItemSource).
  const SEPARATORS = /[\s,]+/;
  // The id (e.g. 123) or the key (e.g. PROJ-123) of a work item, as OpenProjectClient#isWorkPackageIdOrKey checks it.
  const ID_OR_KEY = /^(\d+|[A-Za-z][A-Za-z0-9_]*-\d+)$/;

  /**
   * @param ids the ids, possibly repeated
   * @return the ids without the empty ones and the repeated ones, in the order of their first occurrence
   */
  let dedupe = function (ids) {
    let seen = new Set();
    return (ids || []).map(id => String(id).trim()).filter(id => {
      if (id === '' || seen.has(id)) {
        return false;
      }
      seen.add(id);
      return true;
    });
  };

  /**
   * @param text the content of a macro using the "list" source, or a pasted list of ids
   * @return the listed ids, in the written order, without repetition
   */
  let parseList = function (text) {
    return dedupe(String(text || '').split(SEPARATORS));
  };

  /**
   * @param ids the ids to write
   * @return the content of a macro using the "list" source, e.g. 236, 237, PROJ-42
   */
  let formatList = function (ids) {
    return dedupe(ids).join(', ');
  };

  /**
   * @param value a value of the list
   * @return true if the value has the shape of a work item id (e.g. 123) or key (e.g. PROJ-123)
   */
  let isIdOrKey = function (value) {
    return typeof value === 'string' && ID_OR_KEY.test(value);
  };

  /**
   * @param ids the ids selected so far
   * @param added the ids to add after them
   * @return the selected ids followed by the added ones that they don't hold yet
   */
  let extend = function (ids, added) {
    return dedupe([...(ids || []), ...(added || [])]);
  };

  /**
   * @param item a work item returned by the REST API (WorkItemsResource)
   * @param listedId the id with which the work item is listed, e.g. its key, instead of its id
   * @return the option of the picker showing the work item, e.g. {value: '236', label: 'DEMO-236 Fix the login',
   *     hint: 'Demo project · New', key: 'DEMO-236'}, labelled by its key when it has one (as the tool displays it),
   *     or undefined if the work item has no id
   */
  let toOption = function (item, listedId) {
    let id = listedId || item?.identifier?.value;
    if (!item || id === undefined || id === null || id === '') {
      return undefined;
    }
    id = String(id);
    let hasKey = typeof item.key === 'string' && item.key !== '';
    let label = hasKey ? item.key : (/^\d+$/.test(id) ? '#' + id : id);
    let summary = item.summary?.value;
    if (summary) {
      label += ' ' + summary;
    }
    let hint = [item.project?.value, item.status].filter(value => typeof value === 'string' && value !== '')
      .join(' · ');
    let option = {value: id, label, hint};
    // The key is kept so that the work items found by their key match the searched text.
    if (hasKey) {
      option.key = item.key;
    }
    return option;
  };

  /**
   * Builds the URLs of the REST resources (WorkItemsResource) that the picker calls: the search is the one of the
   * work package search of the WYSIWYG editor, a "summary contains" filter, which also matches the work item keys.
   *
   * @param restURL the URL of the REST API, e.g. /xwiki/rest
   * @param wiki the current wiki
   * @param client the id of the project management client, e.g. openproject
   * @param params the parameters of the client added to each request, e.g. {instance: 'main'}
   * @return the functions {searchUrl(text), itemUrl(id)} expected by the picker
   */
  let restUrls = function (restURL, wiki, client, params) {
    let base = `${restURL}/wikis/${encodeURIComponent(wiki)}/projectmanagement/${encodeURIComponent(client)}/workitems`;
    let withParams = function (url, extraParams) {
      let query = Object.entries({...(params || {}), ...(extraParams || {})})
        .filter(([, value]) => value !== undefined && value !== null && value !== '')
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        .join('&');
      return query ? `${url}?${query}` : url;
    };
    return {
      searchUrl: function (text) {
        let filter = {query: {filters: [{property: 'summary', constraints: [{operator: 'contains', value: text}]}]}};
        return withParams(base, {filter: JSON.stringify(filter)});
      },
      itemUrl: function (id) {
        return withParams(`${base}/${encodeURIComponent(id)}`);
      }
    };
  };

  return {parseList, formatList, isIdOrKey, extend, toOption, restUrls};
});
