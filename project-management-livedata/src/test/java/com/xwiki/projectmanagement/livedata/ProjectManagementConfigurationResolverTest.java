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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.livedata.LiveDataActionDescriptor;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectManagementConfigurationResolver}.
 *
 * @version $Id$
 */
@ComponentTest
class ProjectManagementConfigurationResolverTest
{
    private static final String CLIENT = "test";

    private static final String TRANSLATION_PREFIX = "translationPrefix";

    private static final String STATUS = "status";

    private static final String DUE_DATE = "dueDate";

    private static final String CUSTOM = "custom";

    @InjectMockComponents
    private ProjectManagementConfigurationResolver configurationResolver;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @BeforeEach
    void setUp() throws Exception
    {
        // The generic configuration, read from the JSON file of the module.
        when(this.stringLiveDataConfigResolver.resolve(anyString())).thenAnswer(invocation -> {
            LiveDataConfiguration defaultConfig = configuration(Map.of(TRANSLATION_PREFIX, "projectmanagement.",
                "a", "default", "b", "default", "c", "default"), STATUS, DUE_DATE);
            defaultConfig.getMeta().setActions(List.of(new LiveDataActionDescriptor("view")));
            return defaultConfig;
        });
        when(this.l10n.getTranslationPlain("projectmanagement.property.status")).thenReturn("Status");
        when(this.l10n.getTranslationPlain("projectmanagement.property.dueDate")).thenReturn("Due date");
        when(this.l10n.getTranslationPlain("projectmanagement.property.dueDate.hint")).thenReturn("The due date");
        when(this.l10n.getTranslationPlain("projectmanagement.action.view")).thenReturn("View");
        when(this.l10n.getTranslationPlain("projectmanagement.action.view.hint")).thenReturn("View the item");
    }

    @Test
    void resolveMergesTheInputOverTheClientOverTheGenericConfiguration() throws Exception
    {
        LiveDataConfiguration clientConfig =
            configuration(Map.of(TRANSLATION_PREFIX, "client.", "b", "client", "c", "client"), STATUS, CUSTOM);
        LiveDataConfiguration input = configuration(Map.of("client", CLIENT, "c", "input"));
        registerClientConfiguration(input, clientConfig);
        // The client translates the properties it knows, the generic translations are the fallback.
        when(this.l10n.getTranslationPlain("client.property.status")).thenReturn("State");
        when(this.l10n.getTranslationPlain("client.property.status.hint")).thenReturn("The state");

        LiveDataConfiguration result = this.configurationResolver.resolve(input);

        Map<String, Object> parameters = result.getQuery().getSource().getParameters();
        assertEquals("default", parameters.get("a"));
        assertEquals("client", parameters.get("b"));
        assertEquals("input", parameters.get("c"));
        assertEquals("client.", parameters.get(TRANSLATION_PREFIX));
        assertEquals(CLIENT, parameters.get("client"));
        assertEquals("projectmanagement", result.getQuery().getSource().getId());
        // The properties of the client come first. The properties without translation are named after their id.
        assertEquals(List.of("status: State (The state)", "custom: custom (null)", "dueDate: Due date (The due date)"),
            describe(result));
        assertEquals(List.of("view: View (View the item)"), result.getMeta().getActions().stream()
            .map(action -> String.format("%s: %s (%s)", action.getId(), action.getName(), action.getDescription()))
            .collect(Collectors.toList()));
    }

    @Test
    void resolveWithoutClient() throws Exception
    {
        LiveDataConfiguration input = configuration(Map.of("c", "input"));
        input.setId("myLiveData");

        LiveDataConfiguration result = this.configurationResolver.resolve(input);

        assertEquals("myLiveData", result.getId());
        Map<String, Object> parameters = result.getQuery().getSource().getParameters();
        assertEquals("default", parameters.get("b"));
        assertEquals("input", parameters.get("c"));
        assertNull(parameters.get("client"));
        assertEquals(List.of("status: Status (null)", "dueDate: Due date (The due date)"), describe(result));
    }

    @Test
    void resolveWithAClientWithoutConfiguration() throws Exception
    {
        // No configuration resolver nor configuration file for this client: the generic configuration is used.
        LiveDataConfiguration input = configuration(Map.of("client", "unknown", "c", "input"));

        LiveDataConfiguration result = this.configurationResolver.resolve(input);

        Map<String, Object> parameters = result.getQuery().getSource().getParameters();
        assertEquals("default", parameters.get("b"));
        assertEquals("input", parameters.get("c"));
        assertEquals(List.of("status: Status (null)", "dueDate: Due date (The due date)"), describe(result));
    }

    private void registerClientConfiguration(LiveDataConfiguration input, LiveDataConfiguration clientConfig)
        throws Exception
    {
        LiveDataConfigurationResolver<LiveDataConfiguration> clientResolver =
            configuration -> configuration == input ? clientConfig : null;
        this.componentManager.registerComponent(
            new DefaultParameterizedType(null, LiveDataConfigurationResolver.class, LiveDataConfiguration.class),
            CLIENT, clientResolver);
    }

    private static LiveDataConfiguration configuration(Map<String, Object> parameters, String... propertyIds)
    {
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setQuery(new LiveDataQuery());
        configuration.getQuery().setSource(new LiveDataQuery.Source("projectmanagement"));
        configuration.getQuery().getSource().getParameters().putAll(new LinkedHashMap<>(parameters));
        if (propertyIds.length > 0) {
            configuration.setMeta(new LiveDataMeta());
            List<LiveDataPropertyDescriptor> properties = new ArrayList<>();
            for (String propertyId : propertyIds) {
                LiveDataPropertyDescriptor property = new LiveDataPropertyDescriptor();
                property.setId(propertyId);
                properties.add(property);
            }
            configuration.getMeta().setPropertyDescriptors(properties);
        }
        return configuration;
    }

    private static List<String> describe(LiveDataConfiguration configuration)
    {
        return configuration.getMeta().getPropertyDescriptors().stream()
            .map(property -> String.format("%s: %s (%s)", property.getId(), property.getName(),
                property.getDescription()))
            .collect(Collectors.toList());
    }
}
