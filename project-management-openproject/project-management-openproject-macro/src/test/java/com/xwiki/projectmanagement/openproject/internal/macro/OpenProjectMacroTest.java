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

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.event.BeforeOpenProjectMacroExecutionEvent;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectMacroTest
{
    private static final String LIVE_DATA_DISPLAYER = "proj-manag-liveData";

    @RegisterExtension
    private final LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectMacro macro;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private UserTokenChecker userTokenChecker;

    @MockComponent
    private StylingSetupManager stylingSetupManager;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectIdentifierResolver identifierResolver;

    @MockComponent
    @Named("ssrx")
    private SkinExtension ssrx;

    @MockComponent
    @Named("jsx")
    private SkinExtension jsx;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayerMacro;

    @Mock
    private MacroTransformationContext context;

    @Mock
    private OpenProjectApiClient apiClient;

    private final OpenProjectMacroParameters parameters = new OpenProjectMacroParameters();

    private final List<Block> displayed = List.of(new WordBlock("work items"));

    @BeforeEach
    void setUp() throws Exception
    {
        this.parameters.setInstance("main");
        when(this.componentManager.getInstance(Macro.class, LIVE_DATA_DISPLAYER)).thenReturn(this.displayerMacro);
        when(this.displayerMacro.supportsInlineMode()).thenReturn(true);
        when(this.asyncExecutor.execute(same(this.displayerMacro), same(this.parameters), any(), same(this.context),
            any(), anyBoolean())).thenReturn(this.displayed);
    }

    @Test
    void descriptor()
    {
        assertTrue(this.macro.supportsInlineMode());
        assertEquals(Set.of("OpenProject", "Content"), this.macro.getDescriptor().getDefaultCategories());
    }

    @Test
    void displaysTheWorkItemsInAGroup() throws Exception
    {
        this.parameters.setIdentifier("query 5");

        List<Block> result = this.macro.execute(this.parameters, "content", this.context);

        assertEquals(List.of(new GroupBlock(this.displayed, Map.of("class", "open-project-macro"))), result);
        assertEquals("projectmanagement", this.parameters.getSource());
        assertEquals("client=openproject&instance=main&identifier=query+5&translationPrefix=openproject.",
            this.parameters.getSourceParameters());
        verify(this.ssrx).use("openproject/css/propertyStyles.css");
        verify(this.jsx).use("OpenProject.Code.ViewAction");
        verify(this.stylingSetupManager).useInstanceStyle("main");
        // The live data displayer is not rendered asynchronously.
        verify(this.asyncExecutor).execute(same(this.displayerMacro), same(this.parameters), eq("content"),
            same(this.context), any(), eq(false));
    }

    @Test
    void displaysTheWorkItemsInlineInAFormatBlock() throws Exception
    {
        when(this.context.isInline()).thenReturn(true);

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals(List.of(new FormatBlock(this.displayed, Format.NONE, Map.of("class", "open-project-macro"))),
            result);
    }

    @Test
    void usesTheInstanceResolvedByTheListeners() throws Exception
    {
        this.parameters.setInstance(null);
        doAnswer(invocation -> {
            this.parameters.setInstance("dashboard");
            return null;
        }).when(this.observationManager).notify(any(BeforeOpenProjectMacroExecutionEvent.class), same(this.macro),
            same(this.parameters));

        this.macro.execute(this.parameters, "", this.context);

        verify(this.stylingSetupManager).useInstanceStyle("dashboard");
        verify(this.userTokenChecker).getWarningBlock("dashboard", this.context);
        assertEquals("client=openproject&instance=dashboard&translationPrefix=openproject.",
            this.parameters.getSourceParameters());
    }

    @Test
    void returnsTheMissingLicenseMessage() throws Exception
    {
        List<Block> licenseBlock = List.of(new WordBlock("license"));
        when(this.licenseChecker.getMissingLicenseBlock(this.context)).thenReturn(licenseBlock);

        assertSame(licenseBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.observationManager, this.ssrx, this.jsx, this.asyncExecutor);
    }

    @Test
    void returnsTheUserTokenWarning() throws Exception
    {
        List<Block> warningBlock = List.of(new WordBlock("warning"));
        when(this.userTokenChecker.getWarningBlock("main", this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.asyncExecutor);
        assertNull(this.parameters.getSourceParameters());
    }

    @Test
    void asyncProcessParametersAddsTheSavedQueryResultsUrl() throws Exception
    {
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectConfiguration.getOpenProjectApiClient("main")).thenReturn(this.apiClient);
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        this.macro.asyncProcessParameters(this.parameters);

        assertEquals("savedQueryResultsUrl=results", this.parameters.getSourceParameters());
    }

    @Test
    void asyncProcessParametersIgnoresIdentifiersThatAreNotSavedQueries()
    {
        this.parameters.setIdentifier("12");

        this.macro.asyncProcessParameters(this.parameters);

        assertNull(this.parameters.getSourceParameters());
        verifyNoInteractions(this.openProjectConfiguration);
    }

    @Test
    void asyncProcessParametersWithoutApiClient() throws Exception
    {
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");

        this.macro.asyncProcessParameters(this.parameters);

        assertNull(this.parameters.getSourceParameters());
        verify(this.identifierResolver, never()).getSavedQueryResultsUrl(any(), any());
    }

    @Test
    void asyncProcessParametersWhenTheSavedQueryCannotBeRetrieved() throws Exception
    {
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectConfiguration.getOpenProjectApiClient("main")).thenReturn(this.apiClient);
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5"))
            .thenThrow(new ProjectManagementException("forbidden"));

        this.macro.asyncProcessParameters(this.parameters);

        assertNull(this.parameters.getSourceParameters());
        assertEquals("Failed to retrieve the saved query [5] of the instance [main]. "
            + "Cause: [ProjectManagementException: forbidden].", this.logCapture.getMessage(0));
    }
}
