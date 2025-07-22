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
package com.xwiki.projectmanagement.openproject.internal.service;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.refactoring.internal.ModelBridge;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;

/**
 * Class for creating the connection.
 *
 * @version $Id$
 */
@Component(roles = HandleConnectionsService.class)
@Singleton
public class HandleConnectionsService
{
    private static final String CONNECTION_NAME = "connectionName";

    private static final String SERVER_URL = "serverURL";

    private static final String CLIENT_ID = "clientId";

    private static final String CLIENT_SECRET = "clientSecret";

    private static final String PROJECT_MANAGEMENT = "ProjectManagement";

    private static final String INSTANCE_CONFIGURATION = "InstanceConfiguration";

    private static final String OPEN_PROJECT_CONNECTION_CLASS = "OpenProjectConnectionClass";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userRefResolver;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactSerializer;

    @Inject
    private ModelBridge modelBridge;

    /**
     * Creates a new OpenProject connection configuration document in XWiki.
     *
     * @param openProjectConnection the connection data to be saved
     * @param oldDocumentReference the document reference
     * @param newDocumentReference the new document reference of an updated object
     * @throws Exception if an error occurs while creating or saving the document
     */
    public void createOrUpdateConnection(OpenProjectConnection openProjectConnection,
        DocumentReference oldDocumentReference, DocumentReference newDocumentReference)
        throws Exception
    {

        List<String> result = queryManager.createQuery(
                "select obj.name from XWikiDocument doc, BaseObject obj, StringProperty configName "
                    + "where doc.fullName = obj.name "
                    + "and doc.space = :spaceName "
                    + "and obj.className = :className "
                    + "and obj.id = configName.id.id "
                    + "and configName.id.name = :configFieldName "
                    + "and configName.value = :config", Query.HQL)
            .bindValue("spaceName",
                compactSerializer.serialize(newDocumentReference.getLastSpaceReference()))
            .bindValue(
                "className",
                String.format("%s.%s", PROJECT_MANAGEMENT, OPEN_PROJECT_CONNECTION_CLASS)
            )
            .bindValue("configFieldName", CONNECTION_NAME)
            .bindValue("config", openProjectConnection.getConnectionName())
            .setWiki(this.xcontextProvider.get().getWikiId())
            .execute();

        if (!result.isEmpty()) {
            throw new RuntimeException("The connection name already exists! Please use a different connection name!");
        }

        try {
            handleConnectionObjects(openProjectConnection, oldDocumentReference, newDocumentReference);
        } catch (XWikiException e) {
            throw new RuntimeException("Failed to create connection: " + openProjectConnection.getConnectionName(), e);
        }
    }

    private void handleConnectionObjects(OpenProjectConnection openProjectConnection,
        DocumentReference oldDocumentReference, DocumentReference newDocumentReference) throws XWikiException
    {
        String wikiName = newDocumentReference.getWikiReference().getName();
        XWikiContext context = this.xcontextProvider.get();
        XWikiDocument doc;

        if (oldDocumentReference != null) {
            doc = context.getWiki().getDocument(oldDocumentReference, context);
        } else {
            doc = context.getWiki().getDocument(newDocumentReference, context);
        }

        setDocMetaData(openProjectConnection.getConnectionName(), context, doc);

        DocumentReference configClassRef =
            new DocumentReference(wikiName, PROJECT_MANAGEMENT,
                OPEN_PROJECT_CONNECTION_CLASS);
        BaseObject configObj = doc.getXObject(configClassRef, true, context);
        configObj.setStringValue(CONNECTION_NAME, openProjectConnection.getConnectionName());
        configObj.setStringValue(SERVER_URL, openProjectConnection.getServerURL());
        configObj.setStringValue(CLIENT_ID, openProjectConnection.getClientId());
        configObj.setStringValue(CLIENT_SECRET, openProjectConnection.getClientSecret());

        DocumentReference oidcClassRef =
            new DocumentReference(wikiName, Arrays.asList("XWiki", "OIDC"), "ClientConfigurationClass");

        BaseObject oidcObj = doc.getXObject(oidcClassRef, true, context);
        oidcObj.setStringValue("configurationName", openProjectConnection.getConnectionName());
        oidcObj.setStringValue("authorizationEndpoint", openProjectConnection.getServerURL() + "/oauth/authorize");
        oidcObj.setStringValue("tokenEndpoint", openProjectConnection.getServerURL() + "/oauth/token");
        oidcObj.setStringValue(CLIENT_ID, openProjectConnection.getClientId());
        oidcObj.setStringValue(CLIENT_SECRET, openProjectConnection.getClientSecret());
        oidcObj.setStringValue("tokenEndpointMethod", "client_secret_basic");
        oidcObj.setIntValue("skipped", 0);
        oidcObj.setStringValue("scope", "api_v3");
        oidcObj.setStringValue("responseType", "code");
        oidcObj.setIntValue("enableUser", 1);
        oidcObj.setStringValue("tokenStorageScope", "USER");

        context.getWiki().saveDocument(doc, "Saved OpenProject and OIDC config via REST", context);

        if (oldDocumentReference != null && !oldDocumentReference.equals(newDocumentReference)) {
            modelBridge.rename(oldDocumentReference, newDocumentReference);
            XWikiDocument newDoc = context.getWiki().getDocument(newDocumentReference, context);
            context.getWiki().saveDocument(newDoc, "Created OpenProject and OIDC config via REST", context);
        }
    }

    private void setDocMetaData(String connectionName, XWikiContext context, XWikiDocument doc)
    {
        UserReference currentUser = userRefResolver.resolve(context.getUserReference());
        DocumentAuthors documentAuthors = doc.getAuthors();
        documentAuthors.setCreator(currentUser);
        documentAuthors.setEffectiveMetadataAuthor(currentUser);
        documentAuthors.setContentAuthor(currentUser);
        documentAuthors.setOriginalMetadataAuthor(currentUser);
        doc.setTitle(connectionName + INSTANCE_CONFIGURATION);
        doc.setHidden(true);
    }
}
