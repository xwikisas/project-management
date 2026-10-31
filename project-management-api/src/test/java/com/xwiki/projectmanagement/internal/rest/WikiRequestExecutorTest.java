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
package com.xwiki.projectmanagement.internal.rest;

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WikiRequestExecutor}.
 *
 * @version $Id$
 */
@ComponentTest
class WikiRequestExecutorTest
{
    private static final String WIKI = "subwiki";

    private static final String CURRENT_WIKI = "xwiki";

    @InjectMockComponents
    private WikiRequestExecutor executor;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private final XWikiContext xcontext = new XWikiContext();

    /**
     * The context wiki while the request is handled.
     */
    private String requestWiki;

    @BeforeEach
    void setUp() throws Exception
    {
        this.xcontext.setWikiId(CURRENT_WIKI);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.authorizationManager.hasAccess(Right.VIEW, new WikiReference(WIKI))).thenReturn(true);
        when(this.wikiDescriptorManager.exists(WIKI)).thenReturn(true);
    }

    @Test
    void execute()
    {
        Response response = Response.ok("result").build();

        assertSame(response, this.executor.execute(WIKI, () -> {
            this.requestWiki = this.xcontext.getWikiId();
            return response;
        }));

        // The request is handled with the wiki of the request as context wiki, which is restored after.
        assertEquals(WIKI, this.requestWiki);
        assertEquals(CURRENT_WIKI, this.xcontext.getWikiId());
    }

    @Test
    void executeWhenTheRequestFails()
    {
        Exception expected = new Exception("Failure");

        Exception exception = assertThrows(Exception.class, () -> this.executor.execute(WIKI, () -> {
            this.requestWiki = this.xcontext.getWikiId();
            throw expected;
        }));

        assertSame(expected, exception);
        assertEquals(WIKI, this.requestWiki);
        assertEquals(CURRENT_WIKI, this.xcontext.getWikiId());
    }

    @Test
    void executeWithoutViewRightOnTheWiki()
    {
        when(this.authorizationManager.hasAccess(Right.VIEW, new WikiReference(WIKI))).thenReturn(false);
        when(this.authorizationManager.hasAccess(Right.VIEW)).thenReturn(true);

        Response response = this.executor.execute(WIKI, this::failRequest);

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    void executeOnMissingWiki() throws Exception
    {
        when(this.wikiDescriptorManager.exists(WIKI)).thenReturn(false);

        Response response = this.executor.execute(WIKI, this::failRequest);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        // The rights aren't checked on a missing wiki.
        verifyNoInteractions(this.authorizationManager);
        assertEquals(CURRENT_WIKI, this.xcontext.getWikiId());
    }

    @Test
    void executeWhenTheWikiCantBeChecked() throws Exception
    {
        when(this.wikiDescriptorManager.exists(WIKI)).thenThrow(new WikiManagerException("Database down"));

        Response response = this.executor.execute(WIKI, this::failRequest);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("Failed to check if the wiki exists.", response.getEntity());
        assertEquals("Failed to check if the wiki exists. Root cause: [WikiManagerException: Database down]",
            this.logCapture.getMessage(0));
        assertEquals(CURRENT_WIKI, this.xcontext.getWikiId());
    }

    private Response failRequest()
    {
        return fail("The request must not be handled.");
    }
}
