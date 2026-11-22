package com.xwiki.projectmanagement.openproject.internal;

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
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * Resolves the saved query linked by the identifier of an OpenProject macro (for example
 * {@code https://op.example.com/projects/demo/work_packages?query_id=5}) into the URL of its results, which the client
 * requests to retrieve the work packages. It is done with the account of the current user when the macro is rendered,
 * since a saved query may be private.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = OpenProjectSavedQueryResolver.class)
@Singleton
public class OpenProjectSavedQueryResolver
{
    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private OpenProjectIdentifierResolver identifierResolver;

    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private Logger logger;

    /**
     * @param parameters the parameters of the macro, whose identifier may link a saved query
     * @param inline whether the macro is inline
     * @param sourceParameters receives the source parameter holding the URL of the results of the saved query (its key
     *     and its value), when the identifier links a saved query that the current user can see
     * @return a warning to display instead of the work packages when the saved query doesn't exist or the current user
     *     isn't allowed to see it, or an empty list to display the work packages
     */
    public List<Block> resolve(OpenProjectMacroParameters parameters, boolean inline,
        BiConsumer<String, String> sourceParameters)
    {
        String queryId = this.identifierResolver.getStandaloneQueryId(parameters.getIdentifier());
        if (queryId == null) {
            return Collections.emptyList();
        }

        OpenProjectApiClient apiClient = this.openProjectAccess.resolve(parameters.getInstance()).getClient();
        if (apiClient == null) {
            return Collections.emptyList();
        }

        try {
            sourceParameters.accept(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL,
                this.identifierResolver.getSavedQueryResultsUrl(apiClient, queryId));
        } catch (ProjectManagementException e) {
            // OpenProject answers 404 for the saved queries that the user isn't allowed to see too: an empty list of
            // work packages would wrongly mean that the query matches none.
            if (isNotFoundOrForbidden(e)) {
                this.logger.debug("The saved query [{}] of the instance [{}] is not available. Cause: [{}].", queryId,
                    parameters.getInstance(), ExceptionUtils.getRootCauseMessage(e));
                return this.userTokenChecker.getWarningBlock(parameters.getInstance(),
                    this.l10n.getTranslation("openproject.savedQuery.unavailable").render(queryId), inline);
            }
            this.logger.warn("Failed to retrieve the saved query [{}] of the instance [{}]. Cause: [{}].", queryId,
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
}
