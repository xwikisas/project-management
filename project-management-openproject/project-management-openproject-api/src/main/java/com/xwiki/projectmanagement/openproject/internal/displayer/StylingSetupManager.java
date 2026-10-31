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

package com.xwiki.projectmanagement.openproject.internal.displayer;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.AdminAuthorChecker;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;

/**
 * Using the {@link OpenProjectApiClient} retrieve the colors for the OpenProject properties that support customization
 * and generate a style sheet for each configured OpenProject instance.
 *
 * @version $Id$
 */
@Component(roles = StylingSetupManager.class)
@Singleton
public class StylingSetupManager
{
    private static final String STYLESHEET_EXTENSION_PROP_NAME = "name";

    private static final String STYLESHEET_EXTENSION_VAL_NAME = "open-project-property-status";

    private static final String SPACE_XWIKI = "XWiki";

    private static final String PROP_CODE = "code";

    private static final String PROP_PARSE = "parse";

    private static final EntityReference OPEN_PROJECT_SSX_EXTENSIONS =
        new LocalDocumentReference(Arrays.asList("OpenProject", "Code", "StyleSheets"), "WebHome");

    private static final EntityReference STYLESHEET_EXTENSION = new LocalDocumentReference(SPACE_XWIKI,
        "StyleSheetExtension");

    /**
     * The HTTP status codes of OpenProject when it refuses a request: 401 (not authenticated) and 403 (forbidden).
     */
    private static final Set<Integer> REFUSED_STATUS_CODES = Set.of(401, 403);

    private static final String REFUSED_MESSAGE =
        "Skipping styling update for [{}] since OpenProject refused to give its statuses or types to ";

    private static final Logger LOGGER = LoggerFactory.getLogger(StylingSetupManager.class);

    @Inject
    private OpenProjectConfiguration opConfiguration;

    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private DocumentReferenceResolver<EntityReference> documentReferenceResolver;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userRefResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("ssx")
    private SkinExtension ssx;

    @Inject
    private AdminAuthorChecker adminAuthorChecker;

    /**
     * The locks serializing the styling updates of each wiki.
     */
    private final WikiLocks wikiLocks = new WikiLocks();

    /**
     * Generate the styling for the configured OpenProject instances, reading their statuses and types with the current
     * user's account or as the users who aren't connected to them when the connection shows its public data. Nothing
     * is done when the current user doesn't have administration right on the current wiki, since the styles are saved
     * as the current user.
     */
    public void setupInstanceStyles()
    {
        setupInstanceStyles(false);
    }

    /**
     * Generate the styling for the configured OpenProject instances. Nothing is done when the current user doesn't
     * have administration right on the current wiki, since the styles are saved as the current user.
     *
     * @param anonymousFallback whether to read the statuses and types of the connections that the current user can't
     *     call (not connected, and no public data shown) without credentials, since they're the same for every user;
     *     these requests aren't cached, so this is meant for the styling job, not for each rendering; the styling
     *     job waits for an update already running on the wiki, while the other callers skip the update then
     * @since 1.4.0
     */
    public void setupInstanceStyles(boolean anonymousFallback)
    {
        XWikiContext context = contextProvider.get();
        if (!this.adminAuthorChecker.isAdmin(context.getUserReference(), context.getWikiReference())) {
            LOGGER.debug("Skipping the styling update since the current user [{}] isn't an administrator.",
                context.getUserReference());
            return;
        }
        // Concurrent updates (e.g. the job triggered by a connection save and the "Sync colors" button) would save
        // the same styles pages: run them one after the other. The rendering of a calendar doesn't wait for a running
        // update, since it generates the same styles.
        String wikiId = context.getWikiId();
        if (anonymousFallback) {
            this.wikiLocks.lock(wikiId);
        } else if (!this.wikiLocks.tryLock(wikiId)) {
            LOGGER.debug("Skipping the styling update since another one is running on the wiki [{}].", wikiId);
            return;
        }
        try {
            setupInstanceStyles(context, anonymousFallback);
        } finally {
            this.wikiLocks.unlock(wikiId);
        }
    }

    private void setupInstanceStyles(XWikiContext context, boolean anonymousFallback)
    {
        List<String> instanceIds =
            opConfiguration.getOpenProjectConnections().stream().map(OpenProjectConnection::getConnectionName)
                .collect(Collectors.toList());

        LOGGER.debug("Found [{}] configured OpenProject instances.", instanceIds);
        computeStylesheet(instanceIds, context, anonymousFallback);
    }

    /**
     * Inject the styling generated for a given OpenProject instance.
     *
     * @param instance the name of a configured OpenProject instance.
     */
    public void useInstanceStyle(String instance)
    {
        DocumentReference stylesDocRef = documentReferenceResolver.resolve(
            new EntityReference(instance, EntityType.DOCUMENT, OPEN_PROJECT_SSX_EXTENSIONS.getParent()));
        ssx.use(serializer.serialize(stylesDocRef));
    }

