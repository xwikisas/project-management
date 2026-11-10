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
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.commons.document.MacroUtils;
import com.xwiki.projectmanagement.openproject.model.MacroInsertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectMacroInsertResource}, on a page stored in the wiki.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class DefaultOpenProjectMacroInsertResourceTest
{
    private static final DocumentReference PAGE = new DocumentReference("xwiki", List.of("Space"), "Page");

    private static final DocumentReference CURRENT_USER = new DocumentReference("xwiki", "XWiki", "User");

    private static final DocumentReference PREVIOUS_AUTHOR = new DocumentReference("xwiki", "XWiki", "Previous");

    private static final String CONTENT = "Hello world";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private DefaultOpenProjectMacroInsertResource resource;

    @MockComponent
    private ContentParser contentParser;

    @MockComponent
    private MacroUtils macroUtils;

    private UserReferenceResolver<DocumentReference> userResolver;

    @BeforeEach
    void setUp() throws Exception
    {
        this.userResolver = this.componentManager.getInstance(UserReferenceResolver.TYPE_DOCUMENT_REFERENCE,
            "document");
        XWikiContext context = this.oldcore.getXWikiContext();
        context.setUserReference(CURRENT_USER);
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.EDIT, PAGE)).thenReturn(true);

        // The page was last saved by another user.
        XWikiDocument page = new XWikiDocument(PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(CONTENT);
        UserReference previousAuthor = this.userResolver.resolve(PREVIOUS_AUTHOR);
        page.getAuthors().setContentAuthor(previousAuthor);
        page.getAuthors().setEffectiveMetadataAuthor(previousAuthor);
        page.getAuthors().setOriginalMetadataAuthor(previousAuthor);
        this.oldcore.getSpyXWiki().saveDocument(page, context);

        // Parse each line as a paragraph holding its text.
        when(this.contentParser.parse(anyString(), eq(Syntax.XWIKI_2_1))).thenAnswer(invocation -> new XDOM(
            List.of(new ParagraphBlock(List.<Block>of(new WordBlock(invocation.getArgument(0)))))));
        when(this.macroUtils.renderMacroContent(anyList(), eq(Syntax.PLAIN_1_0))).thenAnswer(invocation -> {
            List<Block> blocks = invocation.getArgument(0);
            return ((WordBlock) blocks.get(0).getChildren().get(0)).getWord();
        });
        // Render the inserted macro (the first block of the line) followed by the rest of the line.
        when(this.macroUtils.renderMacroContent(anyList(), eq(Syntax.XWIKI_2_1)))
            .thenAnswer(invocation -> renderLine(invocation.getArgument(0)));
    }

    private static String renderLine(List<Block> blocks)
    {
        MacroBlock macro = (MacroBlock) blocks.get(0).getChildren().get(0);
        WordBlock line = (WordBlock) blocks.get(0).getChildren().get(1);
        return String.format("{{%s %s/}}%s", macro.getId(), macro.getParameters(), line.getWord());
    }

    private XWikiDocument getPage() throws XWikiException
    {
        return this.oldcore.getSpyXWiki().getDocument(PAGE, this.oldcore.getXWikiContext());
    }

    @Test
    void insertIntoPageSavesAsCurrentUser() throws Exception
    {
        Response response = this.resource.insetIntoPage("xwiki", "Space", "Page", createInsertion("openproject"));

        assertEquals(200, response.getStatus());
        XWikiDocument page = getPage();
        assertEquals("{{openproject {identifier=1}/}}" + CONTENT + "\n", page.getContent());
        // The saved page is written by the current user, whatever the previous authors of the page were.
        UserReference currentUser = this.userResolver.resolve(CURRENT_USER);
        assertEquals(currentUser, page.getAuthors().getContentAuthor());
        assertEquals(currentUser, page.getAuthors().getEffectiveMetadataAuthor());
        assertEquals(currentUser, page.getAuthors().getOriginalMetadataAuthor());
        assertEquals("Inserted OpenProject mention.", page.getComment());
        assertTrue(page.isMinorEdit());
    }

    @Test
    void insertIntoPageKeepsConcurrentInsertions() throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        XWikiDocument page = getPage().clone();
        page.setContent(CONTENT + "\nGoodbye moon");
        this.oldcore.getSpyXWiki().saveDocument(page, context);
        MacroInsertion otherInsertion = createInsertion("openproject");
        otherInsertion.setMacroParameters(Map.of("identifier", "2"));
        otherInsertion.setSelectedText("moon");
        otherInsertion.setFullLine("Goodbye moon");
        otherInsertion.setOffset(8);

        // While the first insertion computes the new content of the page, a second insertion is requested on the same
        // page, and waits.
        Execution execution = this.componentManager.getInstance(Execution.class);
        ExecutionContext executionContext = execution.getContext();
        AtomicReference<Thread> otherThread = new AtomicReference<>();
        AtomicReference<Throwable> otherFailure = new AtomicReference<>();
        when(this.macroUtils.renderMacroContent(anyList(), eq(Syntax.XWIKI_2_1))).thenAnswer(invocation -> {
            if (otherThread.get() == null) {
                Thread thread = new Thread(() -> {
                    execution.setContext(executionContext);
                    this.resource.insetIntoPage("xwiki", "Space", "Page", otherInsertion);
                });
                thread.setUncaughtExceptionHandler((failedThread, failure) -> otherFailure.set(failure));
                otherThread.set(thread);
                thread.start();
                waitUntilBlocked(thread);
            }
            return renderLine(invocation.getArgument(0));
        });

        this.resource.insetIntoPage("xwiki", "Space", "Page", createInsertion("openproject"));
        otherThread.get().join(10000);
        assertFalse(otherThread.get().isAlive());
        assertNull(otherFailure.get());

        // The second insertion is done on the page saved by the first one.
        assertEquals("{{openproject {identifier=1}/}}" + CONTENT + "\n{{openproject {identifier=2}/}}Goodbye moon",
            getPage().getContent());
    }

    private void waitUntilBlocked(Thread thread) throws InterruptedException
    {
        long timeout = System.currentTimeMillis() + 10000;
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TERMINATED
            && System.currentTimeMillis() < timeout)
        {
            Thread.sleep(10);
        }
        // The second insertion waits for the first one to release the lock of the page.
        assertEquals(Thread.State.WAITING, thread.getState());
    }

    @Test
    void insertIntoPageLeavesTheCachedPageUnchangedWhenSaveFails() throws Exception
    {
        doThrow(new XWikiException()).when(this.oldcore.getSpyXWiki()).saveDocument(any(XWikiDocument.class),
            anyString(), anyBoolean(), any(XWikiContext.class));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> this.resource.insetIntoPage("xwiki", "Space", "Page", createInsertion("openproject")));

        assertInstanceOf(XWikiException.class, exception.getCause());
        assertPageUnchanged();
    }

    @Test
    void insertIntoPageWithoutEditRight() throws Exception
    {
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.EDIT, PAGE)).thenReturn(false);

        Response response = this.resource.insetIntoPage("xwiki", "Space", "Page", createInsertion("openproject"));

        assertEquals(401, response.getStatus());
        assertPageUnchanged();
    }

    @Test
    void insertIntoPageWithOtherMacro() throws Exception
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.insetIntoPage("xwiki", "Space", "Page", createInsertion("velocity")));

        assertEquals(400, exception.getResponse().getStatus());
        assertEquals("Only the [openproject] macro can be inserted.", exception.getResponse().getEntity());
        assertPageUnchanged();
    }

    @Test
    void insertIntoPageWithInvalidParameterName() throws Exception
    {
        MacroInsertion insertion = createInsertion("openproject");
        insertion.setMacroParameters(Map.of("identifier=\"1\"/}}text{{openproject name", "1"));

        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.insetIntoPage("xwiki", "Space", "Page", insertion));

        assertEquals(400, exception.getResponse().getStatus());
        assertEquals("Invalid macro parameter name.", exception.getResponse().getEntity());
        assertPageUnchanged();
    }

    private void assertPageUnchanged() throws XWikiException
    {
        XWikiDocument page = getPage();
        assertEquals(CONTENT, page.getContent());
        UserReference previousAuthor = this.userResolver.resolve(PREVIOUS_AUTHOR);
        assertEquals(previousAuthor, page.getAuthors().getContentAuthor());
        assertEquals(previousAuthor, page.getAuthors().getEffectiveMetadataAuthor());
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
