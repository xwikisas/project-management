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
package com.xwiki.projectmanagement.openproject.internal.livedata;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.environment.Environment;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConnection;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.internal.processing.WorkPackageFieldsCache;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectLivedataConfigurationResolver}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectLivedataConfigurationResolverTest
{
    private static final String INSTANCE = "myInstance";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private OpenProjectLivedataConfigurationResolver resolver;

    @MockComponent
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @MockComponent
    private Environment environment;

    @MockComponent
    private WorkPackageFieldsCache workPackageFieldsCache;

    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    private final OpenProjectApiClient client = mock(OpenProjectApiClient.class);

    @BeforeEach
    void setUp() throws Exception
    {
        XWikiContext context = mock(XWikiContext.class);
        when(this.contextProvider.get()).thenReturn(context);
        when(context.getWikiId()).thenReturn("wiki");
        when(this.stringLiveDataConfigResolver.resolve(anyString())).thenAnswer(invocation -> {
            LiveDataConfiguration configuration = new LiveDataConfiguration();
            configuration.setMeta(new LiveDataMeta());
            configuration.getMeta().setPropertyDescriptors(new ArrayList<>());
            return configuration;
        });
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.client);
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(access);
        when(this.workPackageFieldsCache.getFields(INSTANCE, access))
            .thenAnswer(invocation -> OpenProjectWorkItemFields.of(this.client));
    }

    @Test
    void resolveAddsTheWorkPackageFields() throws Exception
    {
        mockTargetVersionsField();

        LiveDataConfiguration configuration = this.resolver.resolve(getInput(INSTANCE));

        List<LiveDataPropertyDescriptor> descriptors =
            new ArrayList<>(configuration.getMeta().getPropertyDescriptors());
        assertEquals(List.of("targetVersion"),
            descriptors.stream().map(LiveDataPropertyDescriptor::getId).collect(Collectors.toList()));
        assertEquals("/rest/wikis/wiki/openproject/instance/myInstance/suggest/versions?search={encodedQuery}",
            descriptors.get(0).getFilter().getParameters().get("searchURL"));
        assertEquals(true, descriptors.get(0).getFilter().getParameters().get("multiValue"));
        verify(this.openProjectConfiguration, never()).getDefaultConnection();
    }

    @Test
    void resolveMovesTheIgnoredSortEntriesLast() throws Exception
    {
        mockTargetVersionsField();
        LiveDataConfiguration input = getInput(INSTANCE);
        input.setId("myLiveData");
        input.getQuery().setSort(List.of(new LiveDataQuery.SortEntry("foo", false),
            new LiveDataQuery.SortEntry("updateDate", true), new LiveDataQuery.SortEntry("targetVersion", false),
            new LiveDataQuery.SortEntry("summary", false)));

        this.resolver.resolve(input);

        // Live Data shows the direction of the first sort entry in the header of its column: the sort entries that
        // OpenProject can't apply (an unknown property, a field that can't be sorted) come after the applied ones.
        assertEquals(List.of("updateDate", "summary", "foo", "targetVersion"), input.getQuery().getSort().stream()
            .map(LiveDataQuery.SortEntry::getProperty).collect(Collectors.toList()));
    }

    @Test
    void resolveWithoutInstanceUsesTheDefaultConnectionWhenAsked() throws Exception
    {
        // The filters of a preset are edited without a connection: they get the suggestions of the default one.
        mockTargetVersionsField();
        when(this.openProjectConfiguration.getDefaultConnection())
            .thenReturn(new OpenProjectConnection(INSTANCE, "http://openproject", "id", "secret", "instanceId"));

        LiveDataConfiguration input = getInput(null);
        input.getQuery().getSource().setParameter("defaultInstance", true);
        LiveDataConfiguration configuration = this.resolver.resolve(input);

        List<LiveDataPropertyDescriptor> descriptors =
            new ArrayList<>(configuration.getMeta().getPropertyDescriptors());
        assertEquals("/rest/wikis/wiki/openproject/instance/myInstance/suggest/versions?search={encodedQuery}",
            descriptors.get(0).getFilter().getParameters().get("searchURL"));
        // The preset editor can tell which connection suggests the values.
        assertEquals(INSTANCE, configuration.getQuery().getSource().getParameters().get("instance"));
    }

    @Test
    void resolveWithoutInstanceNorDefaultConnectionWhenTheDefaultOneIsAsked() throws Exception
    {
        // The filters of a preset can't get suggestions when there is no default connection: they don't request them.
        mockSuggestedProperties();

        LiveDataConfiguration input = getInput(null);
        input.getQuery().getSource().setParameter("defaultInstance", true);
        LiveDataConfiguration configuration = this.resolver.resolve(input);

        List<LiveDataPropertyDescriptor> descriptors =
            new ArrayList<>(configuration.getMeta().getPropertyDescriptors());
        assertNull(descriptors.get(0).getFilter().getParameters().get("searchURL"));
        // The suggestions that don't depend on the connection are kept.
        assertEquals("/rest/wikis/wiki/other?search={encodedQuery}",
            descriptors.get(1).getFilter().getParameters().get("searchURL"));
        assertNull(configuration.getQuery());
    }

    @Test
    void resolveWithoutInstanceKeepsTheSuggestionURLsToComplete() throws Exception
    {
        // The macro editor completes the URLs with the connection chosen in it.
        mockSuggestedProperties();

        LiveDataConfiguration configuration = this.resolver.resolve(getInput(null));

        assertEquals("/rest/wikis/wiki/openproject/instance/{instance}/suggest/statuses?search={encodedQuery}",
            configuration.getMeta().getPropertyDescriptors().iterator().next().getFilter().getParameters()
                .get("searchURL"));
    }

    private void mockSuggestedProperties() throws Exception
    {
        when(this.stringLiveDataConfigResolver.resolve(anyString())).thenAnswer(invocation -> {
            LiveDataConfiguration configuration = new LiveDataConfiguration();
            configuration.setMeta(new LiveDataMeta());
            configuration.getMeta().setPropertyDescriptors(new ArrayList<>(List.of(
                getListProperty("status", "/rest/wikis/{wikiName}/openproject/instance/{instance}/suggest/statuses"
                    + "?search={encodedQuery}"),
                getListProperty("other", "/rest/wikis/{wikiName}/other?search={encodedQuery}"))));
            return configuration;
        });
    }

    private static LiveDataPropertyDescriptor getListProperty(String id, String searchURL)
    {
        LiveDataPropertyDescriptor property = new LiveDataPropertyDescriptor();
        property.setId(id);
        LiveDataPropertyDescriptor.FilterDescriptor filter = new LiveDataPropertyDescriptor.FilterDescriptor("list");
        filter.setParameter("searchURL", searchURL);
        property.setFilter(filter);
        return property;
    }

    private void mockTargetVersionsField() throws Exception
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey("targetVersions");
        field.setName("Target versions");
        field.setFilterKey("targetVersion");
        field.setFilterValueType("[]Version");
        field.setFilterOperators(List.of("="));
        when(this.client.getWorkPackageFields()).thenReturn(new PaginatedResult<>(List.of(field), 0, 1, 1));
        when(this.workPackageFieldsCache.getMultiValueFields(eq(INSTANCE), eq(this.client), any()))
            .thenReturn(Set.of("targetVersions"));
    }

    @Test
    void resolveEncodesTheConnectionNameInTheSuggestionURLs() throws Exception
    {
        String connection = "Local <b>\"OP\"</b> #2 & co/x";
        OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(this.client);
        when(this.openProjectAccess.resolve(connection)).thenReturn(access);
        when(this.workPackageFieldsCache.getFields(connection, access))
            .thenAnswer(invocation -> OpenProjectWorkItemFields.of(this.client));
        WorkPackageField field = new WorkPackageField();
        field.setKey("targetVersions");
        field.setName("Target versions");
        field.setFilterKey("targetVersion");
        field.setFilterValueType("[]Version");
        field.setFilterOperators(List.of("="));
        when(this.client.getWorkPackageFields()).thenReturn(new PaginatedResult<>(List.of(field), 0, 1, 1));

        LiveDataConfiguration configuration = this.resolver.resolve(getInput(connection));

        assertEquals("/rest/wikis/wiki/openproject/instance/Local%20%3Cb%3E%22OP%22%3C%2Fb%3E%20%232%20%26%20co%2Fx"
                + "/suggest/versions?search={encodedQuery}",
            configuration.getMeta().getPropertyDescriptors().iterator().next().getFilter().getParameters()
                .get("searchURL"));
    }

    @Test
    void resolveWhenTheMultiValueFieldsCantBeFound() throws Exception
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey("targetVersions");
        field.setName("Target versions");
        field.setFilterKey("targetVersion");
        field.setFilterValueType("[]Version");
        field.setFilterOperators(List.of("=", "&="));
        when(this.client.getWorkPackageFields()).thenReturn(new PaginatedResult<>(List.of(field), 0, 1, 1));
        when(this.workPackageFieldsCache.getMultiValueFields(eq(INSTANCE), eq(this.client), any()))
            .thenThrow(new ProjectManagementException("Forbidden"));

        LiveDataConfiguration configuration = this.resolver.resolve(getInput(INSTANCE));

        // The field can still be filtered on, as a single-value one.
        LiveDataPropertyDescriptor descriptor = configuration.getMeta().getPropertyDescriptors().iterator().next();
        assertEquals(List.of("="), descriptor.getFilter().getOperators().stream()
            .map(LiveDataPropertyDescriptor.OperatorDescriptor::getId).collect(Collectors.toList()));
        assertNull(descriptor.getFilter().getParameters().get("multiValue"));
        assertEquals("Failed to find out which work package fields of the OpenProject instance [myInstance] are "
            + "multi-value. Root cause: [ProjectManagementException: Forbidden]", this.logCapture.getMessage(0));
    }

    @Test
    void resolveWhenTheFieldsCantBeRetrieved() throws Exception
    {
        when(this.client.getWorkPackageFields()).thenThrow(new ProjectManagementException("Forbidden"));
        LiveDataConfiguration input = getInput(INSTANCE);
        List<LiveDataQuery.SortEntry> sort =
            List.of(new LiveDataQuery.SortEntry("foo", false), new LiveDataQuery.SortEntry("updateDate", true));
        input.getQuery().setSort(sort);

        LiveDataConfiguration configuration = this.resolver.resolve(input);

        assertEquals(0, configuration.getMeta().getPropertyDescriptors().size());
        // The unknown property may be a field of the instance: the sort is left as it is.
        assertEquals(sort, input.getQuery().getSort());
        assertEquals("Failed to retrieve the work package fields of the OpenProject instance [myInstance]. "
            + "Root cause: [ProjectManagementException: Forbidden]", this.logCapture.getMessage(0));
    }

    @Test
    void resolveWithoutInstance() throws Exception
    {
        LiveDataConfiguration configuration = this.resolver.resolve(getInput(null));

        assertEquals(0, configuration.getMeta().getPropertyDescriptors().size());
        verify(this.openProjectAccess, never()).resolve(anyString());
        // E.g. the macro editor, which reloads the properties for the connection chosen in it.
        verify(this.openProjectConfiguration, never()).getDefaultConnection();
    }

    @Test
    void configurationDeclaresEachPropertyOnce() throws Exception
    {
        JsonNode descriptors = readPropertyDescriptors();

        // A property declared twice is proposed twice by the property pickers of the macros.
        List<String> ids = new ArrayList<>();
        descriptors.forEach(descriptor -> ids.add(descriptor.path("id").asText()));
        assertFalse(ids.isEmpty());
        assertEquals(new LinkedHashSet<>(ids).size(), ids.size(), ids.toString());
    }

    @Test
    void configurationFiltersTheDatesOnOrBeforeAndOnOrAfterADay() throws Exception
    {
        JsonNode descriptors = readPropertyDescriptors();

        // The filter builder of the macros shows and writes the dates compared with <= and >= in the query (for
        // example dueDate <= 2026-12-31) with these operators, which OpenProject applies as ranges of dates.
        List<String> dateProperties = new ArrayList<>();
        descriptors.forEach(descriptor -> {
            if ("date".equals(descriptor.path("filter").path("id").asText())) {
                List<String> operators = new ArrayList<>();
                descriptor.path("filter").path("operators").forEach(operator -> operators.add(
                    operator.path("id").asText()));
                assertTrue(operators.containsAll(List.of("before", "after")), descriptor.toString());
                dateProperties.add(descriptor.path("id").asText());
            }
        });
        assertEquals(List.of("startDate", "dueDate", "creationDate", "updateDate"), dateProperties);
    }

    private JsonNode readPropertyDescriptors() throws Exception
    {
        try (InputStream configuration = getClass().getResourceAsStream("/openProjectLiveDataConfiguration.json")) {
            return new ObjectMapper().readTree(configuration).path("meta").path("propertyDescriptors");
        }
    }

    private static LiveDataConfiguration getInput(String instance)
    {
        LiveDataConfiguration input = new LiveDataConfiguration();
        LiveDataQuery query = new LiveDataQuery();
        LiveDataQuery.Source source = new LiveDataQuery.Source();
        if (instance != null) {
            source.setParameter("instance", instance);
        }
        query.setSource(source);
        input.setQuery(query);
        return input;
    }
}
