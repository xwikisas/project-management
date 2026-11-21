package com.xwiki.projectmanagement.internal.source;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

/**
 * The {@code filters} source: the content holds the filters in the Live Data JSON format of the {@code filters}
 * parameter, e.g. {@code {"query":{"filters":[{"property":"status","constraints":[{"value":"1"}]}]}}}, without the
 * escaping that a parameter value requires.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component
@Named("filters")
@Singleton
public class FiltersWorkItemSource implements WorkItemSource
{
    @Inject
    private LiveDataConfigurationResolver<String> configurationResolver;

    @Override
    public WorkItemSelection read(String content, WorkItemSourceContext context) throws WorkItemSourceException
    {
        LiveDataConfiguration configuration;
        try {
            configuration = this.configurationResolver.resolve(content);
        } catch (LiveDataException | RuntimeException e) {
            throw new WorkItemSourceException(String.format("The macro content isn't valid Live Data JSON: %s",
                ExceptionUtils.getRootCauseMessage(e)), e);
        }
        if (configuration == null || configuration.getQuery() == null
            || configuration.getQuery().getFilters() == null)
        {
            return WorkItemSelection.ofFilters(Collections.emptyList());
        }
        return WorkItemSelection.ofFilters(configuration.getQuery().getFilters());
    }
}
