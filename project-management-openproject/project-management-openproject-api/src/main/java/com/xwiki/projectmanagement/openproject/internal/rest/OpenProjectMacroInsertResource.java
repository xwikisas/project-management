package com.xwiki.projectmanagement.openproject.internal.rest;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

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

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;

public class OpenProjectMacroInsertResource extends XWikiResource
{
    @Inject
    private ContentParser contentParser;

    public Response insetIntoPage(String wiki, String space, String page, String selection)
    {
        XWikiContext context = getXWikiContext();
        try {
            DocumentReference documentReference = new DocumentReference(wiki, parseSpaceSegments(space), page);
            XWikiDocument document = context.getWiki().getDocument(documentReference, context);

            if (document.isNew()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            // Return is doc syntax is not xwiki syntax.

            String docContent = document.getContent();
            int i = 0;
            String selectionLine = "";
            // Try to find with REGEX line that contains the selection.
            for (String line : docContent.split("\\R")) {
                i++;
                if (line.contains(selection)) {
                    selectionLine = line;
                    break;
                }
            }
            if (selectionLine.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            // Parse the with xwiki syntax the line.
            XDOM xdom = contentParser.parse(selectionLine, Syntax.XWIKI_2_1);
            // Append the OP macro in the beginning of the line
            List<Block> listToPrepend = xdom.getChildren();
            Block opBLock = new MacroBlock("openproject",
                Map.of("instance", "smth", "identifier", "1", "workItemsDisplayer",
                    WorkItemsDisplayer.workItemInline.toString()), true);
            if (listToPrepend.size() == 1) {
                listToPrepend = listToPrepend.get(0).
                prependPoint = xdom.getChildren().get(0);
            } else {
                prependPoint = xdom.pa
            }
            // Render the block in xwiki syntax again.

            // Replace the line.
        } catch (XWikiRestException e) {
            throw new RuntimeException(e);
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        } catch (MissingParserException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
