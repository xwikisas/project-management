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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.internal.macro.ProjectManagementAsyncExecutor;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.macro.ProjectManagementAsyncMacroParams;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.CurrentUserFilterDetector;
import com.xwiki.projectmanagement.openproject.internal.LicenseChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectLinkChecker;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectLinkQueryResolver;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectLiveDataColumnsResolver;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectNameResolver;
import com.xwiki.projectmanagement.openproject.internal.OpenProjectSavedQueryResolver;
import com.xwiki.projectmanagement.openproject.internal.UserTokenChecker;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.displayer.StylingSetupManager;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectIdentifierResolver;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.internal.processing.WorkPackageFieldsCache;
import com.xwiki.projectmanagement.openproject.internal.source.OpenProjectUrlWorkItemSource;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectMacroParameters;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemName;
import com.xwiki.projectmanagement.source.WorkItemSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectMacro}.
 *
 * @version $Id$
 */
@ComponentTest
// The saved query of the identifier is resolved with the mocked components (its cases are in
// OpenProjectSavedQueryResolverTest).
@ComponentList({ WorkItemSourceReader.class, OpenProjectSavedQueryResolver.class, OpenProjectLinkQueryResolver.class,
    OpenProjectLiveDataColumnsResolver.class })
class OpenProjectMacroTest
{
    private static final String LIVE_DATA_DISPLAYER = "proj-manag-liveData";

    private static final String SAVED_QUERY_LINK = "http://op/wp?query_id=5";

    private static final Set<String> LIST = Set.of("=", "!");

    private static final String PROJECT_LIST = "https://op.example.com/projects/demo/work_packages";

    private static final String OTHER_SERVER_LINK = "https://other.example.com/projects/demo/work_packages";

    @RegisterExtension
    private final LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectMacro macro;

    @MockComponent
    private OpenProjectLinkChecker linkChecker;

    @MockComponent
    private LicenseChecker licenseChecker;

    @MockComponent
    private UserTokenChecker userTokenChecker;

    @MockComponent
    private CurrentUserFilterDetector currentUserFilterDetector;

    // Needed by the saved query resolver.
    @MockComponent
    private ContextualLocalizationManager l10n;

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

    // Needed by the link query resolver.
    @MockComponent
    private WorkPackageFieldsCache workPackageFieldsCache;

    @MockComponent
    @Named("openproject")
    private LiveDataConfigurationResolver<LiveDataConfiguration> liveDataConfigurationResolver;

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
        this.parameters.setIdentifier(SAVED_QUERY_LINK);
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertEquals("savedQueryResultsUrl=results", this.parameters.getSourceParameters());
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
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");

