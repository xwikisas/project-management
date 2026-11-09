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
package com.xwiki.projectmanagement.relations;

import java.io.StringReader;
import java.util.List;
import java.util.Properties;

import javax.inject.Named;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.configuration.ExtendedRenderingConfiguration;
import org.xwiki.rendering.internal.util.XWikiSyntaxEscaper;
import org.xwiki.rendering.macro.MacroCategoryManager;
import org.xwiki.rendering.script.RenderingScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;
import org.xwiki.uiextension.UIExtension;
import org.xwiki.uiextension.script.UIExtensionScriptService;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.relations.script.ProjectManagementRelationsScriptService;
import com.xwiki.projectmanagement.relations.store.ProjectManagementRelation;
import com.xwiki.projectmanagement.script.ProjectManagementScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Page test for the UI extension of {@code ProjectManagement.Code.RelationDocExtra}, which displays the relations of
 * a page.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList({
    ModelScriptService.class,
    ProjectManagementScriptService.class,
    RenderingScriptService.class,
    XWikiSyntaxEscaper.class
})
class RelationDocExtraPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final LocalDocumentReference UIX_CLASS = new LocalDocumentReference("XWiki", "UIExtensionClass");

    private static final String CLIENT = "client\"{{/html}}<b class=\"injected\">client</b>";

    @MockComponent(classToMock = ProjectManagementRelationsScriptService.class)
    @Named("projectmanagement.relations")
    private ScriptService relationsScriptService;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ExtendedRenderingConfiguration renderingConfiguration;

    @MockComponent
    private MacroCategoryManager macroCategoryManager;

    @MockComponent
    private ProjectManagementClientExecutionContext clientExecutionContext;

    @Test
    void relationValuesAreEscaped() throws Exception
    {
        // A relation inherited from a page with special characters in its name.
        DocumentReference ownerReference = new DocumentReference(WIKI, "Space", "<i class=\"injected\">owner</i>");
        BaseObject relationObject = new BaseObject();
        relationObject.setDocumentReference(ownerReference);
        relationObject.setXClassReference(new LocalDocumentReference(List.of("ProjectManagement", "Code"),
            "RelationClass"));
        relationObject.setStringValue("client", CLIENT);
        relationObject.setLargeStringValue("clientParams", "{}");
        XWikiDocument page = new XWikiDocument(new DocumentReference(WIKI, List.of("Space", "Child"), "WebHome"));
        when(((ProjectManagementRelationsScriptService) this.relationsScriptService)
            .getRelations(page.getDocumentReference(), true))
            .thenReturn(List.of(new ProjectManagementRelation(relationObject)));

        Document document = Jsoup.parse(renderDocExtra(page));

        assertTrue(document.select(".injected").isEmpty(), document.html());
        assertEquals(CLIENT, document.selectFirst(".proj-manag-relation").attr("data-client"));
        assertEquals("Space.<i class=\"injected\">owner</i>", document.selectFirst(".infomessage a").text());
    }

    @Test
    void relationExtensionsDontChangeTheHTMLMacro() throws Exception
    {
        // The rendered extension starts an HTML macro, which would make the HTML macro it is inserted in end later.
        UIExtensionScriptService uixScriptService = mock(UIExtensionScriptService.class);
        UIExtension extension = mock(UIExtension.class);
        when(extension.execute()).thenReturn(new RawBlock("{{html}}<p class=\"extension\">left</p>",
            Syntax.HTML_5_0));
        when(uixScriptService.getExtensions("com.xwiki.projectmanagement.client.relations.left"))
            .thenReturn(List.of(extension));
        this.componentManager.registerComponent(ScriptService.class, "uix", uixScriptService);
        XWikiDocument page = new XWikiDocument(new DocumentReference(WIKI, List.of("Space", "Child"), "WebHome"));
        BaseObject relationObject = new BaseObject();
        relationObject.setDocumentReference(new DocumentReference(WIKI, "Space", "WebHome"));
        relationObject.setXClassReference(new LocalDocumentReference(List.of("ProjectManagement", "Code"),
            "RelationClass"));
        relationObject.setStringValue("client", "client");
        relationObject.setLargeStringValue("clientParams", "{}");
        when(((ProjectManagementRelationsScriptService) this.relationsScriptService)
            .getRelations(page.getDocumentReference(), true))
            .thenReturn(List.of(new ProjectManagementRelation(relationObject)));

        Document document = Jsoup.parse(renderDocExtra(page));

        assertEquals("left", document.selectFirst(".proj-manag-relation .extension").text(), document.html());
        assertTrue(document.selectFirst(".proj-manag-relation").text().contains("{{html}}"), document.html());
        assertFalse(document.body().text().contains("{{/html}}"), document.html());
    }

    @Test
    void tabShowsTheRelationsCount() throws Exception
    {
        // The extension parameters are evaluated without programming rights, each one on its own, in any order.
        XWikiDocument page = new XWikiDocument(new DocumentReference(WIKI, List.of("Space", "Child"), "WebHome"));
        BaseObject relationObject = new BaseObject();
        relationObject.setDocumentReference(page.getDocumentReference());
        relationObject.setXClassReference(new LocalDocumentReference(List.of("ProjectManagement", "Code"),
            "RelationClass"));
        relationObject.setStringValue("client", "client");
        relationObject.setLargeStringValue("clientParams", "{}");
        when(((ProjectManagementRelationsScriptService) this.relationsScriptService)
            .getRelations(page.getDocumentReference(), true))
            .thenReturn(List.of(new ProjectManagementRelation(relationObject)));

        assertEquals("true", evaluateParameter(page, "show"));
        assertEquals("1", evaluateParameter(page, "itemnumber"));

        // A page without relations has no tab.
        XWikiDocument otherPage = new XWikiDocument(new DocumentReference(WIKI, "Space", "Other"));
        assertEquals("false", evaluateParameter(otherPage, "show"));
    }

    private String evaluateParameter(XWikiDocument page, String name) throws Exception
    {
        XWikiDocument docExtra = loadDocExtra();
        Properties parameters = new Properties();
        parameters.load(new StringReader(docExtra.getXObject(UIX_CLASS).getLargeStringValue("parameters")));
        this.context.setDoc(page);
        String rendered = docExtra.getRenderedContent("{{velocity}}" + parameters.getProperty(name) + "{{/velocity}}",
            Syntax.XWIKI_2_1, Syntax.HTML_5_0, false, docExtra, false, this.context);
        return Jsoup.parse(rendered).text();
    }

    private XWikiDocument loadDocExtra() throws Exception
    {
        return loadPage(new DocumentReference(WIKI, List.of("ProjectManagement", "Code"), "RelationDocExtra"));
    }

    private String renderDocExtra(XWikiDocument page) throws Exception
    {
        XWikiDocument docExtra = loadDocExtra();
        String content = docExtra.getXObject(UIX_CLASS).getLargeStringValue("content");
        this.context.setDoc(page);
        return docExtra.getRenderedContent(content, Syntax.XWIKI_2_1, Syntax.HTML_5_0, false, docExtra, false,
            this.context);
    }
}
