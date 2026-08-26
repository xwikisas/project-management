package com.xwiki.projectmanagement.internal.macro;

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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.job.JobException;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.internal.displayers.WorkItemLivedataDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Provides the logic and extension points for implementing the base work item filtering macro. When implementing a
 * project management client, this macro should also be implemented and minimally add the client ID to the source
 * parameters.
 *
 * @param <T> the type of your macro implementation parameters. This will allow you to add custom parameters for the
 *     macro implementation.
 * @version $Id$
 */
public abstract class AbstractProjectManagementMacro<T extends ProjectManagementMacroParameters>
    extends AbstractMacro<T>
{
    @Inject
    private ComponentManager componentManager;

    @Inject
    private ProjectManagementAsyncExecutor asyncExecutor;

    /**
     * @param name the name of the macro.
     * @param description the description of the macro.
     * @param descriptor the content descriptor of the macro.
     * @param clazz the class of the parameters.
     */
    public AbstractProjectManagementMacro(String name, String description, ContentDescriptor descriptor, Class<?> clazz)
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
    public List<Block> execute(T parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        WorkItemsDisplayer displayer = parameters.getWorkItemsDisplayer();
        parameters.setSource("projectmanagement");
        processParameters(parameters);
        try {
            String displayerId = displayer.name();
            if (WorkItemsDisplayer.liveDataCards.equals(displayer) || WorkItemsDisplayer.liveData.equals(displayer)) {
                displayerId = WorkItemLivedataDisplayer.ROLE_HINT;
            }
            Macro<ProjectManagementAsyncMacroParams> displayerMacro =
                componentManager.getInstance(Macro.class, displayerId);
            if (!displayerMacro.supportsInlineMode() && context.isInline()) {
                throw new MacroExecutionException(
                    String.format("Macro displayer [%s] is standalone but is being used inline.", displayerId));
            }
            return asyncExecutor.execute(displayerMacro, parameters, content, context, getAsyncParametersProcessor(),
                isAsync(parameters));
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException(String.format("Could not find the displayer [%s].", displayer.name()), e);
        } catch (JobException | RenderingException e) {
            throw new MacroExecutionException(
                String.format("Failed to asynchronously render the work items using [%s] displayer.", displayer.name()),
                e);
        }
    }

    /**
     * @param parameters the parameters that will be passed to the livedata macro call.
     */
    public abstract void processParameters(T parameters);

    /**
     * This method offers the possibility to update the parameters right before the executing the displayer in the async
     * thread. The updated parameters will be passed down to the displayer macro.
     *
     * @param parameters the parameters of the currently executing macro. Updating this will affect how the macro is
     *     rendered.
     * @since 1.3.0
     */
    public abstract void asyncProcessParameters(T parameters);

    /**
     * Denotes whether this macro, given it's parameters (mainly the displayer), should be rendered asynchronously or
     * not. Some displayers might have their own async rendering handlers which will make our async rendering redundant
     * i.e. the livedata displayers. By default, this method returns false in the case of the livedata displayers and
     * true otherwise.
     *
     * @param parameters the parameters of the currently executing macro.
     * @return true if the rendering should be done on a separate thread. false otherwise.
     * @since 1.3.0
     */
    public boolean isAsync(T parameters)
    {
        return !WorkItemsDisplayer.liveData.equals(parameters.getWorkItemsDisplayer())
            && !WorkItemsDisplayer.liveDataCards.equals(parameters.getWorkItemsDisplayer());
    }

    protected Consumer<ProjectManagementAsyncMacroParams> getAsyncParametersProcessor()
    {
        return (params -> {
            asyncProcessParameters((T) params);
        });
    }

    protected void addToSourceParams(T parameters, String key, String value)
    {
        if (value == null || value.isEmpty()) {
            return;
        }
        String sourceParameters = parameters.getSourceParameters();
        if (sourceParameters == null || sourceParameters.isEmpty()) {
            parameters.setSourceParameters(String.format("%s=%s", key, value));
        } else {
            parameters.setSourceParameters(
                String.format("%s&%s=%s", sourceParameters, URLEncoder.encode(key, StandardCharsets.UTF_8),
                    URLEncoder.encode(value, StandardCharsets.UTF_8)));
        }
    }
}
