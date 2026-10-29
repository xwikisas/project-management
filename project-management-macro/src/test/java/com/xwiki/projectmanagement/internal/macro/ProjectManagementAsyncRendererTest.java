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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.util.ErrorBlockGenerator;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectManagementAsyncRenderer}.
 *
 * @version $Id$
 */
@ComponentTest
class ProjectManagementAsyncRendererTest
{
    private static final String CONTENT = "content";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private ProjectManagementAsyncRenderer renderer;

    @MockComponent
    private ErrorBlockGenerator errorBlockGenerator;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayer;

    private final ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();

    @BeforeEach
    void setUp()
    {
        this.renderer.initialize(this.displayer, this.parameters, CONTENT, new MacroTransformationContext(), null);
    }

    @Test
    void execute() throws Exception
    {
        Block block = new WordBlock("item");
        when(this.displayer.execute(any(), any(), any())).thenReturn(List.of(block));

        assertEquals(List.of(block), this.renderer.execute(true, false).getChildren());
    }

    @Test
    void executeAsyncWhenTheDisplayerFails() throws Exception
    {
        when(this.displayer.execute(any(), any(), any()))
            .thenThrow(new MacroExecutionException("Failed", new RuntimeException("Details")));
        Block errorBlock = new WordBlock("error");
        when(this.errorBlockGenerator.generateErrorBlocks(false, "projectmanagement.macro.async.error",
            "Failed to display the work items.", "See the server logs for more details.")).thenReturn(
            List.of(errorBlock));

        // The details of the failure don't reach the result of the asynchronous rendering.
        assertEquals(List.of(errorBlock), this.renderer.execute(true, false).getChildren());

        assertEquals("Failed to display the work items.", this.logCapture.getMessage(0));
    }

    @Test
    void executeAsyncWhenTheDisplayerThrowsARuntimeException() throws Exception
    {
        when(this.displayer.execute(any(), any(), any())).thenThrow(new ClassCastException("Details"));
        Block errorBlock = new WordBlock("error");
        when(this.errorBlockGenerator.generateErrorBlocks(anyBoolean(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(errorBlock));

        assertEquals(List.of(errorBlock), this.renderer.execute(true, false).getChildren());

        assertEquals("Failed to display the work items.", this.logCapture.getMessage(0));
    }

    @Test
    void executeSyncWhenTheDisplayerFails() throws Exception
    {
        MacroExecutionException cause = new MacroExecutionException("Failed");
        when(this.displayer.execute(any(), any(), any())).thenThrow(cause);

        // The macro calling the renderer synchronously handles the failure itself.
        RenderingException exception = assertThrows(RenderingException.class, () -> this.renderer.execute(false,
            false));

        assertSame(cause, exception.getCause());
        verify(this.errorBlockGenerator, never()).generateErrorBlocks(anyBoolean(), anyString(), anyString(),
            anyString());
    }
}
