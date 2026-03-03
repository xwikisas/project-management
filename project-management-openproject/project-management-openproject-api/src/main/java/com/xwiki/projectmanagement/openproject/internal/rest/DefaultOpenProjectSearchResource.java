package com.xwiki.projectmanagement.openproject.internal.rest;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.xwiki.contrib.oidc.OIDCConsent;
import org.xwiki.contrib.oidc.provider.internal.store.OIDCStore;
import org.xwiki.contrib.oidc.provider.internal.store.XWikiBearerAccessToken;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.resources.wikis.WikiSearchQueryResourceImpl;
import org.xwiki.rest.model.jaxb.SearchResults;

import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.rest.OpenProjectSearchResource;
import com.xwiki.projectmanagement.openproject.store.WorkPackageLink;

public class DefaultOpenProjectSearchResource extends WikiSearchQueryResourceImpl implements OpenProjectSearchResource
{
    @Inject
    private OIDCStore oidcStore;

    @Inject
    private OpenProjectConfiguration configuration;

    @Override
    public SearchResults getProjects(String wikiName, String projectId, Integer number, Integer start, Boolean distinct,
        String orderField, String order, Boolean withPrettyNames) throws XWikiRestException
    {
        OpenProjectConnection connection = null;
        try {
            String authorizationString = getXWikiContext().getRequest().getHeader("Authorization");
            XWikiBearerAccessToken xwikiAccessToken = XWikiBearerAccessToken.parse(authorizationString);

            // Retrieve currently authenticated user and try to retrieve the OIDCConsent object.
            // It should contain
            OIDCConsent consent = this.oidcStore.getConsent(xwikiAccessToken);
            String clientId = consent.getClientID();
            connection =
                configuration.getOpenProjectConnections().stream().filter(cfg -> cfg.getClientId().equals(clientId))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            throw new WebApplicationException(Response.serverError().entity(e).build());
        }
        String connectionId = connection == null ? "" : connection.getClientId();
        int id = 0;
        try {
            id = Integer.parseInt(projectId);
        } catch (NumberFormatException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("WorkPackage id should be an integer.").build());
        }
        String statement = String.format(",doc.object(%s) as link and link.project = '%d' and link.instance = '%s'",
            WorkPackageLink.CLASS_FULLNAME, id, connectionId);

        return super.search(wikiName, statement, "xwql", number, start, distinct, orderField, order, withPrettyNames,
            WorkPackageLink.CLASS_FULLNAME);
    }

    @Override
    public SearchResults getWorkPackages(String wikiName, String workPackageId, Integer number, Integer start,
        Boolean distinct, String orderField, String order, Boolean withPrettyNames) throws XWikiRestException
    {
        int id = 0;
        try {
            id = Integer.parseInt(workPackageId);
        } catch (NumberFormatException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("WorkPackage id should be an integer.").build());
        }
        String statement = String.format(",doc.object(%s) as link and link.workPackage = %d",
            WorkPackageLink.CLASS_FULLNAME, id);

        return super.search(wikiName, statement, "xwql", number, start, distinct, orderField, order, withPrettyNames,
            WorkPackageLink.CLASS_FULLNAME);
    }
}
