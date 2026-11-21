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
import java.util.function.BiFunction;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.job.JobException;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceRequest;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceResult;
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

    @Inject
    private PresetFiltersCombiner presetFiltersCombiner;

    @Inject
    private WorkItemSourceReader sourceReader;

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
     * @param description the description of the macro content
     * @return the descriptor of a macro content read by the source chosen by the {@code source} parameter
     * @since 1.5.0
     */
    protected static ContentDescriptor createSourceContentDescriptor(String description)
    {
        return new DefaultContentDescriptor(description, false);
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
        WorkItemsDisplayer displayer = parameters.getDisplayer();
        WorkItemSourceResult source = readSource(parameters, content);
        parameters.setFilters(source.getFilters());
        parameters.setIdentifier(source.getIdentifier());
        parameters.setLiveDataSource("projectmanagement");
        processParameters(parameters);
        source.getClientScope().forEach((key, value) -> addToSourceParams(parameters, key, value));
        maybeUpdateFilterFromPreset(parameters);
        try {
            String displayerId = displayer.getMacroHint();
            Macro<ProjectManagementAsyncMacroParams> displayerMacro =
                componentManager.getInstance(Macro.class, displayerId);
            if (!displayerMacro.supportsInlineMode() && context.isInline()) {
                throw new MacroExecutionException(
                    String.format("Macro displayer [%s] is standalone but is being used inline.", displayerId));
            }
            // The content has been read by the source: the displayers don't use it, unless it's a legacy Live Data
            // configuration.
            String displayerContent = isLiveDataConfiguration(parameters, content) ? content : "";
            return asyncExecutor.execute(displayerMacro, parameters, displayerContent, context,
                getAsyncParametersProcessor(), isAsync(parameters));
        } catch (ComponentLookupException e) {
            throw new MacroExecutionException(String.format("Could not find the displayer [%s].", displayer), e);
        } catch (JobException | RenderingException e) {
            throw new MacroExecutionException(
                String.format("Failed to asynchronously render the work items using [%s] displayer.", displayer),
                e);
        }
    }

    /**
     * Read the content of the macro with the source chosen by its {@code source} parameter. This has no side effect:
     * the extending classes can call it before executing the macro, e.g. to know whether the work items are filtered
     * on the current user.
     *
     * @param parameters the parameters of the macro
     * @param content the content of the macro
     * @return the work items selected by the macro: its {@code filters} and {@code identifier} parameters when the
     *     content is empty, otherwise the filters and the identifier read from the content
     * @throws MacroExecutionException if the content can't be read, or if it is used together with the
     *     {@code filters} or {@code identifier} parameters, which it replaces
     * @since 1.5.0
     */
    protected WorkItemSourceResult readSource(T parameters, String content) throws MacroExecutionException
    {
        String sourceContent = isLiveDataConfiguration(parameters, content) ? null : content;
        return this.sourceReader.read(new WorkItemSourceRequest().client(getClientId()).content(sourceContent)
            .source(parameters.getWorkItemSource()).filters(parameters.getFilters(), true)
            .identifier(parameters.getIdentifier()));
    }

    /**
     * Before the {@code source} parameter, the content was passed to the Live Data macro of the table and cards
     * displayers, as its advanced JSON configuration (e.g. {@code {"query":{"sort":[...]}}}). Such a content is still
     * passed to the displayers when no source is chosen: no source content starts with a curly bracket, except the
     * Live Data JSON of the {@code filters} source, which has to be chosen explicitly.
     */
    private static boolean isLiveDataConfiguration(ProjectManagementMacroParameters parameters, String content)
    {
        return StringUtils.isBlank(parameters.getWorkItemSource()) && StringUtils.startsWith(StringUtils.trim(content),
            "{");
    }

    /**
     * @return the id of the client retrieving the work items (e.g. {@code openproject}), which can provide its own
     *     sources for the macro content, or {@code null} if the macro isn't specific to a client
     * @since 1.5.0
     */
    protected String getClientId()
    {
        return null;
    }

    private void maybeUpdateFilterFromPreset(T parameters) throws MacroExecutionException
    {
        // The preset is combined with the filters of the macro, including the ones set by processParameters().
        if (StringUtils.isNotEmpty(parameters.getPresetId())) {
            parameters.setFilters(
                this.presetFiltersCombiner.combine(parameters.getPresetId(), parameters.getFilters()));
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
     * @param context the macro transformation context of the macro
     * @return the blocks to display instead of the work items, e.g. a message explaining why they can't be retrieved,
     *     or an empty list to display the work items
     * @since 1.3.0
     */
    public abstract List<Block> asyncProcessParameters(T parameters, MacroTransformationContext context);

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
        return !parameters.getDisplayer().isLiveData();
    }

    protected BiFunction<ProjectManagementAsyncMacroParams, MacroTransformationContext, List<Block>>
        getAsyncParametersProcessor()
    {
        return (params, context) -> asyncProcessParameters((T) params, context);
    }

    protected void addToSourceParams(T parameters, String key, String value)
    {
        if (value == null || value.isEmpty()) {
            return;
        }
        String parameter = String.format("%s=%s", URLEncoder.encode(key, StandardCharsets.UTF_8),
            URLEncoder.encode(value, StandardCharsets.UTF_8));
        String sourceParameters = parameters.getSourceParameters();
        if (sourceParameters == null || sourceParameters.isEmpty()) {
            parameters.setSourceParameters(parameter);
        } else {
            parameters.setSourceParameters(String.format("%s&%s", sourceParameters, parameter));
        }
    }
}
