package com.xwiki.projectmanagement.internal.displayer;

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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.parser.Parser;

import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.internal.displayer.property.DatePropertyDisplayer;
import com.xwiki.projectmanagement.internal.displayer.property.LinkablePropertyDisplayer;
import com.xwiki.projectmanagement.internal.displayer.property.ListPropertyDisplayer;
import com.xwiki.projectmanagement.internal.displayer.property.StringPropertyDisplayer;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.WorkItem;

/**
 * The default implementation of the {@link WorkItemPropertyDisplayerManager} that registers all the known
 * {@link WorkItemPropertyDisplayer} implementations. If no displayer is registered for a given property, it falls back
 * on the displayer registered for its type, if it exists. Ultimately, it falls back on the
 * {@link StringPropertyDisplayer}.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultWorkItemPropertyDisplayerManager implements WorkItemPropertyDisplayerManager, Initializable
{
    @Inject
    @Named("plain/1.0")
    private Parser plainParser;

    @Inject
    @Named("wiki")
    private ConfigurationSource wikiConfigSource;

    private final Map<String, WorkItemPropertyDisplayer> displayers = new HashMap<>();

    @Override
    public List<Block> displayProperty(String propertyName, Object propertyValue, Map<String, String> parameters)
    {
        WorkItemPropertyDisplayer propertyDisplayer = getDisplayerForProperty(propertyName);

        if (propertyValue == null) {
            return Collections.emptyList();
        }
        if (propertyDisplayer == null) {
            propertyDisplayer = getDisplayerForProperty(propertyValue.getClass().getName());
        }
        if (propertyValue instanceof Map && ((Map<?, ?>) propertyValue).containsKey(Linkable.KEY_VALUE) && ((Map<?,
            ?>) propertyValue).containsKey(Linkable.KEY_LOCATION))
        {
            propertyDisplayer = getDisplayerForProperty(Linkable.class.getName());
        }
        if (propertyDisplayer != null) {
            return propertyDisplayer.display(propertyValue, parameters);
        }
        return getDisplayerForProperty(String.class.getName()).display(propertyValue.toString(), parameters);
    }

    @Override
    public WorkItemPropertyDisplayer getDisplayerForProperty(String property)
    {
        return displayers.get(property);
    }

    @Override
    public void initialize() throws InitializationException
    {
        displayers.put(Linkable.class.getName(), new LinkablePropertyDisplayer(plainParser));
        displayers.put(String.class.getName(), new StringPropertyDisplayer(plainParser));
        WorkItemPropertyDisplayer propertyDisplayer = new ListPropertyDisplayer(this);
        displayers.put(WorkItem.KEY_ASSIGNEES, propertyDisplayer);
        displayers.put(WorkItem.KEY_LABELS, propertyDisplayer);
        displayers.put("list", propertyDisplayer);
        String format = wikiConfigSource.getProperty("dateformat", "dd/MM/yyyy hh:mm:ss");
        displayers.put(Date.class.getName(), new DatePropertyDisplayer(plainParser, format));
    }
}
