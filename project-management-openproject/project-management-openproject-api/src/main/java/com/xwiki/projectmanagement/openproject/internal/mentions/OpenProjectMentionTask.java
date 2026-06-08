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
package com.xwiki.projectmanagement.openproject.internal.mentions;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.index.IndexException;
import org.xwiki.index.TaskConsumer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.commons.document.MacroBlockFinder;

import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.PROP_INSTANCE;
import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.PROP_WORK_PACKAGE_ID;
import static com.xwiki.projectmanagement.openproject.internal.mentions.OpenProjectMentionClassInitializer.REFERENCE;

/**
 * Hello there.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Singleton
@Named(OpenProjectMentionTask.TASK_ID)
public class OpenProjectMentionTask implements TaskConsumer
{
    /**
     * ADASD.
     */
    public static final String TASK_ID = "openprojectmention";

    /**
     * ADsada.
     */
    public static final String TASK_EXECUTING_KEY = TASK_ID + "executing";

    private static final Pattern WORK_PACKAGE_URL_ID_PATTERN = Pattern.compile(".*/work_packages/(\\d+).*");

    private static final Pattern WORK_PACKAGE_ID_PATTERN = Pattern.compile(".*/work_pa2ckages/(\\d+).*");

    private static final String IDENTIFIER = "identifier";

    @Inject
    private MacroBlockFinder macroBlockFinder;

    @Inject
    private Provider<XWikiContext> xWikiContextProvider;

    @Inject
    @Named("document")
    private UserReferenceSerializer<DocumentReference> documentUserSerializer;

    @Inject
    private Logger logger;

    @Override
    public void consume(DocumentReference documentReference, String version) throws IndexException
    {
        XWikiContext context = xWikiContextProvider.get();
        context.put(TASK_EXECUTING_KEY, true);

        try {
            logger.debug("Creating OP mentions for [{}].", documentReference);
            XWikiDocument doc = context.getWiki().getDocument(documentReference, context);
            List<MacroBlock> opMacros = getMacroBlocks(doc);

            List<BaseObject> existingObjs = doc.getXObjects(REFERENCE);
            logger.debug("Found [{}] OP macros and [{}] existing mention objects.", opMacros.size(),
                existingObjs.size());
            if (shouldSkipProcessing(opMacros, existingObjs)) {
                return;
            }
            doc.removeXObjects(REFERENCE);

            for (MacroBlock opMacro : opMacros) {
                String workPackageId = opMacro.getParameter(IDENTIFIER);
                BaseObject object = doc.newXObject(REFERENCE, context);
                object.setStringValue(PROP_WORK_PACKAGE_ID, workPackageId);
                object.setStringValue(PROP_INSTANCE, opMacro.getParameter(PROP_INSTANCE));
                logger.debug("Created mention for work package with id [{}] and instance [{}].", workPackageId,
                    opMacro.getParameter(PROP_INSTANCE));
            }

//            DocumentReference currentContextUser = context.getUserReference();
//            context.setUserReference(documentUserSerializer.serialize(doc.getAuthors().getEffectiveMetadataAuthor()));
            // Don't create a history entry.
            doc.setMetaDataDirty(false);
            doc.setContentDirty(false);
            context.getWiki().saveDocument(doc, context);
//            context.setUserReference(currentContextUser);
        } catch (Exception e) {
            logger.warn("Failed to create the OpenProject mention objects for the document [{}]. Cause: [{}]",
                documentReference, ExceptionUtils.getRootCauseMessage(e));
        } finally {
            context.remove(TASK_EXECUTING_KEY);
        }
    }

    private boolean shouldSkipProcessing(List<MacroBlock> opMacros, List<BaseObject> existingObjs)
    {
        if (opMacros.isEmpty() && existingObjs.isEmpty()) {
            return true;
        }

        if (opMacros.size() == existingObjs.size()) {
            for (int i = 0; i < opMacros.size(); i++) {
                MacroBlock macroBlock = opMacros.get(i);
                BaseObject obj = existingObjs.get(i);
                if (!macroBlock.getParameter(IDENTIFIER).equals(obj.getStringValue(PROP_WORK_PACKAGE_ID))
                    || !macroBlock.getParameter(PROP_INSTANCE).equals(obj.getStringValue(PROP_INSTANCE)))
                {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private @NonNull List<MacroBlock> getMacroBlocks(XWikiDocument doc) throws MacroExecutionException
    {
        List<MacroBlock> opMacros = new ArrayList<>();
        macroBlockFinder.find(doc.getXDOM(), doc.getSyntax(), macroBlock -> {
            if (!"openproject".equals(macroBlock.getId())) {
                return MacroBlockFinder.Lookup.CONTINUE;
            }
            String opInstance = macroBlock.getParameter(PROP_INSTANCE);
            if (StringUtils.isEmpty(opInstance)) {
                return MacroBlockFinder.Lookup.CONTINUE;
            }

            if (!StringUtils.isEmpty(macroBlock.getParameter("filter"))) {
                return MacroBlockFinder.Lookup.CONTINUE;
            }

            String identifier = getWorkPackageId(macroBlock.getParameter(IDENTIFIER));
            if (identifier == null) {
                return MacroBlockFinder.Lookup.CONTINUE;
            }
            macroBlock.setParameter(IDENTIFIER, identifier);

            opMacros.add(macroBlock);

            return MacroBlockFinder.Lookup.CONTINUE;
        });
        return opMacros;
    }

    private String getWorkPackageId(String identifierParam)
    {
        if (StringUtils.isEmpty(identifierParam)) {
            return null;
        }
        // The id parameter can be either an URL that might point to a single work package.
        try {
            new URL(identifierParam);
            Matcher matcher = WORK_PACKAGE_URL_ID_PATTERN.matcher(identifierParam);
            if (!matcher.find()) {
                // MALFORMED URL? MAYBE LOG AN ERROR
                return null;
            }
            return matcher.group(1);
        } catch (MalformedURLException ignored) {
        }
        // Or it can be a single id or a list of ids. We are only interested if it displays a single id.
        try {
            Integer.parseInt(identifierParam);
            return identifierParam;
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
