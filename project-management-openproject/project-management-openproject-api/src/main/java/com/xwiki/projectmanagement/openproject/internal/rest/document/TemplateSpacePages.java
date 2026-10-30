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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.xpn.xwiki.XWikiException;

/**
 * Computes the pages of a space created from the OpenProject template: the template pages are copied under the target
 * space, except those that already exist there.
 *
 * @version $Id$
 */
public final class TemplateSpacePages
{
    private static final String WEB_HOME = "WebHome";

    /**
     * Tells whether a page matches a condition, that may require reading the wiki.
     */
    @FunctionalInterface
    public interface PageCondition
    {
        /**
         * @param page the page to check
         * @return whether the page matches the condition
         * @throws XWikiException if the page can't be checked
         */
        boolean test(DocumentReference page) throws XWikiException;
    }

    private TemplateSpacePages()
    {
    }

    /**
     * @param target the home page of the space created from the template
     * @param templateSpace the space holding the template pages
     * @param templatePages the pages of the template space
     * @param exists tells whether a page exists
     * @return the pages that the create job will copy from the template children, i.e. those that don't exist yet; none
     *     when the target is not the home page of a space, since the template children are then not copied
     * @throws XWikiException if the existence of a page can't be checked
     */
    public static List<DocumentReference> getChildrenToCreate(DocumentReference target, SpaceReference templateSpace,
        Collection<DocumentReference> templatePages, PageCondition exists) throws XWikiException
    {
        List<DocumentReference> children = new ArrayList<>();
        if (!WEB_HOME.equals(target.getName())) {
            return children;
        }
        SpaceReference targetSpace = target.getLastSpaceReference();
        for (DocumentReference templatePage : templatePages) {
            DocumentReference child = templatePage.replaceParent(templateSpace, targetSpace);
            if (!child.equals(target) && !exists.test(child)) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * @param target the home page of the space created from the template
     * @param childrenToCreate the children that the create job had to create, see
     *     {@link #getChildrenToCreate(DocumentReference, SpaceReference, Collection, PageCondition)}
     * @param createdAndViewable tells whether a page exists, once the create job is done, and can be viewed by the
     *     current user; the create job skips the children that the current user is not allowed to create
     * @return the target followed by the children that were created and can be viewed
     * @throws XWikiException if the existence of a page can't be checked
     */
    public static List<DocumentReference> getCreatedPages(DocumentReference target,
        List<DocumentReference> childrenToCreate, PageCondition createdAndViewable) throws XWikiException
    {
        List<DocumentReference> createdPages = new ArrayList<>();
        createdPages.add(target);
        for (DocumentReference child : childrenToCreate) {
            if (createdAndViewable.test(child)) {
                createdPages.add(child);
            }
        }
        return createdPages;
    }
}
