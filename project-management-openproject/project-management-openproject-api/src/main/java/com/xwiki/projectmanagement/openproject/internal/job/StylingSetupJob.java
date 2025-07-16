package com.xwiki.projectmanagement.openproject.internal.job;

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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.model.EntityType;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.plugin.scheduler.AbstractJob;
import com.xpn.xwiki.web.Utils;
import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.openproject.apiclient.internal.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.openproject.model.Type;

/**
 * Using the {@link OpenProjectApiClient} retrieve the colors for the OpenProject properties that support customization
 * and generate a style sheet for each configured OpenProject instance.
 *
 * @version $Id$
 */
public class StylingSetupJob extends AbstractJob
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

    private static final Logger LOGGER = LoggerFactory.getLogger(StylingSetupJob.class);

    @Override
    protected void executeJob(JobExecutionContext jobContext)
    {
        OpenProjectConfiguration opConfiguration = Utils.getComponent(OpenProjectConfiguration.class);
        DocumentReferenceResolver<EntityReference> documentReferenceResolver =
            Utils.getComponent(new DefaultParameterizedType(null, DocumentReferenceResolver.class,
                EntityReference.class));
        UserReferenceResolver<DocumentReference> userRefResolver =
            Utils.getComponent(new DefaultParameterizedType(null, UserReferenceResolver.class, DocumentReference.class),
                "document");
        XWikiContext context = getXWikiContext();
        List<String> instanceIds = null;
        try {
            instanceIds =
                opConfiguration.getOpenProjectConnections().stream().map(OpenProjectConnection::getConnectionName)
                    .collect(Collectors.toList());
        } catch (AuthenticationException e) {
            LOGGER.warn("Failed to retrieve the configured instance ids. Cause: [{}].",
                ExceptionUtils.getRootCauseMessage(e));
            return;
        }
        LOGGER.debug("Found [{}] configured OpenProject instances.", instanceIds);
        computeStylesheet(instanceIds, context, opConfiguration, documentReferenceResolver, userRefResolver);
    }

    private void computeStylesheet(List<String> openProjCfgNames, XWikiContext context,
        OpenProjectConfiguration opConfiguration, DocumentReferenceResolver<EntityReference> documentReferenceResolver,
        UserReferenceResolver<DocumentReference> userRefResolver)
    {
        for (String openProjCfgName : openProjCfgNames) {
            DocumentReference stylesDocRef = documentReferenceResolver.resolve(
                new EntityReference(openProjCfgName, EntityType.DOCUMENT, OPEN_PROJECT_SSX_EXTENSIONS.getParent()));
            LOGGER.debug("Generating style for instance [{}] at document [{}].", openProjCfgName, stylesDocRef);
            try {
                OpenProjectApiClient apiClient = opConfiguration.getOpenProjectApiClient(openProjCfgName);

                StringBuilder stringBuilder = new StringBuilder();

                composeStatusStyles(openProjCfgName, apiClient, stringBuilder);

                composeTypeStyles(openProjCfgName, apiClient, stringBuilder);

                if (stringBuilder.toString().isEmpty()) {
                    LOGGER.debug("Generated style sheet is empty.");
                    continue;
                }

                updateStylePage(context, stylesDocRef, stringBuilder, userRefResolver);
            } catch (AuthenticationException | XWikiException e) {
                LOGGER.warn("Failed to update the styling for the configured open project instance [{}]. Cause: [{}].",
                    openProjCfgName, ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }

    private void composeTypeStyles(String openProjCfgName, OpenProjectApiClient apiClient, StringBuilder stringBuilder)
    {
        List<Type> types = apiClient.getTypes();
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
        StringBuilder stringBuilder) throws AuthenticationException
    {
        List<Status> statuses = apiClient.getStatuses();
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
