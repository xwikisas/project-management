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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.job.Job;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;
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
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;
import com.xwiki.projectmanagement.openproject.internal.rest.RestErrorResponses;
import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectSpaceResource;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;
import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * Default implementation of the {@link OpenProjectSpaceResource}.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectSpaceResource")
public class DefaultOpenProjectSpaceResource extends XWikiResource implements OpenProjectSpaceResource
{
    private static final String WEB_HOME = "WebHome";

    private static final LocalDocumentReference OPEN_PROJECT_TEMPLATE =
        new LocalDocumentReference(Arrays.asList("OpenProject", "Code", "Template"), WEB_HOME);

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

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private AdminAuthorChecker adminAuthorChecker;

    @Override
    public Response createSpace(String documentReference, Boolean withId, String instance, Integer project,
        Integer workPackage, String title) throws XWikiRestException
    {
        if (documentReference == null || documentReference.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Missing `docRef` query parameter pointing to the space that will be created.")
                .build();
        }
        DocumentReference docRef =
            resolver.resolve(documentReference, new WikiReference(wikiDescriptorManager.getMainWikiId()));

        XWikiContext context = getXWikiContext();
        XWiki xWiki = context.getWiki();
        try {
            if (!authorizationManager.hasAccess(Right.VIEW, docRef) || !authorizationManager.hasAccess(Right.EDIT,
                docRef))
            {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            DocumentReference templateProviderReference =
                new DocumentReference(OPEN_PROJECT_TEMPLATE, docRef.getWikiReference());
            Response invalidTargetResponse = checkTarget(docRef, templateProviderReference, context);
            if (invalidTargetResponse != null) {
                return invalidTargetResponse;
            }
            List<DocumentReference> childrenToCreate = getChildrenToCreate(docRef, templateProviderReference, context);

            XWikiDocument document = context.getWiki().getDocument(docRef, context);
            document.readFromTemplate(templateProviderReference, context);
            document.setTitle(title);
            UserReference userReference = userReferenceResolver.resolve(context.getUserReference());
            String configuredInstance = findConfigurationWithInstance(instance);
            maybeAddRelationObj(document, configuredInstance, workPackage, project);
            document.getAuthors().setCreator(userReference);
            document.getAuthors().setEffectiveMetadataAuthor(userReference);
            document.getAuthors().setOriginalMetadataAuthor(userReference);
            document.getAuthors().setContentAuthor(userReference);
            xWiki.saveDocument(document, context);

            Job childrenCreateJob = startCreateJob(docRef, templateProviderReference);
            childrenCreateJob.join();

            List<Page> restCreatedPages = getCreatedPages(docRef, childrenToCreate, context).stream()
                .map(page ->
                {
                    try {
                        Page restPage = factory.toRestPage(uriInfo.getBaseUri(), uriInfo.getAbsolutePath(),
                            new Document(xWiki.getDocument(page, context), context), false, false, false, false, false);
                        if (Boolean.TRUE.equals(withId)) {
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
            return RestErrorResponses.errorResponse(getLogger(),
                String.format("Failed to create the OpenProject template at [%s].", documentReference), e);
        }
    }

    /**
     * Computes the pages that the create job will copy from the template children, i.e. those that don't exist yet.
     */
    private List<DocumentReference> getChildrenToCreate(DocumentReference docRef, DocumentReference templateReference,
        XWikiContext context) throws XWikiException
    {
        List<DocumentReference> children = new ArrayList<>();
        if (!WEB_HOME.equals(docRef.getName())) {
            return children;
        }
        SpaceReference templateSpace = templateReference.getLastSpaceReference();
        SpaceReference targetSpace = docRef.getLastSpaceReference();
        for (DocumentReference templatePage : this.modelBridge.getDocumentReferences(templateSpace)) {
            DocumentReference child = templatePage.replaceParent(templateSpace, targetSpace);
            if (!child.equals(docRef) && !context.getWiki().exists(child, context)) {
                children.add(child);
            }
        }
        return children;
    }

    private List<DocumentReference> getCreatedPages(DocumentReference docRef, List<DocumentReference> childrenToCreate,
        XWikiContext context) throws XWikiException
    {
        List<DocumentReference> createdPages = new ArrayList<>();
        createdPages.add(docRef);
        for (DocumentReference child : childrenToCreate) {
            // The create job skips the children that the current user is not allowed to create.
            if (context.getWiki().exists(child, context) && this.authorizationManager.hasAccess(Right.VIEW, child)) {
                createdPages.add(child);
            }
        }
        return createdPages;
    }

    private Response checkTarget(DocumentReference docRef, DocumentReference templateReference, XWikiContext context)
        throws XWikiException
    {
        XWiki xWiki = context.getWiki();
        if (!xWiki.exists(templateReference, context)) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity("The requested wiki does not have the OpenProject application installed.").build();
        }
        if (xWiki.exists(docRef, context)) {
            return Response.status(Response.Status.CONFLICT).entity("Document already exists.").build();
        }
        if (!isTemplateLastSavedByAdmin(templateReference, context)) {
            return Response.status(Response.Status.CONFLICT)
                .entity("The OpenProject template must be saved by an administrator.").build();
        }
        return null;
    }

    /**
     * The template pages (with their translations) are copied with the current user as author, so they must have
     * been written by an administrator.
     */
    private boolean isTemplateLastSavedByAdmin(DocumentReference templateReference, XWikiContext context)
        throws XWikiException
    {
        for (DocumentReference templatePage : this.modelBridge.getDocumentReferences(
            templateReference.getLastSpaceReference())) {
            XWikiDocument templateDocument = context.getWiki().getDocument(templatePage, context);
            if (!isLastSavedByAdmin(templateDocument)) {
                return false;
            }
            for (Locale locale : templateDocument.getTranslationLocales(context)) {
                if (!isLastSavedByAdmin(
                    context.getWiki().getDocument(new DocumentReference(templatePage, locale), context)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isLastSavedByAdmin(XWikiDocument templateDocument)
    {
        if (!this.adminAuthorChecker.isLastSavedByAdmin(templateDocument)) {
            getLogger().warn("Not creating the OpenProject space because the template page [{}] wasn't last "
                + "saved by a user with administration right on the wiki.",
                templateDocument.getDocumentReferenceWithLocale());
            return false;
        }
        return true;
    }

    private String findConfigurationWithInstance(String instance)
    {
        if (StringUtils.isEmpty(instance)) {
            return "";
        }
        OpenProjectConnection connection =
            openProjectConfiguration.getOpenProjectConnections().stream()
                .filter(cfg -> instance.equals(cfg.getInstanceId())).findFirst().orElse(null);

        if (connection == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity(String.format("Could not find a configured instance for the id [%s].", instance)).build());
        }

        return connection.getConnectionName();
    }

    private void maybeAddRelationObj(XWikiDocument document, String instance, Integer workPackage, Integer project)
    {
        if (StringUtils.isEmpty(instance) && workPackage == null && project == null) {
            return;
        }
        BaseObject relation = document.getXObject(ProjectManagementRelation.CLASS_REFERENCE, true, getXWikiContext());
        if (!StringUtils.isEmpty(instance)) {
            relation.setLargeStringValue(ProjectManagementRelation.FIELD_CLIENT_PARAMS,
                "{\"instance\":\"" + instance + "\"}");
        }
        if (workPackage != null) {
            relation.setStringValue(ProjectManagementRelation.FIELD_WORK_ITEM, String.valueOf(workPackage));
        }
        if (project != null) {
            relation.setStringValue(ProjectManagementRelation.FIELD_PROJECT, String.valueOf(project));
        }
        relation.setStringValue(ProjectManagementRelation.FIELD_CLIENT, "openproject");
    }

    private Job startCreateJob(EntityReference entityReference, DocumentReference templateReference)
        throws XWikiException, ComponentLookupException
    {
        if (templateReference == null) {
            return null;
        } else {
            RefactoringScriptService
                refactoring = componentManager.getInstance(ScriptService.class, "refactoring");
            CreateRequest request = refactoring.getRequestFactory().createCreateRequest(
                Collections.singletonList(entityReference));
            request.setCheckAuthorRights(false);
            request.setEntityReferences(Collections.singletonList(entityReference));
            request.setTemplateReference(templateReference);
            request.setSkippedEntities(Collections.singletonList(entityReference));
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
