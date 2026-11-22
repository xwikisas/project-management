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
package com.xwiki.projectmanagement.openproject.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataQuery;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLinkQuery;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.internal.processing.WorkPackageFieldsCache;

/**
 * Retrieves the columns of the Live Data of an OpenProject macro, as configured by the OpenProject client for the
 * instance of the macro (including the columns of its fields), to know which filters of a link the Live Data can show.
 *
 * @version $Id$
 * @since 1.5.0
 */
@Component(roles = OpenProjectLiveDataColumnsResolver.class)
@Singleton
public class OpenProjectLiveDataColumnsResolver
{
    @Inject
    private OpenProjectAccess openProjectAccess;

    @Inject
    private WorkPackageFieldsCache workPackageFieldsCache;

    @Inject
    @Named("openproject")
    private LiveDataConfigurationResolver<LiveDataConfiguration> liveDataConfigurationResolver;

    @Inject
    private Logger logger;

    /**
     * @param instance the OpenProject instance of the macro, e.g. {@code main}
     * @param excludedProperties the properties whose filters the Live Data mustn't show, e.g. because the macro
     *     already filters them, the Live Data showing a single filter by property
     * @return the columns of the Live Data of the macro: the fields of the instance (or none, when they can't be
     *     retrieved) and the operators of the filter of each column (or none, when the configuration can't be resolved)
     */
    public OpenProjectLinkQuery.Columns resolve(String instance, Collection<String> excludedProperties)
    {
        Map<String, Set<String>> filterOperators = getFilterOperators(instance);
        filterOperators.keySet().removeAll(excludedProperties);
        return new OpenProjectLinkQuery.Columns(getFields(instance), filterOperators);
    }

    private OpenProjectWorkItemFields getFields(String instance)
    {
        OpenProjectAccessResult access = this.openProjectAccess.resolve(instance);
        if (access.getClient() == null) {
            return OpenProjectWorkItemFields.empty();
        }
        try {
            return this.workPackageFieldsCache.getFields(instance, access);
        } catch (ProjectManagementException | UnsupportedOperationException e) {
            // Only the filters and the sorting on the generic work item properties are moved to the Live Data.
            this.logger.debug("Failed to retrieve the work package fields of the instance [{}]: [{}].", instance,
                ExceptionUtils.getRootCauseMessage(e));
            return OpenProjectWorkItemFields.empty();
        }
    }

    private Map<String, Set<String>> getFilterOperators(String instance)
    {
        LiveDataConfiguration input = new LiveDataConfiguration();
        input.setQuery(new LiveDataQuery());
        input.getQuery().setSource(new LiveDataQuery.Source("projectmanagement"));
        if (StringUtils.isNotBlank(instance)) {
            input.getQuery().getSource().setParameter("instance", instance);
        }
        Map<String, Set<String>> filterOperators = new HashMap<>();
        LiveDataConfiguration configuration;
        try {
            configuration = this.liveDataConfigurationResolver.resolve(input);
        } catch (LiveDataException e) {
            this.logger.debug("Failed to resolve the Live Data configuration of the instance [{}]: [{}].", instance,
                ExceptionUtils.getRootCauseMessage(e));
            return filterOperators;
        }
        if (configuration == null || configuration.getMeta() == null
            || configuration.getMeta().getPropertyDescriptors() == null)
        {
            return filterOperators;
        }
        for (LiveDataPropertyDescriptor descriptor : configuration.getMeta().getPropertyDescriptors()) {
            LiveDataPropertyDescriptor.FilterDescriptor filter = descriptor.getFilter();
            if (Boolean.TRUE.equals(descriptor.isFilterable()) && filter != null && filter.getOperators() != null) {
                filterOperators.put(descriptor.getId(), filter.getOperators().stream()
                    .map(LiveDataPropertyDescriptor.OperatorDescriptor::getId).collect(Collectors.toSet()));
            }
        }
        return filterOperators;
    }
}
