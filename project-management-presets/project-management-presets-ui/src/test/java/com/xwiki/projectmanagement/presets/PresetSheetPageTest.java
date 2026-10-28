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
package com.xwiki.projectmanagement.presets;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test for {@code ProjectManagement.Code.Presets.PresetSheet}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class PresetSheetPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final List<String> PRESETS_SPACE = List.of("ProjectManagement", "Code", "Presets");

    private static final String INJECTED = "\"/><b class=\"injected\">injected</b>{{/html}}";

    private XWikiDocument preset;

    @BeforeEach
    void setUp() throws Exception
    {
        loadPage(new DocumentReference(WIKI, PRESETS_SPACE, "PresetClass"));
        this.preset = new XWikiDocument(new DocumentReference(WIKI, PRESETS_SPACE, "Preset1"));
        BaseObject presetObject =
            this.preset.newXObject(new DocumentReference(WIKI, PRESETS_SPACE, "PresetClass"), this.context);
        presetObject.setIntValue("id", 1);
        this.xwiki.saveDocument(this.preset, this.context);
    }

    @Test
    void requestParametersAreEscaped() throws Exception
    {
        this.context.setAction("edit");
        this.request.put("isMultiple", INJECTED);
        this.request.put("title", INJECTED);
        this.request.put("client", INJECTED);

        Document document = renderSheet();

        assertTrue(document.select(".injected").isEmpty(), document.html());
        // Only a boolean value is kept.
        assertEquals("0", document.getElementById("ProjectManagement.Code.Presets.PresetClass_0_isMultiple").val());
        assertEquals(INJECTED, document.getElementById("ProjectManagement.Code.Presets.PresetClass_0_name").val());
        assertEquals(INJECTED, document.getElementById("ProjectManagement.Code.Presets.PresetClass_0_client").val());
    }

    @Test
    void multipleDatasetsRequested() throws Exception
    {
        this.context.setAction("edit");
        this.request.put("isMultiple", "1");

        Document document = renderSheet();

        assertEquals("1", document.getElementById("ProjectManagement.Code.Presets.PresetClass_0_isMultiple").val());
    }

    private Document renderSheet() throws Exception
    {
        XWikiDocument sheet = loadPage(new DocumentReference(WIKI, PRESETS_SPACE, "PresetSheet"));
        this.context.setDoc(this.preset);
        return Jsoup.parse(sheet.getRenderedContent(sheet.getContent(), Syntax.XWIKI_2_1, Syntax.HTML_5_0, false,
            sheet, false, this.context));
    }
}
