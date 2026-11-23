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
package com.xwiki.projectmanagement.openproject.internal.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.rest.XWikiResource;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.rest.WikiRequestExecutor;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLink;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLinkQuery;
import com.xwiki.projectmanagement.openproject.model.SavedQuery;

/**
 * Describes the link of OpenProject given to a macro, so that the macro editor can tell what the link selects before
 * the macro is inserted: a saved query (named, with the account of the current user, since a saved query may be
 * private), a work package list with its filters, the work packages of a project, a single work package, or why the
 * link can't be used.
 * <p>
 * The response is a JSON object with the following properties:
 * <ul>
 * <li>{@code kind}: one of {@code savedQuery}, {@code list}, {@code project}, {@code workPackage},
 * {@code otherServer}, {@code invalid} and {@code unavailable} (a saved query the current user can't see)</li>
 * <li>{@code level}: {@code info}, or {@code warning} when the macro wouldn't display the work packages of the
 * link</li>
 * <li>{@code message}: the description of the link, as translated plain text</li>
 * <li>{@code name}: the name of the saved query, for the {@code savedQuery} kind</li>
 * <li>{@code filterCount}: the number of filters of the link, for the {@code list} and {@code project} kinds</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.rest.OpenProjectLinkResource")
@Singleton
@Path("/wikis/{wikiName}/openproject/link")
public class OpenProjectLinkResource extends XWikiResource
{
    private static final String KIND = "kind";

    private static final String LEVEL = "level";

    private static final String MESSAGE = "message";

    private static final String NAME = "name";

    private static final String FILTER_COUNT = "filterCount";

    private static final String INFO = "info";

    private static final String WARNING = "warning";

    private static final String KIND_SAVED_QUERY = "savedQuery";

    private static final String KIND_UNAVAILABLE = "unavailable";

    private static final String KIND_INVALID = "invalid";

    private static final String KIND_WORK_PACKAGE = "workPackage";

    private static final String KIND_LIST = "list";

    private static final String KIND_PROJECT = "project";

    private static final String DESCRIBE_PREFIX = "openproject.link.describe.";

    @Inject
    private OpenProjectConfiguration configuration;

    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private OpenProjectIdentifierResolver identifierResolver;

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private WikiRequestExecutor wikiRequestExecutor;

    @Inject
    private PageEditRightChecker pageEditRightChecker;

    /**
     * @param wiki the wiki whose OpenProject connections are used
     * @param instance the name of the OpenProject connection of the macro, or an empty value for the default
     *     connection
     * @param url the link to describe
     * @param document the page of the given wiki whose macro is edited: only the users who can edit it get the
     *     description, which reveals the server URL of the connection (guests, in particular, don't)
     * @return the description of the link (see the description of this class), the 403 status when the current user
     *     can't edit the given page or when it's on another wiki, or the 404 status when the connection isn't
     *     configured
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLinkDescription(@PathParam("wikiName") String wiki, @QueryParam("instance") String instance,
        @QueryParam("url") String url, @QueryParam("document") String document)
    {
        return this.wikiRequestExecutor.execute(wiki, () -> {
            // The connections are the ones of the given wiki, which a page of another wiki doesn't give access to.
            if (!this.pageEditRightChecker.canEdit(wiki, document)) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
            return getLinkDescription(instance, url);
        });
    }

    private Response getLinkDescription(String instance, String url)
    {
        OpenProjectConnection connection = StringUtils.isBlank(instance) ? this.configuration.getDefaultConnection()
            : this.configuration.getConnection(instance);
        if (connection == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        OpenProjectLink link = OpenProjectLink.parse(url);
        if (link == null) {
            return describe(KIND_INVALID, WARNING, getMessage(KIND_INVALID));
        }
        if (!link.belongsTo(connection.getServerURL())) {
            return describe("otherServer", WARNING, translate("openproject.link.otherServer", link.getServer(),
                connection.getConnectionName(), connection.getServerURL()));
        }
        String workPackageId = link.getWorkPackageId();
        if (workPackageId != null) {
            return describe(KIND_WORK_PACKAGE, INFO, getMessage(KIND_WORK_PACKAGE, workPackageId));
        }
        String queryId = link.getSavedQueryId();
        return queryId != null ? describeSavedQuery(connection.getConnectionName(), link, queryId)
            : describeList(link);
    }

    /**
     * @param link the link of a work package list, of all the projects or of a project
     */
    private Response describeList(OpenProjectLink link)
    {
        int filterCount;
        try {
            filterCount = OpenProjectLinkQuery.countFilters(link);
        } catch (IllegalArgumentException e) {
            return describe(KIND_INVALID, WARNING, getMessage(KIND_INVALID));
        }
        String project = link.getProject();
        Map<String, Object> description = project == null
            ? getDescription(KIND_LIST, INFO, getMessage(KIND_LIST, filterCount))
            : getDescription(KIND_PROJECT, INFO, getMessage(KIND_PROJECT, project, filterCount));
        description.put(FILTER_COUNT, filterCount);
        return Response.ok(description).build();
    }

    private Response describeSavedQuery(String connectionName, OpenProjectLink link, String queryId)
    {
        OpenProjectApiClient client = this.openProjectAccess.resolve(connectionName).getClient();
        if (client == null) {
            // The current user isn't connected to a connection that requires it.
            return describeUnavailable(queryId);
        }
        SavedQuery query;
        try {
            query = client.getQuery(queryId);
        } catch (ProjectManagementException e) {
            // OpenProject answers 404 for the saved queries that the user isn't allowed to see too.
            if (isNotFoundOrForbidden(e)) {
                return describeUnavailable(queryId);
            }
            // The exception message can hold the OpenProject URL and the body of its error response, which are only
            // meant for the administrators: log them and send a generic message to the client.
            this.slf4Jlogger.warn("Failed to retrieve the saved query [{}] of the OpenProject connection [{}]. "
                + "Root cause: [{}]", queryId, connectionName, ExceptionUtils.getRootCauseMessage(e));
            return Response.serverError().type(MediaType.TEXT_PLAIN_TYPE)
                .entity("Failed to retrieve the saved query from OpenProject.").build();
        }
        Map<String, Object> description;
        if (isKeptAsIs(link, query)) {
            description = getDescription(KIND_SAVED_QUERY, INFO, getMessage("savedQuery.keptAsIs", query.getName()));
        } else if (query.getProjectName() == null) {
            description =
                getDescription(KIND_SAVED_QUERY, INFO, getMessage("savedQuery.allProjects", query.getName()));
        } else {
            description = getDescription(KIND_SAVED_QUERY, INFO,
                getMessage(KIND_SAVED_QUERY, query.getName(), query.getProjectName()));
        }
        description.put(NAME, query.getName());
        return Response.ok(description).build();
    }

    /**
     * @return {@code true} when the macro uses the saved query as it is, without showing its filters in its Live Data,
     *     because a work package list can't express the query (see
     *     {@link OpenProjectIdentifierResolver#getSavedQueryLink(OpenProjectLink, String)})
     */
    private boolean isKeptAsIs(OpenProjectLink link, SavedQuery query)
    {
        try {
            return this.identifierResolver.getSavedQueryLink(link, query.getResultsUrl()) == null;
        } catch (WorkItemRetrievalException e) {
            // The macro then uses the saved query as it is too.
            return true;
        }
    }

    private Response describeUnavailable(String queryId)
    {
        return describe(KIND_UNAVAILABLE, WARNING, translate("openproject.savedQuery.unavailable", queryId));
    }

    private static boolean isNotFoundOrForbidden(ProjectManagementException e)
    {
        Integer statusCode = e instanceof WorkItemRetrievalException ? ((WorkItemRetrievalException) e).getStatusCode()
            : null;
        return statusCode != null
            && (statusCode == Response.Status.NOT_FOUND.getStatusCode()
                || statusCode == Response.Status.FORBIDDEN.getStatusCode());
    }

    private Response describe(String kind, String level, String message)
    {
        return Response.ok(getDescription(kind, level, message)).build();
    }

    private static Map<String, Object> getDescription(String kind, String level, String message)
    {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put(KIND, kind);
        description.put(LEVEL, level);
        description.put(MESSAGE, message);
        return description;
    }

    private String getMessage(String kind, Object... parameters)
    {
        return translate(DESCRIBE_PREFIX + kind, parameters);
    }

    private String translate(String key, Object... parameters)
    {
        String message = this.l10n.getTranslationPlain(key, parameters);
        return message != null ? message : key;
    }
}
