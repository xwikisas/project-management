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
package com.xwiki.projectmanagement.internal.macro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PresetFiltersCombiner}.
 *
 * @version $Id$
 */
@ComponentTest
class PresetFiltersCombinerTest
{
    private static final String PRESET_ID = "7";

    private static final String STATUS_OPEN =
        "{\"property\":\"status\",\"constraints\":[{\"operator\":\"=\",\"value\":\"open\"}]}";

    private static final String TYPE_BUG =
        "{\"property\":\"type\",\"constraints\":[{\"operator\":\"=\",\"value\":\"bug\"}]}";

    @InjectMockComponents
    private PresetFiltersCombiner combiner;

    @MockComponent
    private PresetsManager presetsManager;

    @Test
    void combineWithoutPreset() throws Exception
    {
        String filters = config(TYPE_BUG);

        assertSame(filters, this.combiner.combine(null, filters));
        assertSame(filters, this.combiner.combine("", filters));
        verify(this.presetsManager, never()).getPreset(any());
    }

    @Test
    void combineWithNonNumericPresetId()
    {
        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.combiner.combine("abc", null));

        assertEquals("The preset id should be a number.", exception.getMessage());
    }

    @Test
    void combineWithMissingPreset()
    {
        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.combiner.combine(PRESET_ID, null));

        assertEquals("There is no preset with id [7].", exception.getMessage());
    }

    @Test
    void combineWhenOnlyOneSideFilters() throws Exception
    {
        String presetFilters = config(STATUS_OPEN);
        String macroFilters = config(TYPE_BUG);

        setPresetFilter(presetFilters);
        assertSame(presetFilters, this.combiner.combine(PRESET_ID, null));
        assertSame(presetFilters, this.combiner.combine(PRESET_ID, " "));
        assertSame(presetFilters, this.combiner.combine(PRESET_ID, "[]"));

        setPresetFilter(null);
        assertSame(macroFilters, this.combiner.combine(PRESET_ID, macroFilters));
        setPresetFilter("[]");
        assertSame(macroFilters, this.combiner.combine(PRESET_ID, macroFilters));
    }

    @Test
    void combineFiltersOnDifferentProperties() throws Exception
    {
        setPresetFilter(config(STATUS_OPEN));

        // The macro narrows down the preset: the filters on different properties are combined with AND.
        assertEquals(config(STATUS_OPEN + ',' + TYPE_BUG), this.combiner.combine(PRESET_ID, config(TYPE_BUG)));
    }

    @Test
    void combineFiltersOnTheSameProperty() throws Exception
    {
        setPresetFilter(config(STATUS_OPEN + ',' + TYPE_BUG));

        // A single filter is kept for the property, holding the constraints of both sides, which must all match.
        assertEquals(config(STATUS_OPEN + ",{\"property\":\"type\",\"constraints\":["
            + "{\"operator\":\"=\",\"value\":\"bug\"},{\"operator\":\"!\",\"value\":\"task\"}],\"matchAll\":true}"),
            this.combiner.combine(PRESET_ID, config("{\"property\":\"type\",\"matchAll\":false,"
                + "\"constraints\":[{\"operator\":\"!\",\"value\":\"task\"}]}")));
    }

    @Test
    void combineFiltersOnAPropertyWithoutPresetConstraints() throws Exception
    {
        setPresetFilter(config("{\"property\":\"type\"}"));

        // Only the macro restricts the property, so its own matchAll is kept.
        assertEquals(config("{\"property\":\"type\",\"matchAll\":false,"
            + "\"constraints\":[{\"operator\":\"=\",\"value\":\"bug\"},{\"operator\":\"=\",\"value\":\"task\"}]}"),
            this.combiner.combine(PRESET_ID, config("{\"property\":\"type\",\"matchAll\":false,\"constraints\":["
                + "{\"operator\":\"=\",\"value\":\"bug\"},{\"operator\":\"=\",\"value\":\"task\"}]}")));
        assertEquals(config(TYPE_BUG), this.combiner.combine(PRESET_ID, config(TYPE_BUG)));
        // A macro filter without constraints doesn't change the preset filter.
        setPresetFilter(config(TYPE_BUG));
        assertEquals(config(TYPE_BUG), this.combiner.combine(PRESET_ID, config("{\"property\":\"type\"}")));
    }

    @Test
    void combineKeepsTheOtherSettings() throws Exception
    {
        setPresetFilter("{\"meta\":{\"a\":1},\"query\":{\"limit\":5,\"sort\":[\"preset\"],\"filters\":["
            + STATUS_OPEN + "]}}");

        // The settings of the macro take precedence over the ones of the preset.
        assertEquals("{\"meta\":{\"a\":2},\"query\":{\"limit\":5,\"sort\":[\"macro\"],\"filters\":[" + STATUS_OPEN
            + ',' + TYPE_BUG + "]},\"id\":\"x\"}", this.combiner.combine(PRESET_ID,
            "{\"meta\":{\"a\":2},\"id\":\"x\",\"query\":{\"sort\":[\"macro\"],\"filters\":[" + TYPE_BUG + "]}}"));

        // A preset without query.
        setPresetFilter("{\"meta\":{}}");
        assertEquals("{\"meta\":{},\"query\":{\"filters\":[" + TYPE_BUG + "]}}",
            this.combiner.combine(PRESET_ID, config(TYPE_BUG)));
    }

    @Test
    void combineDatasets() throws Exception
    {
        // The filters of the macro are applied to each dataset of the preset.
        setPresetFilter('[' + config(STATUS_OPEN) + ',' + config("") + ']');
        assertEquals('[' + config(STATUS_OPEN + ',' + TYPE_BUG) + ',' + config(TYPE_BUG) + ']',
            this.combiner.combine(PRESET_ID, config(TYPE_BUG)));

        // The filters of the preset are applied to each dataset of the macro.
        setPresetFilter(config(STATUS_OPEN));
        assertEquals('[' + config(STATUS_OPEN + ',' + TYPE_BUG) + ',' + config(STATUS_OPEN) + ']',
            this.combiner.combine(PRESET_ID, '[' + config(TYPE_BUG) + ',' + config("") + ']'));
    }

    @Test
    void combineSeveralDatasetsOnBothSides()
    {
        setPresetFilter('[' + config(STATUS_OPEN) + ',' + config("") + ']');

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.combiner.combine(PRESET_ID, '[' + config(TYPE_BUG) + ',' + config("") + ']'));

        assertEquals("The filters of the macro and the preset can't both define several datasets.",
            exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{\"query\":{\"filters\":[]}}|not json",
        "{\"query\":{\"filters\":[]}}|[1]",
        "{\"query\":{\"filters\":[1]}}|{\"query\":{\"filters\":[]}}",
        "{\"query\":{\"filters\":[]}}|{\"query\":{\"filters\":[1]}}"
    })
    void combineInvalidFilters(String presetFilters, String macroFilters)
    {
        setPresetFilter(presetFilters);

        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.combiner.combine(PRESET_ID, macroFilters));

        assertEquals("Failed to combine the filters of the preset [7] with the filters of the macro.",
            exception.getMessage());
    }

    private void setPresetFilter(String filter)
    {
        Preset preset = mock(Preset.class);
        when(preset.getFilter()).thenReturn(filter);
        when(this.presetsManager.getPreset(7)).thenReturn(preset);
    }

    private static String config(String filters)
    {
        return "{\"query\":{\"filters\":[" + filters + "]}}";
    }
}
