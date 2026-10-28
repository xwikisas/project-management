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

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminAuthorChecker}.
 *
 * @version $Id$
 */
@ComponentTest
class AdminAuthorCheckerTest
{
    private static final WikiReference WIKI = new WikiReference("wiki");

    private static final DocumentReference ADMIN = new DocumentReference("wiki", "XWiki", "Admin");

    private static final DocumentReference USER = new DocumentReference("wiki", "XWiki", "User");

    @InjectMockComponents
    private AdminAuthorChecker checker;

    @MockComponent
    private AuthorizationManager authorizationManager;

    @MockComponent
    @Named("document")
    private UserReferenceSerializer<DocumentReference> serializer;

    @Mock
    private XWikiDocument document;

    @Mock
    private DocumentAuthors authors;

    private final UserReference adminReference = mock(UserReference.class);

    private final UserReference userReference = mock(UserReference.class);

    @BeforeEach
    void setUp()
    {
        when(this.document.getDocumentReference()).thenReturn(new DocumentReference("wiki", "Space", "Page"));
        when(this.document.getAuthors()).thenReturn(this.authors);
        when(this.serializer.serialize(this.adminReference)).thenReturn(ADMIN);
        when(this.serializer.serialize(this.userReference)).thenReturn(USER);
        when(this.authorizationManager.hasAccess(Right.ADMIN, ADMIN, WIKI)).thenReturn(true);
    }

    @Test
    void isLastSavedByAdmin()
    {
        when(this.authors.getEffectiveMetadataAuthor()).thenReturn(this.adminReference);
        when(this.authors.getContentAuthor()).thenReturn(this.adminReference);

        assertTrue(this.checker.isLastSavedByAdmin(this.document));
        assertEquals(ADMIN, this.checker.getMetadataAuthor(this.document));
    }

    @Test
    void isLastSavedByAdminWhenContentAuthorIsNotAdmin()
    {
        when(this.authors.getEffectiveMetadataAuthor()).thenReturn(this.adminReference);
        when(this.authors.getContentAuthor()).thenReturn(this.userReference);

        assertFalse(this.checker.isLastSavedByAdmin(this.document));
    }

    @Test
    void isLastSavedByAdminWhenMetadataAuthorIsNotAdmin()
    {
        when(this.authors.getEffectiveMetadataAuthor()).thenReturn(this.userReference);
        when(this.authors.getContentAuthor()).thenReturn(this.adminReference);

        assertFalse(this.checker.isLastSavedByAdmin(this.document));
    }
}
