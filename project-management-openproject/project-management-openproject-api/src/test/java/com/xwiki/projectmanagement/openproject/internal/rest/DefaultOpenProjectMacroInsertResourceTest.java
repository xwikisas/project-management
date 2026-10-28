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
package com.xwiki.projectmanagement.openproject.internal.rest;

import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.commons.document.MacroUtils;
import com.xwiki.projectmanagement.openproject.model.MacroInsertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectMacroInsertResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultOpenProjectMacroInsertResourceTest
{
    private static final DocumentReference DOCUMENT_REFERENCE =
        new DocumentReference("xwiki", List.of("Space"), "Page");

    private static final DocumentReference CURRENT_USER = new DocumentReference("xwiki", "XWiki", "User");

    private static final String CONTENT = "Hello world";

    @InjectMockComponents
    private DefaultOpenProjectMacroInsertResource resource;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    private ContentParser contentParser;

    @MockComponent
    private MacroUtils macroUtils;

    @MockComponent
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private XWikiContext context;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument cachedDocument;

    @Mock
    private XWikiDocument document;

    @Mock
    private DocumentAuthors authors;

    @BeforeComponent
    void beforeComponent()
    {
        // The context is needed when initializing the resource.
        this.context = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(this.context);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.context.getWiki()).thenReturn(this.xwiki);
        when(this.context.getUserReference()).thenReturn(CURRENT_USER);
        when(this.authorizationManager.hasAccess(Right.EDIT, DOCUMENT_REFERENCE)).thenReturn(true);
        when(this.xwiki.getDocument(DOCUMENT_REFERENCE, this.context)).thenReturn(this.cachedDocument);
        when(this.cachedDocument.clone()).thenReturn(this.document);
        when(this.document.getSyntax()).thenReturn(Syntax.XWIKI_2_1);
        when(this.document.getContent()).thenReturn(CONTENT);
        when(this.document.getAuthors()).thenReturn(this.authors);

        when(this.contentParser.parse(anyString(), eq(Syntax.XWIKI_2_1))).thenAnswer(invocation -> new XDOM(
            List.of(new ParagraphBlock(List.<Block>of(new WordBlock("Hello"), new WordBlock("world"))))));
        when(this.macroUtils.renderMacroContent(anyList(), eq(Syntax.PLAIN_1_0))).thenReturn(CONTENT);
        when(this.macroUtils.renderMacroContent(anyList(), eq(Syntax.XWIKI_2_1)))
            .thenReturn("{{openproject identifier=\"1\"/}}" + CONTENT);
    }

    @Test
    void insertIntoPageSavesAsCurrentUser() throws Exception
    {
        UserReference currentUserReference = mock(UserReference.class);
        when(this.userReferenceResolver.resolve(CURRENT_USER)).thenReturn(currentUserReference);

        Response response = this.resource.insetIntoPage("xwiki", "Space", "Page", createInsertion("openproject"));

        assertEquals(200, response.getStatus());
        verify(this.document).setContent("{{openproject identifier=\"1\"/}}" + CONTENT + "\n");
        verify(this.authors).setContentAuthor(currentUserReference);
        verify(this.authors).setEffectiveMetadataAuthor(currentUserReference);
        verify(this.authors).setOriginalMetadataAuthor(currentUserReference);
        verify(this.xwiki).saveDocument(this.document, "Inserted OpenProject mention.", true, this.context);
        verify(this.cachedDocument, never()).setContent(anyString());
    }

    @Test
    void insertIntoPageWithOtherMacro() throws Exception
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.insetIntoPage("xwiki", "Space", "Page", createInsertion("velocity")));

        assertEquals(400, exception.getResponse().getStatus());
        assertEquals("Only the [openproject] macro can be inserted.", exception.getResponse().getEntity());
        verify(this.document, never()).setContent(anyString());
        verify(this.xwiki, never()).saveDocument(any(XWikiDocument.class), anyString(), anyBoolean(),
            any(XWikiContext.class));
    }

    private MacroInsertion createInsertion(String macroId)
    {
        MacroInsertion insertion = new MacroInsertion();
        insertion.setMacroId(macroId);
        insertion.setMacroParameters(Map.of("identifier", "1"));
        insertion.setSelectedText("world");
        insertion.setFullLine(CONTENT);
        insertion.setOffset(6);
        return insertion;
    }
}
