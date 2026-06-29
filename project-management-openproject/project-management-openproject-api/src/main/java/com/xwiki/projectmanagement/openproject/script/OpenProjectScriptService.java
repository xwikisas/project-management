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
package com.xwiki.projectmanagement.openproject.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.commons.document.MacroUtils;
import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.FilterBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.model.WikiPageLink;

/**
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("openproject")
@Singleton
public class OpenProjectScriptService implements ScriptService
{
    /**
     * The value of the option representing the "use the dashboard-selected connection" choice.
     */
    public static final String USE_SELECTED_DASHBOARD_CONNECTION_VALUE = "use_selected_dashboard_connection";

    /**
     * The value of the option representing the "use the dashboard-selected project" choice.
     *
     */
    public static final String USE_SELECTED_DASHBOARD_PROJECT_VALUE = "use_selected_dashboard_project";

    private static final String USE_SELECTED_DASHBOARD_CONNECTION_LABEL = "Use selected dashboard connection";

    private static final String USE_SELECTED_DASHBOARD_PROJECT_LABEL = "Use selected dashboard project";

    private static final String NAME = "name";

    private static final String VALUE = "value";

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    @Inject
    private StylingSetupManager stylingSetupManager;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    private Provider<XWikiContext> xContextProvider;

    @Inject
    private MacroUtils macroUtils;

    @Inject
    private UserTokenChecker userTokenChecker;

    /**
     * Retrieves a list of available OpenProject connections.
     *
     * @return a list of {@code OpenProjectConnection} instances;
     */
    public List<Map<String, String>> getConnectionOptions() throws AuthenticationException
    {
        List<OpenProjectConnection> openProjectConnections = openProjectConfiguration.getOpenProjectConnections();

        List<Map<String, String>> options = new ArrayList<>();
        if (isDashboardContext()) {
            Map<String, String> emptyOption = new HashMap<>();
            emptyOption.put(NAME, USE_SELECTED_DASHBOARD_CONNECTION_LABEL);
            emptyOption.put(VALUE, USE_SELECTED_DASHBOARD_CONNECTION_VALUE);
            options.add(emptyOption);
        }

        for (OpenProjectConnection openProjectConnection : openProjectConnections) {
            Map<String, String> option = new HashMap<>();
            option.put(NAME, openProjectConnection.getConnectionName());
            option.put(VALUE, openProjectConnection.getConnectionName());
            options.add(option);
        }
        return options;
    }

    /**
     * @param instance the configured OpenProject connection.
     * @return the server url of the given OpenProject connection.
     * @since 1.2.0
     */
    public String getConnectionUrl(String instance)
    {
        OpenProjectConnection openProjectConnection = openProjectConfiguration.getConnection(instance);
        if (openProjectConnection == null) {
            return null;
        }
        return openProjectConnection.getServerURL();
    }

    /**
     * @param instanceName the name of the OpenProject instance configuration.
     * @return whether the user was authorized to the configured OpenProject instance or not.
     * @since 1.0-rc-3
     */
    public boolean isUserAuthorized(String instanceName)
    {
        String accessToken = openProjectConfiguration.getAccessTokenForConfiguration(instanceName);
        return accessToken != null;
    }

    /**
     * @return the option representing the "use the dashboard-selected project" choice.
     * @since 1.2
     */
    public Map<String, String> getUseSelectedDashboardProjectOption()
    {
        Map<String, String> option = new HashMap<>();
        option.put(VALUE, USE_SELECTED_DASHBOARD_PROJECT_VALUE);
        option.put(NAME, USE_SELECTED_DASHBOARD_PROJECT_LABEL);
        return option;
    }

    /**
     * Generate the styling for the configured instances. Since this method will need to communicate with the configured
     * instances and make http requests, it should be run in a separate thread.
     */
    public void generateStyling() throws ProjectManagementException
    {
        if (!authorizationManager.hasAccess(Right.ADMIN)) {
            throw new ProjectManagementException(
                "The styles for the configured OpenProject instances should be generated by an admin.");
        }
        stylingSetupManager.setupInstanceStyles();
    }

    /**
     * Determines whether the current document is the OpenProject.WebHome dashboard page. When this returns true, macros
     * may leave their connection and project parameters empty (or set to the inferred from the dashboard pickers).
     *
     * @return true if the current document is OpenProject.WebHome, false otherwise
     */
    public boolean isDashboardContext()
    {
        XWikiContext xContext = xContextProvider.get();
        XWikiDocument currentDoc = xContext.getDoc();

        if (currentDoc == null) {
            return false;
        }

        String currentSpace = currentDoc.getDocumentReference().getLastSpaceReference().getName();
        String currentPage = currentDoc.getDocumentReference().getName();

        return "OpenProject".equals(currentSpace) && "WebHome".equals(currentPage);
    }

    /**
     *
     * @param instance the configured OpenProject instance against which the user should be authenticated.
     * @param syntax the syntax in which the message will be rendered.
     * @return a warning message rendered in the given syntax.
     * @throws ComponentLookupException if the syntax does not exist.
     * @since 1.2.0
     */
    public String getNotAuthorizedMessage(String instance, Syntax syntax) throws ComponentLookupException
    {
        return macroUtils.renderMacroContent(userTokenChecker.getWarningBlock(instance), syntax);
    }

    /**
     * @return a new instance of a filter builder that can be used to create OpenProject filters.
     * @since 1.2.0
     */
    public FilterBuilder getFilterBuilder()
    {
        return new FilterBuilder();
    }

    /**
     * @param instance the OP instance configuration that will be used.
     * @param page the number of the page.
     * @param pageSize the number of elements per page.
     * @param filters the OP rest filters.
     * @return a list of OpenProject wiki links - entities that represent wiki pages mentioned inside a work package.
     * @throws ProjectManagementException if some exception is thrown.
     * @since 1.2.0
     */
    public PaginatedResult<WikiPageLink> getMentioningWorkPackages(String instance, int page, int pageSize,
        String filters) throws ProjectManagementException
    {
        OpenProjectApiClient openProjectApiClient =
            openProjectConfiguration.getOpenProjectApiClient(instance);

        try {
            return openProjectApiClient.getPageLinks(page, pageSize, filters);
        } catch (WorkItemRetrievalException e) {
            // This means that the endpoint is not available. The UI should print a message informating the user
            // about that.
            if (e.getStatusCode() == 404) {
                return null;
            } else {
                throw e;
            }
        }
    }
}
