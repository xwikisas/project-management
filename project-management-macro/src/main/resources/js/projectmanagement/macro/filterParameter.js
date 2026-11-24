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
 * Binds the filter builders of the macro editor and of the preset page to what they edit, by the role of their
 * container (its data-builder-role attribute):
 * - "query": the matching filters of the widget of the source parameter (workItemSourceParameter.js), edited as a
 *   query, e.g. project = 12 AND status = open. The widget gives the query to show with the pm:setQuery(text) event, or the
 *   deprecated filters parameter with pm:importFilters(json), triggered on the container, which keeps the last one as
 *   its pmPendingInput data until the builder is ready. The builder triggers pm:queryChanged(query) on the container
 *   once the user edits the filters, and pm:queryError(message) when it can't show the query. It never writes a macro
 *   parameter itself. The query is parsed by the REST API, so that a single parser reads the queries. The conditions
 *   of the deprecated filters parameter that the builder can't show (e.g. an operator that the property doesn't have)
 *   are dropped, and reported, as text, with pm:unsupportedFilters(conditions), the whole list each time. The
 *   handlers of pm:collectQuery(holder) get the query of the filters shown by the builder, in holder.query.
 * - "series" (the series of a chart) and no role (e.g. the preset page): the filters are written as Live Data JSON in
 *   the .proj-manag-filter field, one configuration per builder when there are several. The series read from the
 *   macro are kept as they are until the user edits them, the conditions that their builders can't show being dropped
 *   then, and reported with pm:unsupportedFilters(conditions) on the container of the series.
 */
