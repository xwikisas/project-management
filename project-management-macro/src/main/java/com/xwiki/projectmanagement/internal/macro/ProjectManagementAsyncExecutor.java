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
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.job.JobException;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.async.internal.AsyncRendererConfiguration;
import org.xwiki.rendering.async.internal.block.BlockAsyncRendererExecutor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.internal.context.XWikiContextContextStore;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;

/**
 * Executes project management macros asynchronously.
 *
 * @version $Id$
 * @since 1.2.0
 */
@Singleton
@Component(roles = ProjectManagementAsyncExecutor.class)
public class ProjectManagementAsyncExecutor

{
    @Inject
    private BlockAsyncRendererExecutor executor;

    @Inject
    private ProjectManagementAsyncRenderer asyncRenderer;

    /**
     * @param displayerMacro asda
     * @param parameters asd
     * @param content fdsfd
     * @param context wqewq
     * @return dasda
     * @throws RenderingException fsdfsd
     * @throws JobException asdsa
     */
    public List<Block> execute(Macro<ProjectManagementAsyncMacroParams> displayerMacro,
        ProjectManagementAsyncMacroParams parameters, String content, MacroTransformationContext context)
        throws RenderingException, JobException
    {
        AsyncRendererConfiguration configuration = new AsyncRendererConfiguration();

        // Pass some properties that might be of interest to a potential displayer macro.
        configuration.setContextEntries(
            Set.of(XWikiContextContextStore.PROP_USER, XWikiContextContextStore.PROP_WIKI,
                XWikiContextContextStore.PROP_ACTION, XWikiContextContextStore.PROP_LOCALE));
        asyncRenderer.initialize(displayerMacro, parameters, content, context);
        Block result = executor.execute(asyncRenderer, configuration);
        return result instanceof CompositeBlock ? result.getChildren() : Collections.singletonList(result);
    }
}
