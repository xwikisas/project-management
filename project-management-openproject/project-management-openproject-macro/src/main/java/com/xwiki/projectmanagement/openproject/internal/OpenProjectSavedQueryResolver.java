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
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLink;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

/**
 * Resolves the saved query linked by the identifier of an OpenProject macro (for example
 * {@code https://op.example.com/projects/demo/work_packages?query_id=5}), with the account of the current user when
 * the macro is rendered, since a saved query may be private.
 * <p>
 * The filters and the sorting of the query are moved to the filters and the sorting of the macro, so that its Live Data
 * shows them and its viewers can change them: the identifier becomes the link of the work package list of the project
 * of the query, keeping only the filters that the Live Data can't show (see {@link OpenProjectLinkQueryResolver}). The
 * identifier is left as it is when the Live Data can show neither the filters nor the sorting of the query, or when the
 * query has settings that a work package list can't express (see
 * {@link OpenProjectIdentifierResolver#getSavedQueryLink(OpenProjectLink, String)}): the URL of the results of the
 * query is then added to the source parameters, so that the client doesn't read the query again.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = OpenProjectSavedQueryResolver.class)
@Singleton
public class OpenProjectSavedQueryResolver
{
    private static final String IDENTIFIER = "identifier";

    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private OpenProjectIdentifierResolver identifierResolver;

    @Inject
    private OpenProjectLinkQueryResolver linkQueryResolver;

    @Inject
    private UserTokenChecker userTokenChecker;

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private Logger logger;

    /**
     * @param parameters the parameters of the macro, whose identifier may link a saved query; their filters, sorting
     *     and identifier receive the ones of the saved query when the Live Data can show its filters or its sorting
     * @param inline whether the macro is inline
     * @param showInLiveData whether to move the filters and the sorting of the saved query to the parameters, or to
     *     keep the saved query as it is, e.g. when the Live Data configuration of the macro content replaces them
     * @param sourceParameters sets a source parameter (its key and its value), replacing its current value: the
     *     identifier, when it's replaced, or else the URL of the results of the saved query, when the identifier links
     *     a saved query that the current user can see
     * @return a warning to display instead of the work packages when the saved query doesn't exist or the current user
     *     isn't allowed to see it, or an empty list to display the work packages
     */
    public List<Block> resolve(OpenProjectMacroParameters parameters, boolean inline, boolean showInLiveData,
        BiConsumer<String, String> sourceParameters)
    {
        OpenProjectLink link = OpenProjectLink.parse(parameters.getIdentifier());
        String queryId = link != null ? link.getSavedQueryId() : null;
        if (queryId == null) {
            return Collections.emptyList();
        }

        OpenProjectApiClient apiClient = this.openProjectAccess.resolve(parameters.getInstance()).getClient();
        if (apiClient == null) {
            return Collections.emptyList();
        }

        try {
            String resultsUrl = this.identifierResolver.getSavedQueryResultsUrl(apiClient, queryId);
            if (!showInLiveData || !moveToLiveData(parameters, link, resultsUrl, sourceParameters)) {
                sourceParameters.accept(OpenProjectIdentifierResolver.SAVED_QUERY_RESULTS_URL, resultsUrl);
            }
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

    /**
     * Moves the filters and the sorting of the saved query to the parameters of the macro.
     *
     * @return {@code true} if the Live Data shows filters or a sorting of the saved query, whose link is then replaced
     *     by the link of a work package list, or {@code false} if the parameters are left as they are
     */
    private boolean moveToLiveData(OpenProjectMacroParameters parameters, OpenProjectLink link, String resultsUrl,
        BiConsumer<String, String> sourceParameters)
    {
        WorkItemSourceResult source;
        WorkItemSourceResult resolved;
        try {
            String listLink = this.identifierResolver.getSavedQueryLink(link, resultsUrl);
            if (listLink == null) {
                return false;
            }
            source = new WorkItemSourceResult(parameters.getFilters(), listLink, Map.of());
            resolved = this.linkQueryResolver.resolve(parameters.getInstance(), parameters.getSort(), source);
        } catch (ProjectManagementException | MacroExecutionException e) {
            // The client applies the filters of the saved query.
            this.logger.debug("Failed to show the filters of the saved query [{}] in the Live Data: [{}].", link,
                ExceptionUtils.getRootCauseMessage(e));
            return false;
        }
        // The saved query keeps what is stored with it (e.g. the inclusion of the subprojects) when the Live Data
        // wouldn't show anything more.
        if (resolved.getSort() == null && Objects.equals(resolved.getFilters(), source.getFilters())) {
            return false;
        }
        parameters.setFilters(resolved.getFilters());
        if (resolved.getSort() != null) {
            parameters.setSort(resolved.getSort());
        }
        parameters.setIdentifier(resolved.getIdentifier());
        sourceParameters.accept(IDENTIFIER, resolved.getIdentifier());
        return true;
    }

    private static boolean isNotFoundOrForbidden(ProjectManagementException e)
    {
        Integer statusCode = e instanceof WorkItemRetrievalException ? ((WorkItemRetrievalException) e).getStatusCode()
            : null;
        return statusCode != null
            && (statusCode == HttpURLConnection.HTTP_NOT_FOUND || statusCode == HttpURLConnection.HTTP_FORBIDDEN);
    }
}
