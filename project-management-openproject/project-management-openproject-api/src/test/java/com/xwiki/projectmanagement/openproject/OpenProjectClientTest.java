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
package com.xwiki.projectmanagement.openproject;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.exception.WorkPackageRetrievalBadRequestException;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectClient;
import com.xwiki.projectmanagement.openproject.model.WorkPackage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ComponentTest
public class OpenProjectClientTest
{
    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private ProjectManagementClientExecutionContext executionContext;

    @MockComponent
    private OpenProjectApiClient openProjectApiClient;

    @InjectMocks
    @InjectMockComponents
    private OpenProjectClient openProjectClient;

    @BeforeEach
    public void setUp() throws ProjectManagementException
    {
        when(executionContext.get("instance")).thenReturn("");
        when(openProjectConfiguration.getOpenProjectApiClient(any())).thenReturn(this.openProjectApiClient);
        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenReturn(
            generateWorkItems());
        when(openProjectApiClient.getProjectWorkPackages(anyString(), anyInt(), anyInt(), anyString(),
            anyString())).thenReturn(generateWorkItems());
    }

    @Test
    public void getWorkItemsWithoutIdentifierTest() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn(null);

        PaginatedResult<WorkItem> results = openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        verify(openProjectApiClient, times(1)).getWorkPackages(anyInt(), anyInt(), anyString(), anyString());

        verify(openProjectApiClient, times(0)).getProjectWorkPackages(anyString(), anyInt(), anyInt(), anyString(),
            anyString());

        assertEquals(2, results.getItems().size());
    }

    @Test
    public void getWorkItemsWithIdentifier() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn("http://open-project-instance"
            + "/work_packages");

        PaginatedResult<WorkItem> result = openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        verify(openProjectApiClient, times(1)).getWorkPackages(anyInt(), anyInt(), anyString(), anyString());

        verify(openProjectApiClient, times(0)).getProjectWorkPackages(anyString(), anyInt(), anyInt(), anyString(),
            anyString());

        assertEquals(2, result.getItems().size());
    }

    @Test
    public void getWorkItemsWithIdentifierAndProjectTest() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn("http://open-project-instance/projects/first-project"
            + "/work_packages");

        PaginatedResult<WorkItem> result = openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        verify(openProjectApiClient, times(0)).getWorkPackages(anyInt(), anyInt(), anyString(), anyString());

        verify(openProjectApiClient, times(1)).getProjectWorkPackages(anyString(), anyInt(), anyInt(), anyString(),
            anyString());

        assertEquals(2, result.getItems().size());
    }

    @Test
    public void getWorkPackagesInvalidFiltersOrSortingTest() throws ProjectManagementException
    {
        when(executionContext.get("identifier")).thenReturn("http://open-project-instance"
            + "/work_packages");

        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenThrow(
            WorkPackageRetrievalBadRequestException.class);

        PaginatedResult<WorkItem> result = openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        assertEquals(0, result.getItems().size());
    }

    @Test
    public void handleNullOpenProjectApiClientTest()
    {
        when(openProjectConfiguration.getOpenProjectApiClient(anyString())).thenReturn(null);

        assertThrows(WorkItemRetrievalException.class,
            () -> openProjectClient.getWorkItems(1, 10, List.of(), List.of()));
    }

    private PaginatedResult<WorkPackage> generateWorkItems()
    {
        PaginatedResult<WorkPackage> result = new PaginatedResult<>();
        List<WorkPackage> workPackages = new ArrayList<>();
        WorkPackage firstWorkPackage = new WorkPackage();
        firstWorkPackage.setId(1);

        WorkPackage secondWorkPackage = new WorkPackage();
        secondWorkPackage.setId(1);

        workPackages.add(firstWorkPackage);
        workPackages.add(secondWorkPackage);

        result.setItems(workPackages);
        return result;
    }
}
