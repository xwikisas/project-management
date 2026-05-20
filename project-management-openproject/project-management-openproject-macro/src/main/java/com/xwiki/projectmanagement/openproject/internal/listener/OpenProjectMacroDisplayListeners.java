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
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

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
    private static final String PARAMETERS_KEY = "parameters";

    private static final String OPEN_PROJECT = "OpenProject";

    private static final String DASHBOARD_PAGE_NAME = "WebHome";

    private static final List<String> DASHBOARD_CONFIG_CLASS_SPACE = Arrays.asList(OPEN_PROJECT, "Code");

    private static final String DASHBOARD_CONFIG_CLASS_NAME = "DashboardConnectionConfigClass";

    private static final String SELECTED_CONNECTION_PROPERTY = "selectedConnection";

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> xContextProvider;

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

        if (currentDoc == null) {
            return;
        }

        String currentSpace = currentDoc.getDocumentReference().getLastSpaceReference().getName();
        String currentPage = currentDoc.getDocumentReference().getName();

        if (!OPEN_PROJECT.equals(currentSpace) || !DASHBOARD_PAGE_NAME.equals(currentPage)) {
            return;
        }

        Map<String, OpenProjectMacroParameters> eventData = (Map<String, OpenProjectMacroParameters>) data;
        Object parametersObj = eventData.get(PARAMETERS_KEY);

        if (!(parametersObj instanceof OpenProjectMacroParameters)) {
            return;
        }

        OpenProjectMacroParameters parameters = (OpenProjectMacroParameters) parametersObj;

        DocumentReference classReference =
            new DocumentReference(
                xContext.getWikiId(), DASHBOARD_CONFIG_CLASS_SPACE, DASHBOARD_CONFIG_CLASS_NAME
            );

        BaseObject configObject = currentDoc.getXObject(classReference);

        if (configObject == null) {
            logger.debug("No DashboardConnectionConfigClass object found on [{}]", currentDoc.getDocumentReference());
            return;
        }

        String selectedConnection = configObject.getStringValue(SELECTED_CONNECTION_PROPERTY);

        if (StringUtils.isNotBlank(selectedConnection) && !selectedConnection.equals(parameters.getInstance())) {
            parameters.setInstance(selectedConnection);
        }
    }
}
