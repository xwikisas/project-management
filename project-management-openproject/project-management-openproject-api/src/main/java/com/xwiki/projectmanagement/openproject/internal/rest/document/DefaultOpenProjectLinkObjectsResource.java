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
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.ModelFactory;
import org.xwiki.rest.internal.Utils;
import org.xwiki.rest.internal.resources.objects.BaseObjectsResource;
import org.xwiki.rest.model.jaxb.Object;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.rest.resources.objects.ObjectResource;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.model.WorkPackageLink;
import com.xwiki.projectmanagement.openproject.rest.document.OpenProjectLinkObjectsResource;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * Default implementation of the {@link OpenProjectLinkObjectsResource}. It extends the default
 * {@link org.xwiki.rest.resources.objects.ObjectsResource} and prepares the parameters for its methods.
 *
 * @version $Id$
 * @since 1.2.0-rc-1
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.document.DefaultOpenProjectLinkObjectsResource")
public class DefaultOpenProjectLinkObjectsResource extends BaseObjectsResource implements OpenProjectLinkObjectsResource
{
    private static final String CLIENT = "client";

    private static final String OPENPROJECT = "openproject";

    @Inject
    private URLShortenerManager urlShortenerManager;

    @Inject
    private ModelFactory factory;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Override
    public Response link(String id, Boolean minorRevision, WorkPackageLink link)
        throws XWikiRestException
    {
        try {
            if (link == null) {
                throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("Missing link entity").build());
            }

            DocumentReference documentReference = urlShortenerManager.getDocumentReference(null, id);

            if (documentReference == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            maybeAddInstance(link);

            Object object = linkToObject(link);

            return addObject(documentReference.getWikiReference().getName(),
                documentReference.getSpaceReferences().stream().map(SpaceReference::getName)
                    .collect(Collectors.toList()), documentReference.getName(), minorRevision, object);
        } catch (WebApplicationException e) {
            return Response.fromResponse(e.getResponse()).build();
        } catch (Exception e) {
            return Response.serverError().entity(ExceptionUtils.getRootCauseMessage(e)).build();
        }
    }

    @Override
    public Object getLink(String id)
    {
        try {
            DocumentReference documentReference = urlShortenerManager.getDocumentReference(null, id);
            if (documentReference == null) {
                throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
            }

            DocumentInfo documentInfo = getDocumentInfo(documentReference.getWikiReference().getName(),
                documentReference.getSpaceReferences().stream().map(SpaceReference::getName)
                    .collect(Collectors.toList()), documentReference.getName(), null, null, true, false);

            Document doc = documentInfo.getDocument();

            com.xpn.xwiki.api.Object xwikiObject = doc.getObject(ProjectManagementRelation.CLASS_FULLNAME, CLIENT,
                OPENPROJECT);

            com.xpn.xwiki.objects.BaseObject baseObject =
                getBaseObject(doc, ProjectManagementRelation.CLASS_FULLNAME, xwikiObject.getNumber());
            if (baseObject == null) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            return this.factory.toRestObject(this.uriInfo.getBaseUri(), doc, baseObject, false, false);
        } catch (Exception e) {
            throw new WebApplicationException(Response.serverError().entity(ExceptionUtils.getStackTrace(e)).build());
        }
    }

    private Response addObject(String wikiName, List<String> spaces, String pageName, Boolean minorRevision,
        Object object)
        throws XWikiRestException
    {
        if (object.getClassName() == null) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        try {
            DocumentInfo documentInfo = getDocumentInfo(wikiName, spaces, pageName, null, null, true, false);

            Document doc = documentInfo.getDocument();

            if (!doc.hasAccessLevel("edit", Utils.getXWikiUser(componentManager))) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }

            com.xpn.xwiki.api.Object xwikiObject = doc.getObject(object.getClassName(), CLIENT, OPENPROJECT);
            com.xpn.xwiki.api.Object finalXwikiObject = xwikiObject;
            if (xwikiObject != null && object.getProperties().stream()
                .allMatch(p -> p.getValue().equals(finalXwikiObject.getValue(p.getName()))))
            {
                return Response.status(Response.Status.NOT_MODIFIED).build();
            }
//            if (xwikiObject.getValue("workItem"))
            boolean newObj = false;
            if (xwikiObject == null) {
                newObj = true;
                xwikiObject = doc.newObject(object.getClassName());
            }

            if (xwikiObject == null) {
                throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            }

            this.factory.toObject(xwikiObject, object);

            doc.save("", Boolean.TRUE.equals(minorRevision));

            BaseObject baseObject = getBaseObject(doc, object.getClassName(), xwikiObject.getNumber());

            Response.ResponseBuilder responseBuilder;
            if (newObj) {
                responseBuilder = Response.created(
                    Utils.createURI(this.uriInfo.getBaseUri(), ObjectResource.class, wikiName, spaces, pageName,
                        object.getClassName(), baseObject.getNumber()));
            } else {
                responseBuilder = Response.status(Response.Status.ACCEPTED);
            }
            return responseBuilder.entity(
                this.factory.toRestObject(this.uriInfo.getBaseUri(), doc, baseObject, false, false)).build();
        } catch (XWikiException e) {
            throw new XWikiRestException(e);
        }
    }

    private Object linkToObject(WorkPackageLink link)
    {
        Object object = new Object();
        object.withClassName(ProjectManagementRelation.CLASS_FULLNAME);
        List<Property> properties = new ArrayList<>();
        if (!StringUtils.isEmpty(link.getProject())) {
            createProperty(ProjectManagementRelation.FIELD_PROJECT, link.getProject(), properties);
        }
        if (!StringUtils.isEmpty(link.getInstance())) {
            createProperty(ProjectManagementRelation.FIELD_CLIENT_PARAMS,
                "{ \"instance\": \"" + link.getInstance() + "\" }", properties);
        }
        if (!StringUtils.isEmpty(link.getWorkPackage())) {
            createProperty(ProjectManagementRelation.FIELD_WORK_ITEM, link.getWorkPackage(), properties);
        }
        createProperty(ProjectManagementRelation.FIELD_CLIENT, OPENPROJECT, properties);
        object.withProperties(properties);
        return object;
    }

    private static void createProperty(String fieldProject, String link, List<Property> properties)
    {
        Property projectProperty = new Property();
        projectProperty.setName(fieldProject);
        projectProperty.setValue(link);
        properties.add(projectProperty);
    }

    private void maybeAddInstance(com.xwiki.projectmanagement.openproject.model.WorkPackageLink link)
    {
        String instance = link.getInstance();
        if (StringUtils.isEmpty(instance)) {
            return;
        }
        OpenProjectConnection connection =
            openProjectConfiguration.getOpenProjectConnections().stream()
                .filter(cfg -> instance.equals(cfg.getInstanceId())).findFirst().orElse(null);

        if (connection == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .entity(String.format("Could not find a configured instance for the id [%s].", instance)).build());
        }

        link.setInstance(connection.getConnectionName());
    }
}
