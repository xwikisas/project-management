package com.xwiki.projectmanagement.openproject.internal.macro;

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

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.macro.AbstractProjectManagementMacro;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.internal.CurrentUserFilterDetector;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectClient;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * OpenProject macro.
 *
 * @version $Id$
 */
@Component
@Singleton
@Named("openproject")
public class OpenProjectMacro extends AbstractProjectManagementMacro<OpenProjectMacroParameters>
{
    private static final String CLASS = "class";

    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    @Inject
    @Named("jsx")
    private SkinExtension jsx;

    @Inject
    private LicenseChecker licenseChecker;

    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    private StylingSetupManager stylingSetupManager;

    @Inject
    private ObservationManager observationManager;

    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private OpenProjectIdentifierResolver identifierResolver;

    @Inject
    private CurrentUserFilterDetector currentUserFilterDetector;

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public OpenProjectMacro()
    {
        super("OpenProject", "Retrieve work items from OpenProject.", null, OpenProjectMacroParameters.class);
        setDefaultCategories(Set.of(OpenProjectClient.CLIENT_PRETTY_NAME, DEFAULT_CATEGORY_CONTENT));
    }

    /**
     * Constructor for subclasses that need to customise the macro name, description and parameters class.
     *
     * @param name the macro name
     * @param description the macro description
     * @param parametersClass the parameters class
     */
    protected OpenProjectMacro(String name, String description,
        Class<? extends OpenProjectMacroParameters> parametersClass)
    {
        super(name, description, null, parametersClass);
        setDefaultCategories(Set.of(OpenProjectClient.CLIENT_PRETTY_NAME, DEFAULT_CATEGORY_CONTENT));
    }

    @Override
    public void processParameters(OpenProjectMacroParameters parameters)
    {
        addToSourceParams(parameters, "client", "openproject");

        addToSourceParams(parameters, "instance", parameters.getInstance());

        addToSourceParams(parameters, "identifier", parameters.getIdentifier());

        addToSourceParams(parameters, "translationPrefix", "openproject.");
    }

    @Override
    public List<Block> asyncProcessParameters(OpenProjectMacroParameters parameters,
        MacroTransformationContext context)
    {
        String queryId = identifierResolver.getStandaloneQueryId(parameters.getIdentifier());
        if (queryId == null) {
            return Collections.emptyList();
        }

        OpenProjectApiClient apiClient = openProjectAccess.resolve(parameters.getInstance()).getClient();
        if (apiClient == null) {
            return Collections.emptyList();
        }

        try {
            addToSourceParams(parameters, OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL,
                identifierResolver.getSavedQueryResultsUrl(apiClient, queryId));
        } catch (ProjectManagementException e) {
            // OpenProject answers 404 for the saved queries that the user isn't allowed to see too: an empty list of
            // work packages would wrongly mean that the query matches none.
            if (isNotFoundOrForbidden(e)) {
                logger.debug("The saved query [{}] of the instance [{}] is not available. Cause: [{}].", queryId,
                    parameters.getInstance(), ExceptionUtils.getRootCauseMessage(e));
                return userTokenChecker.getWarningBlock(parameters.getInstance(),
                    l10n.getTranslation("openproject.savedQuery.unavailable").render(queryId), context.isInline());
            }
            logger.warn("Failed to retrieve the saved query [{}] of the instance [{}]. Cause: [{}].", queryId,
                parameters.getInstance(), ExceptionUtils.getRootCauseMessage(e));
        }
        return Collections.emptyList();
    }

    private static boolean isNotFoundOrForbidden(ProjectManagementException e)
    {
        Integer statusCode = e instanceof WorkItemRetrievalException ? ((WorkItemRetrievalException) e).getStatusCode()
            : null;
        return statusCode != null
            && (statusCode == HttpURLConnection.HTTP_NOT_FOUND || statusCode == HttpURLConnection.HTTP_FORBIDDEN);
    }

    /**
     * @param parameters the parameters of the macro
     * @return {@code true} if the displayed work packages depend on the OpenProject account of the current user, which
     *     anonymous requests can't use: the work packages are filtered on the current user
     */
    protected boolean requiresUserAccount(OpenProjectMacroParameters parameters)
    {
        return this.currentUserFilterDetector.hasCurrentUserFilter(parameters.getPresetId(), parameters.getFilters())
            || this.currentUserFilterDetector.hasCurrentUserFilterInIdentifier(parameters.getIdentifier());
    }

    @Override
    public List<Block> execute(OpenProjectMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        List<Block> licenseBlock = licenseChecker.getMissingLicenseBlock(context);
        if (!licenseBlock.isEmpty()) {
            return licenseBlock;
        }
        observationManager.notify(new BeforeOpenProjectMacroExecutionEvent(), this, parameters);

        String instanceToUse = parameters.getInstance();
        ssrx.use("openproject/css/propertyStyles.css");
        stylingSetupManager.useInstanceStyle(instanceToUse);
        jsx.use("OpenProject.Code.ViewAction");

        List<Block> warningBlock =
            userTokenChecker.getWarningBlock(instanceToUse, requiresUserAccount(parameters), context);

        if (!warningBlock.isEmpty()) {
            return warningBlock;
        }
        List<Block> result = new ArrayList<>(super.execute(parameters, content, context));
        Map<String, String> params = Collections.singletonMap(CLASS, "open-project-macro");
        return context.isInline() ? Collections.singletonList(new FormatBlock(result, Format.NONE, params))
            : Collections.singletonList(new GroupBlock(result, params));
    }
}
