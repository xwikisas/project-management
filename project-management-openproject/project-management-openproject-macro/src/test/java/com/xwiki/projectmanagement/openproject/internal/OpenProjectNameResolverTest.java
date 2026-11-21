package com.xwiki.projectmanagement.openproject.internal;

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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.internal.processing.WorkPackageFieldsCache;
import com.xwiki.projectmanagement.openproject.model.Status;
import com.xwiki.projectmanagement.source.WorkItemName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectNameResolver}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectNameResolverTest
{
    private static final String INSTANCE = "main";

    @InjectMockComponents
    private OpenProjectNameResolver resolver;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private WorkPackageFieldsCache workPackageFieldsCache;

    @Mock
    private OpenProjectApiClient apiClient;

    private OpenProjectAccessResult access;

    @BeforeEach
    void setUp() throws Exception
    {
        this.access = OpenProjectAccessResult.authenticated(this.apiClient);
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(this.access);
        when(this.workPackageFieldsCache.getFields(INSTANCE, this.access))
            .thenReturn(OpenProjectWorkItemFields.empty());
        Status closed = new Status();
        closed.setId(12);
        closed.setName("Closed");
        when(this.apiClient.getStatuses()).thenReturn(new PaginatedResult<>(List.of(closed), 0, 1, 1));
    }

    @Test
    void resolveReplacesTheNamesByIds() throws Exception
    {
        // Without the fields of the instance, the names of the generic properties are still resolved.
        when(this.workPackageFieldsCache.getFields(INSTANCE, this.access))
            .thenThrow(new WorkItemRetrievalException("Failed", 500));

        List<LiveDataQuery.Filter> resolved =
            this.resolver.resolve(INSTANCE, List.of(statusFilter(new WorkItemName("closed"))));

        assertEquals("12", resolved.get(0).getConstraints().get(0).getValue());
    }

    @Test
    void resolveFailsOnAnUnknownName()
    {
        List<LiveDataQuery.Filter> filters = List.of(statusFilter(new WorkItemName("Done")));

        assertEquals("The OpenProject status [Done] of the filter on [status] doesn't exist or you are not allowed to "
            + "view it.", assertThrows(MacroExecutionException.class, () -> this.resolver.resolve(INSTANCE, filters))
            .getMessage());
    }

    @Test
    void resolveFailsOnAnUnknownProperty() throws Exception
    {
        when(this.workPackageFieldsCache.getFields(INSTANCE, this.access))
            .thenReturn(new OpenProjectWorkItemFields(List.of(), List.of("status")));
        // The property is checked even without names.
        List<LiveDataQuery.Filter> filters = List.of(new LiveDataQuery.Filter("colour", "=", "red"));

        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.resolver.resolve(INSTANCE, filters));

        assertEquals("Unknown property [colour]: use the id of a work package property, for example status, "
            + "assignees or targetVersion, or the name of a custom field.", exception.getMessage());
        // The macro error box displays the message of the root cause.
        assertNull(exception.getCause().getCause());
    }

    @Test
    void resolveKeepsTheFiltersWhenOpenProjectCantBeReached() throws Exception
    {
        when(this.apiClient.getStatuses()).thenThrow(new WorkItemRetrievalException("Unreachable", 503));
        List<LiveDataQuery.Filter> filters = List.of(statusFilter(new WorkItemName("Closed")));

        assertSame(filters, this.resolver.resolve(INSTANCE, filters));
    }

    @Test
    void resolveKeepsTheFiltersWithoutOpenProjectClient() throws Exception
    {
        when(this.openProjectAccess.resolve(any())).thenReturn(OpenProjectAccessResult.notConfigured());
        List<LiveDataQuery.Filter> filters = List.of(statusFilter(new WorkItemName("Closed")));

        assertSame(filters, this.resolver.resolve("unknown", filters));
    }

    private static LiveDataQuery.Filter statusFilter(Object value)
    {
        return new LiveDataQuery.Filter("status", "=", value);
    }
}
