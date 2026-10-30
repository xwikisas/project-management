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

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultOpenProjectDocumentResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultOpenProjectDocumentResourceTest
{
    private static final DocumentReference SPACE_HOME = new DocumentReference("xwiki", "Space", "WebHome");

    private static final DocumentReference TERMINAL_PAGE = new DocumentReference("xwiki", "Space", "Page");

    @InjectMockComponents
    private DefaultOpenProjectDocumentResource resource;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private DocumentReferenceResolver<String> resolver;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    private Query query;

    @BeforeEach
    void setUp() throws Exception
    {
        this.query = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), eq(Query.XWQL))).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.setWiki(anyString())).thenReturn(this.query);
        when(this.serializer.serialize(any(EntityReference.class))).thenReturn("Space");
        when(this.resolver.resolve(eq("Space.WebHome"), any(DocumentReference.class))).thenReturn(SPACE_HOME);
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
}
