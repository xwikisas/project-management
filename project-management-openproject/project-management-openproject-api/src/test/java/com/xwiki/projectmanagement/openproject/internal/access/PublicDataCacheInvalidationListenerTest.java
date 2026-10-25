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
package com.xwiki.projectmanagement.openproject.internal.access;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.event.Event;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.event.XObjectDeletedEvent;
import com.xpn.xwiki.internal.event.XObjectUpdatedEvent;
import com.xpn.xwiki.objects.BaseObjectReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicDataCacheInvalidationListener}.
 *
 * @version $Id$
 */
@ComponentTest
class PublicDataCacheInvalidationListenerTest
{
    @InjectMockComponents
    private PublicDataCacheInvalidationListener listener;

    @MockComponent
    private PublicDataClientProvider publicDataClientProvider;

    @Test
    void events()
    {
        assertEquals(PublicDataCacheInvalidationListener.NAME, this.listener.getName());
        assertEquals(5, this.listener.getEvents().size());
        assertTrue(this.listener.getEvents().stream().anyMatch(event -> event.matches(
            new XObjectDeletedEvent(objectReference("OpenProjectConnectionClass")))));
        assertTrue(this.listener.getEvents().stream().anyMatch(event -> event.matches(
            new XObjectUpdatedEvent(objectReference("OpenProjectConfigurationClass")))));
    }

    private BaseObjectReference objectReference(String className)
    {
        DocumentReference documentReference = new DocumentReference("wiki", List.of("OpenProject", "Code"), "Page");
        return new BaseObjectReference(
            new EntityReference("OpenProject.Code." + className + "[0]", EntityType.OBJECT, documentReference));
    }

    @Test
    void clearsTheCacheOfTheWikiOfTheDocument()
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getDocumentReference())
            .thenReturn(new DocumentReference("subwiki", "OpenProject", "Connection"));

        this.listener.onEvent(mock(Event.class), document, null);

        verify(this.publicDataClientProvider).clearCache("subwiki");
    }
}
