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
package com.xwiki.projectmanagement.openproject.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccess;
import com.xwiki.projectmanagement.openproject.internal.access.OpenProjectAccessResult;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLink;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectLinkQuery;
import com.xwiki.projectmanagement.openproject.internal.processing.OpenProjectWorkItemFields;
import com.xwiki.projectmanagement.openproject.internal.processing.WorkPackageFieldsCache;
import com.xwiki.projectmanagement.openproject.model.WorkPackageField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectLiveDataColumnsResolver}: which filters of a link the columns it resolves let the
 * Live Data show.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectLiveDataColumnsResolverTest
{
    /**
     * A link filtering on the open statuses, on a priority and on a custom field.
     */
    private static final OpenProjectLink LINK = OpenProjectLink.parse("https://op.example.com/work_packages?query_props="
        + URLEncoder.encode("{\"f\":[{\"n\":\"status\",\"o\":\"o\",\"v\":[]},{\"n\":\"priority\",\"o\":\"=\","
            + "\"v\":[\"8\"]},{\"n\":\"customField3\",\"o\":\"=\",\"v\":[\"t\"]}]}", StandardCharsets.UTF_8));

    @InjectMockComponents
    private OpenProjectLiveDataColumnsResolver resolver;

    @MockComponent
    private OpenProjectAccess openProjectAccess;

    @MockComponent
    private WorkPackageFieldsCache workPackageFieldsCache;

    @MockComponent
    @Named("openproject")
    private LiveDataConfigurationResolver<LiveDataConfiguration> liveDataConfigurationResolver;

    @Mock
    private OpenProjectApiClient client;

    private final OpenProjectAccessResult access = OpenProjectAccessResult.authenticated(null);

    @BeforeEach
    void setUp() throws Exception
    {
        OpenProjectAccessResult authenticated = OpenProjectAccessResult.authenticated(this.client);
        when(this.openProjectAccess.resolve("main")).thenReturn(authenticated);
        WorkPackageField approved = new WorkPackageField();
        approved.setKey("customField3");
        approved.setName("Approved");
        approved.setFilterKey("customField3");
        approved.setFilterValueType("Boolean");
        when(this.workPackageFieldsCache.getFields("main", authenticated))
            .thenReturn(new OpenProjectWorkItemFields(List.of(approved)));
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setMeta(new LiveDataMeta());
        configuration.getMeta().setPropertyDescriptors(List.of(descriptor("status", true, "=", "o"),
            descriptor("priority", true, "="), descriptor("Approved", true, "="),
            // Not filterable, or without filter.
            descriptor("description", false, "~"), new LiveDataPropertyDescriptor()));
        when(this.liveDataConfigurationResolver.resolve(any())).thenReturn(configuration);
    }

    @Test
    void showsTheFiltersOfTheColumnsOfTheInstance()
    {
        assertEquals(Set.of("status", "priority", "Approved"), getShownProperties("main", Set.of()));
    }

    @Test
    void keepsInTheLinkTheFiltersOnTheExcludedProperties()
    {
        assertEquals(Set.of("status"), getShownProperties("main", Set.of("priority", "Approved")));
    }

    @Test
    void namesTheCustomFieldsByKeyWhenTheirFieldsCantBeRetrieved() throws Exception
    {
        when(this.workPackageFieldsCache.getFields("main", this.openProjectAccess.resolve("main")))
            .thenThrow(new ProjectManagementException("fields"));

        // The custom field filter, named by its key, has no column: it stays in the link.
        assertEquals(Set.of("status", "priority"), getShownProperties("main", Set.of()));
    }

    @Test
    void namesTheCustomFieldsByKeyWithoutConnection()
    {
        when(this.openProjectAccess.resolve("other")).thenReturn(OpenProjectAccessResult.authenticated(null));

        assertEquals(Set.of("status", "priority"), getShownProperties("other", Set.of()));
    }

    @Test
    void showsNoFilterWhenTheConfigurationCantBeResolved() throws Exception
    {
        when(this.liveDataConfigurationResolver.resolve(any())).thenThrow(new LiveDataException("configuration"));

        assertEquals(Set.of(), getShownProperties("main", Set.of()));
    }

    @Test
    void showsNoFilterWithoutColumns() throws Exception
    {
        when(this.liveDataConfigurationResolver.resolve(any())).thenReturn(new LiveDataConfiguration());
        when(this.openProjectAccess.resolve(null)).thenReturn(this.access);

        // Without instance, the default connection of the macro.
        assertEquals(Set.of(), getShownProperties(null, Set.of()));
    }

    private Set<String> getShownProperties(String instance, Set<String> excludedProperties)
    {
        OpenProjectLinkQuery.Columns columns = this.resolver.resolve(instance, excludedProperties);
        return OpenProjectLinkQuery.read(LINK, () -> columns).getFilters().stream()
            .map(filter -> filter.getProperty()).collect(Collectors.toSet());
    }

    private static LiveDataPropertyDescriptor descriptor(String id, boolean filterable, String... operators)
    {
        LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();
        descriptor.setId(id);
        descriptor.setFilterable(filterable);
        descriptor.setFilter(new LiveDataPropertyDescriptor.FilterDescriptor("list"));
        for (String operator : operators) {
            descriptor.getFilter().addOperator(operator, operator);
        }
        return descriptor;
    }
}
