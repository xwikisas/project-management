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
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.resources.pages.PageResourceImpl;
import org.xwiki.rest.model.jaxb.Page;

import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectDocumentResource;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * Default implementation of the {@link OpenProjectDocumentResource}. It uses the URLShortener Application to attach
 * unique ids to the documents. It extends the default implementation of the
 * {@link org.xwiki.rest.resources.pages.PageResource} to perform the document operations.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Component
@Singleton
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectDocumentResource")
public class DefaultOpenProjectDocumentResource extends PageResourceImpl
    implements OpenProjectDocumentResource, Initializable
{
    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private URLShortenerManager urlShortenerManager;

    @Override
    public Response getDocument(String wiki, String id, Boolean withPrettyNames,
        Boolean withObjects, Boolean withXClass, Boolean withAttachments) throws XWikiRestException
    {
        try {
            if (id == null || id.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Missing page id.").build();
            }
            DocumentReference documentReference = urlShortenerManager.getDocumentReference(wiki, id);

            if (documentReference == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            String spaces = getRestSpaces(documentReference);
            Page page = getPage(documentReference.getWikiReference().getName(), spaces, documentReference.getName(),
                withPrettyNames, withObjects, withXClass, withAttachments);

            page.setId(id);

            return Response.ok(page).build();
        } catch (Exception e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response updateDocument(String wiki, String documentReference, Boolean minorRevision, Page page)
        throws XWikiRestException
    {
        if (documentReference == null || documentReference.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Missing `docRef` query parameter pointing to the document that needs creation/updating.")
                .build();
        }
        DocumentReference docRef = resolver.resolve(documentReference, new WikiReference(wiki));
        String spaces = getRestSpaces(docRef);
        Response createResponse = putPage(wiki, spaces, docRef.getName(), minorRevision, page);
        if (createResponse.getStatus() >= 400) {
            return createResponse;
        }

        String idResponse = null;

        try {
            idResponse = urlShortenerManager.createShortenedURL(docRef);
        } catch (Exception e) {
            getLogger().error("Failed to initialize the shortened url for document [{}].", documentReference, e);
            return Response.serverError().entity(String.format("Could not attach a unique id to the page. Cause [%s].",
                ExceptionUtils.getRootCauseMessage(e))).build();
        }

        Page createdPage = (Page) createResponse.getEntity();
        createdPage.setId(idResponse);

        return Response.status(createResponse.getStatus()).entity(createdPage).build();
    }

    private static String getRestSpaces(DocumentReference docRef)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (EntityReference entityReference : docRef.getReversedReferenceChain()) {
            if (entityReference instanceof SpaceReference) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append("/spaces/");
                }
                stringBuilder.append(entityReference.getName());
            }
        }
        return stringBuilder.toString();
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Override
    public Response putPage(String wikiName, String spaceName, String pageName, Boolean minorRevision, Page page)
        throws XWikiRestException
    {
        return super.putPage(wikiName, spaceName, pageName, minorRevision, page);
    }
}
