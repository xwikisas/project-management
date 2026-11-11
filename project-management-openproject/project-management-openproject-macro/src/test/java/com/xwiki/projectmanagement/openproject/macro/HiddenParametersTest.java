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

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.properties.BeanManager;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Checks that the parameters that the OpenProject macros force or ignore are hidden: the macro editor never displays
 * them, even when a macro call sets them, and the macros don't take their value from the call.
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
class HiddenParametersTest
{
    private static final String CLIENT = "client";

    private static final String FILTERS = "filters";

    private static final String DESCRIPTION = "description";

    private static final String TYPE = "type";

    private static final String PROPERTY = "property";

    private static final String PRESET_ID = "presetId";

    private static final String OFFSET = "offset";

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    private BeanManager beanManager;

    @BeforeEach
    void setUp() throws Exception
    {
        // Used by the converter manager to look up the converters.
        this.componentManager.registerComponent(ComponentManager.class, "context", this.componentManager);
        this.beanManager = this.componentManager.getInstance(BeanManager.class);
    }

    static Stream<Arguments> hiddenParameters()
    {
        return Stream.of(
            arguments(OpenProjectChartMacroParameters.class, CLIENT),
            arguments(OpenProjectCalendarMacroParameters.class, CLIENT),
            arguments(OpenProjectAssignedToMeMacroParameters.class, FILTERS),
            arguments(OpenProjectAssignedToMeMacroParameters.class, "identifier"),
            arguments(OpenProjectMacroParameters.class, DESCRIPTION),
            arguments(OpenProjectAssignedToMeMacroParameters.class, DESCRIPTION),
            arguments(OpenProjectOpenClosedMacroParameters.class, FILTERS),
            arguments(OpenProjectOpenClosedMacroParameters.class, TYPE),
            arguments(OpenProjectOpenClosedMacroParameters.class, PROPERTY),
            arguments(OpenProjectOpenClosedMacroParameters.class, PRESET_ID),
            arguments(OpenProjectOpenClosedMacroParameters.class, OFFSET),
            arguments(OpenProjectOpenClosedMacroParameters.class, "datasetsLabels"));
    }

    @ParameterizedTest
    @MethodSource("hiddenParameters")
    void parameterIsHidden(Class<?> parametersClass, String parameter)
    {
        assertNull(this.beanManager.getBeanDescriptor(parametersClass).getProperty(parameter));
    }

    @Test
    void openClosedMacroKeepsItsChartWhateverTheCallSays() throws Exception
    {
        OpenProjectOpenClosedMacroParameters parameters = new OpenProjectOpenClosedMacroParameters();
        String filters = parameters.getFilters();

        this.beanManager.populate(parameters,
            Map.of(FILTERS, "[]", TYPE, "pie", PROPERTY, "status", PRESET_ID, "1", OFFSET, "5"));

        assertEquals(filters, parameters.getFilters());
        assertEquals("line", parameters.getType());
        assertEquals(WorkItem.KEY_CREATION_DATE, parameters.getProperty());
        assertNull(parameters.getPresetId());
        assertEquals(0L, parameters.getOffset());
    }

    @Test
    void idCanBeSet() throws Exception
    {
        // The id of the Live Data displaying the work packages as a table or cards.
        OpenProjectMacroParameters parameters = new OpenProjectMacroParameters();

        this.beanManager.populate(parameters, Map.of("id", "myTable"));

        assertEquals("myTable", parameters.getId());
    }
}
