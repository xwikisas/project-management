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

import java.util.ArrayList;
import java.util.List;
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

import com.xpn.xwiki.XWikiContext;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(this.openProjectAccess.resolve(INSTANCE)).thenReturn(OpenProjectAccessResult.authenticated(this.client));
    }

    @Test
    void resolveAddsTheWorkPackageFields() throws Exception
    {
        WorkPackageField field = new WorkPackageField();
        field.setKey("targetVersions");
        field.setName("Target versions");
        field.setFilterKey("targetVersion");
        field.setFilterValueType("[]Version");
        field.setFilterOperators(List.of("="));
        when(this.client.getWorkPackageFields()).thenReturn(new PaginatedResult<>(List.of(field), 0, 1, 1));

        LiveDataConfiguration configuration = this.resolver.resolve(getInput(INSTANCE));

        List<LiveDataPropertyDescriptor> descriptors =
            new ArrayList<>(configuration.getMeta().getPropertyDescriptors());
        assertEquals(List.of("targetVersion"),
            descriptors.stream().map(LiveDataPropertyDescriptor::getId).collect(Collectors.toList()));
        assertEquals("/rest/wikis/wiki/openproject/instance/myInstance/suggest/versions?search={encodedQuery}",
            descriptors.get(0).getFilter().getParameters().get("searchURL"));
    }

    @Test
    void resolveWhenTheFieldsCantBeRetrieved() throws Exception
    {
        when(this.client.getWorkPackageFields()).thenThrow(new ProjectManagementException("Forbidden"));

        LiveDataConfiguration configuration = this.resolver.resolve(getInput(INSTANCE));

        assertEquals(0, configuration.getMeta().getPropertyDescriptors().size());
        assertEquals("Failed to retrieve the work package fields of the OpenProject instance [myInstance]. "
            + "Root cause: [ProjectManagementException: Forbidden]", this.logCapture.getMessage(0));
    }

    @Test
    void resolveWithoutInstance() throws Exception
    {
        LiveDataConfiguration configuration = this.resolver.resolve(getInput(null));

        assertEquals(0, configuration.getMeta().getPropertyDescriptors().size());
        verify(this.openProjectAccess, never()).resolve(anyString());
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
