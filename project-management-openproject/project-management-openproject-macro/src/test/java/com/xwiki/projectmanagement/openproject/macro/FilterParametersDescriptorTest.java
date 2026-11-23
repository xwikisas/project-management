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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.properties.BeanDescriptor;
import org.xwiki.properties.BeanManager;
import org.xwiki.properties.PropertyDescriptor;
import org.xwiki.properties.annotation.PropertyOrder;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.openproject.OpenProjectFilters;
import com.xwiki.projectmanagement.openproject.OpenProjectWorkItemSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validate how the macro editor groups the parameters of the OpenProject work package macros.
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
            Arguments.of(OpenProjectMacroParameters.class, List.of("source", "identifier", "filters", "presetId")),
            Arguments.of(OpenProjectChartMacroParameters.class, List.of("source", "filters", "presetId")),
            Arguments.of(OpenProjectCalendarMacroParameters.class, List.of("source", "filters", "presetId")));
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

    static Stream<Arguments> advancedDisplayParameters()
    {
        return Stream.of(
            Arguments.of(OpenProjectMacroParameters.class, "pageSizes", 90),
            Arguments.of(OpenProjectMacroParameters.class, "id", 91),
            Arguments.of(OpenProjectAssignedToMeMacroParameters.class, "pageSizes", 90),
            Arguments.of(OpenProjectAssignedToMeMacroParameters.class, "id", 91));
    }

    @ParameterizedTest
    @MethodSource("advancedDisplayParameters")
    void advancedDisplayParametersAreLastInDisplayGroup(Class<?> parametersClass, String parameter, int order)
        throws Exception
    {
        PropertyDescriptor property = this.componentManager.<BeanManager>getInstance(BeanManager.class)
            .getBeanDescriptor(parametersClass).getProperty(parameter);

        // The page sizes and the table id are displayed at the end of the Display tab, under "More options".
        assertEquals(List.of("display"), property.getGroupDescriptor().getGroup());
        assertTrue(property.isAdvanced());
        assertEquals(order, property.getWriteMethod().getAnnotation(PropertyOrder.class).value());
        assertFalse(property.isDisplayHidden());
    }

    @ParameterizedTest
    @ValueSource(classes = {
        OpenProjectMacroParameters.class,
        OpenProjectChartMacroParameters.class,
        OpenProjectCalendarMacroParameters.class
    })
    void connectionGroupHoldsTheInstance(Class<?> parametersClass) throws Exception
    {
        PropertyDescriptor property = this.componentManager.<BeanManager>getInstance(BeanManager.class)
            .getBeanDescriptor(parametersClass).getProperty("instance");

        // The macro editor script looks for the Work Packages, Display and Connection tabs by these group ids.
        assertEquals(List.of("connection"), property.getGroupDescriptor().getGroup());
    }

    @ParameterizedTest
    @ValueSource(classes = {
        OpenProjectMacroParameters.class,
        OpenProjectCalendarMacroParameters.class,
        OpenProjectChartMacroParameters.class
    })
    void sourceIsTheWorkPackageSelectionWidget(Class<?> parametersClass) throws Exception
    {
        BeanDescriptor descriptor =
            this.componentManager.<BeanManager>getInstance(BeanManager.class).getBeanDescriptor(parametersClass);

        // The source parameter is displayed as the widget choosing how the macro selects its work packages, first in
        // the Work Packages tab. It's not hidden: the macro editor only writes the parameters it displays.
        PropertyDescriptor source = descriptor.getProperty("source");
        assertEquals(OpenProjectWorkItemSource.class, source.getDisplayType());
        assertFalse(source.isDisplayHidden());
        assertEquals(1, getOrder(source));
        // The widget displays the preset under the filters.
        assertEquals(15, getOrder(descriptor.getProperty("presetId")));
    }

    @ParameterizedTest
    @ValueSource(classes = {
        OpenProjectMacroParameters.class,
        OpenProjectCalendarMacroParameters.class
    })
    void filtersAreEditedByTheWorkPackageSelectionWidget(Class<?> parametersClass) throws Exception
    {
        PropertyDescriptor filters = this.componentManager.<BeanManager>getInstance(BeanManager.class)
            .getBeanDescriptor(parametersClass).getProperty("filters");

        // The widget edits the filters, written as a query in the content: the macro editor only holds the value of
        // the deprecated filters parameter.
        assertTrue(filters.isDisplayHidden());
    }

    @Test
    void chartSeriesAreDisplayedAfterTheWorkPackageSelectionWidget() throws Exception
    {
        PropertyDescriptor series = this.componentManager.<BeanManager>getInstance(BeanManager.class)
            .getBeanDescriptor(OpenProjectChartMacroParameters.class).getProperty("filters");

        // The filters of a chart are its series, displayed for every way of selecting the charted work packages.
        assertEquals(OpenProjectFilters.class, series.getDisplayType());
        assertFalse(series.isDisplayHidden());
        assertEquals(50, getOrder(series));
    }

    private static int getOrder(PropertyDescriptor property)
    {
        return property.getWriteMethod().getAnnotation(PropertyOrder.class).value();
    }
}
