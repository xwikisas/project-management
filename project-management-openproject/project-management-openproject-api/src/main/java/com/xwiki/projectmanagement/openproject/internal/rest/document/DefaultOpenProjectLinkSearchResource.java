package com.xwiki.projectmanagement.openproject.internal.rest.document;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.Utils;
import org.xwiki.rest.internal.resources.BaseSearchResult;
import org.xwiki.rest.internal.resources.search.SearchSource;
import org.xwiki.rest.model.jaxb.SearchResults;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectLinkSearchResource;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;

/**
 * Default implementation of the {@link OpenProjectLinkSearchResource}. It extends the default implementation of the
 * {@link org.xwiki.rest.resources.wikis.WikiSearchQueryResource} and simply prepares the parameters for its methods.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectLinkSearchResource")
public class DefaultOpenProjectLinkSearchResource extends BaseSearchResult implements
    OpenProjectLinkSearchResource
{
    private static final String MULTIWIKI_QUERY_TEMPLATE_INFO =
        "q={solrquery}(&number={number})(&start={start})(&orderField={fieldname}(&order={asc|desc}))(&distinct=1)"
            + "(&prettyNames={false|true})(&wikis={wikis})(&className={classname})";

    @Inject
    @Named("solr")
    private SearchSource solrSearch;

    @Inject
    private OpenProjectConfiguration configuration;

    @Override
    public SearchResults getProjects(String projectId, String filterInstance, Integer number,
        Integer start, String orderField, String order, Boolean withPrettyNames) throws XWikiRestException
    {

        int id = 0;
        try {
            id = Integer.parseInt(projectId);
        } catch (NumberFormatException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("Project id should be an integer.").build());
        }
        StringBuilder statement = new StringBuilder();
        statement.append(String.format("property.%s.project:%d", ProjectManagementRelation.CLASS_FULLNAME, id));

        maybeAddInstanceFilter(statement, filterInstance);

        return searchInternal(statement.toString(), number, start, orderField, order,
            withPrettyNames);
    }

    @Override
    public SearchResults getWorkPackages(String workPackageId, String filterInstance, Integer number,
        Integer start, String orderField, String order, Boolean withPrettyNames)
        throws XWikiRestException
    {
        int id = 0;
        try {
            id = Integer.parseInt(workPackageId);
        } catch (NumberFormatException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("WorkPackage id should be an integer.").build());
        }
        StringBuilder statement = new StringBuilder();
        statement.append(
            String.format("property.%s.workItem:%d",
                ProjectManagementRelation.CLASS_FULLNAME, id));

        maybeAddInstanceFilter(statement, filterInstance);

        return searchInternal(statement.toString(), number, start, orderField, order,
            withPrettyNames);
    }

    private SearchResults searchInternal(String query, Integer number, Integer start,
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

    private void maybeAddInstanceFilter(StringBuilder query, String filterInstance)
    {
        if (filterInstance == null || filterInstance.isEmpty()) {
            return;
        }
        // TODO: Maybe we should enforce the instance names to be alphanumeric only.
        query.append(String.format(" and link.cliemtParams like '%%%s%%'", filterInstance));
    }
}
