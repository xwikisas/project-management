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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
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
import com.xwiki.projectmanagement.openproject.model.MacroInsertion;
import com.xwiki.projectmanagement.openproject.rest.OpenProjectMacroInsertResource;

/**
 * Default implementation of {@link OpenProjectMacroInsertResource}. Splits the document in lines, renders each as
 * plain text, looks for matching words, calculates some scores for each match, inserts the macro at the beginning of
 * the best line.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.DefaultOpenProjectMacroInsertResource")
@Singleton
public class DefaultOpenProjectMacroInsertResource extends XWikiResource implements OpenProjectMacroInsertResource
{
    class SelectionSearch
    {
        private List<FoundLine> foundLines;

        private List<String> splitText;

        SelectionSearch(List<FoundLine> foundLines, List<String> splitText)
        {
            this.foundLines = foundLines;
            this.splitText = splitText;
        }

        public List<FoundLine> getFoundLines()
        {
            return foundLines;
        }

        public void setFoundLines(
            List<FoundLine> foundLines)
        {
            this.foundLines = foundLines;
        }

        public List<String> getSplitText()
        {
            return splitText;
        }

        public void setSplitText(List<String> splitText)
        {
            this.splitText = splitText;
        }
    }

    class Candidate
    {
        private final FoundLine foundLine;

        private final int score;

        Candidate(FoundLine foundLine, int score)
        {
            this.foundLine = foundLine;
            this.score = score;
        }

        public int getScore()
        {
            return score;
        }

        public FoundLine getFoundLine()
        {
            return foundLine;
        }
    }

    class FoundLine
    {
        private String plainText;

        private int position;

        private int lineIndex;

        private int fullTextOffset;

        FoundLine(String plainText, int position, int lineIndex, int fullTextOffset)
        {

            this.plainText = plainText;
            this.position = position;
            this.lineIndex = lineIndex;
            this.fullTextOffset = fullTextOffset;
        }

        public String getPlainText()
        {
            return plainText;
        }

        public void setPlainText(String plainText)
        {
            this.plainText = plainText;
        }

        public int getPosition()
        {
            return position;
        }

        public void setPosition(int position)
        {
            this.position = position;
        }

        public int getLineIndex()
        {
            return lineIndex;
        }

        public void setLineIndex(int lineIndex)
        {
            this.lineIndex = lineIndex;
        }

        public int getFullTextOffset()
        {
            return fullTextOffset;
        }

        public void setFullTextOffset(int fullTextOffset)
        {
            this.fullTextOffset = fullTextOffset;
        }
    }

    private static final String LINE_BREAK = "\n";

    @Inject
    private ContentParser contentParser;

    @Inject
    private MacroUtils macroUtils;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    private final ConcurrentMap<DocumentReference, ReentrantLock> documentLocks =
        new ConcurrentHashMap<>();

    @Override
    public Response insetIntoPage(String wikiName, String spaces, String pageName, MacroInsertion macroInsertion)
    {
        XWikiContext context = getXWikiContext();
        try {
            DocumentReference documentReference = new DocumentReference(wikiName, parseSpaceSegments(spaces), pageName);
            if (!authorizationManager.hasAccess(Right.EDIT, documentReference)) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            XWikiDocument document = context.getWiki().getDocument(documentReference, context);

            maybeEarlyExit(document, macroInsertion);

            ReentrantLock lock = documentLocks.computeIfAbsent(documentReference, id -> new ReentrantLock());
            lock.lock();
            try {
                // Return is doc syntax is not xwiki syntax.
                String docContent = document.getContent();
                SelectionSearch selectionSearch = findSelectionLine(macroInsertion, docContent);
                if (selectionSearch.foundLines.isEmpty()) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                // Parse the with xwiki syntax the line.
                // Replace the line.
                String newContent = insertAndGetContent(selectionSearch, macroInsertion);
                if (!document.getContent().equals(newContent)) {
                    document.setContent(newContent);
                    context.getWiki().saveDocument(document, "Inserted OpenProject mention.", true, context);
                }
            } finally {
                lock.unlock();
                if (!lock.hasQueuedThreads()) {
                    documentLocks.remove(documentReference, lock);
                }
            }
        } catch (XWikiRestException | XWikiException | MissingParserException | ParseException
                 | ComponentLookupException e) {
            throw new RuntimeException(e);
        }
        return Response.ok().build();
    }

    private SelectionSearch findSelectionLine(MacroInsertion selection, String docContent)
    {
        List<FoundLine> foundLines = new ArrayList<>();
        List<String> searchLines = List.of(docContent.split("\\n"));

        // Try to find with REGEX line that contains the selection.

        int currentTextOffset = 0;
        for (int i = 0; i < searchLines.size(); i++) {
            String line = searchLines.get(i);
            String plainTextLine = maybeGetPlainLine(line);
            int position = plainTextLine.indexOf(selection.getSelectedText());
            if (position >= 0) {
                foundLines.add(new FoundLine(plainTextLine, position, i, currentTextOffset + position));
            }
            currentTextOffset += plainTextLine.length();
        }
        return new SelectionSearch(foundLines, searchLines);
    }

    private String insertAndGetContent(SelectionSearch selectionSearch, MacroInsertion macroInsertion)
        throws ParseException, MissingParserException, ComponentLookupException
    {
        // Compute the best line.
        FoundLine bestLine =
            calculateScores(selectionSearch, macroInsertion)
                .stream()
                .max(Comparator.comparingInt(Candidate::getScore))
                .orElseThrow().foundLine;

        String newLine = getNewLine(selectionSearch, macroInsertion, bestLine);
        // Reconstruct the document content.
        return getNewContent(selectionSearch, bestLine, newLine);
    }

    private String getNewLine(SelectionSearch selectionSearch, MacroInsertion macroInsertion, FoundLine bestLine)
        throws ParseException, MissingParserException, ComponentLookupException
    {
        // Append the OP macro in the beginning of the line
        XDOM xdom = contentParser.parse(selectionSearch.splitText.get(bestLine.lineIndex), Syntax.XWIKI_2_1);
        Block workBlock = xdom.getFirstBlock(new ClassBlockMatcher(WordBlock.class), Block.Axes.DESCENDANT_OR_SELF);
        List<Block> listToPrepend = xdom.getChildren();
        if (workBlock != null && workBlock.getParent() != null) {
            listToPrepend = workBlock.getParent().getChildren();
        }
        Block opBLock = new MacroBlock(macroInsertion.getMacroId(), macroInsertion.getMacroParameters(), true);
        listToPrepend.add(0, opBLock);
        // Render the block in xwiki syntax again.
        String newLine = macroUtils.renderMacroContent(xdom.getChildren(), Syntax.XWIKI_2_1);
        return newLine;
    }

    private static String getNewContent(SelectionSearch selectionSearch, FoundLine bestLine, String newLine)
    {
        StringBuilder newContent = new StringBuilder();
        if (bestLine.lineIndex == 0) {
            newContent.append(newLine);
            newContent.append(LINE_BREAK);
            newContent.append(
                String.join(LINE_BREAK, selectionSearch.splitText.subList(1, selectionSearch.splitText.size())));
        } else if (bestLine.lineIndex == selectionSearch.splitText.size() - 1) {
            newContent.append(
                String.join(LINE_BREAK, selectionSearch.splitText.subList(0, selectionSearch.splitText.size() - 1)));
            newContent.append(LINE_BREAK);
            newContent.append(newLine);
        } else {
            newContent.append(
                String.join(LINE_BREAK, selectionSearch.splitText.subList(0, bestLine.lineIndex)));
            newContent.append(LINE_BREAK);
            newContent.append(newLine);
            newContent.append(LINE_BREAK);
            newContent.append(String.join(LINE_BREAK,
                selectionSearch.splitText.subList(bestLine.lineIndex + 1, selectionSearch.splitText.size())));
        }
        return newContent.toString();
    }

    private void maybeEarlyExit(XWikiDocument document, MacroInsertion macroInsertion) throws XWikiException
    {
        if (!Syntax.XWIKI_2_1.equals(document.getSyntax())) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("Document syntax is not xwiki/2.1.").build());
        }

        XWikiLock lock = document.getLock(getXWikiContext());
        if (!macroInsertion.isForce() && lock != null && !getXWikiContext().getUser().equals(lock.getUserName())) {
            throw new WebApplicationException(Response.status(423).build());
        }

        if (document.isNew()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        if (StringUtils.isEmpty(macroInsertion.getMacroId())) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }
    }

    private List<Candidate> calculateScores(SelectionSearch selectionSearch, MacroInsertion macroInsertion)
    {
        List<Candidate> candidates = new ArrayList<>();
        for (FoundLine foundLine : selectionSearch.foundLines) {
            int score = 0;

            // left
            int htmlPos = macroInsertion.getFullLine().indexOf(macroInsertion.getSelectedText());
            int i = 1;
            while (htmlPos - i >= 0 && foundLine.position - i >= 0) {

                if (macroInsertion.getFullLine().charAt(htmlPos - i)
                    != foundLine.plainText.charAt(foundLine.position - i))
                {
                    break;
                }

                score++;
                i++;
            }

            // right
            i = 0;
            int selectionLength = macroInsertion.getSelectedText().length();
            while (htmlPos + selectionLength + i < macroInsertion.getFullLine().length()
                && foundLine.position + selectionLength + i < foundLine.plainText.length()) {

                if (macroInsertion.getFullLine().charAt(htmlPos + selectionLength + i)
                    != foundLine.plainText.charAt(foundLine.position + selectionLength + i))
                {
                    break;
                }

                score++;
                i++;
            }

            // Distance from original offset
            score -= Math.abs(foundLine.fullTextOffset - macroInsertion.getOffset()) / 10;

            candidates.add(new Candidate(foundLine, score));
        }
        return candidates;
    }

    private String maybeGetPlainLine(String line)
    {
        try {
            XDOM xdom = contentParser.parse(line, Syntax.XWIKI_2_1);
            return macroUtils.renderMacroContent(xdom.getChildren(), Syntax.PLAIN_1_0);
        } catch (Exception e) {
            return line;
        }
    }
}
