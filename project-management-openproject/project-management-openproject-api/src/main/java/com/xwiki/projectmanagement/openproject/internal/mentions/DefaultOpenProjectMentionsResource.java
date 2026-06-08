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
package com.xwiki.projectmanagement.openproject.internal.mentions;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.DomainObjectFactory;
import org.xwiki.rest.internal.RangeIterable;
import org.xwiki.rest.internal.Utils;
import org.xwiki.rest.model.jaxb.Objects;
import org.xwiki.rest.model.jaxb.SearchResults;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.internal.rest.BaseOpenProjectWikiSearchResource;
import com.xwiki.projectmanagement.openproject.rest.mentions.OpenProjectMentionResource;
import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * Default implementation of the {@link OpenProjectMentionResource}.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.mentions.DefaultOpenProjectMentionsResource")
public class DefaultOpenProjectMentionsResource extends BaseOpenProjectWikiSearchResource
    implements OpenProjectMentionResource
{
    @Inject
    private URLShortenerManager urlShortenerManager;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Override
    public SearchResults getPagesMentioningWorkPackage(String workPackageId, String filterInstance, Integer number,
        Integer start, String orderField, String order, Boolean withPrettyNames) throws XWikiRestException
    {
        String id = workPackageId;
        if (!StringUtils.isEmpty(workPackageId)) {
            try {
                Integer.parseInt(workPackageId);
            } catch (NumberFormatException e) {
                throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("Work package id should be an integer.")
                        .build());
            }
        } else {
            id = "*";
        }

        StringBuilder statement = new StringBuilder();
        statement.append(String.format("property.%s.%s:%s", OpenProjectMentionClassInitializer.CLASS_NAME,
            OpenProjectMentionClassInitializer.PROP_WORK_PACKAGE_ID, id));

        maybeAddInstanceFilter(statement, filterInstance);

        return searchInternal(statement.toString(), number, start, orderField, order,
            withPrettyNames);
    }

    @Override
    public Objects getMentionsWithId(String pageId, String filterInstance, Integer number, Integer start,
        Boolean withPrettyNames) throws XWikiRestException
    {
        try {

            if (pageId == null || pageId.isEmpty()) {
                throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("Missing page id.").build());
            }
            DocumentReference documentReference = urlShortenerManager.getDocumentReference(null, pageId);

            if (documentReference == null) {
                throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
            }

            return getMentions(documentReference, filterInstance, start, number, withPrettyNames);
        } catch (URLShortenerException e) {
            throw new WebApplicationException(
                Response.serverError().entity(ExceptionUtils.getRootCauseMessage(e)).build());
        }
    }

    @Override
    public Objects getMentionsWithRef(String reference, String filterInstance, Integer number, Integer start,
        Boolean withPrettyNames) throws XWikiRestException
    {

        return getMentions(documentReferenceResolver.resolve(reference), filterInstance, start, number,
            withPrettyNames);
    }

    private Objects getMentions(DocumentReference documentReference, String filterInstance, Integer start,
        Integer number,
        Boolean withPrettyNames) throws XWikiRestException
    {
        try {

            DocumentInfo documentInfo = getDocumentInfo(documentReference.getWikiReference().getName(),
                documentReference.getSpaceReferences().stream().map(SpaceReference::getName)
                    .collect(Collectors.toList()), documentReference.getName(), null, null, true, false);

            Document doc = documentInfo.getDocument();

            Objects objects = objectFactory.createObjects();

            List<BaseObject> objectList =
                getBaseObjects(doc.getDocumentReference()).stream().filter(java.util.Objects::nonNull).collect(
                    Collectors.toList());

            RangeIterable<BaseObject> ri = new RangeIterable<BaseObject>(objectList, start, number);

            for (BaseObject object : ri) {
                /* By deleting objects, some of them might become null, so we must check for this */
                if (object != null) {
                    objects.getObjectSummaries().add(
                        DomainObjectFactory.createObjectSummary(objectFactory, uriInfo.getBaseUri(),
                            Utils.getXWikiContext(componentManager), doc, object, false,
                            Utils.getXWikiApi(componentManager), withPrettyNames));
                }
            }

            return objects;
        } catch (XWikiException e) {
            throw new WebApplicationException(
                Response.serverError().entity(ExceptionUtils.getRootCauseMessage(e)).build());
        }
    }

    protected List<BaseObject> getBaseObjects(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xWikiContext = this.xcontextProvider.get();
        XWikiDocument xwikiDocument = xWikiContext.getWiki().getDocument(documentReference, xWikiContext);

        return xwikiDocument.getXObjects(OpenProjectMentionClassInitializer.REFERENCE);
    }
}
