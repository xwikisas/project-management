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
import java.util.Map;

import javax.inject.Named;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Page test for the UI extensions of {@code OpenProject.Code.Relations.OpenProjectRelationsUIXP}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class OpenProjectRelationsUIXPPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    @Test
    void mentionsOnlyUseConfiguredConnections() throws Exception
    {
        when(((OpenProjectScriptService) this.openProjectScriptService).getConnectionOptions())
            .thenReturn(List.of(Map.of("name", "main", "value", "main")));
        XWikiDocument relationOwner = new XWikiDocument(new DocumentReference(WIKI, "Space", "WebHome"));
        BaseObject relation = relationOwner.newXObject(
            new LocalDocumentReference(List.of("ProjectManagement", "Code"), "RelationClass"), this.context);
        relation.setLargeStringValue("clientParams", "{\"instance\": \"main/../../../other\"}");
        registerVelocityTool("relationOwnerDoc", relationOwner.newDocument(this.context));

        String result = renderExtension("com.xwiki.projectmanagement.openproject.relations.right");

        assertEquals("openproject.relations.mentions.noInstance",
            Jsoup.parse(result).select(".warningmessage").text(), result);
    }

    private String renderExtension(String extensionPointId) throws Exception
    {
        XWikiDocument extensions = loadPage(new DocumentReference(WIKI, List.of("OpenProject", "Code", "Relations"),
            "OpenProjectRelationsUIXP"));
        String content = null;
        for (BaseObject extension : extensions.getXObjects(new LocalDocumentReference("XWiki", "UIExtensionClass"))) {
            if (extensionPointId.equals(extension.getStringValue("extensionPointId"))) {
                content = extension.getLargeStringValue("content");
            }
        }
        this.context.setDoc(new XWikiDocument(new DocumentReference(WIKI, "Space", "Page")));
        return extensions.getRenderedContent(content, Syntax.XWIKI_2_1, Syntax.HTML_5_0, false, extensions, false,
            this.context);
    }
}
