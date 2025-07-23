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
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
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

    private static final String OPEN_PROJECT = "OpenProject";

    private static final String CODE = "Code";

    private static final String OPEN_PROJECT_CONNECTION_CLASS = "OpenProjectConnectionClass";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    /**
     * Creates a new OpenProject connection configuration document in XWiki.
     *
     * @param openProjectConnection the connection data to be saved
     * @param documentReference the document reference
     * @throws ProjectManagementException if a configuration with the same name already exists.
     * @throws ProjectManagementException if the query failed or the document retrieval/creation fails.
     */
    public void handleConnection(OpenProjectConnection openProjectConnection,
        DocumentReference documentReference)
        throws ProjectManagementException
    {
        List<String> result;
        try {
            result = queryManager.createQuery(
                    "select obj.name from XWikiDocument doc, BaseObject obj, StringProperty configName "
                        + "where doc.fullName = obj.name "
                        + "and obj.className = :className "
                        + "and obj.id = configName.id.id "
                        + "and configName.id.name = :configFieldName "
                        + "and configName.value = :config", Query.HQL)
                .bindValue(
                    "className",
                    String.format("%s.%s.%s", OPEN_PROJECT, CODE, OPEN_PROJECT_CONNECTION_CLASS)
                )
                .bindValue("configFieldName", CONNECTION_NAME)
                .bindValue("config", openProjectConnection.getConnectionName())
                .setWiki(this.xcontextProvider.get().getWikiId())
                .execute();

            if (!result.isEmpty()) {
                throw new ProjectManagementException(
                    String.format("Connection %s already exists. Use another connection name.",
                        openProjectConnection.getConnectionName()));
            }

            handleConnectionObjects(openProjectConnection, documentReference);
        } catch (QueryException | XWikiException e) {
            throw new ProjectManagementException("There was a problem while saving the connection", e);
        }
    }

    private void handleConnectionObjects(OpenProjectConnection openProjectConnection,
        DocumentReference documentReference) throws XWikiException
    {
        String wikiName = documentReference.getWikiReference().getName();
        XWikiContext context = this.xcontextProvider.get();
        XWikiDocument doc;

        doc = context.getWiki().getDocument(documentReference, context);

        setDocMetaData(context, doc);

        DocumentReference configClassRef =
            new DocumentReference(wikiName, Arrays.asList(OPEN_PROJECT, CODE),
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
    }

    private void setDocMetaData(XWikiContext context, XWikiDocument doc)
    {
        UserReference currentUser = userReferenceResolver.resolve(context.getUserReference());
        DocumentAuthors documentAuthors = doc.getAuthors();
        documentAuthors.setCreator(currentUser);
        documentAuthors.setEffectiveMetadataAuthor(currentUser);
        documentAuthors.setContentAuthor(currentUser);
        documentAuthors.setOriginalMetadataAuthor(currentUser);
    }
}
