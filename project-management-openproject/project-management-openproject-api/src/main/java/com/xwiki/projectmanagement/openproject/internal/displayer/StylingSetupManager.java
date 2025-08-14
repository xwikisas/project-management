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

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
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
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.DefaultOpenProjectApiClient;
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

    private static final EntityReference OPEN_PROJECT_SSX_EXTENSIONS =
        new LocalDocumentReference(Arrays.asList("OpenProject", "Code", "StyleSheets"), "WebHome");

    private static final EntityReference STYLESHEET_EXTENSION = new LocalDocumentReference(SPACE_XWIKI,
        "StyleSheetExtension");

    private static final String REGEX_SPACES = "\\s*";

    private static final String BRACKET_START = " {\n";

    private static final String BRACKET_END = "\n}\n";

    private static final Logger LOGGER = LoggerFactory.getLogger(StylingSetupManager.class);

    @Inject
    private OpenProjectConfiguration opConfiguration;

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

    /**
     * Generate the styling for the configured OpenProject instances.
     */
    public void setupInstanceStyles()
    {
        XWikiContext context = contextProvider.get();
        List<String> instanceIds = null;
        instanceIds =
            opConfiguration.getOpenProjectConnections().stream().map(OpenProjectConnection::getConnectionName)
                .collect(Collectors.toList());

        LOGGER.debug("Found [{}] configured OpenProject instances.", instanceIds);
        computeStylesheet(instanceIds, context);
    }

    /**
     * Inject the styling generated for a given Open Project instance.
     *
     * @param instance the name of a configured Open Project instance.
     */
    public void useInstanceStyle(String instance)
    {
        DocumentReference stylesDocRef = documentReferenceResolver.resolve(
            new EntityReference(instance, EntityType.DOCUMENT, OPEN_PROJECT_SSX_EXTENSIONS.getParent()));
        ssx.use(serializer.serialize(stylesDocRef));
    }

    private void computeStylesheet(List<String> openProjCfgNames, XWikiContext context)
    {
        for (String openProjCfgName : openProjCfgNames) {
            DocumentReference stylesDocRef = documentReferenceResolver.resolve(
                new EntityReference(openProjCfgName, EntityType.DOCUMENT, OPEN_PROJECT_SSX_EXTENSIONS.getParent()));
            LOGGER.debug("Generating style for instance [{}] at document [{}].", openProjCfgName, stylesDocRef);
            try {
                OpenProjectConnection connection = opConfiguration.getConnection(openProjCfgName);
                String accessToken = opConfiguration.getAccessTokenForConfiguration(openProjCfgName);
                if (connection == null || StringUtils.isEmpty(accessToken)) {
                    LOGGER.warn("Skipping styling update for [{}] due to missing configuration or access token.",
                        openProjCfgName);
                    continue;
                }
                OpenProjectApiClient apiClient =
                    new DefaultOpenProjectApiClient(connection.getServerURL(), accessToken, HttpClient.newHttpClient());

                StringBuilder stringBuilder = new StringBuilder();

                composeStatusStyles(openProjCfgName, apiClient, stringBuilder);

                composeTypeStyles(openProjCfgName, apiClient, stringBuilder);

                if (stringBuilder.toString().isEmpty()) {
                    LOGGER.debug("Generated style sheet is empty.");
                    continue;
                }

                updateStylePage(context, stylesDocRef, stringBuilder, userRefResolver);
            } catch (XWikiException | ProjectManagementException e) {
                LOGGER.warn("Failed to update the styling for the configured open project instance [{}]. Cause: [{}].",
                    openProjCfgName, ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }

    private void composeTypeStyles(String openProjCfgName, OpenProjectApiClient apiClient, StringBuilder stringBuilder)
        throws ProjectManagementException
    {
        List<Type> types = apiClient.getTypes().getItems();
        LOGGER.debug("Retrieved [{}] types.", types.size());
        for (Type type : types) {
            if (type.getColor() == null || type.getColor().isEmpty()) {
                continue;
            }
            stringBuilder.append(".openproject-property-type-");
            stringBuilder.append(type.getName().toLowerCase().replaceAll(REGEX_SPACES, ""));
            stringBuilder.append('-');
            stringBuilder.append(openProjCfgName);
            stringBuilder.append(BRACKET_START);
            stringBuilder.append("\tcolor: ");
            stringBuilder.append(type.getColor());
            stringBuilder.append(';');
            stringBuilder.append(BRACKET_END);
        }
    }

    private void composeStatusStyles(String openProjCfgName, OpenProjectApiClient apiClient,
        StringBuilder stringBuilder) throws ProjectManagementException
    {
        List<Status> statuses = apiClient.getStatuses().getItems();
        LOGGER.debug("Retrieved [{}] statuses.", statuses.size());
        for (Status status : statuses) {
            if (status.getColor() == null || status.getColor().isEmpty()) {
                continue;
            }
            stringBuilder.append(".openproject-property-status-");
            stringBuilder.append(status.getName().toLowerCase().replaceAll(REGEX_SPACES, ""));
            stringBuilder.append('-');
            stringBuilder.append(openProjCfgName);
            stringBuilder.append("::before");
            stringBuilder.append(BRACKET_START);
            stringBuilder.append("\tbackground: ");
            stringBuilder.append(status.getColor());
            stringBuilder.append(";");
            stringBuilder.append("\tborder: 1px solid ");
            stringBuilder.append("color-mix(in srgb, ");
            stringBuilder.append(status.getColor());
            stringBuilder.append(" 85%, black 15%);");
            stringBuilder.append(BRACKET_END);
        }
    }

    private void updateStylePage(XWikiContext context, DocumentReference stylesDocRef, StringBuilder stringBuilder,
        UserReferenceResolver<DocumentReference> userRefResolver)
        throws XWikiException
    {
        XWikiDocument stylesDoc = context.getWiki().getDocument(stylesDocRef, context);
        BaseObject styleObj = stylesDoc.getXObject(STYLESHEET_EXTENSION, STYLESHEET_EXTENSION_PROP_NAME,
            STYLESHEET_EXTENSION_VAL_NAME, false);
        if (styleObj == null) {
            LOGGER.debug("Stylesheet object did not exist before. Creating it.");
            styleObj = stylesDoc.getXObject(STYLESHEET_EXTENSION, true, context);
        }
        String existingCode = styleObj.getLargeStringValue(PROP_CODE);
        if (stringBuilder.toString().equals(existingCode)) {
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
        LOGGER.debug("Setting the stylesheet code to [{}].", stringBuilder.toString());
        styleObj.set(PROP_CODE, stringBuilder.toString(), context);
        context.getWiki().saveDocument(stylesDoc, "Updated styles.", context);
    }
}
