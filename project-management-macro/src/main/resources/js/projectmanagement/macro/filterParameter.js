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
      /*
       * The filter builder of a macro having a content (the openproject and calendar macros) edits the filters as a
       * query written in the macro content, read by the default "query" source, e.g. project = 12 AND status = open.
       * The query is parsed back by the REST API, so that a single parser reads the queries. The series of a chart
       * keep their filters in the filters parameter.
       */
      // The builder updates its filters by itself too, e.g. when the properties of the chosen instance are loaded,
      // possibly before the query of the macro is read: the macro is only rewritten once the user edits the filters.
      let edited = false;
      $(document).on('click.projManagQuery keyup.projManagQuery change.projManagQuery',
        '.proj-manag-constraint-builder, .daterangepicker', function (e) {
          if (e.originalEvent && e.originalEvent.isTrusted) {
            edited = true;
          }
        });
      // The bounds that the "between" operator of the builder takes for the ranges of dates open on one side, which
      // the query writes with a single comparison, e.g. dueDate >= 2026-11-01 (the same as in the OpenProject client).
      const NO_LOWER_BOUND = '1970-01-01';
      const NO_UPPER_BOUND = '3000-01-01';
      const BARE_WORD = /^[^\s(),=!~<>"]+$/;
      const DATE = /^\d{4}-\d{2}-\d{2}$/;
      const KEYWORDS = ['and', 'or', 'not', 'in', 'all', 'is', 'empty', 'order'];
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
      let quote = function (text, forced) {
        text = String(text);
        if (!forced && BARE_WORD.test(text) && !KEYWORDS.includes(text.toLowerCase())) {
          return text;
        }
        return '"' + text.replace(/["\\]/g, '\\$&') + '"';
      };
      // A value written unquoted is read as a date when it looks like a date, and as an id when it's a number.
      let quoteValue = function (property, value) {
        let isName = names.has(property) && names.get(property).has(String(value));
        return quote(value, DATE.test(String(value)) || (isName && /^\d+$/.test(String(value))));
      };
      let toDate = function (value) {
        return String(value || '').substring(0, 10);
      };
      let listClause = function (filter, values, operator, keyword) {
        let property = quote(filter.property);
        let quoteFilterValue = value => quoteValue(filter.property, value);
        return values.length === 1 && operator ? `${property} ${operator} ${quoteFilterValue(values[0])}`
          : `${property} ${keyword} (${values.map(quoteFilterValue).join(', ')})`;
      };
      let toQuery = function (filters) {
        let clauses = [];
        filters.forEach(filter => {
          let property = quote(filter.property);
          let valuesByOperator = new Map();
          (filter.constraints || []).forEach(constraint => {
            let values = valuesByOperator.get(constraint.operator) || [];
            values.push(constraint.value);
            valuesByOperator.set(constraint.operator, values);
          });
          valuesByOperator.forEach((values, operator) => {
            switch (operator) {
              case '!':
                clauses.push(listClause(filter, values, '!=', 'NOT IN'));
                break;
              case '&=':
                clauses.push(listClause(filter, values, null, 'ALL IN'));
                break;
              case '~':
              case '!~':
              case '>=':
              case '<=':
                values.forEach(value => clauses.push(`${property} ${operator} ${quote(value)}`));
                break;
              case '*':
                clauses.push(`${property} IS NOT EMPTY`);
                break;
              case '!*':
                clauses.push(`${property} IS EMPTY`);
                break;
              case 'o':
                clauses.push(`${property} = open`);
                break;
              case 'c':
                clauses.push(`${property} = closed`);
                break;
              case 't':
                clauses.push(`${property} = today()`);
                break;
              case 'w':
                clauses.push(`${property} = thisWeek()`);
                break;
              case '=d':
                values.forEach(value => clauses.push(`${property} = ${toDate(value)}`));
                break;
              case 'between':
                values.forEach(value => {
                  let bounds = String(value).split('/');
                  let lower = toDate(bounds[0]);
                  let upper = toDate(bounds.length > 1 ? bounds[1] : bounds[0]);
                  if (lower && lower !== NO_LOWER_BOUND) {
                    clauses.push(`${property} >= ${lower}`);
                  }
                  if (upper && upper !== NO_UPPER_BOUND) {
                    clauses.push(`${property} <= ${upper}`);
                  }
                });
                break;
              default:
                clauses.push(listClause(filter, values, '=', 'IN'));
            }
          });
        });
        return clauses.join(' AND ');
      };
      let writeQuery = function (filters) {
        getContentField().val(toQuery(filters)).trigger('input').trigger('change');
        // The query replaces the deprecated filters parameter and the content read by another source.
        $('.proj-manag-filter[data-query]').val('');
        if (!['', 'query'].includes(getSource())) {
          getMacroForm().find('[name="source"]').val('').trigger('change');
        }
      };
      // The builder only has the "between" operator for the ranges of dates.
      let toBuilderConstraint = function (constraint) {
        if (constraint.operator === 'after') {
          return { operator: 'between', value: constraint.value + '/' + NO_UPPER_BOUND };
        } else if (constraint.operator === 'before') {
          return { operator: 'between', value: NO_LOWER_BOUND + '/' + constraint.value };
        }
        return constraint;
      };
      let showQueryWarning = function (bld, attribute, parameter) {
        let template = $('.proj-manag-filter[data-query]').attr(attribute) || '{0}';
        bld.element.before($('<div class="box warningmessage proj-manag-query-error"></div>')
          .text(template.replace('{0}', () => parameter)));
      };
      let readQuery = function (bld) {
        let query = String(getContentField().val() || '').trim();
        if (!query) {
          return;
        } else if (!['', 'query'].includes(getSource())) {
          // Editing the filters replaces the content read by the other source.
          showQueryWarning(bld, 'data-other-source', getSource());
          return;
        }
        $.get(`${XWiki.contextPath}/rest/wikis/${encodeURIComponent(XWiki.currentWiki)}`
          + '/projectmanagement/query/filters', { query: query }).done(function (data) {
          (data.filters || []).forEach(filter => {
            (filter.constraints || []).filter(constraint => constraint.name).forEach(constraint => {
              names.set(filter.property, (names.get(filter.property) || new Set()).add(String(constraint.value)));
            });
            bld.addFilter({ property: filter.property,
              constraints: (filter.constraints || []).map(toBuilderConstraint) });
          });
        }).fail(function (response) {
          showQueryWarning(bld, 'data-query-error',
            (response.responseJSON && response.responseJSON.message) || response.statusText);
        });
      };
      let updateFilterInput = function (e, constraints) {
        let livedataCfg = { query: { filters: [] } };
        for (key in constraints) {
          livedataCfg.query.filters.push(constraints[key]);
        }
        livedataCfgs.set(e.target, livedataCfg);
        if (isQueryMode()) {
          if (edited) {
            writeQuery(livedataCfg.query.filters);
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
        edited = false;
        names.clear();
        $('.proj-manag-query-error').remove();
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

