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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.job.JobException;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessStatus;
import com.xwiki.projectmanagement.openproject.internal.macro.OpenProjectProjectsMacro;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectsMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AbstractOpenProjectDirectMacro}, through {@link OpenProjectProjectsMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class AbstractOpenProjectDirectMacroTest
{
    @InjectMockComponents
    private OpenProjectProjectsMacro macro;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private UserTokenChecker userTokenChecker;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @Mock
    private MacroTransformationContext context;

    @Mock
    private OpenProjectApiClient apiClient;

    private final OpenProjectProjectsMacroParameters parameters = new OpenProjectProjectsMacroParameters();

    @BeforeEach
    void setUp()
    {
        this.parameters.setInstance("main");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
    }

    @Test
    void descriptor()
    {
        assertFalse(this.macro.supportsInlineMode());
        assertEquals(Set.of("OpenProject", "Content"), this.macro.getDescriptor().getDefaultCategories());
    }

    @Test
    void returnsTheMissingLicenseMessage() throws Exception
    {
        List<Block> licenseBlock = List.of(new WordBlock("license"));
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(licenseBlock);

        assertSame(licenseBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.observationManager, this.userTokenChecker, this.asyncExecutor);
    }

    @Test
    void returnsTheUserTokenWarning() throws Exception
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.notConnected());
        List<Block> warningBlock = List.of(new WordBlock("warning"));
        when(this.userTokenChecker.getWarningBlock("main", OpenProjectAccessStatus.NOT_CONNECTED, this.context))
            .thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "", this.context));

        verify(this.observationManager).notify(any(BeforeOpenProjectMacroExecutionEvent.class), same(this.macro),
            same(this.parameters));
        verifyNoInteractions(this.asyncExecutor);
    }

    @Test
    void usesTheInstanceResolvedByTheListeners() throws Exception
    {
        this.parameters.setInstance(null);
        doAnswer(invocation -> {
            this.parameters.setInstance("main");
            return null;
        }).when(this.observationManager).notify(any(BeforeOpenProjectMacroExecutionEvent.class), any(), any());
        OpenProjectMacroTestUtils.executeSynchronously(this.asyncExecutor);
        when(this.apiClient.getProjects(null, 5, "")).thenReturn(new PaginatedResult<>(List.of(), 1, 5, 0));

        this.macro.execute(this.parameters, "", this.context);

        verify(this.userTokenChecker).getWarningBlock("main", OpenProjectAccessStatus.AUTHENTICATED, this.context);
        verify(this.apiClient).getProjects(null, 5, "");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendersAsynchronouslyWithAPlaceholder() throws Exception
    {
        List<Block> rendered = List.of(new WordBlock("rendered"));
        ArgumentCaptor<Macro<ProjectManagementAsyncMacroParams>> macroCaptor = ArgumentCaptor.forClass(Macro.class);
        when(this.asyncExecutor.execute(macroCaptor.capture(), same(this.parameters), eq("content"),
            same(this.context), isNull(), eq(true))).thenReturn(rendered);

        assertSame(rendered, this.macro.execute(this.parameters, "content", this.context));

        // The wrapped macro has the same inline support as the direct macro.
        assertFalse(macroCaptor.getValue().supportsInlineMode());
        verify(this.apiClient, never()).getProjects(any(), any(), anyString());
    }

    @Test
    void wrapsAsynchronousRenderingErrors() throws Exception
    {
        JobException cause = new JobException("job failed");
        when(this.asyncExecutor.execute(any(), any(), any(), any(), any(), anyBoolean())).thenThrow(cause);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        assertEquals("Failed to render the macro asynchronously.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