    private void computeStylesheet(List<String> openProjCfgNames, XWikiContext context, boolean anonymousFallback)
    {
        for (String openProjCfgName : openProjCfgNames) {
            DocumentReference stylesDocRef = documentReferenceResolver.resolve(
                new EntityReference(openProjCfgName, EntityType.DOCUMENT, OPEN_PROJECT_SSX_EXTENSIONS.getParent()));
            LOGGER.debug("Generating style for instance [{}] at document [{}].", openProjCfgName, stylesDocRef);
            OpenProjectAccessResult access = this.openProjectAccess.resolve(openProjCfgName);
            OpenProjectApiClient apiClient = access.getClient();
            if (apiClient == null && anonymousFallback
                && access.getStatus() == OpenProjectAccessStatus.NOT_CONNECTED)
            {
                LOGGER.debug("The current user isn't connected to [{}]: reading its statuses and types anonymously.",
                    openProjCfgName);
                apiClient = this.openProjectAccess.getAnonymousClient(openProjCfgName);
            }
            if (apiClient == null) {
                LOGGER.debug("Skipping styling update for [{}] since the current user can't call it.",
                    openProjCfgName);
                continue;
            }
            try {
                computeStylesheet(openProjCfgName, apiClient, stylesDocRef, context);
            } catch (WorkItemRetrievalException e) {
                logRetrievalFailure(openProjCfgName, access.getAuthenticatedClient() != null, context, e);
            } catch (XWikiException | ProjectManagementException e) {
                logFailure(openProjCfgName, e);
            }
        }
    }

    private void computeStylesheet(String openProjCfgName, OpenProjectApiClient apiClient,
        DocumentReference stylesDocRef, XWikiContext context) throws XWikiException, ProjectManagementException
    {
        List<Status> statuses = apiClient.getStatuses().getItems();
        LOGGER.debug("Retrieved [{}] statuses.", statuses.size());
        List<Type> types = apiClient.getTypes().getItems();
        LOGGER.debug("Retrieved [{}] types.", types.size());
        String styleSheet = StyleSheetComposer.compose(IdGenerator.generate(openProjCfgName), statuses, types);

        if (styleSheet.isEmpty()) {
            LOGGER.debug("Generated style sheet is empty.");
            return;
        }

        updateStylePage(context, stylesDocRef, styleSheet, userRefResolver);
    }

    private void logRetrievalFailure(String connectionName, boolean authenticated, XWikiContext context,
        WorkItemRetrievalException e)
    {
        Integer statusCode = e.getStatusCode();
        if (statusCode == null || !REFUSED_STATUS_CODES.contains(statusCode)) {
            logFailure(connectionName, e);
        } else if (authenticated) {
            LOGGER.warn(REFUSED_MESSAGE
                + "the user [{}]: their OpenProject account may have been disconnected or lack permissions. Cause: "
                + "[{}].", connectionName, context.getUserReference(), ExceptionUtils.getRootCauseMessage(e));
        } else {
            LOGGER.warn(REFUSED_MESSAGE
                + "anonymous users. Either connect the user [{}] to OpenProject, or allow anonymous users to see them "
                + "in the OpenProject administration (e.g. by disabling \"Login required\" and making a project "
                + "public).", connectionName, context.getUserReference());
        }
    }

    private void logFailure(String connectionName, Exception e)
    {
        LOGGER.warn("Failed to update the styling for the configured OpenProject instance [{}]. Cause: [{}].",
            connectionName, ExceptionUtils.getRootCauseMessage(e));
    }

    private void updateStylePage(XWikiContext context, DocumentReference stylesDocRef, String styleSheet,
        UserReferenceResolver<DocumentReference> userRefResolver)
        throws XWikiException
    {
        // Work on a copy of the cached document.
        XWikiDocument stylesDoc = context.getWiki().getDocument(stylesDocRef, context).clone();
        if (!stylesDoc.isNew() && !this.adminAuthorChecker.isLastSavedByAdmin(stylesDoc)) {
            // The page is saved as the current user below: only keep what an administrator wrote.
            LOGGER.warn("Replacing the styles page [{}] since it wasn't last saved by a user with administration "
                + "right on the wiki.", stylesDocRef);
            context.getWiki().deleteDocument(stylesDoc, context);
            stylesDoc = context.getWiki().getDocument(stylesDocRef, context).clone();
        }
        BaseObject styleObj = stylesDoc.getXObject(STYLESHEET_EXTENSION, STYLESHEET_EXTENSION_PROP_NAME,
            STYLESHEET_EXTENSION_VAL_NAME, false);
        if (styleObj == null) {
            LOGGER.debug("Stylesheet object did not exist before. Creating it.");
            styleObj = stylesDoc.getXObject(STYLESHEET_EXTENSION, true, context);
        }
        String existingCode = styleObj.getLargeStringValue(PROP_CODE);
        if (styleSheet.equals(existingCode) && styleObj.getIntValue(PROP_PARSE) == 0) {
            LOGGER.debug("Existing stylesheet is equal to the generated one.");
            return;
        }
        LOGGER.debug("Setting the metadata users as [{}].", context.getUserReference());
        UserReference currentUser = userRefResolver.resolve(context.getUserReference());
        DocumentAuthors documentAuthors = stylesDoc.getAuthors();
        documentAuthors.setCreator(currentUser);
        documentAuthors.setEffectiveMetadataAuthor(currentUser);
        documentAuthors.setContentAuthor(currentUser);
        documentAuthors.setOriginalMetadataAuthor(currentUser);
        stylesDoc.setHidden(true);
        LOGGER.debug("Setting the stylesheet code to [{}].", styleSheet);
        styleObj.set(PROP_CODE, styleSheet, context);
        // The generated code contains values received from OpenProject: it must not be evaluated.
        styleObj.setIntValue(PROP_PARSE, 0);
        context.getWiki().saveDocument(stylesDoc, "Updated styles.", context);
    }
}
