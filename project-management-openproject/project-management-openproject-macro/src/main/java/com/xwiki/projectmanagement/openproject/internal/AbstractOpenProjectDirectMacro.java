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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;

import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE;

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
    private OpenProjectAccess openProjectAccess;

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
        setDefaultCategories(Set.of(OpenProjectClient.CLIENT_PRETTY_NAME, DEFAULT_CATEGORY_CONTENT));
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

        OpenProjectAccessResult access = openProjectAccess.resolve(instanceToUse);
        List<Block> warningBlock = userTokenChecker.getWarningBlock(instanceToUse, access.getStatus(), context);
        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }

        OpenProjectApiClient apiClient = access.getClient();
        try {
            return asyncExecutor.execute(new AbstractMacro<ProjectManagementAsyncMacroParams>("")
            {
                @Override
                public boolean supportsInlineMode()
                {
                    return AbstractOpenProjectDirectMacro.this.supportsInlineMode();
                }

                @Override
                public List<Block> execute(ProjectManagementAsyncMacroParams x, String y, MacroTransformationContext z)
                    throws MacroExecutionException
                {
                    List<Block> result = new ArrayList<>(
                        userTokenChecker.getConnectHintBlock(instanceToUse, access.getStatus(), context));
                    result.addAll(executeInternal(parameters, content, context, apiClient, instanceToUse));
                    return result;
                }
            }, parameters, content, context, null, true);
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
     * @param serverUrl the URL of the OpenProject server
     * @param project the project parameter of the macro, i.e. the id or the identifier of an OpenProject project
     * @return the URL of the project overview on the OpenProject server
     */
    protected static String getProjectURL(String serverUrl, String project)
    {
        // The project is used as a single path segment.
        return String.format("%s/projects/%s", serverUrl,
            URLEncoder.encode(String.valueOf(project), StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /**
     * @param project the project parameter of the macro
     * @return {@code true} if the project is set, {@code false} if it's empty
     * @throws MacroExecutionException if the project is the project selected on the OpenProject dashboard and it
     *     couldn't be resolved, e.g. when the macro is not on the dashboard
     */
    protected boolean hasProject(String project) throws MacroExecutionException
    {
        if (USE_SELECTED_DASHBOARD_PROJECT_VALUE.equals(project)) {
            throw new MacroExecutionException("The project selected on the OpenProject dashboard couldn't be resolved."
                + " Select a project explicitly when the macro is not on the dashboard.");
        }
        return project != null && !project.isBlank();
    }

    /**
     * @param project the project parameter of the macro
     * @return the OpenProject id of the project
     * @throws MacroExecutionException if the project is not an OpenProject project id, e.g. when it is empty or when
     *     the selected dashboard project couldn't be resolved
     */
    protected int getProjectId(String project) throws MacroExecutionException
    {
        try {
            return Integer.parseInt(project);
        } catch (NumberFormatException e) {
            throw new MacroExecutionException(
                String.format("The project [%s] is not a valid OpenProject project id.", project));
        }
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
