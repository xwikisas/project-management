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

import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataActionDescriptor;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.projectmanagement.livadata.ProjectManagementConfigurationResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the flow of the {@link ProjectManagementConfigurationResolver}.
 *
 * @version $Id$
 * @since 1.0-rc-4
 */
@ComponentTest
public class ProjectManagementConfigurationResolverTest
{
    @InjectMockComponents
    private ProjectManagementConfigurationResolver configurationResolver;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private ComponentManager componentManager;

    @MockComponent
    private LiveDataConfigurationResolver<String> stringLiveDataConfigResolver;

    @Mock
    private LiveDataConfigurationResolver<LiveDataConfiguration> dynamicCfgResolver;

    @Test
    void testResolve() throws LiveDataException, ComponentLookupException
    {
        LiveDataConfiguration clientConfig = new LiveDataConfiguration();
        clientConfig.setQuery(new LiveDataQuery());
        clientConfig.getQuery().setSource(new LiveDataQuery.Source());
        clientConfig.getQuery().getSource().setId("projectmanagement");
        clientConfig.getQuery().getSource().setParameter("translationPrefix", "testtranslation.");

        LiveDataConfiguration defaultConfig = new LiveDataConfiguration();
        defaultConfig.setQuery(new LiveDataQuery());
        defaultConfig.getQuery().setSource(new LiveDataQuery.Source());
        defaultConfig.getQuery().getSource().setId("projectmanagement");
        defaultConfig.getQuery().getSource().setParameter("translationPrefix", "projectmanagement.");
        defaultConfig.setMeta(new LiveDataMeta());
        LiveDataPropertyDescriptor propertyDescriptor = new LiveDataPropertyDescriptor();
        propertyDescriptor.setId("propId");
        defaultConfig.getMeta().setPropertyDescriptors(Collections.singletonList(propertyDescriptor));
        LiveDataActionDescriptor actionDescriptor = new LiveDataActionDescriptor("someaction");
        defaultConfig.getMeta().setActions(List.of(actionDescriptor));
        when(stringLiveDataConfigResolver.resolve(contains("projectmanagement."))).thenReturn(defaultConfig);

        LiveDataConfiguration input = new LiveDataConfiguration();
        input.setQuery(new LiveDataQuery());
        input.getQuery().setSource(new LiveDataQuery.Source());
        input.getQuery().getSource().setParameter("client", "test");

        when(componentManager.hasComponent(any(ParameterizedType.class), eq("test"))).thenReturn(true);

        when(componentManager.getInstance(any(ParameterizedType.class), eq("test"))).thenReturn(dynamicCfgResolver);
        when(dynamicCfgResolver.resolve(input)).thenReturn(clientConfig);

        LiveDataConfiguration result = configurationResolver.resolve(input);

        // Verify that the default config is handled.
        verify(stringLiveDataConfigResolver).resolve(contains("projectmanagement."));
        // Verify that the entries get translated.
        verify(l10n).getTranslationPlain("projectmanagement.property.propId");
        verify(l10n).getTranslationPlain("projectmanagement.property.propId.hint");

        verify(l10n).getTranslationPlain("projectmanagement.action.someaction");
        verify(l10n).getTranslationPlain("projectmanagement.action.someaction.hint");

        verify(componentManager).getInstance(any(ParameterizedType.class), eq("test"));
        verify(dynamicCfgResolver).resolve(input);

        assertEquals("testtranslation.", result.getQuery().getSource().getParameters().get("translationPrefix"));
        assertEquals("test", result.getQuery().getSource().getParameters().get("client"));
        assertEquals("projectmanagement", result.getQuery().getSource().getId());
    }
}
