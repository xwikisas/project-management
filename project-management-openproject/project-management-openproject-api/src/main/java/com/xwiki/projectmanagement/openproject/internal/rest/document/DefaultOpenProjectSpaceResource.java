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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.job.Job;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.refactoring.internal.ModelBridge;
import org.xwiki.refactoring.job.CreateRequest;
import org.xwiki.refactoring.script.RefactoringScriptService;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.ModelFactory;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectSpaceResource;
import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * Default implementation of the {@link OpenProjectSpaceResource}.
 *
 * @version $Id$
 * @since 1.1.0-rc-1
 */
@Component
@Singleton
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectSpaceResource")
public class DefaultOpenProjectSpaceResource extends XWikiResource implements OpenProjectSpaceResource
{
    private static final LocalDocumentReference OPEN_PROJECT_TEMPLATE =
        new LocalDocumentReference(Arrays.asList("OpenProject", "Code", "Template"), "WebHome");

    @Inject
    protected ModelFactory factory;

    @Inject
    protected ContextualAuthorizationManager authorizationManager;

    @Inject
    protected ModelBridge modelBridge;

    @Inject
    private URLShortenerManager urlShortenerManager;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Override
    public Response createSpace(String wiki, String documentReference, Boolean withId) throws XWikiRestException
    {
        if (documentReference == null || documentReference.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Missing `docRef` query parameter pointing to the space that will be created.")
                .build();
        }
        DocumentReference docRef = resolver.resolve(documentReference, new WikiReference(wiki));

        XWikiContext context = getXWikiContext();
        XWiki xWiki = context.getWiki();
        try {
            if (!authorizationManager.hasAccess(Right.EDIT)) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            DocumentReference templateProviderReference =
                new DocumentReference(OPEN_PROJECT_TEMPLATE, docRef.getWikiReference());
            if (!xWiki.exists(templateProviderReference, context)) {
                return Response.status(Response.Status.NOT_ACCEPTABLE)
                    .entity("The requested wiki does not have the OpenProject application installed.").build();
            }
            if (xWiki.exists(docRef, context)) {
                return Response.status(Response.Status.FORBIDDEN).entity("Document already exists.").build();
            }

            XWikiDocument document = context.getWiki().getDocument(docRef, context);
            document.readFromTemplate(templateProviderReference, context);
            UserReference userReference = userReferenceResolver.resolve(context.getUserReference());
            document.getAuthors().setCreator(userReference);
            document.getAuthors().setEffectiveMetadataAuthor(userReference);
            xWiki.saveDocument(document, context);

            Job childrenCreateJob = startCreateJob(docRef, templateProviderReference);
            childrenCreateJob.join();

            List<DocumentReference> createdPages =
                this.modelBridge.getDocumentReferences(docRef.getLastSpaceReference());

            List<Page> restCreatedPages = createdPages.stream()
                .map(page ->
                {
                    try {
                        Page restPage = factory.toRestPage(uriInfo.getBaseUri(), uriInfo.getAbsolutePath(),
                            new Document(xWiki.getDocument(page, context), context), false, false, false, false, false);
                        if (withId) {
                            String id = urlShortenerManager.createShortenedURL(page);
                            restPage.setId(id);
                        }
                        return restPage;
                    } catch (XWikiException | URLShortenerException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

            return Response.ok(restCreatedPages).build();
        } catch (XWikiException | InterruptedException | ComponentLookupException e) {
            getLogger().error("Failed to create the OpenProject template at [{}].", documentReference, e);
            return Response.serverError().entity(ExceptionUtils.getStackTrace(e)).build();
        }
    }

    private Job startCreateJob(EntityReference entityReference, DocumentReference templateReference)
        throws XWikiException, ComponentLookupException
    {
        if (templateReference == null) {
            return null;
        } else {
            RefactoringScriptService
                refactoring = componentManager.getInstance(ScriptService.class, "refactoring");
            CreateRequest request = refactoring.getRequestFactory().createCreateRequest(Arrays.asList(entityReference));
            request.setCheckAuthorRights(false);
            request.setEntityReferences(Arrays.asList(entityReference));
            request.setTemplateReference(templateReference);
            request.setSkippedEntities(Arrays.asList(entityReference));
            Job createJob = refactoring.create(request);
            if (createJob != null) {
                return createJob;
            } else {
                throw new XWikiException(String.format("Failed to schedule the create job for [%s]", entityReference),
                    refactoring.getLastError());
            }
        }
    }
}
