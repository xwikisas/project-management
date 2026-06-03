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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.observation.ObservationManager;

import com.xwiki.projectmanagement.openproject.OpenProjectInstanceHolder;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;

import static com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService.USE_SELECTED_DASHBOARD_CONNECTION_VALUE;

/**
 * The resolver notifies the {@link com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent}
 * event, allowing listeners to provide an OpenProject instance. If the macro parameters already specify an instance,
 * that value takes precedence. Otherwise, the instance provided through the event is used and propagated back to the
 * macro parameters.
 *
 * @version $Id$
 */
@Component(roles = InstanceResolver.class)
@Singleton
public class InstanceResolver
{
    private static final String INSTANCE_KEY = "instance";

    private static final String EFFECTIVE_INSTANCE_KEY = "effectiveInstance";

    @Inject
    private ObservationManager observationManager;

    /**
     * Resolves the OpenProject instance to use for the given macro parameters. If no instance is specified in the
     * parameters, listeners of {@link BeforeOpenProjectMacroExecutionEvent} can provide one.
     *
     * @param parameters the macro parameters
     * @return the resolved OpenProject instance
     */
    public String resolve(OpenProjectInstanceHolder parameters)
    {
        String currentInstance = parameters.getInstance();
        Map<String, String> eventData = new HashMap<>();
        eventData.put(INSTANCE_KEY, currentInstance);

        observationManager.notify(new BeforeOpenProjectMacroExecutionEvent(), this, eventData);

        String instanceFromEvent = eventData.get(EFFECTIVE_INSTANCE_KEY);
        boolean hasExplicitInstance = isExplicitInstance(currentInstance);

        String instanceToUse = hasExplicitInstance
            ? currentInstance
            : instanceFromEvent;

        if (!hasExplicitInstance && StringUtils.isNotBlank(instanceFromEvent)) {
            parameters.setInstance(instanceFromEvent);
        }

        return instanceToUse;
    }

    private boolean isExplicitInstance(String instance)
    {
        return StringUtils.isNotBlank(instance) && !USE_SELECTED_DASHBOARD_CONNECTION_VALUE.equals(instance);
    }
}
