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
package com.xwiki.projectmanagement.openproject.internal.rest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

/**
 * Builds the parameters of the displayer macro that shows a single OpenProject work package.
 *
 * @version $Id$
 */
public final class WorkPackageDisplayerParameters
{
    private static final String CLIENT_ID = "openproject";

    private static final String LIVE_DATA_SOURCE = "projectmanagement";

    private static final String PROPERTIES = "identifier,type,summary,description,startDate,assignees";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private WorkPackageDisplayerParameters()
    {
    }

    /**
     * @param displayer the displayer that shows the work package
     * @param instance the name of the OpenProject connection configuration
     * @param workPackageId the id of the work package to display
     * @return the parameters of the displayer macro; their filters are also the content of the Live Data displayer
     * @throws JsonProcessingException if the filters can't be serialized
     */
    public static ProjectManagementMacroParameters create(WorkItemsDisplayer displayer, String instance,
        String workPackageId) throws JsonProcessingException
    {
        ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();
        parameters.setWorkItemsDisplayer(displayer);
        // The Live Data displayers get the work items from the project management Live Data source, and need
        // properties known to OpenProject, unlike the generic default ones.
        parameters.setSource(LIVE_DATA_SOURCE);
        parameters.setProperties(PROPERTIES);
        // TODO: Would be nicer if we retrieved the translation prefix from the json configuration.
        parameters.setSourceParameters(String.format("instance=%s&client=%s&translationPrefix=%s", encode(instance),
            CLIENT_ID, encode(CLIENT_ID + '.')));
        LiveDataConfiguration liveDataConfiguration = new LiveDataConfiguration();
        liveDataConfiguration.setQuery(new LiveDataQuery());
        liveDataConfiguration.getQuery()
            .setFilters(List.of(new LiveDataQuery.Filter("identifier", "=", workPackageId)));
        // The work items displayers read the filters from the parameters, the Live Data one from the content.
        parameters.setFilters(JSON_MAPPER.writeValueAsString(liveDataConfiguration));
        return parameters;
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
