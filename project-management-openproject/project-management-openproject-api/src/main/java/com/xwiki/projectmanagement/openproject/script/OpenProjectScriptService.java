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
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.projectmanagement.exception.AuthenticationException;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;

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
     * @param instanceName the name of the Open Project instance configuration.
     * @return whether the user was authorized to the configured Open Project instance or not.
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
                "The styles for the configured Open Project instances should be generated by an admin.");
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
}