setTimeout(function () {
  let depsElem = document.querySelector('.proj-manag-filter[data-deps], .pm-work-item-source[data-deps]');
  let projManagFilterDeps = JSON.parse((depsElem && depsElem.getAttribute('data-deps')) || '{}') || {};
  let mainPage = new XWiki.Document(new XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT));
  projManagFilterDeps.filterBuilder = mainPage.getURL('jsx',
    'resource=js/projectmanagement/filterBuilder.js&minify=true');
  projManagFilterDeps.projectManagementQueryWriter = mainPage.getURL('jsx',
    'resource=js/projectmanagement/macro/queryWriter.js&minify=true');
  projManagFilterDeps.projectManagementQueryEdits = mainPage.getURL('jsx',
    'resource=js/projectmanagement/macro/queryEdits.js&minify=true');
  require.config({
    paths: projManagFilterDeps
  });
  require(['filterBuilder'], function () {
    require(['jquery', 'project-management-filter-builder', 'projectManagementQueryWriter',
        'projectManagementQueryEdits'], function ($, builder, queryWriter, queryEdits) {
      const QUERY_CONTAINER = '[data-builder-role="query"]';
      const EVENT_NAMESPACE = '.pmFilterParameter';

      let getQueryContainer = function (element) {
        return $(element).closest(QUERY_CONTAINER);
      };
      let isQueryBuilder = function (bld) {
        return getQueryContainer(bld.element).length > 0;
      };
      let getQueryBuilder = function (container) {
        let element = $(container).find('.proj-manag-constraint-builder').first()[0];
        return element && builder.instances.get(element);
      };
      // The builders whose filters are written as JSON: the series of a chart, the filters of a preset.
      let getJsonBuilders = function () {
        return Array.from(builder.instances.values()).filter(bld => !isQueryBuilder(bld));
      };
      // The field holding the JSON filters, not the deprecated filters parameter that the query builder imports.
      let getJsonField = function () {
        return $('.proj-manag-filter').not('[data-legacy-filters]');
      };

      // -------------------------------------------------------------------------------------------------------------
      // The builders writing JSON.
      // -------------------------------------------------------------------------------------------------------------

      let livedataCfgs = new Map();
      const SERIES_CONTAINER = '[data-builder-role="series"]';
      let isSeriesBuilder = function (bld) {
        return bld.element.closest(SERIES_CONTAINER).length > 0;
      };
      // The series of a chart read from the macro are kept as they are until the user edits them: their builders don't
      // show the conditions that they can't show (e.g. Description empty, written by an older version), which the
      // client ignores. Once edited, the series are written from the builders, without these conditions, which are
      // then reported, as text, with pm:unsupportedFilters(conditions) on the container of the series.
      let seriesKept = false;
      let seriesUnsupported = [];
      let writeJsonField = function (seriesEdited) {
        if (seriesKept && !seriesEdited) {
          return;
        }
        if (seriesKept) {
          seriesKept = false;
          $(SERIES_CONTAINER).first().trigger('pm:unsupportedFilters', [seriesUnsupported.slice()]);
        }
        getJsonField().val(serializeFilters());
      };
      // The filter builders of a chart are its series, titled from the translated template of their container.
      let setSeriesTitle = function (bld, number) {
        let titleTemplate = bld.element.closest('.project-management-filters').attr('data-series-title');
        if (titleTemplate) {
          bld.setTitle(titleTemplate.replace('__N__', number));
        }
      };
      let setSeriesTitles = function () {
        getJsonBuilders().forEach((bld, index) => setSeriesTitle(bld, index + 1));
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
      let initJsonBuilders = function () {
        let jsonBuilders = getJsonBuilders();
        let initialFilter = getJsonField().val();
        if (!initialFilter || !jsonBuilders.length) {
          return;
        }
        let filterCfgs = JSON.parse(initialFilter);
        filterCfgs = filterCfgs instanceof Array ? filterCfgs : [filterCfgs];
        let firstBuilder = jsonBuilders[0];
        let series = isSeriesBuilder(firstBuilder);
        seriesKept = series;
        seriesUnsupported = [];
        for (let i = 1; i < filterCfgs.length; i++) {
          let clone = firstBuilder.element.clone();
          firstBuilder.element.parent().append(clone);
          builder.inializeBuilder(clone);
        }
        getJsonBuilders().forEach((bld, index) => {
          setSeriesTitle(bld, index + 1);
          let filterCfg = filterCfgs[index] || {};
          // The builder groups the values of a filter into rows.
          ((filterCfg.query && filterCfg.query.filters) || []).forEach(filter => bld.addFilter(series
            ? Object.assign({}, filter, {dropUnsupported: true}) : filter));
          if (series) {
            setReadFilters(bld);
          }
        });
      };

      // -------------------------------------------------------------------------------------------------------------
      // The builder of the matching filters, edited as a query.
      // -------------------------------------------------------------------------------------------------------------

      // The builder updates its filters by itself too, e.g. when the properties of the chosen instance are loaded,
      // possibly before the query of the macro is read: the query is only reported as changed once the user edits the
      // filters, that is once the user acts on the builder, or once its filters differ from the ones read from the
      // macro (the events of some tools, e.g. a script or an assistive technology, being reported as untrusted). The
      // edits are tracked per builder: editing a series of a chart doesn't edit its matching filters.
      let edits = queryEdits.createTracker();
      let setReadFilters = function (bld) {
        edits.setReadFilters(bld.element[0], Object.values(bld.getConstraints() || {}));
      };
      // Listened to in the capture phase, before the handlers of the builder: removing a filter detaches its row,
      // which would then no longer be found inside the builder.
      ['click', 'keyup', 'change'].forEach(type => document.addEventListener(type, function (e) {
        if (e.isTrusted && e.target instanceof Element) {
          edits.recordAction(e.target, document.activeElement);
        }
      }, true));
      // The values of the query read as names, by property: a name made of digits is written back between quotes,
      // so that it isn't read as an id.
      let names = new Map();
      // Whether a warning is shown above the builder because the query can't be shown as filters.
      let contentUnread = false;
      let clearQueryWarning = function () {
        contentUnread = false;
        $('.proj-manag-query-error').remove();
        builder.instances.forEach(bld => isQueryBuilder(bld) && bld.setUnreadable(false));
      };
      // The number of the last reading of the query: the result of a previous reading, e.g. of a query replaced
      // since, is ignored.
      let queryRequest = 0;
      // The values of the properties whose filter has the valueNames parameter (e.g. the projects of OpenProject) are
      // written with their names (e.g. the identifier of a project, the name of a status), which the client resolves
      // into ids, rather than their ids: the query is then readable. The keywords (e.g. me) and the values whose name
      // isn't known are written as they are.
      let withValueNames = function (bld, filters, queryNames) {
        let getName = function (propertyId, value) {
          let property = (bld.cfg || []).find(item => item.id == propertyId);
          return property && property.filter && property.filter.valueNames
            ? bld.getValueName(propertyId, value) : undefined;
        };
        return queryWriter.withValueNames(filters, getName, queryNames);
      };
      let getQuery = function (bld, filters) {
        // The names written for this query are only quoted in it: a later id equal to one of them stays an id.
        let queryNames = new Map([...names].map(([property, values]) => [property, new Set(values)]));
        return queryWriter.toQuery(withValueNames(bld, filters, queryNames), queryNames);
      };
      let writeQuery = function (bld, filters) {
        // The query is now the one of the filters, which the builder shows: a reading of the previous query, if any,
        // is ignored.
        queryRequest++;
        bld.setLoading(false);
        clearQueryWarning();
        getQueryContainer(bld.element).trigger('pm:queryChanged', [getQuery(bld, filters)]);
      };
      /*
       * Tells why the query can't be shown: the explanation of the server (a sentence), else the status text of the
       * failed request, if it has one (not the generic "error" of jQuery, e.g. for an HTTP/2 response, which has no
       * status text).
       *
       * @param container the container of the builder, holding the templates of the warning
       * @param response the failed request
       * @return the text of the warning
       */
      let getQueryWarning = function (container, response) {
        let reason = response.responseJSON && response.responseJSON.message;
        let template = container.attr('data-query-error');
        if (!reason) {
          reason = response.status >= 400 && response.statusText !== 'error' ? (response.statusText || '').trim() : '';
          template = container.attr(reason ? 'data-query-failed' : 'data-query-failed-no-reason');
        }
        return (template || '{0}').replace('{0}', () => reason);
      };
      let showQueryWarning = function (bld, message) {
        clearQueryWarning();
        bld.element.before($('<div class="box warningmessage proj-manag-query-error"></div>').text(message));
        contentUnread = true;
        // The builder shows no filter, while the query is kept: its summary would contradict the warning.
        bld.setUnreadable(true);
        edits.clearEditedProperty(bld.element[0]);
        getQueryContainer(bld.element).trigger('pm:queryError', [message]);
      };
      let readQuery = function (bld, query) {
        let request = ++queryRequest;
        // A previous reading, if any, is ignored.
        bld.setLoading(false);
        if (!query) {
          setReadFilters(bld);
          return;
        }
        // Marks the builder while the query is read.
        bld.setLoading(true);
        $.get(`${XWiki.contextPath}/rest/wikis/${encodeURIComponent(XWiki.currentWiki)}`
          + '/projectmanagement/query/filters', { query: query }).always(function () {
          if (request === queryRequest) {
            bld.setLoading(false);
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
          showQueryWarning(bld, getQueryWarning(getQueryContainer(bld.element), response));
          // Editing the filters replaces the query that can't be read.
          setReadFilters(bld);
        });
      };
      let resetQueryBuilder = function (bld) {
        clearQueryWarning();
        edits.reset(bld.element[0]);
        names.clear();
        bld.clear();
      };
      // Shows the filters of the given query.
      let setQuery = function (bld, query) {
        resetQueryBuilder(bld);
        readQuery(bld, String(query || '').trim());
      };
      // The conditions of the deprecated filters parameter that the builder dropped, as text, e.g. "Description empty".
      let unsupportedFilters = [];
      let reportUnsupportedFilters = function (bld, conditions) {
        unsupportedFilters.push(...conditions);
        getQueryContainer(bld.element).trigger('pm:unsupportedFilters', [unsupportedFilters.slice()]);
      };
      // Shows the filters of the deprecated filters parameter (a Live Data configuration as JSON), written as a query
      // once they're edited. The conditions that the builder can't show are dropped: the client ignores them too.
      let importFilters = function (bld, json) {
        resetQueryBuilder(bld);
        // A reading of a query, if any, is ignored.
        queryRequest++;
        bld.setLoading(false);
        unsupportedFilters = [];
        reportUnsupportedFilters(bld, []);
        try {
          let cfg = JSON.parse(json);
          cfg = cfg instanceof Array ? cfg[0] : cfg;
          ((cfg && cfg.query && cfg.query.filters) || []).forEach(filter => bld.addFilter(Object.assign({}, filter,
            {dropUnsupported: true})));
        } catch (e) {
          console.error('Failed to read the filters parameter.', e);
          // The filters can't show any of them.
          reportUnsupportedFilters(bld, [json]);
        }
        setReadFilters(bld);
      };
      let applyInput = function (bld, input) {
        if (input && input.type === 'filters') {
          importFilters(bld, input.value);
        } else {
          setQuery(bld, input && input.value);
        }
      };
      let initQueryBuilders = function () {
        $(QUERY_CONTAINER).each(function () {
          let bld = getQueryBuilder(this);
          if (bld) {
            applyInput(bld, $(this).data('pmPendingInput'));
          }
        });
      };
      $(document).on('pm:setQuery' + EVENT_NAMESPACE, QUERY_CONTAINER, function (e, query) {
        if (e.target === this) {
          let input = {type: 'query', value: query};
          $(this).data('pmPendingInput', input);
          let bld = getQueryBuilder(this);
          bld && applyInput(bld, input);
        }
      });
      $(document).on('pm:importFilters' + EVENT_NAMESPACE, QUERY_CONTAINER, function (e, json) {
        if (e.target === this) {
          let input = {type: 'filters', value: json};
          $(this).data('pmPendingInput', input);
          let bld = getQueryBuilder(this);
          bld && applyInput(bld, input);
        }
      });
      $(document).on('pm:collectQuery' + EVENT_NAMESPACE, QUERY_CONTAINER, function (e, holder) {
        let bld = getQueryBuilder(this);
        if (e.target === this && bld && holder) {
          holder.query = getQuery(bld, Object.values(bld.getConstraints() || {}));
        }
      });
      let updateQuery = function (bld, filters) {
        let element = bld.element[0];
        if (contentUnread) {
          let editedProperty = edits.getEditedProperty(element);
          // The query that the filters can't show is only replaced once the filters select something: a filter with a
          // value, or with an operator taking none that the user chose (e.g. "is this week"), not the default operator
          // of a new filter (e.g. "is today" for a date). Filters without value, or removing a filter, don't replace
          // it.
          let selects = filters.some(filter => filter.constraints.some(constraint =>
            queryWriter.takesValue(constraint.operator) ? constraint.value !== ''
              : String(filter.property) === editedProperty));
          if (edits.isChanged(element, filters) && selects) {
            writeQuery(bld, filters);
          }
          return;
        }
        if (edits.isEdited(element, filters)) {
          writeQuery(bld, filters);
        }
      };

      // -------------------------------------------------------------------------------------------------------------
      // All the builders.
      // -------------------------------------------------------------------------------------------------------------

      let updateFilterInput = function (e, constraints) {
        let bld = builder.instances.get(e.target);
        let filters = Object.values(constraints || {});
        if (bld && isQueryBuilder(bld)) {
          updateQuery(bld, filters);
        } else {
          livedataCfgs.set(e.target, { query: { filters: filters } });
          writeJsonField(!!bld && isSeriesBuilder(bld) && edits.isEdited(e.target, filters));
        }
      };
      let bindBuilder = function (builderElem) {
        builderElem.off('constraintsUpdated' + EVENT_NAMESPACE).on('constraintsUpdated' + EVENT_NAMESPACE,
          updateFilterInput);
        builderElem.off('builderRemoved' + EVENT_NAMESPACE).on('builderRemoved' + EVENT_NAMESPACE, function (e) {
          livedataCfgs.delete(e.target);
          writeJsonField(true);
        });
        builderElem.off('unsupportedConstraints' + EVENT_NAMESPACE).on('unsupportedConstraints' + EVENT_NAMESPACE,
          function (e, propertyName, constraints) {
            let bld = builder.instances.get(e.target);
            let conditions = constraints.map(constraint => [propertyName, constraint.operator, constraint.value]
              .filter(part => part !== undefined && part !== null && part !== '').join(' '));
            if (bld && isQueryBuilder(bld)) {
              reportUnsupportedFilters(bld, conditions);
            } else if (bld && isSeriesBuilder(bld)) {
              seriesUnsupported.push(...conditions);
            }
          });
      };
      $(document).on('filterBuilderInitialized', function (e, builderElem) {
        bindBuilder(builderElem);
      });
      builder.instances.forEach(bld => bindBuilder(bld.element));
      let initBuilders = function () {
        initQueryBuilders();
        initJsonBuilders();
      };
      let init = function () {
        if ($('.proj-manag-constraint-builder').length <= 0) {
          return;
        }
        $('.proj-manag-constraint-builder').each(function () {
          builder.inializeBuilder($(this));
        });
        builder.instances.forEach(bld => {
          bld.clean();
          bld.init();
        });
        setSeriesTitles();
        initBuilders();
      };
      $(document).on('click', '.project-management-new-dataset', function (e) {
        e.preventDefault();
        let bld = builder.newBuilder($(this).parent().find('.project-management-filters').first());
        // A new series has no filters: the ones added to it edit the series.
        bld && setReadFilters(bld);
        setSeriesTitles();
      });
      // This script is loaded by the first macro editor (or page) displaying a filter builder, after it's displayed.
      initBuilders();
      $(document).on('hide.bs.modal', '.modal', function () {
        if ($(this).find('.proj-manag-constraint-builder').length <= 0) {
          return;
        }
        livedataCfgs.clear();
        seriesKept = false;
        seriesUnsupported = [];
        edits.clear();
        names.clear();
        unsupportedFilters = [];
        queryRequest++;
        clearQueryWarning();
        builder.instances.forEach(bld => bld.clean());
        builder.instances.clear();
        $('.proj-manag-constraint-builder').remove();
      });
      $(document).on('shown.bs.modal', '.modal', function () {
        let modal = $(this);
        setTimeout(function () {
          const content = modal.find('.macro-editor');
          if (!content || content.length <= 0) {
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
