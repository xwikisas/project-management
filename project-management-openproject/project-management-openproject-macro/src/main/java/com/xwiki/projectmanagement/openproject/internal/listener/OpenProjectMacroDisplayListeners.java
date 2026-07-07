package com.xwiki.projectmanagement.openproject.internal.listener;

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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.model.Project;

import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE;
import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_PROJECT_VALUE;

/**
 * Listener for OpenProject macro before execution event. Handles{@link BeforeOpenProjectMacroExecutionEvent} When the
 * macro is rendered on {@code OpenProject.WebHome}, the instance parameter is overridden with the selectedConnection
 * value from the OpenProject.Code.DashboardConnectionConfigClass object on that page, allowing the dashboard connection
 * picker to drive all macros on the page.
 *
 * @version $Id$
 */
@Component
@Named("com.xwiki.projectmanagement.openproject.internal.listener.OpenProjectMacroDisplayListeners")
@Singleton
public class OpenProjectMacroDisplayListeners extends AbstractEventListener
{
    private static final String INSTANCE_KEY = "instance";

    private static final String EFFECTIVE_INSTANCE_KEY = "effectiveInstance";

    private static final String PROJECT_KEY = "project";

    private static final String EFFECTIVE_PROJECT_KEY = "effectiveProject";

    private static final String OPEN_PROJECT = "OpenProject";

    private static final String DASHBOARD_PAGE_NAME = "WebHome";

    private static final List<String> DASHBOARD_CONFIG_CLASS_SPACE = Arrays.asList(OPEN_PROJECT, "Code");

    private static final String DASHBOARD_CONFIG_CLASS_NAME = "DashboardConnectionConfigClass";

    private static final String SELECTED_CONNECTION_PROPERTY = "selectedConnection";

    private static final String SELECTED_PROJECT_PROPERTY = "selectedProject";

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> xContextProvider;

    @Inject
    private OpenProjectConfiguration openProjectConfiguration;

    /**
     * Default constructor.
     */
    public OpenProjectMacroDisplayListeners()
    {
        super(OpenProjectMacroDisplayListeners.class.getName(), List.of(new BeforeOpenProjectMacroExecutionEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        if (event instanceof BeforeOpenProjectMacroExecutionEvent) {
            handleBeforeOpenProjectMacroExecution(data);
        }
    }

    private void handleBeforeOpenProjectMacroExecution(Object data)
    {
        if (!(data instanceof Map)) {
            return;
        }
        XWikiContext xContext = xContextProvider.get();
        XWikiDocument currentDoc = xContext.getDoc();

        if (!isDashboardPage(currentDoc)) {
            return;
        }

        Map<String, String> eventData = (Map<String, String>) data;

        DocumentReference classReference =
            new DocumentReference(
                xContext.getWikiId(), DASHBOARD_CONFIG_CLASS_SPACE, DASHBOARD_CONFIG_CLASS_NAME
            );

        BaseObject configObject = currentDoc.getXObject(classReference);

        if (configObject == null) {
            logger.debug("No DashboardConnectionConfigClass object found on [{}]", currentDoc.getDocumentReference());
            return;
        }

        if (eventData.containsKey(INSTANCE_KEY)) {
            resolveInstance(eventData, configObject);
        }

        if (eventData.containsKey(PROJECT_KEY)) {
            resolveProject(eventData, configObject);
        }
    }

    private void resolveInstance(Map<String, String> eventData, BaseObject configObject)
    {
        String instance = eventData.get(INSTANCE_KEY);
        if (!(USE_SELECTED_DASHBOARD_CONNECTION_VALUE.equals(instance) || StringUtils.isBlank(instance))) {
            return;
        }

        String effectiveConnection = getEffectiveConnection(configObject);
        if (StringUtils.isNotBlank(effectiveConnection)) {
            eventData.put(EFFECTIVE_INSTANCE_KEY, effectiveConnection);
        }
    }

    private void resolveProject(Map<String, String> eventData, BaseObject configObject)
    {
        String project = eventData.get(PROJECT_KEY);
        if (!(USE_SELECTED_DASHBOARD_PROJECT_VALUE.equals(project) || StringUtils.isBlank(project))) {
            return;
        }

        String selectedProject = configObject.getStringValue(SELECTED_PROJECT_PROPERTY);
        if (StringUtils.isNotBlank(selectedProject)) {
            eventData.put(EFFECTIVE_PROJECT_KEY, selectedProject);
            return;
        }

        String firstProject = getFirstProjectId(getEffectiveConnection(configObject));
        if (StringUtils.isNotBlank(firstProject)) {
            eventData.put(EFFECTIVE_PROJECT_KEY, firstProject);
        }
    }

    private String getEffectiveConnection(BaseObject configObject)
    {
        String selectedConnection = configObject.getStringValue(SELECTED_CONNECTION_PROPERTY);
        if (StringUtils.isNotBlank(selectedConnection)) {
            return selectedConnection;
        }

        List<OpenProjectConnection> connections = openProjectConfiguration.getOpenProjectConnections();
        if (connections != null && !connections.isEmpty()) {
            return connections.get(0).getConnectionName();
        }
        return "";
    }

    private String getFirstProjectId(String connection)
    {
        if (StringUtils.isBlank(connection)) {
            return "";
        }
        OpenProjectApiClient apiClient = openProjectConfiguration.getOpenProjectApiClient(connection);
        if (apiClient == null) {
            return "";
        }
        try {
            PaginatedResult<Project> projects = apiClient.getProjects(1, 1, "");
            if (!projects.getItems().isEmpty()) {
                return String.valueOf(projects.getItems().get(0).getId());
            }
        } catch (ProjectManagementException e) {
            logger.debug("Could not resolve the default project for connection [{}]: [{}]", connection,
                e.getMessage());
        }
        return "";
    }

    private boolean isDashboardPage(XWikiDocument document)
    {
        if (document == null) {
            return false;
        }

        String currentSpace = document.getDocumentReference().getLastSpaceReference().getName();
        String currentPage = document.getDocumentReference().getName();

        return OPEN_PROJECT.equals(currentSpace) && DASHBOARD_PAGE_NAME.equals(currentPage);
    }
}
