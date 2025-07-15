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

import java.util.ArrayList;
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
import org.xwiki.query.QueryManager;
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
@Component(roles = CreateConnectionService.class)
@Singleton
public class CreateConnectionService
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

    /**
     * Creates a new OpenProject connection configuration document in XWiki.
     *
     * @param openProjectConnection the connection data to be saved
     * @param wikiName the name of the wiki that where the document will be stored
     * @param spaceName list of space names that define the document location
     * @param pageName the name of the page where the connection will be created
     * @throws Exception if an error occurs while creating or saving the document
     */
    public void createConnection(OpenProjectConnection openProjectConnection, String wikiName, List<String> spaceName,
        String pageName) throws Exception
    {
        String spacePath = String.join(".", spaceName) + '.' + pageName;

        List<String> result = queryManager.createQuery(
                "select obj.name from XWikiDocument doc, BaseObject obj, StringProperty configName "
                    + "where doc.fullName = obj.name "
                    + "and doc.space = :spaceName "
                    + "and obj.className = :className "
                    + "and obj.id = configName.id.id "
                    + "and configName.id.name = :configFieldName "
                    + "and configName.value = :config", Query.HQL)
            .bindValue("spaceName", spacePath)
            .bindValue(
                "className",
                String.format("%s.%s", PROJECT_MANAGEMENT, OPEN_PROJECT_CONNECTION_CLASS)
            )
            .bindValue("configFieldName", CONNECTION_NAME)
            .bindValue("config", openProjectConnection.getConnectionName())
            .execute();

        if (!result.isEmpty()) {
            throw new RuntimeException(
                "Connection " + openProjectConnection.getConnectionName() + " already exists"
            );
        }

        try {
            createConnectionObjects(openProjectConnection, wikiName, spaceName, pageName);
        } catch (XWikiException e) {
            throw new RuntimeException("Failed to create connection: " + openProjectConnection.getConnectionName(), e);
        }
    }

    private void createConnectionObjects(OpenProjectConnection openProjectConnection,
        String wikiName, List<String> spaceName,
        String pageName) throws XWikiException
    {
        XWikiContext context = this.xcontextProvider.get();

        List<String> space = new ArrayList<>(spaceName);
        space.add(pageName);

        DocumentReference docRef = new DocumentReference(
            wikiName,
            space,
            openProjectConnection.getConnectionName() + INSTANCE_CONFIGURATION
        );

        XWikiDocument doc = context.getWiki().getDocument(docRef, context);
        setDocMetaData(openProjectConnection.getConnectionName(), context, doc);

        DocumentReference configClassRef =
            new DocumentReference(wikiName, PROJECT_MANAGEMENT, OPEN_PROJECT_CONNECTION_CLASS);
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

        context.getWiki().saveDocument(doc, "Created OpenProject and OIDC config via REST", context);
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
