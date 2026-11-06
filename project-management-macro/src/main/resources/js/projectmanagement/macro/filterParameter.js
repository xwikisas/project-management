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
  let depsElems = document.getElementsByClassName('proj-manag-filter');
  let deps = (depsElems && depsElems[0] && depsElems[0].getAttribute('data-deps')) || "{}";
  let projManagFilterDeps = JSON.parse(deps) || {};
  projManagFilterDeps.filterBuilder = new XWiki.Document(
    new XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT)
  ).getURL('jsx', 'resource=js/projectmanagement/filterBuilder.js&minify=true')
  require.config({
    paths: projManagFilterDeps
  });
  require(['filterBuilder'], function () {
    require(['jquery', 'project-management-filter-builder'], function ($, builder) {
      let livedataCfgs = new Map();
      // The filter builders of a chart are its series, titled from the translated template of their container.
      let setSeriesTitle = function (builder, number) {
        let titleTemplate = $('.project-management-filters').attr('data-series-title');
        if (titleTemplate) {
          builder.setTitle(titleTemplate.replace('__N__', number));
        }
      };
      // Several filter builders are the series of a chart: their configurations are kept even without constraints,
      // since each one is a series. A single builder without constraints means no filter, i.e. an empty value, so that
      // the macro editor doesn't add the parameter.
      let serializeFilters = function () {
        let cfgs = Array.from(livedataCfgs.values());
        if (cfgs.length > 1) {
          return JSON.stringify(cfgs);
        }
        return cfgs.length === 1 && cfgs[0].query.filters.length > 0 ? JSON.stringify(cfgs[0]) : '';
      };
      let updateFilterInput = function (e, constraints) {
        let livedataCfg = { query: { filters: [] } };
        for (key in constraints) {
          livedataCfg.query.filters.push(constraints[key]);
        }
        livedataCfgs.set(e.target, livedataCfg);
        $('.proj-manag-filter').val(serializeFilters());
      };
      $(document).on('filterBuilderInitialized', function (e, builderElem) {
        builderElem.on('constraintsUpdated', updateFilterInput);
        builderElem.on('builderRemoved', function (e) {
          livedataCfgs.delete(e.target);
          $('.proj-manag-filter').val(serializeFilters());
        });
      });
      // builder.instances.values().next().value.element.on('constraintsUpdated', updateFilterInput);
      builder.instances.forEach(builder => builder.element.on('constraintsUpdated', updateFilterInput));
      let initBuilder = function () {
        let initialFilter = $('.proj-manag-filter').val();
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
        builder.instances.forEach(bld => {
          let filterCfg = filterCfgs[i];
          i++;
          setSeriesTitle(bld, i);
          let filters = (filterCfg.query && filterCfg.query.filters) || [];
          // The builder groups the values of a filter into rows.
          filters.forEach(filter => bld.addFilter(filter));
        });

      };
      let init = function () {
        if ($('.proj-manag-filter').length <= 0) {
          return;
        }
        $('.proj-manag-constraint-builder').each(function () {
          builder.inializeBuilder($(this))
        });
        let i = 1;
        builder.instances.forEach(builder => {
          builder.clean();
          builder.init();
          setSeriesTitle(builder, i);
          i++;
        });
        initBuilder();
      };
      $(document).on('click', '.project-management-new-dataset', function () {
        builder.newBuilder();
      });
      initBuilder();
      $(document).on('hide.bs.modal', '.modal', function () {
        if ($('.proj-manag-filter').length <= 0) {
          return;
        }
        livedataCfgs.clear();
        builder.instances.forEach(builder => builder.clean());
        builder.instances.clear();
        $('.proj-manag-constraint-builder').each(function () {
          $(this).remove();
        });
      });
      $(document).on('click', '.project-management-new-dataset', function (e) {
        e.preventDefault();
        setTimeout(() => {
          let i = 1;
          builder.instances.forEach(bld => {
            setSeriesTitle(bld, i++);
          })
        }, 0);
      });
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
  });
});

