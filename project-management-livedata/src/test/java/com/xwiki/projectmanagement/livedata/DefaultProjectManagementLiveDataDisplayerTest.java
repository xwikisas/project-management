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

package com.xwiki.projectmanagement.livedata;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.livadata.internal.displayer.DefaultProjectManagementLiveDataDisplayer;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the flow of the {@link DefaultProjectManagementLiveDataDisplayer}.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@ComponentTest
class DefaultProjectManagementLiveDataDisplayerTest
{
    private static final String WORK_ITEM_KEY = "key";

    private static final String WORK_ITEM_VALUE = "value";

    @InjectMockComponents
    private DefaultProjectManagementLiveDataDisplayer displayer;

    @MockComponent
    private WorkItemPropertyDisplayerManager defaultDisplayerManager;

    @MockComponent
    @Named("html/5.0")
    private PrintRendererFactory htmlRendererFactory;

    @MockComponent
    private PrintRenderer renderer;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private ProjectManagementClientExecutionContext executionContext;

    @Mock
    private DefaultWikiPrinter printer;

    @BeforeEach
    void setUp()
    {
        when(htmlRendererFactory.createRenderer(any())).thenReturn(renderer);
        when(renderer.getPrinter()).thenReturn(printer);
    }

    @Test
    void testDisplayWithDefaultPropertyDisplayer()
    {
        WorkItem workItem = prepareWorkItem();

        // Force default displayer manager.
        when(executionContext.get("client")).thenReturn(null);
        when(defaultDisplayerManager.getDisplayerForProperty(WORK_ITEM_KEY))
            .thenReturn(mock(WorkItemPropertyDisplayer.class));
        when(defaultDisplayerManager.displayProperty(anyString(), any(), anyMap()))
            .thenReturn(Collections.emptyList());

        displayer.display(Collections.singleton(workItem));

        verify(defaultDisplayerManager).displayProperty(eq(WORK_ITEM_KEY), eq(WORK_ITEM_VALUE), anyMap());
    }

    @Test
    void testDisplayPropertyWithDate()
    {
        Date now = new Date();
        Map.Entry<String, Object> property = new AbstractMap.SimpleEntry<>("date", now);

        // DisplayerManager does not know the property.
        when(defaultDisplayerManager.getDisplayerForProperty("date")).thenReturn(null);

        displayer.displayProperty(property, renderer, defaultDisplayerManager);

        assertEquals("printer", property.getValue());
    }

    @Test
    void testGetPropertyDisplayerManagerWithInvalidClient()
    {
        WorkItem workItem = prepareWorkItem();

        // Force default displayer manager.
        when(executionContext.get("client")).thenReturn("test");
        when(componentManager.hasComponent(WorkItemPropertyDisplayerManager.class, "test")).thenReturn(false);
        when(defaultDisplayerManager.getDisplayerForProperty(WORK_ITEM_KEY))
            .thenReturn(mock(WorkItemPropertyDisplayer.class));
        when(defaultDisplayerManager.displayProperty(anyString(), any(), anyMap()))
            .thenReturn(Collections.emptyList());

        displayer.display(Collections.singleton(workItem));

        verify(defaultDisplayerManager).displayProperty(eq(WORK_ITEM_KEY), eq(WORK_ITEM_VALUE), anyMap());
    }

    @Test
    void testGetPropertyDisplayerManagerWithValidClientId() throws Exception
    {
        WorkItem workItem = prepareWorkItem();

        String clientId = "test";
        WorkItemPropertyDisplayerManager customManager = mock(WorkItemPropertyDisplayerManager.class);

        when(executionContext.get("client")).thenReturn(clientId);
        when(componentManager.hasComponent(WorkItemPropertyDisplayerManager.class, clientId))
            .thenReturn(true);
        when(componentManager.getInstance(WorkItemPropertyDisplayerManager.class, clientId))
            .thenReturn(customManager);

        when(customManager.getDisplayerForProperty(WORK_ITEM_KEY)).thenReturn(mock(WorkItemPropertyDisplayer.class));
        when(customManager.displayProperty(eq(WORK_ITEM_KEY), eq(WORK_ITEM_VALUE), anyMap()))
            .thenReturn(Collections.emptyList());

        displayer.display(Collections.singleton(workItem));

        verify(customManager).displayProperty(eq(WORK_ITEM_KEY), eq(WORK_ITEM_VALUE), anyMap());
    }

    private static WorkItem prepareWorkItem()
    {
        Map.Entry<String, Object> property = new AbstractMap.SimpleEntry<>(WORK_ITEM_KEY, WORK_ITEM_VALUE);

        WorkItem workItem = mock(WorkItem.class);
        when(workItem.entrySet()).thenReturn(Collections.singleton(property));

        return workItem;
    }
}
