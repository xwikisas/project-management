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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.SecurityConfiguration;
import org.xwiki.security.authorization.Right;
import org.xwiki.security.authorization.script.SecurityAuthorizationScriptService;
import org.xwiki.security.authorization.script.internal.RightConverter;
import org.xwiki.security.script.SecurityScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code OpenProject.Code.OpenProjectGetConnections}, which returns the names of the configured
 * connections.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@ComponentList({
    ModelScriptService.class,
    SecurityScriptService.class,
    SecurityAuthorizationScriptService.class,
    RightConverter.class
})
class OpenProjectGetConnectionsPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final DocumentReference GET_CONNECTIONS_PAGE =
        new DocumentReference(WIKI, List.of("OpenProject", "Code"), "OpenProjectGetConnections");

    private static final DocumentReference DASHBOARD = new DocumentReference(WIKI, "OpenProject", "WebHome");

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    @MockComponent
    private SecurityConfiguration securityConfiguration;

    @BeforeEach
    void setUp() throws Exception
    {
        when(((OpenProjectScriptService) this.openProjectScriptService).getConnectionOptions())
            .thenReturn(List.of(Map.of("name", "main", "value", "main")));
        // The page itself can be viewed by anyone.
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW)).thenReturn(true);
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(eq(Right.VIEW), any())).thenReturn(true);
        this.context.setAction("get");
        // Record the status set by the page.
        this.response = spy(this.response);
        this.context.setResponse(this.response);
    }

    @Test
    void guestDoesNotGetTheConnections() throws Exception
    {
        this.context.setUserReference(null);

        renderPage(GET_CONNECTIONS_PAGE);

        verify((OpenProjectScriptService) this.openProjectScriptService, never()).getConnectionOptions();
        verify(this.response).setStatus(403);
    }

    @Test
    void userWithoutViewRightOnTheDashboardDoesNotGetTheConnections() throws Exception
    {
        this.context.setUserReference(new DocumentReference(WIKI, "XWiki", "User"));
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW, DASHBOARD)).thenReturn(false);

        renderPage(GET_CONNECTIONS_PAGE);

        verify((OpenProjectScriptService) this.openProjectScriptService, never()).getConnectionOptions();
        verify(this.response).setStatus(403);
    }

    @Test
    void userWithViewRightOnTheDashboardGetsTheConnections() throws Exception
    {
        this.context.setUserReference(new DocumentReference(WIKI, "XWiki", "User"));

        renderPage(GET_CONNECTIONS_PAGE);

        verify((OpenProjectScriptService) this.openProjectScriptService).getConnectionOptions();
        verify(this.response, never()).setStatus(403);
    }
}
