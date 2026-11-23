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
package com.xwiki.projectmanagement.presets.internal;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.bridge.event.DocumentUpdatingEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.projectmanagement.presets.Preset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PresetHiddenListener}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class PresetHiddenListenerTest
{
    private static final DocumentReference PRESET_PAGE =
        new DocumentReference("xwiki", Arrays.asList("ProjectManagement", "Code", "Presets"), "Preset1");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private PresetHiddenListener listener;

    @Test
    void hidesCreatedPreset()
    {
        XWikiDocument document = page(Preset.REFERENCE);

        this.listener.onEvent(new DocumentCreatingEvent(), document, null);

        assertTrue(document.isHidden());
    }

    @Test
    void hidesExistingPresetWhenSaved()
    {
        XWikiDocument document = page(Preset.REFERENCE);

        this.listener.onEvent(new DocumentUpdatingEvent(), document, null);

        assertTrue(document.isHidden());
    }

    @Test
    void leavesOtherPagesVisible()
    {
        XWikiDocument document = page(new LocalDocumentReference("Space", "OtherClass"));

        this.listener.onEvent(new DocumentCreatingEvent(), document, null);

        assertFalse(document.isHidden());
    }

    private XWikiDocument page(LocalDocumentReference classReference)
    {
        XWikiDocument document = new XWikiDocument(PRESET_PAGE);
        BaseObject object = new BaseObject();
        object.setXClassReference(classReference);
        document.addXObject(object);
        return document;
    }
}
