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
      let livedataCfgs = new Map();
      let updateFilterInput = function (e, constraints) {
        debugger;
        let livedataCfg = { query: { filters: [] } };
        for (key in constraints) {
          livedataCfg.query.filters.push(constraints[key]);
        }
        livedataCfgs.set(e.target, livedataCfg);
        $('#proj-manag-filter').val(livedataCfgs.size > 1 ? JSON.stringify(Array.from(livedataCfgs.values())) : JSON.stringify(livedataCfg));
      };
      // builder.instances.values().next().value.element.on('constraintsUpdated', updateFilterInput);
      builder.instances.values().forEach(builder => builder.element.on('constraintsUpdated', updateFilterInput));
      let initBuilder = function () {
        let initialFilter = $('#proj-manag-filter').val();
        if (!initialFilter) {
          return;
        }
        let filterCfgs = JSON.parse(initialFilter);

        filterCfgs = filterCfgs instanceof Array ? filterCfgs : [filterCfgs];

        let currentBuilder = builder.instances.values().next().value;

        for (let i = 1; i < filterCfgs.length; i++) {
          let clone = currentBuilder.element.clone();
          currentBuilder.element.parent().append(clone);
          builder.inializeBuilder(clone);
        }

        let i = 0;
        builder.instances.values().forEach(bld => {
          let filterCfg = filterCfgs[i];
          i++;
          let filters = (filterCfg.query && filterCfg.query.filters) || [];
          filters.forEach((filter) => {
            filter.constraints.forEach((constraint) => {
              let filterCopy = { ...filter };
              filterCopy.constraints = [constraint];
              bld.addFilter(filterCopy);
            });
          });
        });

      };
      initBuilder();
      $(document).on('hide.bs.modal', '.modal', function () {
        if ($('#proj-manag-filter').length <= 0) {
          return;
        }
        livedataCfgs.clear();
        builder.instances.values().forEach(builder => builder.clean());
        builder.instances.clear();
        $('.proj-manag-constraint-builder').each(function () {
          $(this).remove();
        });
      });
      $(document).on('shown.bs.modal', '.modal', function () {
        if ($('#proj-manag-filter').length <= 0) {
          return;
        }
        $('.proj-manag-constraint-builder').each(function () {
          builder.inializeBuilder($(this))
        });
        builder.instances.values().forEach(builder => {
          builder.clean();
          builder.init();
        });
        initBuilder();
      });
      $(document).on('filterBuilderInitialized', function (e, builderElem) {
        builderElem.on('constraintsUpdated', updateFilterInput);
      });
    });
  });
});

