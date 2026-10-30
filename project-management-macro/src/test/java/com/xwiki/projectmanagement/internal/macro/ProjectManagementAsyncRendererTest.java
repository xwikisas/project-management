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
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MacroMarkerBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.util.ErrorBlockGenerator;
import org.xwiki.skinx.SkinExtension;
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

    private static final String ASYNC_ERROR_CLASS = "proj-manag-async-error";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private ProjectManagementAsyncRenderer renderer;

    @MockComponent
    private ErrorBlockGenerator errorBlockGenerator;

    @MockComponent
    @Named("jsrx")
    private SkinExtension jsrx;

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
        MacroExecutionException exception =
            new MacroExecutionException("Failed", new RuntimeException("Details"));
        when(this.displayer.execute(any(), any(), any())).thenThrow(exception);
        Block errorBlock = new WordBlock("error");
        when(this.errorBlockGenerator.generateErrorBlocks(false, "projectmanagement.macro.async.error",
            "Failed to display the work items.", null, exception)).thenReturn(List.of(errorBlock));

        // The failure is displayed with its cause and its details, like a failing synchronous macro.
        List<Block> result = this.renderer.execute(true, false).getChildren();

        // The error is wrapped, so that the script made available makes it expandable once inserted in the page.
        assertEquals(List.of(new GroupBlock(List.of(errorBlock), Map.of("class", ASYNC_ERROR_CLASS))), result);
        verify(this.jsrx).use("js/projectmanagement/asyncError.js");

        assertEquals("Failed to display the work items.", this.logCapture.getMessage(0));
        assertEquals("Failed", this.logCapture.getLogEvent(0).getThrowableProxy().getMessage());
    }

    @Test
    void executeAsyncWhenTheDisplayerThrowsARuntimeException() throws Exception
    {
        ClassCastException exception = new ClassCastException("Details");
        when(this.displayer.execute(any(), any(), any())).thenThrow(exception);
        Block errorBlock = new WordBlock("error");
        when(this.errorBlockGenerator.generateErrorBlocks(false, "projectmanagement.macro.async.error",
            "Failed to display the work items.", null, exception)).thenReturn(List.of(errorBlock));

        assertEquals(List.of(new GroupBlock(List.of(errorBlock), Map.of("class", ASYNC_ERROR_CLASS))),
            this.renderer.execute(true, false).getChildren());

        assertEquals("Failed to display the work items.", this.logCapture.getMessage(0));
    }

    @Test
    void executeAsyncWhenTheDisplayerOfAMacroFails() throws Exception
    {
        MacroTransformationContext context = new MacroTransformationContext();
        MacroBlock macroBlock = new MacroBlock("openproject", Map.of("instance", "test"), true);
        context.setCurrentMacroBlock(macroBlock);
        context.setInline(true);
        this.renderer.initialize(this.displayer, this.parameters, CONTENT, context, null);
        MacroExecutionException exception = new MacroExecutionException("Failed");
        when(this.displayer.execute(any(), any(), any())).thenThrow(exception);
        Block errorBlock = new WordBlock("error");
        when(this.errorBlockGenerator.generateErrorBlocks(true, "projectmanagement.macro.async.error",
            "Failed to display the work items.", null, exception)).thenReturn(List.of(errorBlock));

        // The error is wrapped in a macro marker, like the result of a synchronous macro.
        List<Block> result = this.renderer.execute(true, false).getChildren();

        assertEquals(1, result.size());
        MacroMarkerBlock marker = (MacroMarkerBlock) result.get(0);
        assertEquals("openproject", marker.getId());
        assertEquals(Map.of("instance", "test"), marker.getParameters());
        assertEquals(List.of(new FormatBlock(List.of(errorBlock), Format.NONE, Map.of("class", ASYNC_ERROR_CLASS))),
            marker.getChildren());
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
        verify(this.errorBlockGenerator, never()).generateErrorBlocks(anyBoolean(), any(), any(), any(), any());
        verify(this.jsrx, never()).use(any());
    }
}
