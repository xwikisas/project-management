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
package com.xwiki.projectmanagement.openproject;

import java.util.List;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.rendering.configuration.ExtendedRenderingConfiguration;
import org.xwiki.rendering.internal.macro.script.PermissionCheckerListener;
import org.xwiki.rendering.internal.util.XWikiSyntaxEscaper;
import org.xwiki.rendering.internal.macro.velocity.VelocityMacroPermissionPolicy;
import org.xwiki.rendering.macro.MacroCategoryManager;
import org.xwiki.rendering.script.RenderingScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.Right;
import org.xwiki.template.script.TemplateScriptService;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code OpenProject.Code.SpacePageMarkerSheet}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList({
    // Check the script right of the author of the script macros.
    PermissionCheckerListener.class,
    VelocityMacroPermissionPolicy.class,
    // Script services used by the sheet.
    ModelScriptService.class,
    RenderingScriptService.class,
    XWikiSyntaxEscaper.class,
    TemplateScriptService.class
})
class SpacePageMarkerSheetPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final List<String> CODE_SPACE = List.of("OpenProject", "Code");

    private static final DocumentReference USER_WITHOUT_SCRIPT_RIGHT = new DocumentReference(WIKI, "XWiki", "User");

    private static final DocumentReference PAGE = new DocumentReference(WIKI, "Space", "WebHome");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @MockComponent
    private ExtendedRenderingConfiguration renderingConfiguration;

    @MockComponent
    private MacroCategoryManager macroCategoryManager;

    @BeforeEach
    void setUp() throws Exception
    {
        loadPage(new DocumentReference(WIKI, CODE_SPACE, "SpacePageMarkerSheet"));
        loadPage(new DocumentReference(WIKI, CODE_SPACE, "SpacePageMarkerClass"));

        // Only the user above lacks script right.
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(eq(Right.SCRIPT), any()))
            .thenAnswer(invocation -> !USER_WITHOUT_SCRIPT_RIGHT.equals(this.context.getAuthorReference()));
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(eq(Right.SCRIPT)))
            .thenAnswer(invocation -> !USER_WITHOUT_SCRIPT_RIGHT.equals(this.context.getAuthorReference()));
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(eq(Right.VIEW), any())).thenReturn(true);
    }

    @Test
    void displayContentWithTheRightsOfItsAuthor() throws Exception
    {
        String result = displayPageWrittenBy(USER_WITHOUT_SCRIPT_RIGHT);

        assertTrue(result.startsWith("before"), result);
        assertFalse(result.contains("scripted"), result);
    }

    @Test
    void displayContentWrittenByAUserWithScriptRight() throws Exception
    {
        assertEquals("before scripted after",
            displayPageWrittenBy(new DocumentReference(WIKI, "XWiki", "ScriptUser")));
    }

    @Test
    void displayTheRelationOfANewSpace() throws Exception
    {
        String client = "client\"{{/html}}<b class=\"injected\">client</b>";
        XWikiDocument page = new XWikiDocument(PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.newXObject(new DocumentReference(WIKI, CODE_SPACE, "SpacePageMarkerClass"), this.context)
            .setStringValue("marker", "root");
        page.newXObject(new DocumentReference(WIKI, List.of("ProjectManagement", "Code"), "RelationClass"),
            this.context).setStringValue("client", client);

        this.context.setAction("view");
        this.context.setDoc(page);
        Document document = renderHTMLPage(page);

        assertTrue(document.select(".injected").isEmpty(), document.html());
        assertTrue(document.select(".proj-manag-relation").size() == 1, document.html());
        assertEquals(client, document.selectFirst(".proj-manag-relation").attr("data-client"));
        // The UI extensions are not available in this test.
        assertTrue(this.logCapture.getMessage(0).startsWith("Left side ($extensions.size())"));
    }

    private String displayPageWrittenBy(DocumentReference author) throws Exception
    {
        XWikiDocument page = new XWikiDocument(PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent("before {{velocity}}scripted{{/velocity}} after");
        page.setContentAuthorReference(author);
        page.setAuthorReference(author);
        page.newXObject(new DocumentReference(WIKI, CODE_SPACE, "SpacePageMarkerClass"), this.context)
            .setStringValue("marker", "root");
        this.xwiki.saveDocument(page, this.context);

        this.context.setAction("view");
        this.context.setDoc(page);
        return renderHTMLPage(page).text();
    }
}
