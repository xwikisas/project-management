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
package com.xwiki.projectmanagement.livedata.internal.displayer;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.internal.renderer.html5.HTML5Renderer;
import org.xwiki.rendering.internal.renderer.html5.HTML5RendererFactory;
import org.xwiki.rendering.internal.renderer.xhtml.image.DefaultXHTMLImageRenderer;
import org.xwiki.rendering.internal.renderer.xhtml.image.DefaultXHTMLImageTypeRenderer;
import org.xwiki.rendering.internal.renderer.xhtml.link.DefaultXHTMLLinkRenderer;
import org.xwiki.rendering.internal.renderer.xhtml.link.DefaultXHTMLLinkTypeRenderer;
import org.xwiki.rendering.listener.Format;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.xml.html.HTMLElementSanitizer;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayer;
import com.xwiki.projectmanagement.displayer.WorkItemPropertyDisplayerManager;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultProjectManagementLiveDataDisplayer}, with the real HTML 5 renderer.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({
    DefaultProjectManagementClientExecutionContext.class,
    HTML5RendererFactory.class,
    HTML5Renderer.class,
    DefaultXHTMLLinkRenderer.class,
    DefaultXHTMLLinkTypeRenderer.class,
    DefaultXHTMLImageRenderer.class,
    DefaultXHTMLImageTypeRenderer.class
})
class DefaultProjectManagementLiveDataDisplayerTest
{
    private static final String CLIENT = "test";

    private static final String STATUS = "status";

    @InjectMockComponents
    private DefaultProjectManagementLiveDataDisplayer displayer;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private WorkItemPropertyDisplayerManager defaultDisplayerManager;

    @MockComponent
    private HTMLElementSanitizer htmlElementSanitizer;

    @BeforeEach
    void setUp()
    {
        // The sanitizer only matters for HTML coming from the content, which the displayed values don't hold.
        when(this.htmlElementSanitizer.isElementAllowed(anyString())).thenReturn(true);
        when(this.htmlElementSanitizer.isAttributeAllowed(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void displayRendersThePropertiesHavingADisplayerAndTheDatesAndLists()
    {
        Date dueDate = new Date(0);
        List<String> versions = List.of("1.0", "2.0");
        WorkItem workItem = new WorkItem();
        workItem.put(STATUS, "open");
        workItem.put("dueDate", dueDate);
        workItem.put("versions", versions);
        workItem.put("summary", "Some <b>summary</b>");
        Map<String, Object> parameters = Map.of("workItem", workItem);
        // Only the status has a displayer, but the dates and the lists are always displayed.
        when(this.defaultDisplayerManager.getDisplayerForProperty(STATUS))
            .thenReturn(mock(WorkItemPropertyDisplayer.class));
        when(this.defaultDisplayerManager.displayProperty(STATUS, "open", parameters))
            .thenReturn(List.of(new FormatBlock(List.of(new WordBlock("open")), Format.BOLD)));
        when(this.defaultDisplayerManager.displayProperty("dueDate", dueDate, parameters))
            .thenReturn(List.of(new WordBlock("today")));
        when(this.defaultDisplayerManager.displayProperty("versions", versions, parameters))
            .thenReturn(List.of(new FormatBlock(List.<Block>of(new WordBlock("1.0")), Format.ITALIC),
                new FormatBlock(List.<Block>of(new WordBlock("2.0")), Format.ITALIC)));

        this.displayer.display(List.of(workItem));

        // Each property is rendered on its own, without the HTML of the properties displayed before it.
        // The values without displayer are kept as they are.
        assertEquals(Map.of(STATUS, "<strong>open</strong>", "dueDate", "today",
            "versions", "<em>1.0</em><em>2.0</em>", "summary", "Some <b>summary</b>"), workItem);
    }

    @Test
    void displayUsesTheDisplayerManagerOfTheClient() throws Exception
    {
        WorkItemPropertyDisplayerManager clientDisplayerManager = mock(WorkItemPropertyDisplayerManager.class);
        this.componentManager.registerComponent(WorkItemPropertyDisplayerManager.class, CLIENT,
            clientDisplayerManager);
        setClient(CLIENT);
        WorkItem workItem = new WorkItem();
        workItem.put(STATUS, "open");
        when(clientDisplayerManager.getDisplayerForProperty(STATUS)).thenReturn(mock(WorkItemPropertyDisplayer.class));
        when(clientDisplayerManager.displayProperty(STATUS, "open", Map.of("workItem", workItem)))
            .thenReturn(List.of(new WordBlock("client")));

        this.displayer.display(List.of(workItem));

        assertEquals(Map.of(STATUS, "client"), workItem);
    }

    @Test
    void displayUsesTheDefaultDisplayerManagerForAClientWithoutOne() throws Exception
    {
        setClient(CLIENT);
        WorkItem workItem = new WorkItem();
        workItem.put(STATUS, "open");
        when(this.defaultDisplayerManager.getDisplayerForProperty(STATUS))
            .thenReturn(mock(WorkItemPropertyDisplayer.class));
        when(this.defaultDisplayerManager.displayProperty(STATUS, "open", Map.of("workItem", workItem)))
            .thenReturn(List.of(new WordBlock("default")));

        this.displayer.display(List.of(workItem));

        assertEquals(Map.of(STATUS, "default"), workItem);
    }

    private void setClient(String client) throws Exception
    {
        ((DefaultProjectManagementClientExecutionContext) this.componentManager
            .<ProjectManagementClientExecutionContext>getInstance(ProjectManagementClientExecutionContext.class))
            .setContext(Map.of("client", client));
    }
}
