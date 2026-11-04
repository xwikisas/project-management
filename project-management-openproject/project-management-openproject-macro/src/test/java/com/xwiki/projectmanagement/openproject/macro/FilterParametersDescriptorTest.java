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
package com.xwiki.projectmanagement.openproject.macro;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.properties.BeanDescriptor;
import org.xwiki.properties.BeanManager;
import org.xwiki.properties.PropertyDescriptor;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Validate how the macro editor groups the parameters that select the work packages of the OpenProject macros.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({
    DefaultBeanManager.class,
    DefaultConverterManager.class,
    EnumConverter.class,
    ConvertUtilsConverter.class
})
class FilterParametersDescriptorTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @BeforeEach
    void setUp() throws Exception
    {
        // Used by the converter manager to look up the converters.
        this.componentManager.registerComponent(ComponentManager.class, "context", this.componentManager);
    }

    static Stream<Arguments> filterParameters()
    {
        return Stream.of(
            Arguments.of(OpenProjectMacroParameters.class, List.of("identifier", "filters", "presetId")),
            Arguments.of(OpenProjectChartMacroParameters.class, List.of("filters", "presetId")),
            Arguments.of(OpenProjectCalendarMacroParameters.class, List.of("filters", "presetId")));
    }

    @ParameterizedTest
    @MethodSource("filterParameters")
    void filterParametersAreNotExclusive(Class<?> parametersClass, List<String> parameters) throws Exception
    {
        BeanDescriptor descriptor =
            this.componentManager.<BeanManager>getInstance(BeanManager.class).getBeanDescriptor(parametersClass);

        for (String parameter : parameters) {
            PropertyDescriptor property = descriptor.getProperty(parameter);
            // The macros combine the work packages identifier, the filters and the preset, so the macro editor must
            // not offer them as exclusive choices (a feature), only display them together in the Filters tab.
            assertNull(property.getGroupDescriptor().getFeature(), parameter);
            assertEquals(List.of("filters"), property.getGroupDescriptor().getGroup(), parameter);
        }
    }
}
