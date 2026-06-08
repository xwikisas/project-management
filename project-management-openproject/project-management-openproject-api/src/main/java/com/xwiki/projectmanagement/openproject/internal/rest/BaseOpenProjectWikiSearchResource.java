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
package com.xwiki.projectmanagement.openproject.internal.rest;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.Utils;
import org.xwiki.rest.internal.resources.BaseSearchResult;
import org.xwiki.rest.internal.resources.search.SearchSource;
import org.xwiki.rest.model.jaxb.SearchResults;

import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;

/**
 * Base wiki search resource that can be extended to search documents matching specific criteria across all the wikis.
 *
 * @version $Id$
 * @since 1.2.0
 */
public class BaseOpenProjectWikiSearchResource extends BaseSearchResult
{
    protected static final String MULTIWIKI_QUERY_TEMPLATE_INFO =
        "q={solrquery}(&number={number})(&start={start})(&orderField={fieldname}(&order={asc|desc}))(&distinct=1)"
            + "(&prettyNames={false|true})(&wikis={wikis})(&className={classname})";

    @Inject
    @Named("solr")
    protected SearchSource solrSearch;

    protected SearchResults searchInternal(String query, Integer number, Integer start,
        String orderField, String order, Boolean withPrettyNames) throws XWikiRestException
    {
        int limit = number;

        try {
            SearchResults searchResults = objectFactory.createSearchResults();
            searchResults.setTemplate(String.format("%s?%s", uriInfo.getBaseUri().toString(),
                MULTIWIKI_QUERY_TEMPLATE_INFO));

            searchResults.getSearchResults().addAll(
                this.solrSearch.search(
                    query,
                    getXWikiContext().getWikiId(),
                    (String) null,
                    Utils.getXWiki(componentManager).getRightService().hasProgrammingRights(
                        Utils.getXWikiContext(componentManager)), orderField, order, (Boolean) true, limit, start,
                    withPrettyNames, ProjectManagementRelation.CLASS_FULLNAME, uriInfo));

            return searchResults;
        } catch (Exception e) {
            throw new XWikiRestException(e);
        }
    }

    protected void maybeAddInstanceFilter(StringBuilder query, String filterInstance)
    {
        if (filterInstance == null || filterInstance.isEmpty()) {
            return;
        }
        // TODO: Maybe we should enforce the instance names to be alphanumeric only.
        query.append(String.format(" and link.cliemtParams like '%%%s%%'", filterInstance));
    }
}
