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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.openproject.OpenProjectInstanceHolder;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectAssignedToMeMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCalendarMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectChartMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectCreateWorkPackageMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectNewsMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectOpenClosedMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectDetailsMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectMembersMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectsMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSpentTimeMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSubprojectsMacroParameters;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Without a valid license, every OpenProject macro displays the missing-license message instead of its content, and
 * neither resolves its connection nor retrieves any data.
 *
 * @version $Id$
 */
@ComponentTest
class MissingLicenseTest
{
    @InjectMockComponents
    private OpenProjectMacro openProjectMacro;

    @InjectMockComponents
    private OpenProjectAssignedToMeMacro assignedToMeMacro;

    @InjectMockComponents
    private OpenProjectChartMacro chartMacro;

    @InjectMockComponents
    private OpenProjectOpenClosedMacro openClosedMacro;

    @InjectMockComponents
    private OpenProjectCalendarMacro calendarMacro;

    @InjectMockComponents
    private OpenProjectCreateWorkPackageMacro createWorkPackageMacro;

    @InjectMockComponents
    private OpenProjectNewsMacro newsMacro;

    @InjectMockComponents
    private OpenProjectProjectsMacro projectsMacro;

    @InjectMockComponents
    private OpenProjectSubprojectsMacro subprojectsMacro;

    @InjectMockComponents
    private OpenProjectProjectDetailsMacro projectDetailsMacro;

    @InjectMockComponents
    private OpenProjectProjectMembersMacro membersMacro;

    @InjectMockComponents
    private OpenProjectSpentTimeMacro spentTimeMacro;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    private final MacroTransformationContext context = new MacroTransformationContext();

    private static Arguments macro(String name, Function<MissingLicenseTest, Macro<?>> macro,
        Object parameters)
    {
        if (parameters instanceof OpenProjectInstanceHolder) {
            ((OpenProjectInstanceHolder) parameters).setInstance("main");
        }
        return Arguments.of(named(name, macro), parameters);
    }

    static Stream<Arguments> macros()
    {
        return Stream.of(
            macro("openproject", test -> test.openProjectMacro, new OpenProjectMacroParameters()),
            macro("assigned to me", test -> test.assignedToMeMacro, new OpenProjectAssignedToMeMacroParameters()),
            macro("chart", test -> test.chartMacro, new OpenProjectChartMacroParameters()),
            macro("open/closed", test -> test.openClosedMacro, new OpenProjectOpenClosedMacroParameters()),
            macro("calendar", test -> test.calendarMacro, new OpenProjectCalendarMacroParameters()),
            macro("create work package", test -> test.createWorkPackageMacro,
                new OpenProjectCreateWorkPackageMacroParameters()),
            macro("news", test -> test.newsMacro, new OpenProjectNewsMacroParameters()),
            macro("projects", test -> test.projectsMacro, new OpenProjectProjectsMacroParameters()),
            macro("subprojects", test -> test.subprojectsMacro, new OpenProjectSubprojectsMacroParameters()),
            macro("project details", test -> test.projectDetailsMacro,
                new OpenProjectProjectDetailsMacroParameters()),
            macro("members", test -> test.membersMacro, new OpenProjectProjectMembersMacroParameters()),
            macro("spent time", test -> test.spentTimeMacro, new OpenProjectSpentTimeMacroParameters()));
    }

    @ParameterizedTest
    @MethodSource("macros")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void returnsTheMissingLicenseMessage(Function<MissingLicenseTest, Macro> macro, Object parameters)
        throws Exception
    {
        List<Block> licenseBlock = List.of(new WordBlock("license"));
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(licenseBlock);

        assertSame(licenseBlock, macro.apply(this).execute(parameters, "", this.context));

        // The connection is resolved by the listeners of this event.
        verifyNoInteractions(this.observationManager, this.asyncExecutor, this.projectManagementManager);
    }
}
