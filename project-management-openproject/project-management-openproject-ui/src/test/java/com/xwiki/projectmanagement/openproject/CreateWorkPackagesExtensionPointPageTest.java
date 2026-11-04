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
import java.util.stream.Collectors;

import javax.inject.Named;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
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
import com.xwiki.licensing.script.LicensingScriptService;
import com.xwiki.licensing.script.LicensorScriptService;
import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Page test for the UI extension of {@code OpenProject.Code.CreateWorkPackagesExtensionPoint} that lets users create
 * work packages from the selected text.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class CreateWorkPackagesExtensionPointPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final String USER_ACCOUNTS = "userAccounts";

    @MockComponent(classToMock = LicensingScriptService.class)
    @Named("licensing")
    private ScriptService licensingScriptService;

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    private OpenProjectScriptService openProject;

    @BeforeEach
    void setUp() throws Exception
    {
        LicensorScriptService licensor = mock(LicensorScriptService.class);
        when(licensor.hasLicensureForEntity(any())).thenReturn(true);
        doReturn(licensor).when((LicensingScriptService) this.licensingScriptService).get("licensor");
        this.openProject = (OpenProjectScriptService) this.openProjectScriptService;
        when(this.openProject.getConnectionOptions()).thenReturn(List.of(
            Map.of("name", USER_ACCOUNTS, "value", USER_ACCOUNTS),
            Map.of("name", "publicData", "value", "publicData")));
        this.context.setAction("view");
        this.context.setUserReference(new DocumentReference(WIKI, "XWiki", "jdoe"));
    }

    @Test
    void listsOnlyTheConnectionsAllowingUserAccounts() throws Exception
    {
        when(this.openProject.getUserAccountConnections()).thenReturn(List.of(USER_ACCOUNTS));

        Document result = Jsoup.parse(renderExtension());

        assertEquals(List.of(USER_ACCOUNTS), result.select("#connection-select-single-work-package option").stream()
            .map(Element::val).collect(Collectors.toList()), result.html());
    }

    @Test
    void hiddenWithoutAConnectionAllowingUserAccounts() throws Exception
    {
        when(this.openProject.getUserAccountConnections()).thenReturn(List.of());

        String result = renderExtension();

        assertTrue(result.isBlank(), result);
    }

    private String renderExtension() throws Exception
    {
        XWikiDocument extensions =
            loadPage(new DocumentReference(WIKI, List.of("OpenProject", "Code"), "CreateWorkPackagesExtensionPoint"));
        BaseObject extension = extensions.getXObject(new LocalDocumentReference("XWiki", "UIExtensionClass"));
        this.context.setDoc(new XWikiDocument(new DocumentReference(WIKI, "Space", "Page")));
        return extensions.getRenderedContent(extension.getLargeStringValue("content"), Syntax.XWIKI_2_1,
            Syntax.HTML_5_0, false, extensions, false, this.context);
    }
}
