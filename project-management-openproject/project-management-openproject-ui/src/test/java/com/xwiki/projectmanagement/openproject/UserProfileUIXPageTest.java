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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.csrf.script.CSRFTokenScriptService;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.configuration.ExtendedRenderingConfiguration;
import org.xwiki.rendering.internal.macro.DefaultMacroContentParser;
import org.xwiki.rendering.internal.macro.message.ErrorMessageMacro;
import org.xwiki.rendering.internal.macro.message.InfoMessageMacro;
import org.xwiki.rendering.internal.macro.message.SuccessMessageMacro;
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

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.XWikiServletRequestStub;
import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for the user profile tab of {@code OpenProject.Code.UserProfileUIX}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList({
    ErrorMessageMacro.class,
    InfoMessageMacro.class,
    SuccessMessageMacro.class,
    DefaultMacroContentParser.class,
    RenderingScriptService.class,
    XWikiSyntaxEscaper.class
})
class UserProfileUIXPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final DocumentReference USER = new DocumentReference(WIKI, "XWiki", "jdoe");

    private static final String CONNECTED = "connected";

    private static final String NOT_CONNECTED = "notConnected";

    private static final String TOKEN = "token";

    private static final String DISCONNECT_PARAMETER = "openprojectDisconnect";

    @MockComponent(classToMock = CSRFTokenScriptService.class)
    @Named("csrf")
    private ScriptService csrfScriptService;

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    // Required by the rendering script service, used to escape the messages.
    @MockComponent
    private MacroCategoryManager macroCategoryManager;

    @MockComponent
    private ExtendedRenderingConfiguration extendedRenderingConfiguration;

    private OpenProjectScriptService openProject;

    @BeforeEach
    void setUp()
    {
        CSRFTokenScriptService csrf = (CSRFTokenScriptService) this.csrfScriptService;
        when(csrf.getToken()).thenReturn(TOKEN);
        when(csrf.isTokenValid(TOKEN)).thenReturn(true);
        this.openProject = (OpenProjectScriptService) this.openProjectScriptService;
        when(this.openProject.getUserAccountConnections()).thenReturn(List.of(CONNECTED, NOT_CONNECTED));
        when(this.openProject.getConnectionUrl(any())).thenReturn("https://openproject.example.org");
        when(this.openProject.isUserAuthorized(CONNECTED)).thenReturn(true);
        this.context.setUserReference(USER);
    }

    @Test
    void listsTheConnectionsOfTheCurrentUser() throws Exception
    {
        Document result = Jsoup.parse(renderProfileTab(USER));

        assertEquals(2, result.select(".openproject-user-connections tbody tr").size(), result.html());
        assertEquals("openproject.userprofile.disconnect",
            result.select("tr:has(td:contains(" + CONNECTED + ")) button[type=submit]").text());
        String connectURL =
            result.select("tr:has(td:contains(" + NOT_CONNECTED + ")) a.btn-primary").attr("href");
        assertTrue(connectURL.contains("RenewOAuthConnection") && connectURL.contains("connectionName="
            + NOT_CONNECTED) && connectURL.contains("token=" + TOKEN), connectURL);
        verify(this.openProject, never()).disconnect(any());
        assertEquals("https://openproject.example.org",
            result.select("tr:has(td:contains(" + CONNECTED + ")) a[href^=https]").attr("href"));
    }

    @Test
    void serverURLWithoutHTTPSchemeIsNotLinked() throws Exception
    {
        when(this.openProject.getConnectionUrl(any())).thenReturn("javascript:alert(1)");

        Document result = Jsoup.parse(renderProfileTab(USER));

        assertTrue(result.select(".openproject-user-connections a[href^=javascript]").isEmpty(), result.html());
        assertEquals(2, result.select(".openproject-user-connections td").stream()
            .filter(cell -> "javascript:alert(1)".equals(cell.ownText())).count(), result.html());
    }

    @Test
    void noConnectionsOnTheProfileOfAnotherUser() throws Exception
    {
        Document result = Jsoup.parse(renderProfileTab(new DocumentReference(WIKI, "XWiki", "other")));

        assertTrue(result.select(".openproject-user-connections").isEmpty(), result.html());
        assertTrue(result.select(".infomessage").isEmpty(), result.html());
    }

    @Test
    void noConnectionsAllowingUserAccounts() throws Exception
    {
        when(this.openProject.getUserAccountConnections()).thenReturn(List.of());

        Document result = Jsoup.parse(renderProfileTab(USER));

        assertEquals("openproject.userprofile.noConnections", result.select(".infomessage").text());
        assertTrue(result.select(".openproject-user-connections").isEmpty(), result.html());
    }

    @Test
    void disconnect() throws Exception
    {
        when(this.openProject.disconnect(CONNECTED)).thenReturn(true);
        RequestStub request = new RequestStub("POST");
        request.put(DISCONNECT_PARAMETER, CONNECTED);
        request.put("form_token", TOKEN);
        this.context.setRequest(request);

        Document result = Jsoup.parse(renderProfileTab(USER));

        verify(this.openProject).disconnect(CONNECTED);
        assertEquals("openproject.userprofile.disconnected [connected]", result.select(".successmessage").text());
    }

    @Test
    void disconnectFailure() throws Exception
    {
        RequestStub request = new RequestStub("POST");
        request.put(DISCONNECT_PARAMETER, CONNECTED);
        request.put("form_token", TOKEN);
        this.context.setRequest(request);

        Document result = Jsoup.parse(renderProfileTab(USER));

        assertEquals("openproject.userprofile.disconnectFailed [connected]", result.select(".errormessage").text());
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid", "" })
    void dontDisconnectWithAnInvalidCSRFToken(String token) throws Exception
    {
        RequestStub request = new RequestStub("POST");
        request.put(DISCONNECT_PARAMETER, CONNECTED);
        request.put("form_token", token);
        this.context.setRequest(request);

        renderProfileTab(USER);

        verify(this.openProject, never()).disconnect(any());
    }

    @Test
    void dontDisconnectWithAGetRequest() throws Exception
    {
        RequestStub request = new RequestStub("GET");
        request.put(DISCONNECT_PARAMETER, CONNECTED);
        request.put("form_token", TOKEN);
        this.context.setRequest(request);

        renderProfileTab(USER);

        verify(this.openProject, never()).disconnect(any());
    }

    @Test
    void dontDisconnectFromTheProfileOfAnotherUser() throws Exception
    {
        RequestStub request = new RequestStub("POST");
        request.put(DISCONNECT_PARAMETER, CONNECTED);
        request.put("form_token", TOKEN);
        this.context.setRequest(request);

        renderProfileTab(new DocumentReference(WIKI, "XWiki", "other"));

        verify(this.openProject, never()).disconnect(any());
    }

    private String renderProfileTab(DocumentReference profile) throws Exception
    {
        XWikiDocument extension =
            loadPage(new DocumentReference(WIKI, List.of("OpenProject", "Code"), "UserProfileUIX"));
        String content = extension.getXObject(new LocalDocumentReference("XWiki", "UIExtensionClass"))
            .getLargeStringValue("content");
        this.context.setDoc(new XWikiDocument(profile));
        return extension.getRenderedContent(content, Syntax.XWIKI_2_1, Syntax.HTML_5_0, false, extension, false,
            this.context);
    }

    /**
     * A request stub with a method, which the stub of the test framework doesn't have.
     */
    private static final class RequestStub extends XWikiServletRequestStub
    {
        private final String method;

        RequestStub(String method)
        {
            this.method = method;
        }

        @Override
        public String getMethod()
        {
            return this.method;
        }
    }
}
