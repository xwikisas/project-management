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

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.async.internal.block.AbstractBlockAsyncRenderer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MacroMarkerBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Async renderer for the {@link com.xwiki.projectmanagement.internal.WorkItemsDisplayer} available except for the
 * livedata since it work properly in asynchronous contexts.
 *
 * @version $Id$
 */
@Component(roles = ProjectManagementAsyncRenderer.class)
public class ProjectManagementAsyncRenderer extends AbstractBlockAsyncRenderer
{
    private Macro<ProjectManagementMacroParameters> workItemsDisplayer;

    private ProjectManagementMacroParameters parameters;

    private String content;

    private MacroTransformationContext transformationContext;

    private Syntax targetSyntax;

    private List<String> id;

    private boolean inline;

    /**
     * Initialize the async renderer with the required parameters for a project management displayer to function
     * properly.
     *
     * @param displayer the displayer macro instance that will render the work items taking in consideration the
     *     parameters and the content.
     * @param parameters the parameters passed to the displayer macro.
     * @param content the content passed to the displayer macro.
     * @param context the macro transformation context that will be taken in consideration by the executed macro.
     */
    public void initialize(Macro<ProjectManagementMacroParameters> displayer,
        ProjectManagementMacroParameters parameters, String content, MacroTransformationContext context)
    {
        workItemsDisplayer = displayer;
        this.parameters = parameters;
        this.content = content;
        this.transformationContext = context.clone();
        this.inline = this.transformationContext.isInline();
        this.targetSyntax = context.getTransformationContext().getTargetSyntax();

        // TODO: Maybe come up with a better way to generating an id.
        StringBuilder sb = new StringBuilder();
        sb.append(parameters.getId()).append(parameters.getProperties()).append(parameters.getFilters())
            .append(parameters.getSourceParameters());
        id = createId("rendering", "macro", "projectmanagement", String.valueOf(sb.toString().hashCode()));
    }

    @Override
    protected Block execute(boolean async, boolean cached) throws RenderingException
    {
        try {
            List<Block> result = workItemsDisplayer.execute(this.parameters, this.content, this.transformationContext);
            result = List.of(wrapInMacroMarker(this.transformationContext.getCurrentMacroBlock(), result));

            return new CompositeBlock(result);
        } catch (MacroExecutionException e) {
            throw new RenderingException("Failed to render asynchronously the work items displayer [{}].", e);
        }
    }

    @Override
    public boolean isInline()
    {
        return this.inline;
    }

    @Override
    public Syntax getTargetSyntax()
    {
        return this.targetSyntax;
    }

    @Override
    public List<String> getId()
    {
        return this.id;
    }

    @Override
    public boolean isAsyncAllowed()
    {
        // TODO: Not sure if we should take into consideration any external factors when deciding whether to render
        //  this asynchronously or not.
        return true;
    }

    @Override
    public boolean isCacheAllowed()
    {
        return false;
    }

    private Block wrapInMacroMarker(MacroBlock macroBlockToWrap, List<Block> newBlocks)
    {
        return new MacroMarkerBlock(macroBlockToWrap.getId(), macroBlockToWrap.getParameters(),
            macroBlockToWrap.getContent(), newBlocks, macroBlockToWrap.isInline());
    }
}
