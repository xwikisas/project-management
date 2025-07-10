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
setTimeout(function () {
  let projManagFilterDeps = JSON.parse(document.getElementById('proj-manag-filter').getAttribute('data-deps')) || {};
  projManagFilterDeps.filterBuilder = new XWiki.Document(
    new XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT)
  ).getURL('jsx', 'resource=js/projectmanagement/filterBuilder.js&minify=false')
  require.config({
    paths: projManagFilterDeps
  });
  require(['filterBuilder'], function () {
    require(['jquery', 'project-management-filter-builder'], function ($, builder) {
      builder.element.on('constraintsUpdated', function (e, constraints) {
        let livedataCfg = { query: { filters: [] } };
        for (key in constraints) {
          livedataCfg.query.filters.push(constraints[key]);
        }
        $('#proj-manag-filter').val(JSON.stringify(livedataCfg));
      });
      let initBuilder = function () {
        let initialFilter = $('#proj-manag-filter').val();
        if (!initialFilter) {
          return;
        }
        const filterCfg = JSON.parse(initialFilter);
        let filters = (filterCfg.query && filterCfg.query.filters) || [];
        filters.forEach((filter) => {
          filter.constraints.forEach((constraint) => {
            let filterCopy = { ...filter };
            filterCopy.constraints = [constraint];
            builder.addFilter(filterCopy);
          });
        });
      };
      initBuilder();
      $(document).on('hide.bs.modal', '.modal', function () {
        builder.clean();
      });
      $(document).on('shown.bs.modal', '.modal', function () {
        builder.clean();
        builder.init();
        initBuilder();
      });
    });
  });
});
