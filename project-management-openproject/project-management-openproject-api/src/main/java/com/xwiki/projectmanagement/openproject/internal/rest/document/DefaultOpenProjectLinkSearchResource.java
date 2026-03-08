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

import java.net.URI;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.oidc.OIDCConsent;
import org.xwiki.contrib.oidc.provider.internal.store.OIDCStore;
import org.xwiki.contrib.oidc.provider.internal.store.XWikiBearerAccessToken;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.resources.wikis.WikiSearchQueryResourceImpl;
import org.xwiki.rest.model.jaxb.SearchResults;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectLinkSearchResource;
import com.xwiki.projectmanagement.openproject.store.WorkPackageLink;

/**
 * Default implementation of the {@link OpenProjectLinkSearchResource}. It extends the default implementation of the
 * {@link org.xwiki.rest.resources.wikis.WikiSearchQueryResource} and simply prepares the parameters for its methods.
 *
 * @version $Id
 * @since 1.1.0-rc-1
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectLinkSearchResource")
public class DefaultOpenProjectLinkSearchResource extends WikiSearchQueryResourceImpl implements
    OpenProjectLinkSearchResource
{
    @Inject
    private OIDCStore oidcStore;

    @Inject
    private OpenProjectConfiguration configuration;

    @Override
    public SearchResults getProjects(String wikiName, String projectId, Boolean filterInstance, Integer number,
        Integer start, String orderField, String order, Boolean withPrettyNames) throws XWikiRestException
    {

        int id = 0;
        try {
            id = Integer.parseInt(projectId);
        } catch (NumberFormatException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("WorkPackage id should be an integer.").build());
        }
        StringBuilder statement = new StringBuilder();
        statement.append(
            String.format(",doc.object(%s) as link where link.project = '%d'", WorkPackageLink.CLASS_FULLNAME, id));

        maybeAddInstanceFilter(statement, filterInstance);

        return super.search(wikiName, statement.toString(), "xwql", number, start, true, orderField, order,
            withPrettyNames, WorkPackageLink.CLASS_FULLNAME);
    }

    @Override
    public SearchResults getWorkPackages(String wikiName, String workPackageId, Boolean filterInstance, Integer number,
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
            String.format(",doc.object(%s) as link where link.workPackage = '%d'", WorkPackageLink.CLASS_FULLNAME, id));

        maybeAddInstanceFilter(statement, filterInstance);

        return super.search(wikiName, statement.toString(), "xwql", number, start, true, orderField, order,
            withPrettyNames, WorkPackageLink.CLASS_FULLNAME);
    }

    private void maybeAddInstanceFilter(StringBuilder query, Boolean filterInstance)
    {
        if (!filterInstance) {
            return;
        }
        String instance = "";

        try {
            String authorizationString = getXWikiContext().getRequest().getHeader("Authorization");
            if (authorizationString != null && !authorizationString.isEmpty()) {
                XWikiBearerAccessToken xwikiAccessToken = XWikiBearerAccessToken.parse(authorizationString);

                OIDCConsent consent = this.oidcStore.getConsent(xwikiAccessToken);
                if (consent != null) {
                    URI clientId = consent.getRedirectURI();
                    OpenProjectConnection connection =
                        configuration.getOpenProjectConnections().stream()
                            .filter(cfg -> cfg.getClientId().startsWith(clientId.getHost()))
                            .findFirst().orElse(null);
                    if (connection != null) {
                        instance = connection.getConnectionName();
                    }
                }
            }
        } catch (Exception e) {
            throw new WebApplicationException(Response.serverError().entity(e).build());
        }
        // TODO: Maybe we should enforce the instance names to be alphanumeric only.
        query.append(String.format(" and link.instance = '%s'", instance));
    }
}
