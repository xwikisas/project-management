package com.xwiki.projectmanagement.openproject.internal.displayer;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.internal.displayer.property.StringPropertyDisplayer;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.internal.displayer.property.StatusPropertyDisplayer;
import com.xwiki.projectmanagement.openproject.internal.displayer.property.TypePropertyDisplayer;

/**
 * Register custom displayers for the properties of Work Packages.
 *
 * @version $Id$
 */
@Singleton
@Component
@Named("openproject")
public class OpenProjectDisplayerManager implements WorkItemPropertyDisplayerManager, Initializable
{
    private static final String KEY_INSTANCE = "instance";

    @Inject
    private WorkItemPropertyDisplayerManager defaultDisplayerManager;

    @Inject
    private ProjectManagementClientExecutionContext executionContext;

    @Inject
    @Named("plain/1.0")
    private Parser plainTextParser;

    @Inject
    @Named("html/5.0")
    private Parser htmlParser;

    private final Map<String, WorkItemPropertyDisplayer> registeredDisplayers = new HashMap<>();

    @Override
    public void initialize() throws InitializationException
    {
        registeredDisplayers.put(WorkItem.KEY_TYPE, new TypePropertyDisplayer(plainTextParser));
        registeredDisplayers.put(WorkItem.KEY_STATUS, new StatusPropertyDisplayer(plainTextParser));
        registeredDisplayers.put(WorkItem.KEY_DESCRIPTION, new StringPropertyDisplayer(htmlParser));
    }

    @Override
    public List<Block> displayProperty(String propertyName, Object propertyValue, Map<String, String> parameters)
    {
        if (registeredDisplayers.containsKey(propertyName)) {
            Map<String, String> newParams = parameters;
            if (parameters.isEmpty()) {
                newParams = Collections.singletonMap(KEY_INSTANCE,
                    executionContext.getContext().getOrDefault(KEY_INSTANCE, "").toString());
            }
            return registeredDisplayers.get(propertyName).display(propertyValue, newParams);
        }
        return defaultDisplayerManager.displayProperty(propertyName, propertyValue, parameters);
    }

    @Override
    public WorkItemPropertyDisplayer getDisplayerForProperty(String property)
    {
        WorkItemPropertyDisplayer propertyDisplayer = registeredDisplayers.get(property);
        if (propertyDisplayer == null) {
            return defaultDisplayerManager.getDisplayerForProperty(property);
        }
        return propertyDisplayer;
    }
}
