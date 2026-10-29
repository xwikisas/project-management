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

import javax.inject.Named;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.query.QueryParameter;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;
import org.xwiki.velocity.tools.JSONTool;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import net.sf.json.JSONObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code OpenProject.Code.OpenProjectSourceJson}, the source of the connection list of the
 * administration.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList({
    ModelScriptService.class
})
class OpenProjectSourceJsonPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final List<String> CODE_SPACE = List.of("OpenProject", "Code");

    private static final String CONNECTION_NAME = "name onfocus=alert(1) autofocus";

    private static final String SERVER_URL = "javascript:alert(1)//<img class=\"injected\" src=\"x\">";

    @MockComponent(classToMock = QueryManagerScriptService.class)
    @Named("query")
    private ScriptService queryScriptService;

    @Test
    void connectionValuesAreEscaped() throws Exception
    {
        loadPage(new DocumentReference(WIKI, CODE_SPACE, "OpenProjectConnectionClass"));
        DocumentReference connectionReference =
            new DocumentReference(WIKI, List.of("OpenProject", "Code", "OpenProjectConfigurations"), "Connection");
        XWikiDocument connectionDocument = new XWikiDocument(connectionReference);
        BaseObject connection = connectionDocument.newXObject(
            new DocumentReference(WIKI, CODE_SPACE, "OpenProjectConnectionClass"), this.context);
        connection.setStringValue("connectionName", CONNECTION_NAME);
        connection.setStringValue("serverURL", SERVER_URL);
        connection.setStringValue("clientId", "<b class=\"injected\">id</b>");
        this.xwiki.saveDocument(connectionDocument, this.context);

        QueryManagerScriptService queryManager = (QueryManagerScriptService) this.queryScriptService;
        when(queryManager.parameter()).thenReturn(mock(QueryParameter.class, RETURNS_SELF));
        ScriptQuery query = mock(ScriptQuery.class, RETURNS_SELF);
        when(queryManager.xwql(anyString())).thenReturn(query);
        when(query.count()).thenReturn(1L);
        when(query.execute()).thenReturn(List.of("OpenProject.Code.OpenProjectConfigurations.Connection"));
        registerVelocityTool("hasAdmin", true);

        // The page writes the JSON directly to the response: capture it before.
        JSONTool jsonTool = spy(new JSONTool());
        registerVelocityTool("jsontool", jsonTool);
        renderPage(new DocumentReference(WIKI, CODE_SPACE, "OpenProjectSourceJson"));
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(jsonTool).serialize(data.capture());
        JSONObject json = JSONObject.fromObject(new JSONTool().serialize(data.getValue()));

        JSONObject row = json.getJSONArray("rows").getJSONObject(0);
        Document connectionName = Jsoup.parseBodyFragment(row.getString("connectionName"));
        assertEquals(CONNECTION_NAME, connectionName.text());

        Document serverURL = Jsoup.parseBodyFragment(row.getString("serverURL"));
        assertEquals(SERVER_URL, serverURL.text());
        assertTrue(serverURL.select("[href]").isEmpty(), serverURL.html());
        assertTrue(serverURL.select(".injected").isEmpty(), serverURL.html());

        Document clientId = Jsoup.parseBodyFragment(row.getString("clientId"));
        assertTrue(clientId.select(".injected").isEmpty(), clientId.html());

        Document actions = Jsoup.parseBodyFragment(row.getString("actions"));
        Element editButton = actions.selectFirst(".edit-button");
        assertEquals(CONNECTION_NAME, editButton.attr("data-connection-name"));
        assertEquals(SERVER_URL, editButton.attr("data-server-url"));
        assertFalse(editButton.hasAttr("onfocus"), actions.html());
        assertEquals(CONNECTION_NAME, actions.selectFirst(".delete-button").attr("data-connection-name"));
    }
}
