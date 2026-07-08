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
package com.xwiki.projectmanagement.openproject.internal.listener;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.syntax.Syntax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.commons.document.MacroBlockFinder;
import com.xwiki.commons.document.MacroUtils;
import com.xwiki.projectmanagement.relations.RelationsManager;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;

/**
 * Listener responsible with detecting when a OP space is being created. It will find the closest Relation object and
 * update the macros with the instance value.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Named("com.xwiki.projectmanagement.openproject.internal.listener.OpenProjectSpaceCreatingListener")
@Component
@Singleton
public class OpenProjectSpaceCreatingListener extends AbstractEventListener
{
    private static final LocalDocumentReference SPACE_MARKER_OBJECT =
        new LocalDocumentReference(Arrays.asList("OpenProject", "Code"), "SpacePageMarkerClass");

    private static final String INSTANCE = "instance";

    private static final String OPENPROJECT = "openproject";

    private static final Set<String> UPDATABLE_MACROS = Set.of(OPENPROJECT, "openprojectchart", "openprojectcalendar");

    @Inject
    @Named("iterative")
    private MacroBlockFinder macroBlockFinder;

    @Inject
    private MacroUtils macroUtils;

    @Inject
    private RelationsManager relationsManager;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public OpenProjectSpaceCreatingListener()
    {
        super(OpenProjectSpaceCreatingListener.class.getName(), List.of(new DocumentCreatedEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiContext context = (XWikiContext) data;
        XWikiDocument document = (XWikiDocument) source;

        BaseObject spaceMarker = document.getXObject(SPACE_MARKER_OBJECT);
        if (spaceMarker == null) {
            return;
        }
        try {
            ProjectManagementRelation relation =
                relationsManager.getClientRelation(document.getDocumentReference(), OPENPROJECT, true);
            String instance = relation.getClientParamsMap().get(INSTANCE);
            if (instance == null) {
                return;
            }
            findAndUpdateOPMacros(document, instance);
            document.removeXObject(spaceMarker);
            document.setMetaDataDirty(false);
            document.setContentDirty(false);
            context.getWiki().saveDocument(document, context);
        } catch (XWikiException | JsonProcessingException | MacroExecutionException
                 | ComponentLookupException e) {
            logger.error("Failed to set the INSTANCE parameter of the OpenProject macros in [{}].",
                document.getDocumentReference(), e);
        }
    }

    private void findAndUpdateOPMacros(XWikiDocument document, String instance)
        throws MacroExecutionException, ComponentLookupException, XWikiException
    {
        Stack<MacroBlock> modifiedMacros = new Stack<>();
        AtomicBoolean macroUpdated = new AtomicBoolean(false);
        XDOM documentXDOM = document.getXDOM();
        macroBlockFinder.find(documentXDOM, document.getSyntax(), (macroBlock -> {
            modifiedMacros.push(macroBlock);
            if (!UPDATABLE_MACROS.contains(macroBlock.getId())) {
                return MacroBlockFinder.Lookup.CONTINUE;
            }
            macroBlock.setParameter(INSTANCE, instance);
            macroUpdated.set(true);
            return MacroBlockFinder.Lookup.CONTINUE;
        }));
        if (!macroUpdated.get()) {
            return;
        }
        while (!modifiedMacros.isEmpty()) {
            MacroBlock macroBlock = modifiedMacros.pop();
            if (macroBlock.getChildren() != null && !macroBlock.getChildren().isEmpty()) {
                macroUtils.updateMacroContent(macroBlock,
                    macroUtils.renderMacroContent(macroBlock.getChildren(), Syntax.XWIKI_2_1));
            }
        }
        document.setContent(documentXDOM);
    }
}
