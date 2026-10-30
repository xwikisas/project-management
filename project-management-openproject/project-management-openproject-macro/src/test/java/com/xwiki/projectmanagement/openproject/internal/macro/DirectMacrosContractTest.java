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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectInstanceHolder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.AbstractOpenProjectDirectMacro;
import com.xwiki.projectmanagement.openproject.macro.BaseDirectOpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectNewsMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectDetailsMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectMembersMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectsMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSpentTimeMacroParameters;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSubprojectsMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The rules that all the macros extending {@link AbstractOpenProjectDirectMacro} follow for their project parameter
 * and for the errors of the OpenProject API.
 *
 * @version $Id$
 */
@ComponentTest
class DirectMacrosContractTest extends AbstractDirectMacroTest
{
    private static final String UNRESOLVED_DASHBOARD_PROJECT = "use_selected_dashboard_project";

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
    private Provider<Clock> clockProvider;

    /**
     * Makes the OpenProject API call of a macro fail with the given cause.
     */
    @FunctionalInterface
    interface FailingCall
    {
        void stub(OpenProjectApiClient apiClient, ProjectManagementException cause) throws Exception;
    }

    @BeforeEach
    void setUp()
    {
        when(this.clockProvider.get()).thenReturn(Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"),
            ZoneId.of("UTC")));
    }

    private static Arguments macro(String name, Function<DirectMacrosContractTest,
        AbstractOpenProjectDirectMacro<?>> macro, OpenProjectInstanceHolder parameters, Object... others)
    {
        parameters.setInstance("main");
        Object[] arguments = new Object[others.length + 2];
        arguments[0] = named(name, macro);
        arguments[1] = parameters;
        System.arraycopy(others, 0, arguments, 2, others.length);
        return Arguments.of(arguments);
    }

    private static BaseDirectOpenProjectMacroParameters withProject(BaseDirectOpenProjectMacroParameters parameters,
        String project)
    {
        parameters.setProject(project);
        return parameters;
    }

    static Stream<Arguments> failingCalls()
    {
        return Stream.of(
            macro("news", test -> test.newsMacro, new OpenProjectNewsMacroParameters(),
                (FailingCall) (client, cause) -> when(client.getNews(any(), any(), anyString())).thenThrow(cause),
                "Failed to retrieve news from OpenProject."),
            macro("projects", test -> test.projectsMacro, new OpenProjectProjectsMacroParameters(),
                (FailingCall) (client, cause) -> when(client.getProjects(any(), any(), anyString())).thenThrow(cause),
                "Failed to retrieve projects from OpenProject."),
            macro("subprojects", test -> test.subprojectsMacro,
                withProject(new OpenProjectSubprojectsMacroParameters(), "5"),
                (FailingCall) (client, cause) -> when(client.getProject(anyInt())).thenThrow(cause),
                "Failed to retrieve subprojects for project [5]."),
            macro("project details", test -> test.projectDetailsMacro,
                withProject(new OpenProjectProjectDetailsMacroParameters(), "7"),
                (FailingCall) (client, cause) -> when(client.getProject(anyInt())).thenThrow(cause),
                "Failed to retrieve project [7] from OpenProject."),
            macro("members", test -> test.membersMacro,
                withProject(new OpenProjectProjectMembersMacroParameters(), "12"),
                (FailingCall) (client, cause) -> when(client.getMemberships(any(), any(), anyString()))
                    .thenThrow(cause),
                "Failed to retrieve members for project [12]."),
            macro("spent time", test -> test.spentTimeMacro, new OpenProjectSpentTimeMacroParameters(),
                (FailingCall) (client, cause) -> when(client.getTimeEntries(any(), any(), anyString()))
                    .thenThrow(cause),
                "Failed to retrieve time entries from OpenProject."));
    }

    @ParameterizedTest
    @MethodSource("failingCalls")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void failsWhenTheDataCannotBeRetrieved(Function<DirectMacrosContractTest, AbstractOpenProjectDirectMacro> macro,
        OpenProjectInstanceHolder parameters, FailingCall failingCall, String message) throws Exception
    {
        ProjectManagementException cause = new ProjectManagementException("unreachable");
        failingCall.stub(this.apiClient, cause);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> macro.apply(this).execute(parameters, "", this.context));

        assertEquals(message, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    static Stream<Arguments> macrosOfAProjectId()
    {
        return Stream.of("", UNRESOLVED_DASHBOARD_PROJECT).flatMap(project -> Stream.of(
            macro("subprojects", test -> test.subprojectsMacro,
                withProject(new OpenProjectSubprojectsMacroParameters(), project), project),
            macro("project details", test -> test.projectDetailsMacro,
                withProject(new OpenProjectProjectDetailsMacroParameters(), project), project)));
    }

    @ParameterizedTest
    @MethodSource("macrosOfAProjectId")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void failsWhenTheProjectIsNotAnId(Function<DirectMacrosContractTest, AbstractOpenProjectDirectMacro> macro,
        OpenProjectInstanceHolder parameters, String project)
    {
        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> macro.apply(this).execute(parameters, "", this.context));

        assertEquals(String.format("The project [%s] is not a valid OpenProject project id.", project),
            exception.getMessage());
        verifyNoInteractions(this.apiClient);
    }

    static Stream<Arguments> macrosAcceptingTheDashboardProject()
    {
        return Stream.of(
            macro("news", test -> test.newsMacro,
                withProject(new OpenProjectNewsMacroParameters(), UNRESOLVED_DASHBOARD_PROJECT)),
            macro("members", test -> test.membersMacro,
                withProject(new OpenProjectProjectMembersMacroParameters(), UNRESOLVED_DASHBOARD_PROJECT)),
            macro("spent time", test -> test.spentTimeMacro,
                withProject(new OpenProjectSpentTimeMacroParameters(), UNRESOLVED_DASHBOARD_PROJECT)));
    }

    @ParameterizedTest
    @MethodSource("macrosAcceptingTheDashboardProject")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void failsWhenTheDashboardProjectIsNotResolved(
        Function<DirectMacrosContractTest, AbstractOpenProjectDirectMacro> macro,
        OpenProjectInstanceHolder parameters)
    {
        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> macro.apply(this).execute(parameters, "", this.context));

        assertEquals("The project selected on the OpenProject dashboard couldn't be resolved. Select a project "
            + "explicitly when the macro is not on the dashboard.", exception.getMessage());
        verifyNoInteractions(this.apiClient);
    }
}
