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
package com.xwiki.projectmanagement.openproject.script;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.WikiPageLink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectScriptServiceTest
{
    private static final String CONNECTION_NAME = "main";

    @InjectMockComponents
    private OpenProjectScriptService scriptService;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @Mock
    private OpenProjectApiClient apiClient;

    @Test
    void isUserAuthorized()
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));

        assertTrue(this.scriptService.isUserAuthorized(CONNECTION_NAME));
    }

    @Test
    void isUserAuthorizedWhenNotConnected()
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConnected());

        assertFalse(this.scriptService.isUserAuthorized(CONNECTION_NAME));
    }

    @Test
    void isUserAuthorizedWhenNotConfigured()
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConfigured());

        assertFalse(this.scriptService.isUserAuthorized(CONNECTION_NAME));
    }

    @Test
    void getMentioningWorkPackages() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        PaginatedResult<WikiPageLink> links = new PaginatedResult<>(List.of(mock(WikiPageLink.class)), 1, 10, 1);
        when(this.apiClient.getPageLinks(1, 10, "filters")).thenReturn(links);

        assertSame(links, this.scriptService.getMentioningWorkPackages(CONNECTION_NAME, 1, 10, "filters"));
    }

    @Test
    void getMentioningWorkPackagesWhenNotConnected() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME)).thenReturn(OpenProjectAccessResult.notConnected());

        PaginatedResult<WikiPageLink> result =
            this.scriptService.getMentioningWorkPackages(CONNECTION_NAME, 1, 10, "filters");

        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotalItems());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getPageSize());
    }

    @Test
    void getMentioningWorkPackagesWhenTheEndpointIsMissing() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.apiClient.getPageLinks(1, 10, "filters"))
            .thenThrow(new WorkItemRetrievalException("Not found", 404));

        assertNull(this.scriptService.getMentioningWorkPackages(CONNECTION_NAME, 1, 10, "filters"));
    }

    @Test
    void getMentioningWorkPackagesWhenTheRequestFails() throws Exception
    {
        when(this.openProjectAccess.resolve(CONNECTION_NAME))
            .thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        WorkItemRetrievalException exception = new WorkItemRetrievalException("Failure", 500);
        when(this.apiClient.getPageLinks(1, 10, "filters")).thenThrow(exception);

        assertSame(exception, assertThrows(WorkItemRetrievalException.class,
            () -> this.scriptService.getMentioningWorkPackages(CONNECTION_NAME, 1, 10, "filters")));
    }
}
