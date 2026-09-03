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
package com.xwiki.projectmanagement.presets;

import java.util.Collections;
import java.util.List;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.presets.internal.DefaultPresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class DefaultPresetsManagerTest
{
    private static final String DEFAULT_PRESET_ID = "Preset.MyPreset";

    private static final String DEFAULT_CLIENT_ID = "openproject";

    private static final DocumentReference DEFAULT_PRESET_REF = new DocumentReference("xwiki", "Preset", "MyPreset");

    @InjectMockComponents
    private DefaultPresetsManager presetsManager;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private DocumentReferenceResolver<String> resolver;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @Mock
    private XWikiContext context;

    @Mock
    private XWiki xWiki;

    @Mock
    private XWikiDocument document;

    @Mock
    private Query query;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.contextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.xWiki);
        when(this.resolver.resolve(DEFAULT_PRESET_ID)).thenReturn(DEFAULT_PRESET_REF);
        when(this.xWiki.getDocument(DEFAULT_PRESET_REF, this.context)).thenReturn(this.document);
        BaseObject baseObject = new BaseObject();
        when(this.document.getXObject(any(LocalDocumentReference.class))).thenReturn(baseObject);

        when(this.queryManager.createQuery(any(), any())).thenReturn(query);
        when(this.query.setOffset(anyInt())).thenReturn(this.query);
        when(this.query.setLimit(anyInt())).thenReturn(this.query);
        when(this.query.bindValue(any(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(Collections.singletonList(DEFAULT_PRESET_ID));
    }

    @Test
    public void getPresetsTest() throws QueryException
    {
        List<Preset> presets = this.presetsManager.getPresets(false, 0, 10);
        assertQueryContains("isMultiple = 0");
        assertEquals(1, presets.size());
    }

    @Test
    public void getChartPresetsTest() throws QueryException
    {
        List<Preset> presets = this.presetsManager.getPresets(true, 0, 10);
        assertQueryContains("isMultiple = 1");
        assertEquals(1, presets.size());
    }

    @Test
    public void getNoPresetsTest() throws QueryException
    {
        when(this.query.execute()).thenReturn(Collections.emptyList());
        List<Preset> presets = this.presetsManager.getPresets(true, 0, 10);
        assertEquals(0, presets.size());
    }

    @Test
    public void getPresetsFailTest() throws QueryException
    {
        when(this.query.execute()).thenThrow(QueryException.class);
        assertThrows(RuntimeException.class, () -> {
            this.presetsManager.getPresets(true, 0, 10);
        });
    }

    @Test
    public void getClientPresetsTest() throws QueryException
    {
        List<Preset> presets = this.presetsManager.getClientPresets(DEFAULT_CLIENT_ID, false, 0, 10);
        assertQueryContains("isMultiple = 0");
        verify(this.query).bindValue(any(), eq(DEFAULT_CLIENT_ID));
        assertEquals(1, presets.size());
    }

    @Test
    public void getChartClientPresetsTest() throws QueryException
    {
        List<Preset> presets = this.presetsManager.getClientPresets(DEFAULT_CLIENT_ID, true, 0, 10);
        assertQueryContains("isMultiple = 1");
        verify(this.query).bindValue(any(), eq(DEFAULT_CLIENT_ID));
        assertEquals(1, presets.size());
    }

    @Test
    public void getPresetTest()
    {
        Preset preset = this.presetsManager.getPreset(10);
        verify(this.query).bindValue(any(String.class), eq(10));
        assertNotNull(preset);
    }

    @Test
    public void getNoPresetTest() throws QueryException
    {
        when(this.query.execute()).thenReturn(Collections.emptyList());
        Preset preset = this.presetsManager.getPreset(10);
        verify(this.query).bindValue(any(String.class), eq(10));
        assertNull(preset);
    }

    @Test
    public void getPresetFailTest() throws QueryException
    {
        when(this.query.execute()).thenThrow(QueryException.class);
        assertThrows(RuntimeException.class, () -> {
            Preset preset = this.presetsManager.getPreset(10);
        });
    }

    @Test
    public void getNextIdTest() throws QueryException
    {
        int maxExistingId = 10;
        when(this.query.execute()).thenReturn(Collections.singletonList(maxExistingId));
        Integer id = this.presetsManager.getNextId();
        assertEquals(maxExistingId + 1, id);
    }

    @Test
    public void getNextIdWithNoExistingTest() throws QueryException
    {
        when(this.query.execute()).thenReturn(Collections.emptyList());
        Integer id = this.presetsManager.getNextId();
        assertEquals(1, id);
    }

    @Test
    public void getNextIdFailTest() throws QueryException
    {
        when(this.query.execute()).thenThrow(QueryException.class);
        assertThrows(RuntimeException.class, () -> {
            this.presetsManager.getNextId();
        });
    }

    private void assertQueryContains(String contains) throws QueryException
    {
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(this.queryManager).createQuery(argument.capture(), any());
        assertTrue(argument.getValue().contains(contains));
    }
}
