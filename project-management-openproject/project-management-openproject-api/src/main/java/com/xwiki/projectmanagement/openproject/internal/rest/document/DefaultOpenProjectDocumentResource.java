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
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
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
import org.xwiki.rest.internal.resources.pages.ModifiablePageResource;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectDocumentResource;
import com.xwiki.urlshortener.URLShortenerException;
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
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectDocumentResource")
public class DefaultOpenProjectDocumentResource extends ModifiablePageResource
    implements OpenProjectDocumentResource, Initializable
{
    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private URLShortenerManager urlShortenerManager;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Override
    public Response getDocument(String id, Boolean withPrettyNames,
        Boolean withObjects, Boolean withXClass, Boolean withAttachments) throws XWikiRestException
    {
        try {
            if (id == null || id.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Missing page id.").build();
            }
            DocumentReference documentReference = urlShortenerManager.getDocumentReference(null, id);

            if (documentReference == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            Page page = getPage(documentReference.getWikiReference().getName(),
                documentReference.getSpaceReferences().stream().map(EntityReference::getName)
                    .collect(Collectors.toList()), documentReference.getName(), withPrettyNames, withObjects,
                withXClass, withAttachments);

            page.setId(id);

            return Response.ok(page).build();
        } catch (Exception e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response updateDocument(String documentReference, Boolean minorRevision, Boolean create, Page page)
        throws XWikiRestException
    {
        if (documentReference == null || documentReference.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Missing `docRef` query parameter pointing to the document that needs creation/updating.")
                .build();
        }
        DocumentReference docRef =
            resolver.resolve(documentReference, new WikiReference(wikiDescriptorManager.getMainWikiId()));

        try {

            return putPageAndReturn(docRef, page, minorRevision, create);
        } catch (XWikiException e) {
            return Response.serverError().entity(ExceptionUtils.getStackTrace(e)).build();
        }
    }

    @Override
    public Response getDocumentUniqueId(String documentReference, Boolean withPrettyNames,
        Boolean withObjects, Boolean withXClass, Boolean withAttachments) throws XWikiRestException
    {
        if (documentReference == null || documentReference.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Missing `docRef` query parameter pointing to the document with an unique id.")
                .build();
        }
        DocumentReference docRef =
            resolver.resolve(documentReference, new WikiReference(wikiDescriptorManager.getMainWikiId()));

        try {

            Page page = getPage(docRef.getWikiReference().getName(),
                docRef.getSpaceReferences().stream().map(EntityReference::getName)
                    .collect(Collectors.toList()), docRef.getName(), withPrettyNames, withObjects,
                withXClass, withAttachments);

            String id = null;

            id = urlShortenerManager.createShortenedURL(docRef);

            page.setId(id);

            return Response.ok(page).build();
        } catch (URLShortenerException e) {
            getLogger().error("Failed to create the shortened url for document [{}].", docRef, e);
            return Response.serverError().entity(String.format("Could not create a unique id for the page. Cause [%s].",
                ExceptionUtils.getRootCauseMessage(e))).build();
        }
    }

    private Page getPage(String wikiName, List<String> spaceName, String pageName, Boolean withPrettyNames,
        Boolean withObjects, Boolean withXClass, Boolean withAttachments) throws XWikiRestException
    {
        try {
            DocumentInfo documentInfo = getDocumentInfo(wikiName, spaceName, pageName, null, null, true, false);

            Document doc = documentInfo.getDocument();

            URI baseUri = uriInfo.getBaseUri();

            Page page =
                this.factory.toRestPage(baseUri, uriInfo.getAbsolutePath(), doc, false, withPrettyNames, withObjects,
                    withXClass, withAttachments);

            return page;
        } catch (XWikiException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ExceptionUtils.getStackTrace(e))
                    .build());
        }
    }

    private Response putPageAndReturn(DocumentReference docRef, Page page, Boolean minorRevision, Boolean create)
        throws XWikiRestException, XWikiException
    {

        String spaces = getRestSpaces(docRef);
        DocumentInfo documentInfo =
            getDocumentInfo(docRef.getWikiReference().getName(), spaces, docRef.getName(), null, null, false, false);
        if (create && !documentInfo.getDocument().isNew()) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT).build());
        }
        Response createResponse = putPage(documentInfo, page, minorRevision);

        // Attach id after making the request. Even if returns a 400 error code, we still want to try to attach the id.
        if (!authorizationManager.hasAccess(Right.VIEW, docRef)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Access denied in view mode. Can't attach unique identifier.").build();
        }

        String idResponse = null;

        try {
            idResponse = urlShortenerManager.createShortenedURL(docRef);
        } catch (Exception e) {
            getLogger().error("Failed to initialize the shortened url for document [{}].", docRef, e);
            return Response.serverError().entity(String.format("Could not attach a unique id to the page. Cause [%s].",
                ExceptionUtils.getRootCauseMessage(e))).build();
        }

        if (createResponse.getStatus() >= 400) {
            Page pageWithId = new Page();
            pageWithId.setId(idResponse);
            return Response.status(createResponse.getStatus()).entity(pageWithId).build();
        }
        Page createdPage = (Page) createResponse.getEntity();

        // Probably received 304.
        int status = createResponse.getStatus();
        if (createdPage == null) {
            try {
                Document doc = documentInfo.getDocument();
                createdPage = this.factory.toRestPage(this.uriInfo.getBaseUri(), this.uriInfo.getAbsolutePath(), doc,
                    false, false, false, false, false);
                status = Response.Status.ACCEPTED.getStatusCode();
            } catch (XWikiException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ExceptionUtils.getStackTrace(e))
                    .build();
            }
        }
        createdPage.setId(idResponse);

        return Response.status(status).entity(createdPage).build();
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
}
