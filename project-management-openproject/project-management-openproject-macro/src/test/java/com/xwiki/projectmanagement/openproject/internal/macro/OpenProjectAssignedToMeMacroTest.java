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
package com.xwiki.projectmanagement.openproject.internal.macro;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.macro.PresetFiltersCombiner;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectAssignedToMeMacroParameters;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectAssignedToMeMacro}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(PresetFiltersCombiner.class)
class OpenProjectAssignedToMeMacroTest
{
    private static final String STATUS_NEW =
        "{\"property\":\"status\",\"constraints\":[{\"operator\":\"=\",\"value\":\"1\"}]}";

    private static final String ASSIGNED_TO_ME =
        "{\"property\":\"assignees\",\"constraints\":[{\"operator\":\"=\",\"value\":\"me\"}]}";

    @InjectMockComponents
    private OpenProjectAssignedToMeMacro macro;

    @MockComponent
    private OpenProjectIdentifierResolver identifierResolver;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private PresetsManager presetsManager;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private UserTokenChecker userTokenChecker;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayerMacro;

    @Mock
    private OpenProjectApiClient apiClient;

    @Test
    void processParametersFiltersTheWorkPackagesAssignedToTheCurrentUser()
    {
        OpenProjectAssignedToMeMacroParameters parameters = new OpenProjectAssignedToMeMacroParameters();
        parameters.setInstance("main");
        parameters.setFilters("ignored");
        parameters.setIdentifier("ignored");

        this.macro.processParameters(parameters);

        assertEquals("{\"query\":{\"filters\":[{\"property\":\"assignees\","
            + "\"constraints\":[{\"operator\":\"=\",\"value\":\"me\"}]}]}}", parameters.getFilters());
        // The identifier is not passed to the live data source.
        assertEquals("client=openproject&instance=main&translationPrefix=openproject.",
            parameters.getSourceParameters());
    }

    @Test
    void executeCombinesThePresetWithTheAssignedToMeFilter() throws Exception
    {
        // The preset narrows down the work packages assigned to the current user instead of replacing the filter.
        assertEquals("{\"query\":{\"filters\":[" + STATUS_NEW + ',' + ASSIGNED_TO_ME + "]}}",
            executeWithPreset(STATUS_NEW));
    }

    @Test
    void executeKeepsTheAssignedToMeFilterOverThePresetOne() throws Exception
    {
        // The "assigned to me" filter can't be overridden by the preset.
        assertEquals("{\"query\":{\"filters\":[" + STATUS_NEW + ',' + ASSIGNED_TO_ME + "]}}",
            executeWithPreset("{\"property\":\"assignees\",\"constraints\":[{\"operator\":\"=\",\"value\":\"5\"}]},"
                + STATUS_NEW));
    }

    private String executeWithPreset(String presetFilters) throws Exception
    {
        Preset preset = mock(Preset.class);
        when(preset.getFilter()).thenReturn("{\"query\":{\"filters\":[" + presetFilters + "]}}");
        when(this.presetsManager.getPreset(3)).thenReturn(preset);
        when(this.componentManager.getInstance(Macro.class, "proj-manag-liveData")).thenReturn(this.displayerMacro);
        OpenProjectAssignedToMeMacroParameters parameters = new OpenProjectAssignedToMeMacroParameters();
        parameters.setInstance("main");
        parameters.setPresetId("3");

        this.macro.execute(parameters, "", new MacroTransformationContext());

        return parameters.getFilters();
    }

    @Test
    void asyncProcessParametersDoesNotResolveSavedQueries() throws Exception
    {
        OpenProjectAssignedToMeMacroParameters parameters = new OpenProjectAssignedToMeMacroParameters();
        parameters.setInstance("main");
        parameters.setIdentifier("saved");
        // The parent macro would add the URL of the results of this saved query.
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        this.macro.asyncProcessParameters(parameters);

        assertNull(parameters.getSourceParameters());
    }
}
