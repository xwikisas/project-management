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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Checks whether the authors of a page have administration right on the page's wiki.
 *
 * @version $Id$
 */
@Component(roles = AdminAuthorChecker.class)
@Singleton
public class AdminAuthorChecker
{
    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    @Named("document")
    private UserReferenceSerializer<DocumentReference> documentUserSerializer;

    /**
     * @param document the page to check
     * @return the user who last saved the given page (its effective metadata author)
     */
    public DocumentReference getMetadataAuthor(XWikiDocument document)
    {
        return this.documentUserSerializer.serialize(document.getAuthors().getEffectiveMetadataAuthor());
    }

    /**
     * @param user the user to check
     * @param wiki the wiki on which to check the right
     * @return {@code true} if the given user has administration right on the given wiki
     */
    public boolean isAdmin(DocumentReference user, WikiReference wiki)
    {
        return this.authorizationManager.hasAccess(Right.ADMIN, user, wiki);
    }

    /**
     * @param document the page to check
     * @return {@code true} if both the content author and the effective metadata author of the given page have
     *     administration right on the page's wiki
     */
    public boolean isLastSavedByAdmin(XWikiDocument document)
    {
        WikiReference wiki = document.getDocumentReference().getWikiReference();
        return isAdmin(getMetadataAuthor(document), wiki)
            && isAdmin(serialize(document.getAuthors().getContentAuthor()), wiki);
    }

    private DocumentReference serialize(UserReference userReference)
    {
        return this.documentUserSerializer.serialize(userReference);
    }
}
