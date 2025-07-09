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
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

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

    private static final String OPEN_PROJECT_CONFIGURATIONS = "OpenProjectConfigurations";

    private static final String PROJECT_MANAGEMENT = "ProjectManagement";

    private static final String INSTANCE_CONFIGURATION = "InstanceConfiguration";

    private static final String OPEN_PROJECT_CONNECTION_CLASS = "OpenProjectConnectionClass";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private QueryManager queryManager;

    /**
     * Creates a new OpenProject connection configuration document in XWiki.
     *
     * @param connectionName the unique name used to identify the connection. It will be used as part of the
     *     document name.
     * @param serverURL the base URL of the OpenProject server.
     * @param clientId the OAuth2 client ID used for authentication with OpenProject.
     * @param clientSecret the OAuth2 client secret used for authentication with OpenProject.
     * @throws Exception if there is any issue while creating or saving the document, or accessing the context.
     */
    public void createConnection(String connectionName, String serverURL, String clientId,
        String clientSecret) throws Exception
    {

        List<String> result =  queryManager.createQuery(
                "select obj.name from BaseObject obj, StringProperty configName "
                    + "where obj.className = :className and obj.id = configName.id.id "
                    + "and configName.id.name = :configFieldName and configName.value = :config", Query.HQL)
            .bindValue("className", PROJECT_MANAGEMENT + "." + OPEN_PROJECT_CONNECTION_CLASS)
            .bindValue("configFieldName", CONNECTION_NAME)
            .bindValue("config", connectionName).execute();

        if (!result.isEmpty()) {
            throw new RuntimeException(
                "Connection " + connectionName + " already exists"
            );
        }

        try {
            createConnectionObjects(connectionName, serverURL, clientId, clientSecret);
        } catch (XWikiException e) {
            throw new RuntimeException("Failed to create connection: " + connectionName, e);
        }
    }

    private void createConnectionObjects(String connectionName, String serverURL,
        String clientId, String clientSecret) throws XWikiException
    {
        XWikiContext context = this.xcontextProvider.get();
        String wikiName = context.getWikiId();

        DocumentReference docRef = new DocumentReference(
            wikiName,
            Arrays.asList(PROJECT_MANAGEMENT, OPEN_PROJECT_CONFIGURATIONS),
            connectionName + INSTANCE_CONFIGURATION
        );

        XWikiDocument doc = context.getWiki().getDocument(docRef, context);
        doc.setTitle(connectionName + INSTANCE_CONFIGURATION);
        doc.setHidden(true);

        DocumentReference configClassRef =
            new DocumentReference(wikiName, PROJECT_MANAGEMENT, OPEN_PROJECT_CONNECTION_CLASS);
        BaseObject configObj = doc.getXObject(configClassRef, true, context);
        configObj.setStringValue(CONNECTION_NAME, connectionName);
        configObj.setStringValue(SERVER_URL, serverURL);
        configObj.setStringValue(CLIENT_ID, clientId);
        configObj.setStringValue(CLIENT_SECRET, clientSecret);

        DocumentReference oidcClassRef = new DocumentReference(wikiName, "XWiki", "OIDC.ClientConfigurationClass");

        BaseObject oidcObj = doc.getXObject(oidcClassRef, true, context);
        oidcObj.setStringValue("configurationName", connectionName);
        oidcObj.setStringValue("authorizationEndpoint", serverURL + "/oauth/authorize");
        oidcObj.setStringValue("tokenEndpoint", serverURL + "/oauth/token");
        oidcObj.setStringValue(CLIENT_ID, clientId);
        oidcObj.setStringValue(CLIENT_SECRET, clientSecret);
        oidcObj.setStringValue("tokenEndpointMethod", "client_secret_basic");
        oidcObj.setIntValue("skipped", 0);
        oidcObj.setStringValue("scope", "api_v3");
        oidcObj.setStringValue("responseType", "code");
        oidcObj.setIntValue("enableUser", 1);
        oidcObj.setStringValue("tokenStorageScope", "USER");

        context.getWiki().saveDocument(doc, "Created OpenProject and OIDC config via REST", context);
    }
}
