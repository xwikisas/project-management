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

package com.xwiki.projectmanagement.openproject.internal.rest;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.parser.MissingParserException;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.doc.XWikiLock;
import com.xwiki.commons.document.MacroUtils;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;

/**
 * Asd.
 *
 * @version $Id$
 * @since 1.2.0
 */
public class OpenProjectMacroInsertResource extends XWikiResource
{
    private static final String LINE_BREAK = "\n";

    @Inject
    private ContentParser contentParser;

    @Inject
    private MacroUtils macroUtils;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    /**
     * @param wiki wiki.
     * @param space space.
     * @param page page.
     * @param selection selection.
     * @return smth.
     */
    public Response insetIntoPage(String wiki, String space, String page, String selection)
    {
        XWikiContext context = getXWikiContext();
        try {
            DocumentReference documentReference = new DocumentReference(wiki, parseSpaceSegments(space), page);
            if (!authorizationManager.hasAccess(Right.EDIT, documentReference)) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            XWikiDocument document = context.getWiki().getDocument(documentReference, context);

            XWikiLock lock = document.getLock(getXWikiContext());
            if (lock != null && !getXWikiContext().getUser().equals(lock.getUserName())) {
                return Response.status(423).build();
            }

            if (document.isNew()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            // Return is doc syntax is not xwiki syntax.
            StringBuilder before = new StringBuilder();
            StringBuilder after = new StringBuilder();
            String docContent = document.getContent();
            String selectionLine = findSelectionLine(selection, docContent, before, after);
            if (selectionLine.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            // Parse the with xwiki syntax the line.
            String newLine = updateLine(selectionLine);
            // Replace the line.
            String newContent = before.append(newLine).append(LINE_BREAK).append(after).toString();
            if (!document.getContent().equals(newContent)) {
                document.setContent(newContent);
                context.getWiki().saveDocument(document, "Inserted OpenProject mention.", context);
            }
        } catch (XWikiRestException | XWikiException | MissingParserException | ParseException
                 | ComponentLookupException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private String updateLine(String selectionLine)
        throws ParseException, MissingParserException, ComponentLookupException
    {
        XDOM xdom = contentParser.parse(selectionLine, Syntax.XWIKI_2_1);
        // Append the OP macro in the beginning of the line
        List<Block> listToPrepend = xdom.getChildren();
        Block opBLock = new MacroBlock("openproject",
            Map.of("instance", "smth", "identifier", "1", "workItemsDisplayer",
                WorkItemsDisplayer.workItemInline.toString()), true);
        if (listToPrepend.size() == 1) {
            listToPrepend = listToPrepend.get(0).getChildren();
        }
        if (listToPrepend.isEmpty()) {
            listToPrepend.add(opBLock);
        } else {
            listToPrepend.add(0, opBLock);
        }
        // Render the block in xwiki syntax again.
        String newLine = macroUtils.renderMacroContent(xdom.getChildren(), Syntax.XWIKI_2_1);
        return newLine;
    }

    private static @NonNull String findSelectionLine(String selection, String docContent, StringBuilder before,
        StringBuilder after)
    {
        int i = 0;
        String selectionLine = "";
        // Try to find with REGEX line that contains the selection.
        for (String line : docContent.split("\\n")) {
            i++;
            if (line.contains(selection)) {
                selectionLine = line;
                continue;
            }
            if (selectionLine.isEmpty()) {
                before.append(line);
                before.append(LINE_BREAK);
            } else {
                after.append(line);
                after.append(LINE_BREAK);
            }
        }
        return selectionLine;
    }
}
