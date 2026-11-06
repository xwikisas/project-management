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
package com.xwiki.projectmanagement.openproject.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectAccessMode;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserTokenChecker}.
 *
 * @version $Id$
 */
@ComponentTest
class UserTokenCheckerTest
{
    private static final String CONNECTION_NAME = "main";

    @InjectMockComponents
    private UserTokenChecker checker;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private CSRFToken csrfToken;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private MacroTransformationContext macroContext;

    private final Block hint = new WordBlock("hint");

    private final Block linkLabel = new WordBlock("connect");

    private final Block accountRequiredHint = new WordBlock("account required");

    private final Block accountRequiredLinkLabel = new WordBlock("connect to see it");

    private final Block accountUnavailableHint = new WordBlock("account unavailable");

    private final Block publicDataUnavailableHint = new WordBlock("public data unavailable");

    @BeforeEach
    void setUp()
    {
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getUserReference()).thenReturn(new DocumentReference("wiki", "XWiki", "user"));

        XWikiDocument currentDocument = mock(XWikiDocument.class);
        when(this.xcontext.getDoc()).thenReturn(currentDocument);
        when(currentDocument.getURL("view", this.xcontext)).thenReturn("/xwiki/bin/view/Some/Page");
        XWiki xwiki = mock(XWiki.class);
        when(this.xcontext.getWiki()).thenReturn(xwiki);
        when(xwiki.getURL(any(EntityReference.class), eq("view"), same(this.xcontext)))
            .thenAnswer(invocation -> {
                EntityReference reference = invocation.getArgument(0);
                return "/xwiki/bin/view/OpenProject/Code/" + reference.getName();
            });
        when(this.csrfToken.getToken()).thenReturn("csrf token");

