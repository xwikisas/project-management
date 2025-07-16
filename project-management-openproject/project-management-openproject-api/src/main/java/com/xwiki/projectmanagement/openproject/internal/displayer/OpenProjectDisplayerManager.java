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

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
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
    @Inject
    private WorkItemPropertyDisplayerManager defaultDisplayerManager;

    @Inject
    @Named("plain/1.0")
    private Parser plainTextParser;

    private final Map<String, WorkItemPropertyDisplayer> registeredDisplayers = new HashMap<>();

    @Override
    public void initialize() throws InitializationException
    {
        registeredDisplayers.put("type", new TypePropertyDisplayer(plainTextParser));
        registeredDisplayers.put("status", new StatusPropertyDisplayer(plainTextParser));
    }

    @Override
    public List<Block> displayProperty(String propertyName, Object propertyValue, Map<String, String> parameters)
    {
        if (registeredDisplayers.containsKey(propertyName)) {
            return registeredDisplayers.get(propertyName).display(propertyValue, parameters);
        }
        return defaultDisplayerManager.displayProperty(propertyName, propertyValue, parameters);
    }

    @Override
    public WorkItemPropertyDisplayer getDisplayerForProperty(String property)
    {
        return null;
    }
}
