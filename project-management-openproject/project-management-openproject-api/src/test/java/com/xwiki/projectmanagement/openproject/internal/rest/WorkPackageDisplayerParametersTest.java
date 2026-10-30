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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;
import com.xwiki.projectmanagement.macro.ProjectManagementMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link WorkPackageDisplayerParameters}.
 *
 * @version $Id$
 */
class WorkPackageDisplayerParametersTest
{
    @Test
    void create() throws Exception
    {
        ProjectManagementMacroParameters parameters =
            WorkPackageDisplayerParameters.create(WorkItemsDisplayer.workItemsSingle, "instance", "123");

        assertEquals(WorkItemsDisplayer.workItemsSingle, parameters.getWorkItemsDisplayer());
        // The Live Data displayers get the work items from the project management source, with OpenProject properties.
        assertEquals("projectmanagement", parameters.getSource());
        assertEquals("identifier,type,summary,description,startDate,assignees", parameters.getProperties());
        assertEquals("instance=instance&client=openproject&translationPrefix=openproject.",
            parameters.getSourceParameters());
        // The work package is selected by a filter on its identifier.
        List<LiveDataQuery.Filter> filters = new ObjectMapper()
            .readValue(parameters.getFilters(), LiveDataConfiguration.class).getQuery().getFilters();
        assertEquals(1, filters.size());
        assertEquals("identifier", filters.get(0).getProperty());
        assertEquals(1, filters.get(0).getConstraints().size());
        assertEquals("=", filters.get(0).getConstraints().get(0).getOperator());
        assertEquals("123", filters.get(0).getConstraints().get(0).getValue());
    }

    @Test
    void createKeepsTheConnectionNameASingleSourceParameter() throws Exception
    {
        ProjectManagementMacroParameters parameters =
            WorkPackageDisplayerParameters.create(WorkItemsDisplayer.liveData, "My&instance=x y", "1");

        assertEquals(WorkItemsDisplayer.liveData, parameters.getWorkItemsDisplayer());
        assertEquals("instance=My%26instance%3Dx+y&client=openproject&translationPrefix=openproject.",
            parameters.getSourceParameters());
    }
}
