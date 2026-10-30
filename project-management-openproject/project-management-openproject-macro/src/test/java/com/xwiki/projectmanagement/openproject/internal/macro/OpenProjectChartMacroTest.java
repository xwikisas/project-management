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
package com.xwiki.projectmanagement.openproject.internal.macro;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.ProjectManagementManager;
import com.xwiki.projectmanagement.chart.displayer.ChartTypeDisplayer;
import com.xwiki.projectmanagement.internal.DefaultProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.model.WorkItem;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectChartMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectChartMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectChartMacroTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private OpenProjectChartMacro macro;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private UserTokenChecker userTokenChecker;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private ProjectManagementManager projectManagementManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @MockComponent
    @Named("bar")
    private ChartTypeDisplayer barDisplayer;

    private final MacroTransformationContext context = new MacroTransformationContext();

    private final DefaultProjectManagementClientExecutionContext executionContext =
        new DefaultProjectManagementClientExecutionContext();

    private final OpenProjectChartMacroParameters parameters = new OpenProjectChartMacroParameters();

    private final PaginatedResult<WorkItem> workItems = new PaginatedResult<>(List.of(new WorkItem()), 0, 100, 1);

    private final List<Block> chart = List.of(new WordBlock("chart"));

    @BeforeComponent
    void registerExecutionContext() throws Exception
    {
        this.componentManager.registerComponent(ProjectManagementClientExecutionContext.class,
            this.executionContext);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        this.executionContext.setContext(null);
        this.parameters.setInstance("main");
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(List.of());
        OpenProjectMacroTestUtils.executeSynchronously(this.asyncExecutor);
        when(this.barDisplayer.getParameterTypeTemplate()).thenReturn(new HashMap<>());
        when(this.projectManagementManager.getWorkItems("openproject", 0, 100, List.of(), List.of()))
            .thenReturn(this.workItems);
        when(this.barDisplayer.execute(List.of(this.workItems), WorkItem.KEY_STATUS, List.of(), this.context,
            new HashMap<>())).thenReturn(this.chart);
    }

    @Test
    void descriptor()
    {
        assertFalse(this.macro.supportsInlineMode());
        assertEquals(Set.of("OpenProject", "Content"), this.macro.getDescriptor().getDefaultCategories());
    }

    @Test
    void displaysTheWorkItemsOfTheInstance() throws Exception
    {
        assertEquals(this.chart, this.macro.execute(this.parameters, "", this.context));

        assertEquals("openproject", this.parameters.getClient());
        // The instance is made available to the OpenProject client through the execution context.
        assertEquals("main", this.executionContext.get("instance"));
    }

    @Test
    void returnsTheMissingLicenseMessage() throws Exception
    {
        List<Block> licenseBlock = List.of(new WordBlock("license"));
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(licenseBlock);

        assertSame(licenseBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.observationManager, this.userTokenChecker, this.asyncExecutor,
            this.projectManagementManager);
        assertNull(this.executionContext.get("instance"));
    }

    @Test
    void returnsTheUserTokenWarning() throws Exception
    {
        List<Block> warningBlock = List.of(new WordBlock("warning"));
        when(this.userTokenChecker.getWarningBlock("main", this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.asyncExecutor, this.projectManagementManager);
        assertNull(this.executionContext.get("instance"));
    }
}
