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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClientFactory;
import com.xwiki.projectmanagement.openproject.auth.NoOpAuthenticator;
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

    private static final String INSTANCE_ID = "instanceId";

    private static final String OPEN_PROJECT = "OpenProject";

    private static final String CODE = "Code";

    private static final String OPEN_PROJECT_CONNECTION_CLASS = "OpenProjectConnectionClass";

    private static final String OPEN_PROJECT_CONFIGURATIONS = "OpenProjectConfigurations";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactSerializer;

    @Inject
    @Named("document")
    private UserReferenceSerializer<DocumentReference> documentUserSerializer;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    private OpenProjectApiClientFactory openProjectApiClientFactory;

    @Inject
    private Logger logger;

    /**
     * Creates a new OpenProject connection configuration document in XWiki, or updates an existing one. The document
     * must be in the {@code OpenProject.Code.OpenProjectConfigurations} space, whose pages only the administrators
     * can view. Only the connections stored on pages whose author has administration right on the wiki are taken into
     * account when checking if the connection name is already used, and an existing page can be updated only if its
     * author has administration right on the wiki. When updating an existing connection, an empty client secret keeps
     * the current one, so that the client secret doesn't need to be sent back to the browser. The server URL must be
     * an absolute HTTP or HTTPS URL. The current user becomes the author of the saved connection objects, while the
     * creator and the content author of an existing page are kept, since its content isn't modified.
     *
     * @param openProjectConnection the connection data to be saved
     * @param documentReference the document reference
     * @throws ProjectManagementException if the document is not in the connections space, if a configuration with
     *     the same name already exists, if the existing page can't be updated, if no client secret is provided for a
     *     new connection, if the server URL is not valid, if the query failed or the document retrieval/creation fails.
     */
    public void handleConnection(OpenProjectConnection openProjectConnection,
        DocumentReference documentReference)
        throws ProjectManagementException
    {
        SpaceReference connectionsSpace = new SpaceReference(documentReference.getWikiReference().getName(),
            OPEN_PROJECT, CODE, OPEN_PROJECT_CONFIGURATIONS);
        if (!documentReference.hasParent(connectionsSpace)) {
            throw new ProjectManagementException(String.format("The connection can't be saved on the page [%s]: the "
                + "connections must be saved in the [OpenProject.Code.OpenProjectConfigurations] space.",
                this.compactSerializer.serialize(documentReference)));
        }

        checkServerURL(openProjectConnection.getServerURL());

        XWikiContext context = this.xcontextProvider.get();
        try {
            XWikiDocument doc = context.getWiki().getDocument(documentReference, context);
            if (!doc.isNew() && !isAuthorAdmin(doc)) {
                throw new ProjectManagementException(String.format("The page [%s] can't be updated because it was "
                    + "last modified by a user without administration right on the wiki. Delete the connection and "
                    + "create it again.", this.compactSerializer.serialize(documentReference)));
            }

            if (isConnectionNameUsed(openProjectConnection.getConnectionName(), documentReference, context)) {
                throw new ProjectManagementException(
                    String.format("Connection %s already exists. Use another connection name.",
                        openProjectConnection.getConnectionName()));
            }

            String clientSecret = getClientSecret(openProjectConnection, doc);

            handleConnectionObjects(openProjectConnection, clientSecret, doc, context);
        } catch (QueryException | XWikiException e) {
            throw new ProjectManagementException("There was a problem while saving the connection", e);
        }
    }

    private void checkServerURL(String serverURL) throws ProjectManagementException
    {
        boolean valid = false;
        try {
            URI uri = new URI(Objects.toString(serverURL, ""));
            valid = ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && StringUtils.isNotEmpty(uri.getHost());
        } catch (URISyntaxException e) {
            // Not a valid URL.
        }
        if (!valid) {
            throw new ProjectManagementException(String.format("The server URL [%s] is not valid: it must be an "
                + "absolute HTTP or HTTPS URL, e.g. [https://openproject.example.com].", serverURL));
        }
    }

    private boolean isConnectionNameUsed(String connectionName, DocumentReference documentReference,
        XWikiContext context) throws QueryException, XWikiException
    {
        List<String> result = queryManager.createQuery(
                "select obj.name from XWikiDocument doc, BaseObject obj, StringProperty configName "
                    + "where doc.fullName = obj.name "
                    + "and obj.className = :className "
                    + "and obj.id = configName.id.id "
                    + "and configName.id.name = :configFieldName "
                    + "and configName.value = :config "
                    + "and doc.fullName <> :serializedDocRef", Query.HQL)
            .bindValue(
                "className",
                String.format("%s.%s.%s", OPEN_PROJECT, CODE, OPEN_PROJECT_CONNECTION_CLASS)
            )
            .bindValue("configFieldName", CONNECTION_NAME)
            .bindValue("config", connectionName)
            .bindValue("serializedDocRef", compactSerializer.serialize(documentReference))
            .setWiki(documentReference.getWikiReference().getName())
            .execute();

        // The connections stored on pages whose author doesn't have administration right on the wiki are not used, so
        // they don't prevent from using their name.
        for (String documentName : result) {
            DocumentReference otherReference =
                this.documentReferenceResolver.resolve(documentName, documentReference.getWikiReference());
            if (isAuthorAdmin(context.getWiki().getDocument(otherReference, context))) {
                return true;
            }
        }
        return false;
    }

    private String getClientSecret(OpenProjectConnection openProjectConnection, XWikiDocument doc)
        throws ProjectManagementException
    {
        String clientSecret = openProjectConnection.getClientSecret();
        if (StringUtils.isBlank(clientSecret)) {
            // Keep the client secret currently stored on the page, if any.
            BaseObject currentObject = doc.getXObject(getConnectionClassReference(doc));
            clientSecret = currentObject != null ? currentObject.getStringValue(CLIENT_SECRET) : null;
            if (StringUtils.isEmpty(clientSecret)) {
                throw new ProjectManagementException("The client secret is required.");
            }
        }
        return clientSecret;
    }

    private DocumentReference getConnectionClassReference(XWikiDocument doc)
    {
        return new DocumentReference(doc.getDocumentReference().getWikiReference().getName(),
            Arrays.asList(OPEN_PROJECT, CODE), OPEN_PROJECT_CONNECTION_CLASS);
    }

    private boolean isAuthorAdmin(XWikiDocument document)
    {
        DocumentReference author =
            this.documentUserSerializer.serialize(document.getAuthors().getEffectiveMetadataAuthor());
        return this.authorizationManager.hasAccess(Right.ADMIN, author,
            document.getDocumentReference().getWikiReference());
    }

    private void handleConnectionObjects(OpenProjectConnection openProjectConnection, String clientSecret,
        XWikiDocument doc, XWikiContext context) throws XWikiException
    {
        String wikiName = doc.getDocumentReference().getWikiReference().getName();

        setDocMetaData(context, doc);

        BaseObject configObj = doc.getXObject(getConnectionClassReference(doc), true, context);
        configObj.setStringValue(CONNECTION_NAME, openProjectConnection.getConnectionName());
        configObj.setStringValue(SERVER_URL, openProjectConnection.getServerURL());
        configObj.setStringValue(CLIENT_ID, openProjectConnection.getClientId());
        configObj.setStringValue(CLIENT_SECRET, clientSecret);
        configObj.setStringValue(INSTANCE_ID, retrieveInstanceId(openProjectConnection.getServerURL()));
        configObj.setIntValue("showPublicData", openProjectConnection.isShowPublicData() ? 1 : 0);

        DocumentReference oidcClassRef =
            new DocumentReference(wikiName, Arrays.asList("XWiki", "OIDC"), "ClientConfigurationClass");

        BaseObject oidcObj = doc.getXObject(oidcClassRef, true, context);
        oidcObj.setStringValue("configurationName", openProjectConnection.getConnectionName());
        oidcObj.setStringValue("authorizationEndpoint", openProjectConnection.getServerURL() + "/oauth/authorize");
        oidcObj.setStringValue("tokenEndpoint", openProjectConnection.getServerURL() + "/oauth/token");
        oidcObj.setStringValue(CLIENT_ID, openProjectConnection.getClientId());
        oidcObj.setStringValue(CLIENT_SECRET, clientSecret);
        oidcObj.setStringValue("tokenEndpointMethod", "client_secret_basic");
        oidcObj.setIntValue("skipped", 0);
        oidcObj.setStringValue("scope", "api_v3");
        oidcObj.setStringValue("responseType", "code");
        oidcObj.setIntValue("enableUser", 1);
        oidcObj.setStringValue("tokenStorageScope", "USER");

        context.getWiki().saveDocument(doc, "Saved OpenProject and OIDC config via REST", context);
    }

    private String retrieveInstanceId(String serverURL)
    {
        try {
            return openProjectApiClientFactory.builder()
                .serverUrl(serverURL)
                .authentication(NoOpAuthenticator.INSTANCE)
                .build()
                .getInstanceId();
        } catch (ProjectManagementException e) {
            logger.warn("Failed to retrieve the instance id from [{}]. Cause: [{}].", serverURL,
                ExceptionUtils.getRootCauseMessage(e));
            return "";
        } catch (Exception e) {
            logger.warn("There was an issue in communicating with [{}]. Cause: [{}].", serverURL,
                ExceptionUtils.getRootCauseMessage(e));
            return "";
        }
    }

    private void setDocMetaData(XWikiContext context, XWikiDocument doc)
    {
        UserReference currentUser = userReferenceResolver.resolve(context.getUserReference());
        doc.setHidden(true);
        DocumentAuthors documentAuthors = doc.getAuthors();
        // The current user writes the connection objects, so they become the metadata author. The content of an
        // existing page is not modified, so its content author (and its creator) are kept.
        if (doc.isNew()) {
            documentAuthors.setCreator(currentUser);
            documentAuthors.setContentAuthor(currentUser);
        }
        documentAuthors.setEffectiveMetadataAuthor(currentUser);
        documentAuthors.setOriginalMetadataAuthor(currentUser);
    }
}
