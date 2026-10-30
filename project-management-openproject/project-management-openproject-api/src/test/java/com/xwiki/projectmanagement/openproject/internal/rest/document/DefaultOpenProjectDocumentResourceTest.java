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
package com.xwiki.projectmanagement.openproject.internal.rest.document;

import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.query.Query;
import org.xwiki.rest.internal.ModelFactory;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.urlshortener.URLShortenerManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectDocumentResource}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class DefaultOpenProjectDocumentResourceTest
{
    private static final DocumentReference SPACE_HOME = new DocumentReference("xwiki", "Space", "WebHome");

    private static final DocumentReference TERMINAL_PAGE = new DocumentReference("xwiki", "Space", "Page");

    private static final String TERMINAL_PAGE_REFERENCE = "Space.Page";

    private static final String PAGE_ID = "abc";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private DefaultOpenProjectDocumentResource resource;

    @MockComponent
    private URLShortenerManager urlShortenerManager;

    // The conversion between the REST page and the wiki page is not part of the contract of this resource.
    @MockComponent
    private ModelFactory modelFactory;

    // Needed to save the page through its API (missing from MockitoOldcore in XWiki 15.10).
    @MockComponent
    private UserReferenceResolver<CurrentUserReference> currentUserReferenceResolver;

    @MockComponent
    private ObservationManager observationManager;

    private Query query;

    @BeforeEach
    void setUp() throws Exception
    {
        this.query = mock(Query.class);
        when(this.oldcore.getQueryManager().createQuery(anyString(), eq(Query.XWQL))).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.setWiki(anyString())).thenReturn(this.query);

        // Injected by JAX-RS in production.
        ReflectionUtils.setFieldValue(this.resource, "uriInfo", mock(UriInfo.class));
        when(this.modelFactory.toRestPage(any(), any(), any(), anyBoolean(), any(), any(), any(), any()))
            .thenAnswer(invocation -> new Page());

        when(this.urlShortenerManager.createShortenedURL(TERMINAL_PAGE)).thenReturn(PAGE_ID);

        // Everything is allowed, except what a test denies.
        when(this.oldcore.getMockRightService().hasAccessLevel(anyString(), anyString(), anyString(), any()))
            .thenReturn(true);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(eq(Right.EDIT), any(), any())).thenReturn(true);
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW, TERMINAL_PAGE))
            .thenReturn(true);
    }

    @Test
    void findAncestorsOfTerminalPageWhenSpaceHomeIsMissing() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of());

        assertEquals(List.of(TERMINAL_PAGE), this.resource.findAncestors(TERMINAL_PAGE));
    }

    @Test
    void findAncestorsOfTerminalPage() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of("Space.WebHome"));

        assertEquals(List.of(SPACE_HOME, TERMINAL_PAGE), this.resource.findAncestors(TERMINAL_PAGE));
    }

    @Test
    void findAncestorsOfSpaceHome() throws Exception
    {
        when(this.query.execute()).thenReturn(List.of("Space.WebHome"));

        assertEquals(List.of(SPACE_HOME), this.resource.findAncestors(SPACE_HOME));
    }

    @Test
    void updateDocumentCreatesThePageWithItsId() throws Exception
    {
        Response response = this.resource.updateDocument(TERMINAL_PAGE_REFERENCE, false, true, new Page());

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals(PAGE_ID, ((Page) response.getEntity()).getId());
        assertFalse(getPage().isNew());
    }

    @Test
    void updateDocumentWhenThePageExistsAndCreationIsRequested() throws Exception
    {
        savePage();
        String version = getPage().getVersion();

        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.updateDocument(TERMINAL_PAGE_REFERENCE, false, true, new Page()));

        assertEquals(Response.Status.CONFLICT.getStatusCode(), exception.getResponse().getStatus());
        assertEquals(version, getPage().getVersion());
    }

    @Test
    void updateDocumentWhenThePageIsUnchanged() throws Exception
    {
        savePage();
        String version = getPage().getVersion();

        Response response = this.resource.updateDocument(TERMINAL_PAGE_REFERENCE, false, false, new Page());

        // The page is sent back with its id, even if nothing was saved.
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        assertEquals(PAGE_ID, ((Page) response.getEntity()).getId());
        assertEquals(version, getPage().getVersion());
    }

    @Test
    void updateDocumentWhenThePageIsNotViewable() throws Exception
    {
        // Only the view right checked before attaching the id is missing.
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW, TERMINAL_PAGE))
            .thenReturn(false);

        Response response = this.resource.updateDocument(TERMINAL_PAGE_REFERENCE, false, true, new Page());

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertEquals("Access denied in view mode. Can't attach unique identifier.", response.getEntity());
        // No id is created for a page the user can't view.
        verify(this.urlShortenerManager, never()).createShortenedURL(any());
    }

    @Test
    void updateDocumentWhenThePageIsNotEditable() throws Exception
    {
        when(this.oldcore.getMockRightService().hasAccessLevel(eq("edit"), anyString(), anyString(), any()))
            .thenReturn(false);

        Response response = this.resource.updateDocument(TERMINAL_PAGE_REFERENCE, false, true, new Page());

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertTrue(getPage().isNew());
    }

    @Test
    void updateDocumentWithoutReference() throws Exception
    {
        Response response = this.resource.updateDocument("", false, true, new Page());

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("Missing `docRef` query parameter pointing to the document that needs creation/updating.",
            response.getEntity());
    }

    private void savePage() throws Exception
    {
        XWikiContext context = this.oldcore.getXWikiContext();
        this.oldcore.getSpyXWiki().saveDocument(new XWikiDocument(TERMINAL_PAGE), context);
    }

    private XWikiDocument getPage() throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(TERMINAL_PAGE, this.oldcore.getXWikiContext());
    }
}
