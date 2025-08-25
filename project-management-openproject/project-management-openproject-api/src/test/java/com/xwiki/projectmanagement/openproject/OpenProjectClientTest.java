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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;
import org.slf4j.Logger;
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
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectFilterHandler;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectSortingHandler;
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

    @MockComponent
    private Logger logger;

    @InjectMocks
    @InjectMockComponents
    private OpenProjectClient openProjectClient;

    private static final Integer NUMBER_OF_WORK_PACKAGES = 10;

    @BeforeEach
    public void setUp() throws ProjectManagementException
    {
        when(executionContext.get("instance")).thenReturn("");
        when(openProjectConfiguration.getOpenProjectApiClient(any())).thenReturn(this.openProjectApiClient);
        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenReturn(
            generateWorkItems());
        when(openProjectApiClient.getProjectWorkPackages(anyString(), anyInt(), anyInt(), anyString(),
            anyString())).thenReturn(generateWorkItems());
        doNothing().when(logger).warn(anyString());
    }

    @Test
    public void getWorkItemsWithoutIdentifierTest() throws ProjectManagementException
    {
        getWorkItemsTest(null, 1, 0, NUMBER_OF_WORK_PACKAGES);
    }

    @Test
    public void getWorkItemsWithIdentifier() throws ProjectManagementException
    {
        getWorkItemsTest("http://open-project-instance/work_packages", 1, 0, NUMBER_OF_WORK_PACKAGES);
    }

    @Test
    public void getWorkItemsWithIdentifierAndProjectTest() throws ProjectManagementException
    {
        getWorkItemsTest("http://open-project-instance/projects/first-project/work_packages", 0, 1,
            NUMBER_OF_WORK_PACKAGES);
    }

    @Test
    public void getWorkPackagesInvalidFiltersOrSortingTest() throws ProjectManagementException
    {
        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenThrow(
            WorkPackageRetrievalBadRequestException.class);

        getWorkItemsTest("http://open-project-instance/work_packages", 1, 0, 0);
    }

    @Test
    public void getWorkPackagesThrowsProjectManagementExceptionTest() throws ProjectManagementException
    {
        when(openProjectApiClient.getWorkPackages(anyInt(), anyInt(), anyString(), anyString())).thenThrow(
            ProjectManagementException.class);

        assertThrows(WorkItemRetrievalException.class,
            () -> openProjectClient.getWorkItems(1, 10, List.of(), List.of()));
    }

    @Test
    public void getWorkPackagesWithBadFiltersOrSortingJsonRepresentation()
    {
        try (
            MockedStatic<OpenProjectFilterHandler> filterMock = mockStatic(OpenProjectFilterHandler.class);
            MockedStatic<OpenProjectSortingHandler> sortingMock = mockStatic(OpenProjectSortingHandler.class)
        )
        {
            filterMock.when(() -> OpenProjectFilterHandler.convertFilters(any()))
                .thenThrow(new ProjectManagementException("Invalid filters")
                {
                });

            sortingMock.when(() -> OpenProjectSortingHandler.convertSorting(any()))
                .thenThrow(new ProjectManagementException("Invalid sorting")
                {
                });

            assertThrows(WorkItemRetrievalException.class,
                () -> openProjectClient.getWorkItems(1, 10, List.of(), List.of()));
        }
    }

    @Test
    public void handleNullOpenProjectApiClientTest()
    {
        when(openProjectConfiguration.getOpenProjectApiClient(anyString())).thenReturn(null);

        assertThrows(WorkItemRetrievalException.class,
            () -> openProjectClient.getWorkItems(1, 10, List.of(), List.of()));
    }

    private void getWorkItemsTest(String identifier, int expectedWorkPackagesCalls,
        int expectedProjectWorkPackagesCalls, int expectedElements) throws ProjectManagementException
    {

        when(executionContext.get("identifier")).thenReturn(identifier);

        PaginatedResult<WorkItem> result = openProjectClient.getWorkItems(1, 10, List.of(), List.of());

        verify(openProjectApiClient, times(expectedWorkPackagesCalls)).getWorkPackages(anyInt(), anyInt(), anyString(),
            anyString());

        verify(openProjectApiClient, times(expectedProjectWorkPackagesCalls)).getProjectWorkPackages(anyString(),
            anyInt(), anyInt(), anyString(),
            anyString());

        assertEquals(expectedElements, result.getItems().size());
    }

    private PaginatedResult<WorkPackage> generateWorkItems()
    {
        PaginatedResult<WorkPackage> result = new PaginatedResult<>();
        List<WorkPackage> workPackages = new ArrayList<>();
        for (int i = 1; i <= NUMBER_OF_WORK_PACKAGES; i++) {
            WorkPackage workPackage = new WorkPackage();
            workPackage.setId(i);
            workPackages.add(workPackage);
        }
        result.setItems(workPackages);
        return result;
    }
}
