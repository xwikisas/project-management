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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
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
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.exception.WorkItemRetrievalException;
import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.CurrentUserFilterDetector;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectNameResolver;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectSavedQueryResolver;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.internal.source.OpenProjectUrlWorkItemSource;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemName;
import com.xwiki.projectmanagement.source.WorkItemSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
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
// The saved query of the identifier is resolved with the mocked components.
@ComponentList({ WorkItemSourceReader.class, OpenProjectSavedQueryResolver.class })
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
    private CurrentUserFilterDetector currentUserFilterDetector;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @Mock
    private Translation translation;

    @MockComponent
    private StylingSetupManager stylingSetupManager;

    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private OpenProjectIdentifierResolver identifierResolver;

    @MockComponent
    private OpenProjectNameResolver nameResolver;

    @MockComponent
    @Named("ssrx")
    private SkinExtension ssrx;

    @MockComponent
    @Named("jsx")
    private SkinExtension jsx;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    @Named("context")
    private ComponentManager contextComponentManager;

    @MockComponent
    private ProjectManagementAsyncExecutor asyncExecutor;

    @Mock
    private Macro<ProjectManagementAsyncMacroParams> displayerMacro;

    private final MacroTransformationContext context = new MacroTransformationContext();

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
        // The live data displayer is not rendered asynchronously.
        when(this.asyncExecutor.execute(same(this.displayerMacro), same(this.parameters), any(), same(this.context),
            any(), eq(false))).thenReturn(this.displayed);
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

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals(List.of(new GroupBlock(this.displayed, Map.of("class", "open-project-macro"))), result);
        assertEquals("projectmanagement", this.parameters.getSource());
        assertEquals("client=openproject&instance=main&identifier=query+5&translationPrefix=openproject.",
            this.parameters.getSourceParameters());
        verify(this.ssrx).use("openproject/css/propertyStyles.css");
        verify(this.jsx).use("OpenProject.Code.ViewAction");
        verify(this.stylingSetupManager).useInstanceStyle("main");
    }

    @Test
    void displaysTheWorkItemsInlineInAFormatBlock() throws Exception
    {
        this.context.setInline(true);

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals(List.of(new FormatBlock(this.displayed, Format.NONE, Map.of("class", "open-project-macro"))),
            result);
    }

    @Test
    void returnsTheUserTokenWarning() throws Exception
    {
        List<Block> warningBlock = List.of(new WordBlock("warning"));
        when(this.userTokenChecker.getWarningBlock("main", false, this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "", this.context));

        verifyNoInteractions(this.asyncExecutor);
        assertNull(this.parameters.getSourceParameters());
    }

    @Test
    void asyncProcessParametersAddsTheSavedQueryResultsUrl() throws Exception
    {
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertEquals("savedQueryResultsUrl=results", this.parameters.getSourceParameters());
    }

    @Test
    void asyncProcessParametersIgnoresIdentifiersThatAreNotSavedQueries()
    {
        this.parameters.setIdentifier("12");

        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertNull(this.parameters.getSourceParameters());
        verifyNoInteractions(this.openProjectAccess);
    }

    @Test
    void asyncProcessParametersWithoutApiClient() throws Exception
    {
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.notConnected());
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");

        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertNull(this.parameters.getSourceParameters());
        verify(this.identifierResolver, never()).getSavedQueryResultsUrl(any(), any());
    }

    @Test
    void asyncProcessParametersWhenTheSavedQueryCannotBeRetrieved() throws Exception
    {
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5"))
            .thenThrow(new ProjectManagementException("forbidden"));

        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertNull(this.parameters.getSourceParameters());
        assertEquals("Failed to retrieve the saved query [5] of the instance [main]. "
            + "Cause: [ProjectManagementException: forbidden].", this.logCapture.getMessage(0));
    }

    @ParameterizedTest
    @ValueSource(ints = { 403, 404 })
    void asyncProcessParametersWarnsWhenTheSavedQueryIsNotFound(int statusCode) throws Exception
    {
        // OpenProject answers 404 for the saved queries that the user isn't allowed to see too.
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.anonymous(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5"))
            .thenThrow(new WorkItemRetrievalException("Not found", statusCode));
        Block message = new WordBlock("message");
        when(this.l10n.getTranslation("openproject.savedQuery.unavailable")).thenReturn(this.translation);
        when(this.translation.render("5")).thenReturn(message);
        List<Block> warning = List.of(new WordBlock("warning"));
        when(this.userTokenChecker.getWarningBlock("main", message, false)).thenReturn(warning);

        assertSame(warning, this.macro.asyncProcessParameters(this.parameters, this.context));
    }

    @Test
    void asyncProcessParametersDisplaysTheWorkItemsWhenTheSavedQueryFailsOtherwise() throws Exception
    {
        this.parameters.setIdentifier("saved");
        when(this.identifierResolver.getStandaloneQueryId("saved")).thenReturn("5");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5"))
            .thenThrow(new WorkItemRetrievalException("Server error", 500));

        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));
        assertEquals("Failed to retrieve the saved query [5] of the instance [main]. "
            + "Cause: [WorkItemRetrievalException: Server error].", this.logCapture.getMessage(0));
    }

    @Test
    void requiresAnAccountWhenTheFiltersUseTheCurrentUser() throws Exception
    {
        this.parameters.setPresetId("3");
        this.parameters.setFilters("filters");
        when(this.currentUserFilterDetector.hasCurrentUserFilter("3", "filters")).thenReturn(true);
        List<Block> warningBlock = List.of(new WordBlock("connect"));
        when(this.userTokenChecker.getWarningBlock("main", true, this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "", this.context));
    }

    @Test
    void requiresAnAccountWhenTheIdentifierUsesTheCurrentUser() throws Exception
    {
        this.parameters.setIdentifier("url");
        when(this.currentUserFilterDetector.hasCurrentUserFilterInIdentifier("url")).thenReturn(true);
        List<Block> warningBlock = List.of(new WordBlock("connect"));
        when(this.userTokenChecker.getWarningBlock("main", true, this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "", this.context));
    }

    @Test
    void displaysTheWorkPackagesOfTheLinkInTheContent() throws Exception
    {
        useSource("openproject/url", new OpenProjectUrlWorkItemSource());
        this.parameters.setSource("url");

        this.macro.execute(this.parameters, " http://op/projects/demo/work_packages\n", this.context);

        // The link is handled like the identifier parameter, and the displayers don't get the content.
        assertEquals("http://op/projects/demo/work_packages", this.parameters.getIdentifier());
        assertEquals("client=openproject&instance=main&identifier=http%3A%2F%2Fop%2Fprojects%2Fdemo%2Fwork_packages"
            + "&translationPrefix=openproject.", this.parameters.getSourceParameters());
        verify(this.asyncExecutor).execute(same(this.displayerMacro), same(this.parameters), eq(""),
            same(this.context), any(), eq(false));
    }

    @Test
    void requiresAnAccountWhenTheLinkInTheContentUsesTheCurrentUser() throws Exception
    {
        useSource("openproject/url", new OpenProjectUrlWorkItemSource());
        this.parameters.setSource("url");
        when(this.currentUserFilterDetector.hasCurrentUserFilterInIdentifier("http://op/wp?query_props=me"))
            .thenReturn(true);
        List<Block> warningBlock = List.of(new WordBlock("connect"));
        when(this.userTokenChecker.getWarningBlock("main", true, this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "http://op/wp?query_props=me", this.context));
    }

    @Test
    void requiresAnAccountWhenTheFiltersOfTheContentUseTheCurrentUser() throws Exception
    {
        WorkItemSource source = mock(WorkItemSource.class);
        when(source.read(eq("assignee = me"), any())).thenReturn(
            WorkItemSelection.ofFilters(List.of(new LiveDataQuery.Filter("assignees", "=", "me"))));
        useSource("query", source);
        this.parameters.setPresetId("3");
        when(this.currentUserFilterDetector.hasCurrentUserFilter("3",
            "{\"query\":{\"filters\":[{\"property\":\"assignees\",\"constraints\":[{\"operator\":\"=\","
                + "\"value\":\"me\"}]}]}}")).thenReturn(true);
        List<Block> warningBlock = List.of(new WordBlock("connect"));
        when(this.userTokenChecker.getWarningBlock("main", true, this.context)).thenReturn(warningBlock);

        assertSame(warningBlock, this.macro.execute(this.parameters, "assignee = me", this.context));
    }

    @Test
    void resolvesTheNamesOfTheQueryInTheContent() throws Exception
    {
        WorkItemSource source = mock(WorkItemSource.class);
        when(source.read(eq("project = xwiki"), any())).thenReturn(WorkItemSelection.ofFilters(
            List.of(new LiveDataQuery.Filter("project", "=", new WorkItemName("xwiki")))));
        useSource("query", source);
        when(this.nameResolver.resolve(eq("main"), anyList()))
            .thenReturn(List.of(new LiveDataQuery.Filter("project", "=", "14")));

        this.macro.execute(this.parameters, "project = xwiki", this.context);

        // The displayers get the ids, and the names are resolved once: not when checking the current user filter.
        assertEquals("{\"query\":{\"filters\":[{\"property\":\"project\",\"constraints\":[{\"operator\":\"=\","
            + "\"value\":\"14\"}]}]}}", this.parameters.getFilters());
        verify(this.nameResolver).resolve(eq("main"), anyList());
    }

    @Test
    void displaysTheSavedQueryLinkedInTheContent() throws Exception
    {
        useSource("openproject/url", new OpenProjectUrlWorkItemSource());
        this.parameters.setSource("url");
        when(this.identifierResolver.getStandaloneQueryId("http://op/wp?query_id=5")).thenReturn("5");
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        this.macro.execute(this.parameters, "http://op/wp?query_id=5", this.context);
        // Called by the asynchronous renderer, with the parameters prepared by the macro execution.
        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertTrue(this.parameters.getSourceParameters().endsWith("&savedQueryResultsUrl=results"));
    }

    @Test
    void passesTheLiveDataConfigurationInTheContentToTheDisplayer() throws Exception
    {
        String configuration = "{\"query\":{\"sort\":[{\"property\":\"status\"}]}}";

        this.macro.execute(this.parameters, configuration, this.context);

        // Without source, a content holding a Live Data configuration is still passed to Live Data, as before the
        // source parameter: no source is looked up.
        verify(this.asyncExecutor).execute(same(this.displayerMacro), same(this.parameters), eq(configuration),
            same(this.context), any(), eq(false));
        verifyNoInteractions(this.contextComponentManager);
    }

    private void useSource(String hint, WorkItemSource source) throws Exception
    {
        when(this.contextComponentManager.hasComponent(WorkItemSource.class, hint)).thenReturn(true);
        when(this.contextComponentManager.getInstance(WorkItemSource.class, hint)).thenReturn(source);
    }
}
