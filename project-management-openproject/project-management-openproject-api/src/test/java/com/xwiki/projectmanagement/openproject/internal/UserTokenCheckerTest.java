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

import java.util.List;

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
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
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

    private final Block publicDataHint = new WordBlock("public data");

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
        Translation publicDataHintTranslation = mock(Translation.class);
        when(publicDataHintTranslation.render()).thenReturn(this.publicDataHint);
        when(this.l10n.getTranslation("openproject.oauth.publicdata.hint")).thenReturn(publicDataHintTranslation);
        Translation publicDataLinkTranslation = mock(Translation.class);
        when(publicDataLinkTranslation.render()).thenReturn(this.linkLabel);
        when(this.l10n.getTranslation("openproject.oauth.publicdata.link")).thenReturn(publicDataLinkTranslation);
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
        assertEquals(2, group.getChildren().size());
        assertEquals(this.hint, group.getChildren().get(0));
        LinkBlock link = assertInstanceOf(LinkBlock.class, group.getChildren().get(1));
        assertEquals("/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?connectionName=main"
            + "&redirectUrl=%2Fxwiki%2Fbin%2Fview%2FSome%2FPage&token=csrf+token", link.getReference().getReference());
        assertEquals(List.of(this.linkLabel), link.getChildren());
    }

    @Test
    void warningWithoutConnectLinkForGuests()
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        List<Block> result =
            this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.NOT_CONNECTED, this.macroContext);

        GroupBlock group = assertInstanceOf(GroupBlock.class, result.get(0));
        assertEquals(List.of(this.hint), group.getChildren());
    }

    @Test
    void warningWhenTheConnectionIsNotConfigured()
    {
        List<Block> result =
            this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.NOT_CONFIGURED, this.macroContext);

        assertEquals(1, result.size());
    }

    @Test
    void inlineWarning()
    {
        when(this.macroContext.isInline()).thenReturn(true);

        List<Block> result =
            this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.NOT_CONNECTED, this.macroContext);

        FormatBlock format = assertInstanceOf(FormatBlock.class, result.get(0));
        assertEquals("box warningmessage", format.getParameter("class"));
    }

    @Test
    void noWarningWithAnonymousAccess()
    {
        assertTrue(this.checker.getWarningBlock(CONNECTION_NAME, OpenProjectAccessStatus.ANONYMOUS, this.macroContext)
            .isEmpty());
    }

    @Test
    void connectHintWithAnonymousAccess()
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.anonymous(mock(OpenProjectApiClient.class)));

        List<Block> result = this.checker.getConnectHintBlock(CONNECTION_NAME, this.macroContext);

        GroupBlock group = assertInstanceOf(GroupBlock.class, result.get(0));
        assertEquals("box infomessage", group.getParameter("class"));
        assertEquals(this.publicDataHint, group.getChildren().get(0));
        LinkBlock link = assertInstanceOf(LinkBlock.class, group.getChildren().get(1));
        assertTrue(link.getReference().getReference()
            .startsWith("/xwiki/bin/view/OpenProject/Code/RenewOAuthConnection?connectionName=main"));
    }

    @Test
    void noConnectHintForGuests()
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        assertTrue(this.checker.getConnectHintBlock(CONNECTION_NAME, OpenProjectAccessStatus.ANONYMOUS,
            this.macroContext).isEmpty());
    }

    @Test
    void noConnectHintWhenAuthenticated()
    {
        assertTrue(this.checker.getConnectHintBlock(CONNECTION_NAME, OpenProjectAccessStatus.AUTHENTICATED,
            this.macroContext).isEmpty());
    }
}
