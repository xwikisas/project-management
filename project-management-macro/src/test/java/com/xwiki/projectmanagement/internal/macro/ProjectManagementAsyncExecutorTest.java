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
import java.util.Set;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.rendering.async.AsyncContext;
import org.xwiki.rendering.async.internal.AsyncRendererConfiguration;
import org.xwiki.rendering.async.internal.block.BlockAsyncRendererExecutor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.internal.context.XWikiContextContextStore;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectManagementAsyncExecutor}.
 *
 * @version $Id$
 */
@ComponentTest
class ProjectManagementAsyncExecutorTest
{
    private static final String CONTENT = "content";

    @InjectMockComponents
    private ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    private BlockAsyncRendererExecutor blockAsyncRendererExecutor;

    @MockComponent
    private AsyncContext asyncContext;

    @MockComponent
    private ProjectManagementAsyncRenderer asyncRenderer;

    @MockComponent
    @Named("jsrx")
    private SkinExtension jsrx;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayer;

    private final ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();

    private final MacroTransformationContext context = new MacroTransformationContext();

    private final Block result = new WordBlock("result");

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.asyncContext.isEnabled()).thenReturn(true);
        when(this.asyncRenderer.isAsyncAllowed()).thenReturn(true);
        when(this.blockAsyncRendererExecutor.execute(same(this.asyncRenderer), any(AsyncRendererConfiguration.class)))
            .thenReturn(new CompositeBlock(List.of(this.result)));
    }

    @Test
    void executeForcesThePlaceholder() throws Exception
    {
        assertWrapped(GroupBlock.class, execute(true));

        verify(this.asyncRenderer).initialize(this.displayer, this.parameters, CONTENT, this.context, null);
        assertTrue(getConfiguration().isPlaceHolderForced());
    }

    @Test
    void executeRestoresTheCurrentDocument() throws Exception
    {
        execute(true);

        // The asynchronous rendering needs the current document, e.g. for the links that bring the user back to it.
        assertEquals(Set.of(XWikiContextContextStore.PROP_USER, XWikiContextContextStore.PROP_WIKI,
            XWikiContextContextStore.PROP_ACTION, XWikiContextContextStore.PROP_LOCALE,
            XWikiContextContextStore.PROP_DOCUMENT_REFERENCE), getConfiguration().getContextEntries());
    }

    @Test
    void executeWithoutForcingThePlaceholder() throws Exception
    {
        // The result is still rendered asynchronously when it isn't ready in time.
        assertWrapped(GroupBlock.class, execute(false));

        assertFalse(getConfiguration().isPlaceHolderForced());
    }

    @Test
    void executeInline() throws Exception
    {
        this.context.setInline(true);

        assertWrapped(FormatBlock.class, execute(false));
    }

    @Test
    void executeWhenTheRendererDoesNotAllowAsyncRendering() throws Exception
    {
        // E.g. when the WYSIWYG editor renders the macro: nothing would fill the placeholder.
        when(this.asyncRenderer.isAsyncAllowed()).thenReturn(false);

        // The result is rendered synchronously, so it isn't wrapped.
        assertEquals(List.of(this.result), execute(true));

        assertFalse(getConfiguration().isPlaceHolderForced());
        verify(this.jsrx, never()).use(anyString());
    }

    @Test
    void executeWhenTheRequestDoesNotSupportAsyncRendering() throws Exception
    {
        when(this.asyncContext.isEnabled()).thenReturn(false);

        // The result is rendered synchronously, so it isn't wrapped.
        assertEquals(List.of(this.result), execute(true));

        assertFalse(getConfiguration().isPlaceHolderForced());
        verify(this.jsrx, never()).use(anyString());
    }

    private void assertWrapped(Class<? extends Block> wrapperClass, List<Block> blocks)
    {
        // The wrapper lets the script initialize the result once it replaces its placeholder (XWIKI-25675).
        assertEquals(1, blocks.size());
        assertInstanceOf(wrapperClass, blocks.get(0));
        assertEquals("proj-manag-async-wrapper", blocks.get(0).getParameter("class"));
        assertEquals(List.of(this.result), blocks.get(0).getChildren());
        verify(this.jsrx).use("js/projectmanagement/asyncFix.js");
    }

    private List<Block> execute(boolean forcePlaceholder) throws Exception
    {
        return this.asyncExecutor.execute(this.displayer, this.parameters, CONTENT, this.context, null,
            forcePlaceholder);
    }

    private AsyncRendererConfiguration getConfiguration() throws Exception
    {
        ArgumentCaptor<AsyncRendererConfiguration> configuration =
            ArgumentCaptor.forClass(AsyncRendererConfiguration.class);
        verify(this.blockAsyncRendererExecutor).execute(same(this.asyncRenderer), configuration.capture());
        return configuration.getValue();
    }
}
