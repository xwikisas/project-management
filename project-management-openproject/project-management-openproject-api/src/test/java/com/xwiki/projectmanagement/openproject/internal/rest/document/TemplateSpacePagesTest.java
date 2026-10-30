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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TemplateSpacePages}.
 *
 * @version $Id$
 */
class TemplateSpacePagesTest
{
    private static final DocumentReference TEMPLATE =
        new DocumentReference("xwiki", List.of("OpenProject", "Code", "Template"), "WebHome");

    private static final SpaceReference TEMPLATE_SPACE = TEMPLATE.getLastSpaceReference();

    private static final DocumentReference TARGET = new DocumentReference("xwiki", List.of("Space"), "WebHome");

    private static final List<DocumentReference> TEMPLATE_PAGES = List.of(TEMPLATE,
        templatePage(List.of("Child"), "WebHome"), templatePage(List.of("Existing"), "WebHome"),
        templatePage(List.of(), "Page"), templatePage(List.of("Child", "Nested"), "WebHome"));

    @Test
    void getChildrenToCreateSkipsTheTargetAndTheExistingPages() throws Exception
    {
        Set<DocumentReference> existing = Set.of(page(List.of("Existing"), "WebHome"));

        List<DocumentReference> children =
            TemplateSpacePages.getChildrenToCreate(TARGET, TEMPLATE_SPACE, TEMPLATE_PAGES, existing::contains);

        assertEquals(List.of(page(List.of("Child"), "WebHome"), page(List.of(), "Page"),
            page(List.of("Child", "Nested"), "WebHome")), children);
    }

    @Test
    void getChildrenToCreateInANestedTargetSpace() throws Exception
    {
        DocumentReference target = new DocumentReference("xwiki", List.of("Parent", "Space"), "WebHome");

        List<DocumentReference> children = TemplateSpacePages.getChildrenToCreate(target, TEMPLATE_SPACE,
            List.of(TEMPLATE, templatePage(List.of("Child"), "WebHome")), page -> false);

        assertEquals(List.of(new DocumentReference("xwiki", List.of("Parent", "Space", "Child"), "WebHome")),
            children);
    }

    @Test
    void getChildrenToCreateWhenTheTargetIsNotASpaceHomePage() throws Exception
    {
        DocumentReference target = new DocumentReference("xwiki", List.of("Space"), "Page");

        assertEquals(List.of(),
            TemplateSpacePages.getChildrenToCreate(target, TEMPLATE_SPACE, TEMPLATE_PAGES, page -> false));
    }

    @Test
    void getCreatedPagesKeepsTheTargetAndTheCreatedViewableChildren() throws Exception
    {
        DocumentReference created = page(List.of("Created"), "WebHome");
        DocumentReference notCreatedOrDenied = page(List.of("Denied"), "WebHome");
        DocumentReference otherCreated = page(List.of(), "Other");

        List<DocumentReference> pages = TemplateSpacePages.getCreatedPages(TARGET,
            List.of(created, notCreatedOrDenied, otherCreated), Set.of(created, otherCreated)::contains);

        assertEquals(List.of(TARGET, created, otherCreated), pages);
    }

    @Test
    void getCreatedPagesWithoutChildren() throws Exception
    {
        assertEquals(List.of(TARGET), TemplateSpacePages.getCreatedPages(TARGET, List.of(), page -> true));
    }

    private static DocumentReference templatePage(List<String> spaces, String name)
    {
        return reference(List.of("OpenProject", "Code", "Template"), spaces, name);
    }

    private static DocumentReference page(List<String> spaces, String name)
    {
        return reference(List.of("Space"), spaces, name);
    }

    private static DocumentReference reference(List<String> parentSpaces, List<String> spaces, String name)
    {
        List<String> allSpaces = new ArrayList<>(parentSpaces);
        allSpaces.addAll(spaces);
        return new DocumentReference("xwiki", allSpaces, name);
    }
}
