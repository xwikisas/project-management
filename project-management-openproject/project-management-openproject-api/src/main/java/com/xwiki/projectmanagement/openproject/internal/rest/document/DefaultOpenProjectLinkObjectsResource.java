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
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.oidc.OIDCConsent;
import org.xwiki.contrib.oidc.provider.internal.store.OIDCStore;
import org.xwiki.contrib.oidc.provider.internal.store.XWikiBearerAccessToken;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.resources.objects.ObjectsResourceImpl;
import org.xwiki.rest.model.jaxb.Object;
import org.xwiki.rest.model.jaxb.Property;

import com.nimbusds.oauth2.sdk.ParseException;
import com.xpn.xwiki.XWikiException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.model.WorkPackageLink;
import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectLinkObjectsResource;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * Default implementation of the {@link OpenProjectLinkObjectsResource}. It extends the default
 * {@link org.xwiki.rest.resources.objects.ObjectsResource} and prepares the parameters for its methods.
 *
 * @version $Id$
 * @since 1.1.0-rc-1
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectLinkObjectsResource")
public class DefaultOpenProjectLinkObjectsResource extends ObjectsResourceImpl implements OpenProjectLinkObjectsResource
{
    @Inject
    private URLShortenerManager urlShortenerManager;

    @Inject
    private OIDCStore oidcStore;

    @Inject
    private OpenProjectConfiguration configuration;

    @Override
    public Response link(String wikiName, String id, Boolean inferInstance, Boolean minorRevision, WorkPackageLink link)
        throws XWikiRestException
    {
        try {
            if (link == null) {
                throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("Missing link entity").build());
            }

            DocumentReference documentReference = urlShortenerManager.getDocumentReference(wikiName, id);

            if (documentReference == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            StringBuilder stringBuilder = new StringBuilder();
            for (EntityReference entityReference : documentReference.getReversedReferenceChain()) {
                if (entityReference instanceof SpaceReference) {
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append("/spaces/");
                    }
                    stringBuilder.append(entityReference.getName());
                }
            }

            {
                maybeAddInstance(link, inferInstance);
            }
            Object object = new Object();
            object
                .withClassName(com.xwiki.projectmanagement.openproject.store.WorkPackageLink.CLASS_FULLNAME);
            List<Property> properties = new ArrayList<>();
            if (!StringUtils.isEmpty(link.getProject())) {
                createProperty(com.xwiki.projectmanagement.openproject.store.WorkPackageLink.FIELD_PROJECT,
                    link.getProject(), properties);
            }
            if (!StringUtils.isEmpty(link.getInstance())) {
                createProperty(com.xwiki.projectmanagement.openproject.store.WorkPackageLink.FIELD_INSTANCE,
                    link.getInstance(), properties);
            }
            if (!StringUtils.isEmpty(link.getWorkPackage())) {
                createProperty(com.xwiki.projectmanagement.openproject.store.WorkPackageLink.FIELD_WORK_PACKAGE,
                    link.getWorkPackage(),
                    properties);
            }
            object.withProperties(properties);

            return addObject(wikiName, stringBuilder.toString(), documentReference.getName(), minorRevision, object);
        } catch (WebApplicationException e) {
            return Response.fromResponse(e.getResponse()).build();
        } catch (Exception e) {
            return Response.serverError().entity(ExceptionUtils.getRootCauseMessage(e)).build();
        }
    }

    private static void createProperty(String fieldProject, String link, List<Property> properties)
    {
        Property projectProperty = new Property();
        projectProperty.setName(fieldProject);
        projectProperty.setValue(link);
        properties.add(projectProperty);
    }

    private void maybeAddInstance(com.xwiki.projectmanagement.openproject.model.WorkPackageLink link,
        Boolean inferInstance)
    {
        if (!inferInstance) {
            return;
        }

        try {
            String authorizationString = getXWikiContext().getRequest().getHeader("Authorization");
            if (authorizationString == null || authorizationString.isEmpty()) {
                throw new WebApplicationException(
                    Response.status(Response.Status.UNAUTHORIZED).entity("Missing bearer token.").build());
            }
            XWikiBearerAccessToken xwikiAccessToken = XWikiBearerAccessToken.parse(authorizationString);

            OIDCConsent consent = this.oidcStore.getConsent(xwikiAccessToken);
            if (consent == null) {
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Access token is not associated to any xwiki user.").build());
            }
            URI clientId = consent.getRedirectURI();
            OpenProjectConnection connection =
                configuration.getOpenProjectConnections().stream()
                    .filter(cfg -> cfg.getClientId().startsWith(clientId.getHost()))
                    .findFirst().orElse(null);
            if (connection == null) {
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("The Open Project Instance that made the request is not configured in the wiki.").build());
            }
            link.setInstance(connection.getConnectionName());
        } catch (XWikiException | ParseException e) {
            getLogger().error("Failed to retrieve the configured open project client for the request.", e);
            throw new WebApplicationException(Response.serverError().entity(Response.serverError()).build());
        }
    }
}
