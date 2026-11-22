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
  let mainPage = new XWiki.Document(new XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT));
  projManagFilterDeps.filterBuilder = mainPage.getURL('jsx',
    'resource=js/projectmanagement/filterBuilder.js&minify=true');
  projManagFilterDeps.projectManagementQueryWriter = mainPage.getURL('jsx',
    'resource=js/projectmanagement/macro/queryWriter.js&minify=true');
  require.config({
    paths: projManagFilterDeps
  });
  require(['filterBuilder'], function () {
    require(['jquery', 'project-management-filter-builder', 'projectManagementQueryWriter'],
        function ($, builder, queryWriter) {
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
      /*
       * The filter builder of a macro having a content (the openproject and calendar macros) edits the filters as a
       * query written in the macro content, read by the default "query" source, e.g. project = 12 AND status = open.
       * The query is parsed back by the REST API, so that a single parser reads the queries. The series of a chart
       * keep their filters in the filters parameter.
       */
      // The builder updates its filters by itself too, e.g. when the properties of the chosen instance are loaded,
      // possibly before the query of the macro is read: the macro is only rewritten once the user edits the filters,
      // that is once the user acts on the builder, or once its filters differ from the ones read from the macro (the
      // events of some tools, e.g. a script or an assistive technology, being reported as untrusted).
      let edited = false;
      // The property of the filter whose operator or value the user last changed, or null when the last action of the
      // user on the builder was another one, e.g. adding or removing a filter.
      let editedProperty = null;
      // The filters read from the macro, compared ignoring their order, or null while they're being read.
      let readFilters = null;
      let describeFilters = function (filters) {
        return JSON.stringify(filters.map(filter => [String(filter.property), (filter.constraints || [])
          .map(constraint => constraint.operator + ':' + constraint.value).sort()]).sort());
      };
      let setReadFilters = function (bld) {
        readFilters = describeFilters(Object.values((bld && bld.getConstraints()) || {}));
      };
      // Listened to in the capture phase, before the handlers of the builder: removing a filter detaches its row,
      // which would then no longer be found inside the builder.
      ['click', 'keyup', 'change'].forEach(type => document.addEventListener(type, function (e) {
        if (e.isTrusted && e.target instanceof Element
            && e.target.closest('.proj-manag-constraint-builder, .daterangepicker')) {
          edited = true;
          // The date picker of a filter is displayed in the modal, after the filters: it's the one of the filter
          // whose date field has the focus.
          let field = e.target.closest('.daterangepicker') ? document.activeElement : e.target;
          let row = field && field.closest('.proj-manag-constraint');
          let onFilter = row
            && field.closest('.proj-manag-constraint-operator, .proj-manag-constraint-value-container');
          editedProperty = onFilter ? $(row).find('.proj-manag-constraint-name').val() : null;
        }
      }, true));
      let getMacroForm = function () {
        return $('.proj-manag-filter[data-query]').closest('form, .macro-editor');
      };
      let getContentField = function () {
        return getMacroForm().find('[name="$content"]');
      };
      let getSource = function () {
        return String(getMacroForm().find('[name="source"]').val() || '').trim();
      };
      let isQueryMode = function () {
        let contentField = getContentField();
        // Without source, a content starting with a curly bracket is still the advanced Live Data configuration of
        // the macro, passed to the Live Data: the builder then edits the filters parameter, as before.
        return $('.proj-manag-filter[data-query]').length > 0 && contentField.length > 0
          && !(getSource() === '' && String(contentField.val() || '').trim().startsWith('{'));
      };
      // The values of the query read as names, by property: a name made of digits is written back between quotes,
      // so that it isn't read as an id.
      let names = new Map();
      // Whether a warning is shown above the builder because the content can't be shown as filters.
      let contentUnread = false;
      let clearQueryWarning = function () {
        contentUnread = false;
        $('.proj-manag-query-error').remove();
      };
      // The number of the last reading of the query: the result of a previous reading, e.g. of a content replaced
      // since, is ignored.
      let queryRequest = 0;
      let writeQuery = function (filters) {
        // The content is now the query of the filters, which the builder shows: a reading of the previous content, if
        // any, is ignored.
        queryRequest++;
        $('.proj-manag-query-loading').removeClass('proj-manag-query-loading');
        clearQueryWarning();
        getContentField().val(queryWriter.toQuery(filters, names)).trigger('input').trigger('change');
        // The query replaces the deprecated filters parameter and the content read by another source.
        $('.proj-manag-filter[data-query]').val('');
        if (!['', 'query'].includes(getSource())) {
          getMacroForm().find('[name="source"]').val('').trigger('change');
        }
      };
      let showQueryWarning = function (bld, attribute, parameter) {
        clearQueryWarning();
        let template = $('.proj-manag-filter[data-query]').attr(attribute) || '{0}';
        bld.element.before($('<div class="box warningmessage proj-manag-query-error"></div>')
          .text(template.replace('{0}', () => parameter)));
        contentUnread = true;
        editedProperty = null;
      };
      let readQuery = function (bld) {
        let request = ++queryRequest;
        // A previous reading, if any, is ignored.
        bld.element.removeClass('proj-manag-query-loading');
        let query = String(getContentField().val() || '').trim();
        if (!query) {
          setReadFilters(bld);
          return;
        } else if (!['', 'query'].includes(getSource())) {
          // Editing the filters replaces the content read by the other source.
          showQueryWarning(bld, 'data-other-source', getSource());
          setReadFilters(bld);
          return;
        }
        // Marks the builder while the query is read.
        bld.element.addClass('proj-manag-query-loading');
        $.get(`${XWiki.contextPath}/rest/wikis/${encodeURIComponent(XWiki.currentWiki)}`
          + '/projectmanagement/query/filters', { query: query }).always(function () {
          if (request === queryRequest) {
            bld.element.removeClass('proj-manag-query-loading');
          }
        }).done(function (data) {
          if (request !== queryRequest) {
            return;
          }
          queryWriter.fromParsedFilters(data.filters, names).forEach(filter => bld.addFilter(filter));
          setReadFilters(bld);
        }).fail(function (response) {
          if (request !== queryRequest) {
            return;
          }
          showQueryWarning(bld, 'data-query-error',
            (response.responseJSON && response.responseJSON.message) || response.statusText);
          // Editing the filters replaces the query that can't be read.
          setReadFilters(bld);
        });
      };
      // The user edited the query in the content: the builder shows its filters again, without rewriting it.
      let reloadQuery = function () {
        // The warning is about the previous content.
        clearQueryWarning();
        let bld = builder.instances.values().next().value;
        if (!bld || builder.instances.size > 1 || !isQueryMode()) {
          return;
        }
        edited = false;
        readFilters = null;
        names.clear();
        bld.clear();
        readQuery(bld);
      };
      document.addEventListener('change', function (e) {
        // The source reading the content is a part of what the builder shows too, e.g. a list of work packages.
        if (e.isTrusted && e.target instanceof Element && e.target.matches('[name="$content"], [name="source"]')
            && getMacroForm().find(e.target).length > 0) {
          reloadQuery();
        }
      }, true);
      // The macro editor of XWiki 17 opens on the tab of the optional parameters without group, which holds the macro
      // content since it can't be put in a group. The filters are the main parameter of the macro: open on them,
      // unless the content is read by a source that the builder doesn't edit (e.g. a list of work packages).
      let showFiltersTab = function () {
        if (['', 'query'].includes(getSource())) {
          $('.macro-editor-modal a[role="tab"][aria-controls="macroParameterTreeNode-filters"]').first().tab('show');
        }
      };
      let updateFilterInput = function (e, constraints) {
        let livedataCfg = { query: { filters: [] } };
        for (key in constraints) {
          livedataCfg.query.filters.push(constraints[key]);
        }
        livedataCfgs.set(e.target, livedataCfg);
        if (isQueryMode()) {
          let filters = livedataCfg.query.filters;
          let changed = readFilters !== null && describeFilters(filters) !== readFilters;
          if (contentUnread) {
            // The content that the filters can't show is only replaced once the filters select something: a filter
            // with a value, or with an operator taking none that the user chose (e.g. "is this week"), not the
            // default operator of a new filter (e.g. "is today" for a date). Filters without value, or removing a
            // filter, don't replace it.
            let selects = filters.some(filter => filter.constraints.some(constraint =>
              queryWriter.takesValue(constraint.operator) ? constraint.value !== ''
                : String(filter.property) === editedProperty));
            if (changed && selects) {
              writeQuery(filters);
            }
            return;
          }
          edited = edited || changed;
          if (edited) {
            writeQuery(filters);
          }
        } else {
          $('.proj-manag-filter').val(serializeFilters());
        }
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
          if (isQueryMode()) {
            readQuery(builder.instances.values().next().value);
          }
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
        // The filters of the deprecated filters parameter, moved to a query in the content once they're edited.
        setReadFilters(builder.instances.values().next().value);

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
        showFiltersTab();
      };
      $(document).on('click', '.project-management-new-dataset', function () {
        builder.newBuilder();
      });
      // This script is loaded by the first macro editor displaying the filters, after the modal is shown.
      initBuilder();
      showFiltersTab();
      $(document).on('hide.bs.modal', '.modal', function () {
        if ($('.proj-manag-filter').length <= 0) {
          return;
        }
        livedataCfgs.clear();
        edited = false;
        readFilters = null;
        names.clear();
        editedProperty = null;
        queryRequest++;
        clearQueryWarning();
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