        Translation hintTranslation = mock(Translation.class);
        when(hintTranslation.render()).thenReturn(this.hint);
        when(this.l10n.getTranslation("openproject.oauth.notauthorized.hint")).thenReturn(hintTranslation);
        Translation linkTranslation = mock(Translation.class);
        when(linkTranslation.render()).thenReturn(this.linkLabel);
        when(this.l10n.getTranslation("openproject.oauth.notauthorized.link")).thenReturn(linkTranslation);
        mockTranslation("openproject.oauth.accountrequired.hint", this.accountRequiredHint);
        mockTranslation("openproject.oauth.accountrequired.link", this.accountRequiredLinkLabel);
        mockTranslation("openproject.oauth.accountunavailable.hint", this.accountUnavailableHint);
        mockTranslation("openproject.oauth.publicdataunavailable.hint", this.publicDataUnavailableHint);
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.USER_ACCOUNTS);
    }

    private void mockTranslation(String key, Block rendered)
    {
        Translation translation = mock(Translation.class);
        when(translation.render()).thenReturn(rendered);
        when(this.l10n.getTranslation(key)).thenReturn(translation);
    }

    private void mockConnection(String name, OpenProjectAccessMode accessMode)
    {
        OpenProjectConnection connection = new OpenProjectConnection(name, "https://op.example.org", null, null, null);
        connection.setAccessMode(accessMode);
        when(this.openProjectConfiguration.getConnection(name)).thenReturn(connection);
    }

    @Test
    void noWarningWhenAuthenticated()
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(mock(OpenProjectApiClient.class)));

        assertTrue(this.checker.getWarningBlock(CONNECTION_NAME, this.macroContext).isEmpty());
    }

    @Test
    void warningWithConnectLinkWhenNotConnected()
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConnected());

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, this.macroContext);

        assertEquals(1, result.size());
        GroupBlock group = assertInstanceOf(GroupBlock.class, result.get(0));
        assertEquals("box warningmessage", group.getParameter("class"));
        assertEquals(3, group.getChildren().size());
        assertEquals(this.hint, group.getChildren().get(0));
        assertInstanceOf(SpaceBlock.class, group.getChildren().get(1));
        LinkBlock link = assertInstanceOf(LinkBlock.class, group.getChildren().get(2));
        assertEquals("/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?connectionName=main"
            + "&redirectUrl=%2Fxwiki%2Fbin%2Fview%2FSome%2FPage&token=csrf+token", link.getReference().getReference());
        assertEquals(List.of(this.linkLabel), link.getChildren());
    }

    @Test
    void connectLinkEncodesTheConnectionName()
    {
        String connectionName = "a&redirectUrl=https://example.org/#";
        mockConnection(connectionName, OpenProjectAccessMode.USER_ACCOUNTS);

        List<Block> result = this.checker.getWarningBlock(connectionName,
            OpenProjectAccessStatus.NOT_CONNECTED, false, this.macroContext);

        LinkBlock link = (LinkBlock) result.get(0).getChildren().get(2);
        assertEquals("/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection"
            + "?connectionName=a%26redirectUrl%3Dhttps%3A%2F%2Fexample.org%2F%23"
            + "&redirectUrl=%2Fxwiki%2Fbin%2Fview%2FSome%2FPage&token=csrf+token", link.getReference().getReference());
    }

    @Test
    void warningWithoutConnectLinkForGuests()
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        List<Block> result =
            this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.NOT_CONNECTED, false,
                this.macroContext);

        GroupBlock group = assertInstanceOf(GroupBlock.class, result.get(0));
        assertEquals(List.of(this.hint), group.getChildren());
    }

    @Test
    void unknownConnectionWarningListsTheConnections()
    {
        mockConnections(CONNECTION_NAME, "other");
        Block unknownHint = new WordBlock("unknown connection");
        Translation translation = mock(Translation.class);
        when(translation.render("unknown", "main, other")).thenReturn(unknownHint);
        when(this.l10n.getTranslation("openproject.connection.unknown.hint")).thenReturn(translation);

        List<Block> result =
            this.checker.getWarningBlock("unknown", OpenProjectAccessStatus.NOT_CONFIGURED, false, this.macroContext);

        // No link to connect an OpenProject account, since there's no such connection.
        assertEquals(List.of(unknownHint), result.get(0).getChildren());
    }

    @Test
    void missingConnectionWarningListsTheConnections()
    {
        // Several connections and no default one.
        mockConnections(CONNECTION_NAME, "other");
        Block missingHint = new WordBlock("missing connection");
        Translation translation = mock(Translation.class);
        when(translation.render("main, other")).thenReturn(missingHint);
        when(this.l10n.getTranslation("openproject.connection.missing.hint")).thenReturn(translation);

        List<Block> result =
            this.checker.getWarningBlock(null, OpenProjectAccessStatus.NOT_CONFIGURED, false, this.macroContext);

        assertEquals(List.of(missingHint), result.get(0).getChildren());
    }

    @Test
    void noConnectionWarning()
    {
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(List.of());
        Block noConnectionHint = new WordBlock("no connection");
        mockTranslation("openproject.connection.none.hint", noConnectionHint);

        List<Block> result =
            this.checker.getWarningBlock("", OpenProjectAccessStatus.NOT_CONFIGURED, false, this.macroContext);

        assertEquals(List.of(noConnectionHint), result.get(0).getChildren());
    }

    private void mockConnections(String... names)
    {
        List<OpenProjectConnection> connections = new ArrayList<>();
        for (String name : names) {
            connections.add(new OpenProjectConnection(name, "https://op.example.org", null, null, null));
        }
        when(this.openProjectConfiguration.getOpenProjectConnections()).thenReturn(connections);
    }

    @Test
    void publicDataUnavailableWarningWhenOpenProjectRejectsAnonymousRequests()
    {
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.PUBLIC_DATA);

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.NOT_CONNECTED,
            false, this.macroContext);

        assertEquals(List.of(this.publicDataUnavailableHint), result.get(0).getChildren());
    }

    @Test
    void inlineWarning()
    {
        when(this.macroContext.isInline()).thenReturn(true);

        List<Block> result =
            this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.NOT_CONNECTED, false,
                this.macroContext);

        FormatBlock format = assertInstanceOf(FormatBlock.class, result.get(0));
        assertEquals("box warningmessage", format.getParameter("class"));
    }

    @Test
    void noWarningWithAnonymousAccess()
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.anonymous(mock(OpenProjectApiClient.class)));

        assertTrue(this.checker.getWarningBlock(CONNECTION_NAME, this.macroContext).isEmpty());
    }

    @Test
    void noWarningWhenAuthenticatedAndTheAccountIsRequired()
    {
        assertTrue(this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.AUTHENTICATED, true,
            this.macroContext).isEmpty());
    }

    @Test
    void accountRequiredWarningWithAnonymousAccess()
    {
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.PUBLIC_DATA_AND_USER_ACCOUNTS);
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.anonymous(mock(OpenProjectApiClient.class)));

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, true, this.macroContext);

        GroupBlock group = assertInstanceOf(GroupBlock.class, result.get(0));
        assertEquals("box warningmessage", group.getParameter("class"));
        assertEquals(this.accountRequiredHint, group.getChildren().get(0));
        LinkBlock link = assertInstanceOf(LinkBlock.class, group.getChildren().get(2));
        assertTrue(link.getReference().getReference()
            .startsWith("/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?connectionName=main"));
        assertEquals(List.of(this.accountRequiredLinkLabel), link.getChildren());
    }

    @Test
    void accountRequiredWarningWithoutConnectLinkForGuests()
    {
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.PUBLIC_DATA_AND_USER_ACCOUNTS);
        when(this.xcontext.getUserReference()).thenReturn(null);

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.ANONYMOUS, true,
            this.macroContext);

        assertEquals(List.of(this.accountRequiredHint), result.get(0).getChildren());
    }

    @Test
    void accountUnavailableWarningWhenTheConnectionOnlyShowsPublicData()
    {
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.PUBLIC_DATA);

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.ANONYMOUS, true,
            this.macroContext);

        GroupBlock group = assertInstanceOf(GroupBlock.class, result.get(0));
        assertEquals("box warningmessage", group.getParameter("class"));
        // The users can't connect their OpenProject account: no link.
        assertEquals(List.of(this.accountUnavailableHint), group.getChildren());
    }

    @Test
    void messageWarningWithConnectLinkWithAnonymousAccess()
    {
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.PUBLIC_DATA_AND_USER_ACCOUNTS);
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.anonymous(mock(OpenProjectApiClient.class)));
        Block message = new WordBlock("not found");

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, message, false);

        GroupBlock group = assertInstanceOf(GroupBlock.class, result.get(0));
        assertEquals("box warningmessage", group.getParameter("class"));
        assertEquals(message, group.getChildren().get(0));
        LinkBlock link = assertInstanceOf(LinkBlock.class, group.getChildren().get(2));
        assertEquals(List.of(this.accountRequiredLinkLabel), link.getChildren());
    }

    @Test
    void messageWarningWithoutConnectLinkWhenAuthenticated()
    {
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.PUBLIC_DATA_AND_USER_ACCOUNTS);
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(mock(OpenProjectApiClient.class)));
        Block message = new WordBlock("not found");

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, message, true);

        // Displayed inline, without link: the account of the user can't see more.
        assertEquals(List.of(new FormatBlock(List.of(message), Format.NONE, Map.of("class", "box warningmessage"))),
            result);
    }

    @Test
    void messageWarningWithoutConnectLinkWhenTheConnectionOnlyShowsPublicData()
    {
        mockConnection(CONNECTION_NAME, OpenProjectAccessMode.PUBLIC_DATA);
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.anonymous(mock(OpenProjectApiClient.class)));
        Block message = new WordBlock("not found");

        List<Block> result = this.checker.getWarningBlock(CONNECTION_NAME, message, false);

        assertEquals(List.of(new GroupBlock(List.of(message), Map.of("class", "box warningmessage"))), result);
    }
}
