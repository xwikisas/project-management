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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

/**
 * Checks that the current user can edit a page of a given wiki, e.g. the page whose macro is edited, before giving
 * them information meant for the editors of the wiki (such as the server URL of an OpenProject connection).
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = PageEditRightChecker.class)
@Singleton
public class PageEditRightChecker
{
    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    /**
     * @param wiki the wiki, which must be the current one
     * @param document the reference of the page, resolved in the current wiki
     * @return {@code true} if the page is on the given wiki and the current user can edit it, {@code false} otherwise
     *     (e.g. for a page of another wiki, which doesn't give access to the information of the given wiki)
     */
    public boolean canEdit(String wiki, String document)
    {
        if (StringUtils.isBlank(document)) {
            return false;
        }
        DocumentReference documentReference = this.documentReferenceResolver.resolve(document);
        return documentReference.getWikiReference().getName().equals(wiki)
            && this.authorizationManager.hasAccess(Right.EDIT, documentReference);
    }
}
