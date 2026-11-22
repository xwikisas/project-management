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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.descriptor.ComponentDescriptor;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.WorkItemSourceException;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemSource;
import com.xwiki.projectmanagement.source.WorkItemSourceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkItemSourceReader}.
 *
 * @version $Id$
 */
@ComponentTest
class WorkItemSourceReaderTest
{
    private static final String STATUS_FILTER =
        "{\"property\":\"status\",\"constraints\":[{\"operator\":\"equals\",\"value\":\"1\"}]}";

    private static final String TYPE_FILTER =
        "{\"query\":{\"filters\":[{\"property\":\"type\",\"constraints\":[{\"value\":\"bug\"}]}]}}";

    @InjectMockComponents
    private WorkItemSourceReader reader;

    @MockComponent
    @Named("context")
    private ComponentManager componentManager;

    private final WorkItemSource source = mock(WorkItemSource.class);

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.componentManager.hasComponent(WorkItemSource.class, "query")).thenReturn(true);
        when(this.componentManager.getInstance(WorkItemSource.class, "query")).thenReturn(this.source);
    }

    @Test
    void keepsTheParametersWhenTheContentIsEmpty() throws Exception
    {
        WorkItemSourceResult result =
            this.reader.read(new WorkItemSourceRequest().content(" \n").filters(TYPE_FILTER, true).identifier("1, 2"));

        assertEquals(TYPE_FILTER, result.getFilters());
        assertEquals("1, 2", result.getIdentifier());
        assertEquals(Map.of("identifier", "1, 2"), result.getClientContext());
    }

    @Test
    void readsTheContentWithTheQuerySourceByDefault() throws Exception
    {
        when(this.source.read(eq("status = 1"), any())).thenReturn(WorkItemSelection.ofFilters(statusFilter()));

        WorkItemSourceResult result = this.reader.read(new WorkItemSourceRequest().content("status = 1"));

        assertEquals("{\"query\":{\"filters\":[" + STATUS_FILTER + "]}}", result.getFilters());
        assertNull(result.getIdentifier());
        assertEquals(Map.of(), result.getClientContext());
    }

    @Test
    void prefersTheSourceOfTheClient() throws Exception
    {
        WorkItemSource clientSource = mock(WorkItemSource.class);
        when(this.componentManager.hasComponent(WorkItemSource.class, "test/query")).thenReturn(true);
        when(this.componentManager.getInstance(WorkItemSource.class, "test/query")).thenReturn(clientSource);
        when(clientSource.read(eq("content"), any())).thenAnswer(invocation -> {
            assertEquals("test", invocation.getArgument(1, WorkItemSourceContext.class).getClient());
            return WorkItemSelection.ofClientScope(Map.of("identifier", "http://tool/list", "scope", "value"));
        });

        WorkItemSourceResult result =
            this.reader.read(new WorkItemSourceRequest().client("test").source(" query ").content("content"));

        assertNull(result.getFilters());
        assertEquals("http://tool/list", result.getIdentifier());
        assertEquals(Map.of("scope", "value"), result.getClientScope());
        assertEquals(Map.of("scope", "value", "identifier", "http://tool/list"), result.getClientContext());
    }

    @Test
    void joinsTheListedWorkItemsInTheIdentifier() throws Exception
    {
        when(this.source.read(eq("1 PROJ-2"), any())).thenReturn(WorkItemSelection.ofIds(List.of("1", "PROJ-2")));

        WorkItemSourceResult result = this.reader.read(new WorkItemSourceRequest().client("test").content("1 PROJ-2"));

        assertEquals("1,PROJ-2", result.getIdentifier());
    }

    @Test
    void addsTheFiltersOfTheContentToEachChartDataset() throws Exception
    {
        when(this.source.read(eq("status = 1"), any())).thenReturn(WorkItemSelection.ofFilters(statusFilter()));

        WorkItemSourceResult result = this.reader.read(new WorkItemSourceRequest().content("status = 1")
            .filters("[" + TYPE_FILTER + ",{\"query\":{}},{}]", false));

        assertEquals("[{\"query\":{\"filters\":[{\"property\":\"type\",\"constraints\":[{\"value\":\"bug\"}]},"
            + STATUS_FILTER + "]}},{\"query\":{\"filters\":[" + STATUS_FILTER + "]}},{\"query\":{\"filters\":["
            + STATUS_FILTER + "]}}]", result.getFilters());
    }

    @Test
    void createsASingleDatasetWithoutChartFilters() throws Exception
    {
        when(this.source.read(eq("status = 1"), any())).thenReturn(WorkItemSelection.ofFilters(statusFilter()));

        WorkItemSourceResult result =
            this.reader.read(new WorkItemSourceRequest().content("status = 1").filters("[]", false));

        assertEquals("{\"query\":{\"filters\":[" + STATUS_FILTER + "]}}", result.getFilters());
    }

    @Test
    void failsOnInvalidChartFilters() throws Exception
    {
        when(this.source.read(eq("status = 1"), any())).thenReturn(WorkItemSelection.ofFilters(statusFilter()));
        WorkItemSourceRequest request = new WorkItemSourceRequest().content("status = 1").filters("{", false);

        assertEquals("Failed to parse the filters.",
            assertThrows(MacroExecutionException.class, () -> this.reader.read(request)).getMessage());
    }

    @Test
    void failsWhenTheContentReplacesTheFiltersParameter()
    {
        WorkItemSourceRequest request = new WorkItemSourceRequest().content("status = 1").filters(TYPE_FILTER, true);

        assertEquals("The [filters] parameter can't be used together with the macro content: write the filters in "
            + "the content instead.", assertThrows(MacroExecutionException.class, () -> this.reader.read(request))
            .getMessage());
    }

    @Test
    void failsWhenTheContentReplacesTheIdentifierParameter()
    {
        WorkItemSourceRequest request = new WorkItemSourceRequest().content("1, 2").identifier("3");

        assertEquals("The [identifier] parameter can't be used together with the macro content: list the work "
            + "items, or put their link, in the content instead.",
            assertThrows(MacroExecutionException.class, () -> this.reader.read(request)).getMessage());
    }

    @Test
    void ignoresTheCaseOfTheSource() throws Exception
    {
        WorkItemSource listSource = mock(WorkItemSource.class);
        ComponentDescriptor<WorkItemSource> descriptor = mock(ComponentDescriptor.class);
        when(descriptor.getRoleHint()).thenReturn("list");
        when(this.componentManager.<WorkItemSource>getComponentDescriptorList((Type) WorkItemSource.class))
            .thenReturn(List.of(descriptor));
        when(this.componentManager.getInstance(WorkItemSource.class, "list")).thenReturn(listSource);
        when(listSource.read(eq("1, 2"), any())).thenReturn(WorkItemSelection.ofIds(List.of("1", "2")));

        WorkItemSourceResult result =
            this.reader.read(new WorkItemSourceRequest().client("test").source("LIST").content("1, 2"));

        assertEquals("1,2", result.getIdentifier());
    }

    @Test
    void failsOnUnknownSource()
    {
        WorkItemSourceRequest request = new WorkItemSourceRequest().client("test").source("jql").content("x");

        assertEquals("Unknown source [jql].",
            assertThrows(MacroExecutionException.class, () -> this.reader.read(request)).getMessage());
    }

    @Test
    void reportsTheErrorOfTheSource() throws Exception
    {
        WorkItemSourceException error = new WorkItemSourceException("Unexpected [)] at position 3.");
        when(this.source.read(eq("a )"), any())).thenThrow(error);
        WorkItemSourceRequest request = new WorkItemSourceRequest().content("a )");

        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.reader.read(request));

        assertEquals("Unexpected [)] at position 3.", exception.getMessage());
        assertSame(error, exception.getCause());
    }

    private static List<LiveDataQuery.Filter> statusFilter()
    {
        return List.of(new LiveDataQuery.Filter("status", "equals", "1"));
    }
}
