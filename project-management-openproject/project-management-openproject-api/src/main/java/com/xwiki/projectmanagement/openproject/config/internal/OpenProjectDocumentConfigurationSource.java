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
package com.xwiki.projectmanagement.openproject.config.internal;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.internal.AbstractDocumentConfigurationSource;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * Extracts data from the OpenProjectConnection Class.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("openproject")
@Singleton
public class OpenProjectDocumentConfigurationSource extends AbstractDocumentConfigurationSource
{
    private static final String OPENPROJECT_CONNECTION_CLASS = "OpenProjectConnectionClass";

    private static final String SPACE = "ProjectManagement";

    private static final String CONNECTIONS_SPACE = SPACE + ".OpenProjectConfigurations";

    private static final LocalDocumentReference CLASS_REFERENCE =
        new LocalDocumentReference(SPACE, OPENPROJECT_CONNECTION_CLASS);

    private static final LocalDocumentReference DOC_REFERENCE =
        new LocalDocumentReference(SPACE, OPENPROJECT_CONNECTION_CLASS);

    @Inject
    private QueryManager queryManager;

    @Inject
    private EntityReferenceResolver<String> referenceResolver;

    @Override
    protected DocumentReference getDocumentReference()
    {
        return new DocumentReference(DOC_REFERENCE, getCurrentWikiReference());
    }

    @Override
    protected LocalDocumentReference getClassReference()
    {
        return CLASS_REFERENCE;
    }

    @Override
    protected String getCacheId()
    {
        return "configuration.document.openproject";
    }

    @Override
    protected Object getBaseProperty(String propertyName, boolean text) throws XWikiException
    {
        if (!propertyName.equals("openprojectConnections")) {
            return super.getBaseProperty(propertyName, text);
        }

        XWikiContext xContext = this.xcontextProvider.get();
        List<OpenProjectConnection> objects = new ArrayList<>();
        try {
            String query = "where doc.space like :space or doc.space like  ':space.%'";
            List<String> results =
                this.queryManager.createQuery(query, Query.XWQL)
                    .bindValue("space", CONNECTIONS_SPACE)
                    .execute();
            for (String docName : results) {
                EntityReference entityReference =
                    referenceResolver.resolve(docName, EntityType.DOCUMENT,
                        getCurrentWikiReference());
                DocumentReference docRef = new DocumentReference(entityReference);
                XWikiDocument xwikiDocument = xContext.getWiki().getDocument(docRef, xContext);
                BaseObject baseObject = xwikiDocument.getXObject(getClassReference());
                String connectionName = baseObject.getStringValue("connectionName");
                String serverURL = baseObject.getStringValue("serverURL");
                String clientId = baseObject.getStringValue("clientId");
                String clientSecret = baseObject.getStringValue("clientSecret");
                objects.add(new OpenProjectConnection(connectionName, serverURL, clientId, clientSecret));
            }
            return objects;
        } catch (QueryException e) {
            throw new XWikiException("Query creation error", e);
        }
    }
}
