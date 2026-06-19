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
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
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

    private static final Set<String> UPDATABLE_MACROS = Set.of(OPENPROJECT, "openprojectchart");

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("iterative")
    private MacroBlockFinder macroBlockFinder;

    @Inject
    private MacroUtils macroUtils;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public OpenProjectSpaceCreatingListener()
    {
        super(OpenProjectSpaceCreatingListener.class.getName(), List.of(new DocumentCreatingEvent()));
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
            String instance = getOPInstanceFromAncestorRelation(document, context);
            if (instance == null) {
                return;
            }
            findAndUpdateOPMacros(document, instance);
            // any advantage in not removing? document.removeXObject(spaceMarker);
        } catch (QueryException | XWikiException | JsonProcessingException | MacroExecutionException
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

    private String getOPInstanceFromAncestorRelation(XWikiDocument document, XWikiContext context)
        throws QueryException, XWikiException, JsonProcessingException
    {
        BaseObject relation = document.getXObject(ProjectManagementRelation.CLASS_REFERENCE);
        if (relation == null) {
            List<String> parents = document.getDocumentReference().getParent().getReversedReferenceChain().stream()
                .filter(e -> !(e instanceof WikiReference)).map(serializer::serialize).collect(Collectors.toList());
            // Get the closest ancestor that has a
            List<String> result = queryManager.createQuery(
                "from doc.object('ProjectManagement.Code.RelationClass') as obj "
                    + "where obj.client = :client and doc.space in (:ancestorList) "
                    + "order by length(doc.space) desc",
                Query.XWQL).bindValue("ancestorList", parents).bindValue("client", OPENPROJECT).setLimit(1).execute();

            if (result.isEmpty()) {
                return null;
            }

            DocumentReference documentReference = documentReferenceResolver.resolve(result.get(0));

            XWikiDocument xWikiDocument = context.getWiki().getDocument(documentReference, context);
            relation = xWikiDocument.getXObject(ProjectManagementRelation.CLASS_REFERENCE);
        }

        if (relation == null) {
            return null;
        }
        ProjectManagementRelation relationModel = new ProjectManagementRelation(relation);
        Map<String, String> params = relationModel.getClientParamsMap();
        return params.get(INSTANCE);
    }
}
