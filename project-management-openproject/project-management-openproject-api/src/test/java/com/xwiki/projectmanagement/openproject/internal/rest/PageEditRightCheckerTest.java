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

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PageEditRightChecker}.
 *
 * @version $Id$
 */
@ComponentTest
class PageEditRightCheckerTest
{
    private static final String WIKI = "sub";

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @InjectMockComponents
    private PageEditRightChecker checker;

    @Test
    void canEditPageOfTheWiki()
    {
        DocumentReference page = new DocumentReference(WIKI, "Space", "Page");
        when(this.documentReferenceResolver.resolve("Space.Page")).thenReturn(page);
        when(this.authorizationManager.hasAccess(Right.EDIT, page)).thenReturn(true);

        assertTrue(this.checker.canEdit(WIKI, "Space.Page"));
    }

    @Test
    void cannotEditPage()
    {
        DocumentReference page = new DocumentReference(WIKI, "Space", "Page");
        when(this.documentReferenceResolver.resolve("Space.Page")).thenReturn(page);

        assertFalse(this.checker.canEdit(WIKI, "Space.Page"));
    }

    @Test
    void pageOfAnotherWiki()
    {
        // E.g. the profile page of a global user, which they can edit.
        DocumentReference page = new DocumentReference("xwiki", "XWiki", "Me");
        when(this.documentReferenceResolver.resolve("xwiki:XWiki.Me")).thenReturn(page);
        when(this.authorizationManager.hasAccess(Right.EDIT, page)).thenReturn(true);

        assertFalse(this.checker.canEdit(WIKI, "xwiki:XWiki.Me"));
    }

    @Test
    void withoutPage()
    {
        assertFalse(this.checker.canEdit(WIKI, ""));
        assertFalse(this.checker.canEdit(WIKI, null));
    }
}
