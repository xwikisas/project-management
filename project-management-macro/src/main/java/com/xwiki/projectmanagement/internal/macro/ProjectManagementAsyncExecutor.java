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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.job.JobException;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.async.AsyncContext;
import org.xwiki.rendering.async.internal.AsyncRendererConfiguration;
import org.xwiki.rendering.async.internal.block.BlockAsyncRendererExecutor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xpn.xwiki.internal.context.XWikiContextContextStore;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;

/**
 * Executes project management macros asynchronously.
 *
 * @version $Id$
 * @since 1.2.1
 */
@Singleton
@Component(roles = ProjectManagementAsyncExecutor.class)
public class ProjectManagementAsyncExecutor

{
    @Inject
    private BlockAsyncRendererExecutor executor;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private AsyncContext asyncContext;

    @Inject
    @Named("jsrx")
    private SkinExtension jsrx;

    /**
     * @param displayerMacro the macro that will be executed asynchronously.
     * @param parameters the parameters of the macro.
     * @param content the content of the macro.
     * @param context the macro transformation context.
     * @param paramsConsumer processes the parameters of the macro before execution, i.e. make another api call in
     *     the async context, and returns the blocks to display instead of the macro, or an empty list to execute it.
     * @param forcePlaceholder whether to display a placeholder, filled asynchronously, even when the result is
     *     available; ignored when the result can't be rendered asynchronously (e.g. when the WYSIWYG editor renders
     *     the macro, or when the request doesn't support asynchronous rendering), where it is rendered synchronously
     * @return the placeholder blocks that will be asynchronously populated.
     * @throws RenderingException if some exception was thrown during the rendering of the placeholder.
     * @throws JobException if some exception was thrown during the execution of the render job. This should not
     *     happen as we always render the placeholder.
     */
    public List<Block> execute(Macro<ProjectManagementAsyncMacroParams> displayerMacro,
        ProjectManagementAsyncMacroParams parameters, String content, MacroTransformationContext context,
        BiFunction<ProjectManagementAsyncMacroParams, MacroTransformationContext, List<Block>> paramsConsumer,
        boolean forcePlaceholder)
        throws RenderingException, JobException
    {

        try {
            ProjectManagementAsyncRenderer asyncRenderer =
                componentManager.getInstance(ProjectManagementAsyncRenderer.class);
            asyncRenderer.initialize(displayerMacro, parameters, content, context, paramsConsumer);
            boolean asyncAllowed = asyncRenderer.isAsyncAllowed() && this.asyncContext.isEnabled();
            AsyncRendererConfiguration configuration = getAsyncRendererConfiguration(forcePlaceholder && asyncAllowed);
            Block result = executor.execute(asyncRenderer, configuration);
            List<Block> blocks =
                result instanceof CompositeBlock ? result.getChildren() : Collections.singletonList(result);
            return asyncAllowed ? wrap(blocks, context) : blocks;
        } catch (ComponentLookupException e) {
            // Shouldn't happen.
            throw new RuntimeException("We are doomed.", e);
        }
    }

    /**
     * Wraps a result that may be rendered asynchronously. Some results are initialized by JavaScript code on the
     * xwiki:dom:updated event, in the updated elements (e.g. the charts and the Live Data tables), but XWiki sends no
     * event after an asynchronous rendering (before XWiki 18.6) or sends it for the placeholder replaced by the result,
     * which isn't in the page anymore: the wrapper lets the script send the event for the result. Remove them once the
     * minimal supported XWiki version includes the fix of XWIKI-25675 (The xwiki:dom:updated event sent after an
     * asynchronous rendering targets the removed placeholder instead of the rendered content).
     */
    private List<Block> wrap(List<Block> blocks, MacroTransformationContext context)
    {
        this.jsrx.use("js/projectmanagement/asyncFix.js");
        Map<String, String> parameters = Collections.singletonMap("class", "proj-manag-async-wrapper");
        Block wrapper = context.isInline() ? new FormatBlock(blocks, Format.NONE, parameters)
            : new GroupBlock(blocks, parameters);
        return Collections.singletonList(wrapper);
    }

    private AsyncRendererConfiguration getAsyncRendererConfiguration(boolean forcePlaceholder)
    {
        AsyncRendererConfiguration configuration = new AsyncRendererConfiguration();

        // Pass some properties that might be of interest to a potential displayer macro.
        configuration.setContextEntries(
            Set.of(XWikiContextContextStore.PROP_USER, XWikiContextContextStore.PROP_WIKI,
                XWikiContextContextStore.PROP_ACTION, XWikiContextContextStore.PROP_LOCALE));
        // The results are displayed asynchronously since they come from other servers. A forced placeholder is only
        // filled when the request supports asynchronous rendering: otherwise nothing fills it and the macro stays
        // empty, so the result is rendered synchronously instead.
        configuration.setPlaceHolderForced(forcePlaceholder && this.asyncContext.isEnabled());
        return configuration;
    }
}
