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

import javax.inject.Inject;

import org.slf4j.Logger;
import org.xwiki.job.JobException;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.RenderingException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.OpenProjectInstanceHolder;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;

/**
 * Base class for OpenProject macros that render content directly (not via LiveData/async). Handles license checking,
 * instance resolution, token validation, and API client resolution, so concrete subclasses only implement the
 * fetch-and-render logic.
 *
 * @param <P> the macro parameters type, must implement {@link OpenProjectInstanceHolder}
 * @version $Id$
 * @since 1.2
 */
public abstract class AbstractOpenProjectDirectMacro<P extends OpenProjectInstanceHolder> extends AbstractMacro<P>
{
    @Inject
    private LicenseChecker licenseChecker;

    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private Logger logger;

    @Inject
    private ObservationManager observationManager;

    @Inject
    private ProjectManagementAsyncExecutor asyncExecutor;

    /**
     * @param name the macro name.
     * @param description the macro description.
     * @param parametersBeanClass the parameters class.
     */
    protected AbstractOpenProjectDirectMacro(String name, String description, Class<P> parametersBeanClass)
    {
        super(name, description, null, parametersBeanClass);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public final List<Block> execute(P parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        List<Block> licenseBlock = licenseChecker.getMissingLicenseBlock(context);
        if (!licenseBlock.isEmpty()) {
            return licenseBlock;
        }
        observationManager.notify(new BeforeOpenProjectMacroExecutionEvent(), this, parameters);

        String instanceToUse = parameters.getInstance();

        List<Block> warningBlock = userTokenChecker.getWarningBlock(instanceToUse);
        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }

        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(instanceToUse);
        if (apiClient == null) {
            return warningBlock;
        }
        try {
            return asyncExecutor.execute(new AbstractMacro<ProjectManagementAsyncMacroParams>("")
            {
                @Override
                public boolean supportsInlineMode()
                {
                    return AbstractOpenProjectDirectMacro.this.supportsInlineMode();
                }

                @Override
                public List<Block> execute(ProjectManagementAsyncMacroParams x, String y,
                    MacroTransformationContext z) throws MacroExecutionException
                {
                    return executeInternal(parameters, content, context, apiClient, instanceToUse);
                }
            }, parameters, content, context);
        } catch (RenderingException | JobException e) {
            throw new MacroExecutionException("Failed to render the macro asynchronously.", e);
        }
    }

    /**
     * @return the {@link OpenProjectConfiguration} for subclasses that need connection metadata beyond the API client.
     */
    protected OpenProjectConfiguration getOpenProjectConfiguration()
    {
        return openProjectConfiguration;
    }

    /**
     * Executes the macro logic after all guards have passed and the API client is resolved.
     *
     * @param parameters the macro parameters.
     * @param content the macro content (usually empty).
     * @param context the macro transformation context.
     * @param apiClient the resolved and non-null OpenProject API client.
     * @param instance the resolved OpenProject instance name.
     * @return the list of blocks to render.
     * @throws MacroExecutionException if execution fails.
     */
    protected abstract List<Block> executeInternal(P parameters, String content, MacroTransformationContext context,
        OpenProjectApiClient apiClient, String instance) throws MacroExecutionException;
}