        this.macro.execute(this.parameters, SAVED_QUERY_LINK, this.context);
        // Called by the asynchronous renderer, with the parameters prepared by the macro execution.
        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertTrue(this.parameters.getSourceParameters().endsWith("&savedQueryResultsUrl=results"));
    }

    @Test
    void showsTheFiltersAndTheSortingOfTheSavedQueryLinkedInTheContentInTheLiveData() throws Exception
    {
        useSource("openproject/url", new OpenProjectUrlWorkItemSource());
        this.parameters.setSource("url");
        useColumns(OpenProjectWorkItemFields.empty(), Map.of("status", Set.of("=", "!", "o", "c")));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");
        // The saved query selects the open work packages matching a search, the most recent first.
        String search = "{\"f\":[{\"n\":\"search\",\"o\":\"**\",\"v\":[\"wiki\"]}]}";
        when(this.identifierResolver.getSavedQueryLink(any(), eq("results"))).thenReturn(link("{\"f\":["
            + "{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"search\",\"o\":\"**\",\"v\":[\"wiki\"]}],"
            + "\"t\":\"id:desc\"}"));

        this.macro.execute(this.parameters, SAVED_QUERY_LINK, this.context);
        // Called by the asynchronous renderer, with the parameters prepared by the macro execution.
        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        assertEquals("{\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":[{\"operator\":\"o\","
            + "\"value\":\"\"}]}]}}", this.parameters.getFilters());
        assertEquals("identifier:desc", this.parameters.getSort());
        // The client gets the work package list of the query, with the filter that the Live Data can't show.
        assertEquals(link(search), this.parameters.getIdentifier());
        assertEquals("client=openproject&instance=main&translationPrefix=openproject.&identifier="
            + encode(link(search)), this.parameters.getSourceParameters());
    }

    @Test
    void keepsTheFiltersOfTheSavedQueryOnThePropertiesFilteredByTheMacro() throws Exception
    {
        this.parameters.setIdentifier(SAVED_QUERY_LINK);
        String filters = "{\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":"
            + "[{\"operator\":\"c\",\"value\":\"\"}]}]}}";
        this.parameters.setFilters(filters);
        useColumns(OpenProjectWorkItemFields.empty(), Map.of("type", LIST, "status", Set.of("=", "!", "o", "c")));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");
        when(this.identifierResolver.getSavedQueryLink(any(), eq("results"))).thenReturn(link("{\"f\":["
            + "{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"type\",\"o\":\"=\",\"v\":[\"1\"]}]}"));

        this.macro.execute(this.parameters, "", this.context);
        assertEquals(List.of(), this.macro.asyncProcessParameters(this.parameters, this.context));

        // The Live Data shows a single filter by property: the filter of the saved query on the status, which the macro
        // already filters, stays in the link instead of being shown twice.
        assertEquals("{\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":[{\"operator\":\"c\","
            + "\"value\":\"\"}]},{\"property\":\"type\",\"constraints\":[{\"operator\":\"=\",\"value\":\"1\"}]}]}}",
            this.parameters.getFilters());
        assertEquals(link("{\"f\":[{\"n\":\"status\",\"o\":\"o\",\"v\":[]}]}"), this.parameters.getIdentifier());
    }

    @Test
    void keepsTheSavedQueryWhenTheContentIsALiveDataConfiguration() throws Exception
    {
        // A page written before the source parameter, whose content configures the Live Data.
        String configuration = "{\"query\":{\"sort\":[{\"property\":\"summary\"}]}}";
        this.parameters.setIdentifier(SAVED_QUERY_LINK);
        when(this.openProjectAccess.resolve("main")).thenReturn(OpenProjectAccessResult.authenticated(this.apiClient));
        when(this.identifierResolver.getSavedQueryResultsUrl(this.apiClient, "5")).thenReturn("results");
        when(this.identifierResolver.getSavedQueryLink(any(), eq("results")))
            .thenReturn(link("{\"f\":[{\"n\":\"status\",\"o\":\"o\",\"v\":[]}]}"));
        // The asynchronous renderer processes the parameters before executing the displayer.
        when(this.asyncExecutor.execute(same(this.displayerMacro), same(this.parameters), eq(configuration),
            same(this.context), any(), eq(false))).thenAnswer(invocation -> invocation
                .<BiFunction<ProjectManagementAsyncMacroParams, MacroTransformationContext, List<Block>>>getArgument(4)
                .apply(this.parameters, this.context));

        this.macro.execute(this.parameters, configuration, this.context);

        // The configuration replaces the filters and the sorting of the macro: the saved query keeps applying its own.
        assertNull(this.parameters.getFilters());
        assertEquals(SAVED_QUERY_LINK, this.parameters.getIdentifier());
        assertTrue(this.parameters.getSourceParameters().endsWith("&savedQueryResultsUrl=results"));
    }

    @Test
    void reportsALinkOfAnotherServerThanTheOneOfTheConnection() throws Exception
    {
        // The link may also be given by the deprecated identifier parameter.
        this.parameters.setIdentifier(OTHER_SERVER_LINK);
        doThrow(new MacroExecutionException("other server")).when(this.linkChecker).check("main", OTHER_SERVER_LINK);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        assertEquals("other server", exception.getMessage());
        verifyNoInteractions(this.asyncExecutor, this.userTokenChecker);
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

    @Test
    void showsTheFiltersAndTheSortingOfTheIdentifierLinkInTheLiveData() throws Exception
    {
        // A page written before the source parameter, whose link selects the open tasks, the most recent first.
        this.parameters.setIdentifier(link("{\"f\":[{\"n\":\"type\",\"o\":\"=\",\"v\":[\"1\"]},"
            + "{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"priority\",\"o\":\"=\",\"v\":[\"9\"]}],"
            + "\"t\":\"id:desc\"}"));
        this.parameters.setFilters("{\"query\":{\"filters\":[{\"property\":\"priority\",\"constraints\":"
            + "[{\"operator\":\"=\",\"value\":\"8\"}]}]}}");
        useColumns(OpenProjectWorkItemFields.empty(), Map.of("type", LIST, "status", Set.of("=", "!", "o", "c"),
            "priority", LIST, "identifier", LIST));

        this.macro.execute(this.parameters, "", this.context);

        // The Live Data shows the filters and the sorting of the link, which the client no longer applies itself,
        // except the filter on the priority, which the macro already filters: the Live Data shows one filter by
        // property, so the link keeps it.
        assertEquals("{\"query\":{\"filters\":[{\"property\":\"priority\",\"constraints\":[{\"operator\":\"=\","
            + "\"value\":\"8\"}]},{\"property\":\"type\",\"constraints\":[{\"operator\":\"=\",\"value\":\"1\"}]},"
            + "{\"property\":\"status\",\"constraints\":[{\"operator\":\"o\",\"value\":\"\"}]}]}}",
            this.parameters.getFilters());
        assertEquals("identifier:desc", this.parameters.getSort());
        String identifier = link("{\"f\":[{\"n\":\"priority\",\"o\":\"=\",\"v\":[\"9\"]}]}");
        assertEquals(identifier, this.parameters.getIdentifier());
        assertEquals("client=openproject&instance=main&identifier=" + encode(identifier)
            + "&translationPrefix=openproject.", this.parameters.getSourceParameters());
    }

    @Test
    void showsTheFiltersOfTheLinkOnTheFieldsOfTheInstance() throws Exception
    {
        useSource("openproject/url", new OpenProjectUrlWorkItemSource());
        this.parameters.setSource("url");
        WorkPackageField approved = new WorkPackageField();
        approved.setKey("customField3");
        approved.setName("Approved");
        approved.setFilterKey("customField3");
        approved.setFilterValueType("Boolean");
        approved.setSortable(true);
        useColumns(new OpenProjectWorkItemFields(List.of(approved)), Map.of("Approved", Set.of("=")));

        this.macro.execute(this.parameters, link("{\"f\":[{\"n\":\"customField3\",\"o\":\"=\",\"v\":[\"t\"]}],"
            + "\"t\":\"customField3:asc\"}"), this.context);

        // The custom field is named after its property, and its boolean value written as the Live Data writes it.
        assertEquals("{\"query\":{\"filters\":[{\"property\":\"Approved\",\"constraints\":[{\"operator\":\"=\","
            + "\"value\":\"true\"}]}]}}", this.parameters.getFilters());
        assertEquals("Approved:asc", this.parameters.getSort());
        assertEquals(PROJECT_LIST, this.parameters.getIdentifier());
    }

    @Test
    void sortsTheWorkPackagesOfTheLinkInTheContentAfterTheSortParameter() throws Exception
    {
        useSource("openproject/url", new OpenProjectUrlWorkItemSource());
        this.parameters.setSource("url");
        this.parameters.setSort("status:asc");
        // Only the sorting can be shown: the search of the link has no column filter.
        String search = "{\"f\":[{\"n\":\"search\",\"o\":\"**\",\"v\":[\"wiki\"]}]}";
        useColumns(OpenProjectWorkItemFields.empty(), Map.of("status", LIST));

        this.macro.execute(this.parameters, link("{\"t\":\"status:desc,updatedAt:desc\","
            + search.substring(1)), this.context);

        assertNull(this.parameters.getFilters());
        // The sort parameter wins for the properties sorted by both.
        assertEquals("status:asc,updateDate:desc", this.parameters.getSort());
        assertEquals(link(search), this.parameters.getIdentifier());
    }

    @Test
    void keepsTheLinkWhenItsFiltersCantBeShown() throws Exception
    {
        String link = link("{\"f\":[{\"n\":\"status\",\"o\":\"o\",\"v\":[]}]}");
        this.parameters.setIdentifier(link);
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.apiClient);
        when(this.openProjectAccess.resolve("main")).thenReturn(access);
        when(this.workPackageFieldsCache.getFields("main", access)).thenThrow(new IllegalArgumentException("cache"));

        this.macro.execute(this.parameters, "", this.context);

        // The client applies the filters of the link, as before.
        assertEquals(link, this.parameters.getIdentifier());
        assertNull(this.parameters.getFilters());
        assertNull(this.parameters.getSort());
        String warning = "Failed to show the filters of the link [" + link + "] in the Live Data. Root cause: "
            + "[IllegalArgumentException: cache].";
        // The content is read twice: to check the current user filter, then to display the work packages.
        assertEquals(warning, this.logCapture.getMessage(0));
        assertEquals(warning, this.logCapture.getMessage(1));
    }

    @Test
    void keepsTheLinkWhenItsQueryPropertiesAreInvalid() throws Exception
    {
        String link = PROJECT_LIST + "?query_props=%7Bnot+json";
        this.parameters.setIdentifier(link);

        this.macro.execute(this.parameters, "", this.context);

        // The client reports the invalid link when it requests the work packages.
        assertEquals(link, this.parameters.getIdentifier());
        assertNull(this.parameters.getFilters());
        assertNull(this.parameters.getSort());
    }

    private void useColumns(OpenProjectWorkItemFields fields, Map<String, Set<String>> filterOperators)
        throws Exception
    {
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.apiClient);
        when(this.openProjectAccess.resolve("main")).thenReturn(access);
        when(this.workPackageFieldsCache.getFields("main", access)).thenReturn(fields);
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setMeta(new LiveDataMeta());
        List<LiveDataPropertyDescriptor> descriptors = new ArrayList<>();
        filterOperators.forEach((property, operators) -> {
            LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();
            descriptor.setId(property);
            descriptor.setFilterable(true);
            descriptor.setFilter(new LiveDataPropertyDescriptor.FilterDescriptor("list"));
            operators.forEach(operator -> descriptor.getFilter().addOperator(operator, operator));
            descriptors.add(descriptor);
        });
        configuration.getMeta().setPropertyDescriptors(descriptors);
        when(this.liveDataConfigurationResolver.resolve(any())).thenAnswer(invocation -> {
            LiveDataConfiguration input = invocation.getArgument(0);
            // The columns are those of the instance of the macro.
            return "main".equals(input.getQuery().getSource().getParameters().get("instance")) ? configuration : null;
        });
    }

    private static String link(String queryProperties)
    {
        return PROJECT_LIST + "?query_props=" + encode(queryProperties);
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void useSource(String hint, WorkItemSource source) throws Exception
    {
        when(this.contextComponentManager.hasComponent(WorkItemSource.class, hint)).thenReturn(true);
        when(this.contextComponentManager.getInstance(WorkItemSource.class, hint)).thenReturn(source);
    }
}
