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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.macro.PresetFiltersCombiner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CurrentUserFilterDetector}.
 *
 * @version $Id$
 */
@ComponentTest
class CurrentUserFilterDetectorTest
{
    private static final String ASSIGNED_TO_ME =
        "{\"query\":{\"filters\":[{\"property\":\"assignee\",\"constraints\":[{\"operator\":\"=\",\"value\":\"me\"}]}]}}";

    private static final String NEW_STATUS =
        "{\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":[{\"operator\":\"=\",\"value\":\"1\"}]}]}}";

    @InjectMockComponents
    private CurrentUserFilterDetector detector;

    @MockComponent
    private PresetFiltersCombiner presetFiltersCombiner;

    @BeforeEach
    void setUp() throws Exception
    {
        // Without a preset, the filters of the macro are kept as they are.
        when(this.presetFiltersCombiner.combine(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        ASSIGNED_TO_ME,
        "{\"query\":{\"filters\":[{\"property\":\"author\",\"constraints\":[{\"operator\":\"equals\","
            + "\"value\":[\"5\",\"me\"]}]}]}}",
        // A chart holds one configuration per dataset.
        "[" + NEW_STATUS + "," + ASSIGNED_TO_ME + "]"
    })
    void detectsTheFiltersOnTheCurrentUser(String filters)
    {
        assertTrue(this.detector.hasCurrentUserFilter(null, filters));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        NEW_STATUS,
        // "me" is then a text, not the current user.
        "{\"query\":{\"filters\":[{\"property\":\"subject\",\"constraints\":[{\"operator\":\"contains\","
            + "\"value\":\"me\"}]}]}}",
        "{\"query\":{\"filters\":[{\"property\":\"assignee\",\"constraints\":[{\"operator\":\"=\","
            + "\"value\":\"meeting\"}]}]}}",
        "{\"meta\":{}}",
        "not json"
    })
    void ignoresTheOtherFilters(String filters)
    {
        assertFalse(this.detector.hasCurrentUserFilter(null, filters));
    }

    @Test
    void detectsTheFiltersOfThePresetOnTheCurrentUser() throws Exception
    {
        when(this.presetFiltersCombiner.combine("3", NEW_STATUS)).thenReturn(ASSIGNED_TO_ME);

        assertTrue(this.detector.hasCurrentUserFilter("3", NEW_STATUS));
    }

    @Test
    void usesTheFiltersOfTheMacroWhenThePresetIsInvalid() throws Exception
    {
        when(this.presetFiltersCombiner.combine("missing", ASSIGNED_TO_ME))
            .thenThrow(new MacroExecutionException("There is no preset with id [missing]."));

        assertTrue(this.detector.hasCurrentUserFilter("missing", ASSIGNED_TO_ME));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[{\"n\":\"assigneeOrGroup\",\"o\":\"=\",\"v\":[\"me\"]}]",
        "[{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"author\",\"o\":\"!\",\"v\":[\"me\"]}]"
    })
    void detectsTheFiltersOfAnOpenProjectURLOnTheCurrentUser(String urlFilters)
    {
        assertTrue(this.detector.hasCurrentUserFilterInIdentifier(workPackagesURL(urlFilters)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "https://op.example.org/work_packages?query_id=12",
        "12,13",
        "https://op.example.org/work_packages?query_props=%7Bnot-json"
    })
    void ignoresTheIdentifiersWithoutFilterOnTheCurrentUser(String identifier)
    {
        assertFalse(this.detector.hasCurrentUserFilterInIdentifier(identifier));
    }

    @Test
    void ignoresTheTextFiltersOfAnOpenProjectURL()
    {
        assertFalse(this.detector.hasCurrentUserFilterInIdentifier(
            workPackagesURL("[{\"n\":\"subject\",\"o\":\"~\",\"v\":[\"me\"]}]")));
    }

    private static String workPackagesURL(String filters)
    {
        String properties = String.format("{\"f\":%s,\"t\":\"id:asc\"}", filters);
        return "https://op.example.org/projects/demo/work_packages?query_props="
            + URLEncoder.encode(properties, StandardCharsets.UTF_8);
    }
}
