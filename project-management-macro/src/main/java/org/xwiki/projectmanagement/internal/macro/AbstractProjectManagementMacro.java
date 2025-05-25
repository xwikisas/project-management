package org.xwiki.projectmanagement.internal.macro;

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

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.http.client.utils.URLEncodedUtils;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.macro.LiveDataMacroParameters;
import org.xwiki.projectmanagement.ProjectManagementMacroContext;
import org.xwiki.projectmanagement.internal.DefaultProjectManagementMacroContext;
import org.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import org.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Something.
 *
 * @param <T> something.
 * @version $Id$
 */
public abstract class AbstractProjectManagementMacro<T extends ProjectManagementMacroParameters>
    extends AbstractMacro<T>
{
    @Inject
    protected ProjectManagementMacroContext projectManagementMacroContext;

    @Inject
    @Named("liveData")
    private Macro<LiveDataMacroParameters> liveDataMacro;

    @Inject
    private ComponentManager componentManager;

    /**
     * @param name smth
     * @param description smth
     * @param descriptor smth
     * @param clazz smth
     */
    public AbstractProjectManagementMacro(String name, String description, ContentDescriptor descriptor,
        Class<?> clazz)
    {
        super(name, description, descriptor, clazz);
    }

    /**
     * @return true if it supports inline or false otherwise.
     */
    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    /**
     * @param parameters the macro parameters in the form of a bean defined by the {@link Macro} implementation
     * @param content the content of the macro
     * @param context the context of the macros transformation process
     * @return the blocks that can be used for rendering.
     * @throws MacroExecutionException if something went bad.
     */
    @Override
    public List<Block> execute(T parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        WorkItemsDisplayer displayer = parameters.getWorkItemsDisplayer();
        parameters.setSource("projectmanagement");
        processParameters(parameters);
        if (projectManagementMacroContext instanceof DefaultProjectManagementMacroContext) {
            ((DefaultProjectManagementMacroContext) projectManagementMacroContext).setSourceParams(
                URLEncodedUtils.parse(parameters.getSourceParameters(), StandardCharsets.UTF_8));
        }
        try {
            Macro<T> displayerMacro = componentManager.getInstance(Macro.class, displayer.toString());

            return displayerMacro.execute(parameters, content, context);
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException(String.format("Could not find the displayer [%s].", displayer.name()), e);
        }
    }

    /**
     * @param parameters the parameters that will be passed to the livedata macro call.
     */
    public abstract void processParameters(T parameters);
}
