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
package com.xwiki.projectmanagement.presets.script;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PresetsScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
class PresetsScriptServiceTest
{
    @InjectMockComponents
    private PresetsScriptService scriptService;

    @MockComponent
    private PresetsManager manager;

    @Test
    void getNextPresetId()
    {
        when(this.manager.getNextId()).thenReturn(3);

        assertEquals(3, this.scriptService.getNextPresetId());
    }

    @Test
    void getPresetsForClient()
    {
        List<Preset> presets = List.of(mock(Preset.class));
        when(this.manager.getClientPresets("openproject", true, 10, 5)).thenReturn(presets);

        assertSame(presets, this.scriptService.getPresetsForClient("openproject", true, 10, 5));
    }

    @Test
    void getPresets()
    {
        List<Preset> presets = List.of(mock(Preset.class));
        when(this.manager.getPresets(false, 0, 20)).thenReturn(presets);

        assertSame(presets, this.scriptService.getPresets(false, 0, 20));
    }
}
