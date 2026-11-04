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
import org.xwiki.contrib.oidc.OAuth2ClientScriptService;
import org.xwiki.csrf.script.CSRFTokenScriptService;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.configuration.ExtendedRenderingConfiguration;
import org.xwiki.rendering.internal.macro.DefaultMacroContentParser;
import org.xwiki.rendering.internal.macro.message.ErrorMessageMacro;
import org.xwiki.rendering.internal.util.XWikiSyntaxEscaper;
import org.xwiki.rendering.macro.MacroCategoryManager;
import org.xwiki.rendering.script.RenderingScriptService;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code OpenProject.Code.RenewOAuthConnection}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList({
    ErrorMessageMacro.class,
    DefaultMacroContentParser.class,
    RenderingScriptService.class,
    XWikiSyntaxEscaper.class
})
class RenewOAuthConnectionPageTest extends PageTest
{
    private static final DocumentReference PAGE =
        new DocumentReference("xwiki", List.of("OpenProject", "Code"), "RenewOAuthConnection");

    private static final String CONNECTION_NAME = "connection";

    @MockComponent(classToMock = CSRFTokenScriptService.class)
    @Named("csrf")
    private ScriptService csrfScriptService;

    @MockComponent(classToMock = OAuth2ClientScriptService.class)
    @Named("oauth2client")
    private ScriptService oauth2ScriptService;

    // Required by the rendering script service, used to escape the messages.
    @MockComponent
    private MacroCategoryManager macroCategoryManager;

    @MockComponent
    private ExtendedRenderingConfiguration extendedRenderingConfiguration;

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    @BeforeEach
    void setUp()
    {
        when(((CSRFTokenScriptService) this.csrfScriptService).isTokenValid("token")).thenReturn(true);
        this.request.setrequestURL(
            new StringBuffer("http://localhost:8080/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection"));
        this.request.put("connectionName", CONNECTION_NAME);
        when(((OpenProjectScriptService) this.openProjectScriptService).getUserAccountConnections())
            .thenReturn(List.of(CONNECTION_NAME));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/xwiki/bin/view/Main/", "http://localhost:8080/xwiki/bin/view/Main/" })
    void authorizeAndRedirectToALocalPage(String redirectURL) throws Exception
    {
        this.request.put("token", "token");
        this.request.put("redirectUrl", redirectURL);

        renderPage(PAGE);

        verify((OAuth2ClientScriptService) this.oauth2ScriptService).authorize(CONNECTION_NAME, redirectURL);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://www.example.org/", "//www.example.org/", "/\\www.example.org/",
        "http://localhost:8080.example.org/", "http://localhost:8080@www.example.org/", "javascript:alert(1)" })
    void dontRedirectToAnotherServer(String redirectURL) throws Exception
    {
        this.request.put("token", "token");
        this.request.put("redirectUrl", redirectURL);

        renderPage(PAGE);

        verify((OAuth2ClientScriptService) this.oauth2ScriptService, never()).authorize(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid", "" })
    void dontAuthorizeWithAnInvalidCSRFToken(String token) throws Exception
    {
        this.request.put("token", token);
        this.request.put("redirectUrl", "/xwiki/bin/view/Main/");

        renderPage(PAGE);

        verify((OAuth2ClientScriptService) this.oauth2ScriptService, never()).authorize(any(), any());
    }

    @Test
    void dontAuthorizeAConnectionShowingOnlyPublicData() throws Exception
    {
        when(((OpenProjectScriptService) this.openProjectScriptService).getUserAccountConnections())
            .thenReturn(List.of());
        this.request.put("token", "token");
        this.request.put("redirectUrl", "/xwiki/bin/view/Main/");

        Document result = Jsoup.parse(renderPage(PAGE));

        verify((OAuth2ClientScriptService) this.oauth2ScriptService, never()).authorize(any(), any());
        assertEquals("openproject.config.authorize.notAllowed [connection]", result.select(".errormessage").text());
    }
}
