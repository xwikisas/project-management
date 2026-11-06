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
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.properties.BeanManager;
import org.xwiki.properties.PropertyMandatoryException;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.openproject.OpenProjectInstanceHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks that the OpenProject macros don't require the {@code instance} parameter, which defaults to the default
 * connection.
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
class InstanceParameterTest
{
    private static final String INSTANCE = "instance";

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

    static Stream<Class<? extends OpenProjectInstanceHolder>> parameters()
    {
        return Stream.of(OpenProjectMacroParameters.class, OpenProjectAssignedToMeMacroParameters.class,
            OpenProjectChartMacroParameters.class, OpenProjectOpenClosedMacroParameters.class,
            OpenProjectCalendarMacroParameters.class, OpenProjectNewsMacroParameters.class,
            OpenProjectProjectsMacroParameters.class, OpenProjectSubprojectsMacroParameters.class,
            OpenProjectProjectDetailsMacroParameters.class, OpenProjectProjectMembersMacroParameters.class,
            OpenProjectSpentTimeMacroParameters.class);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void instanceIsOptional(Class<? extends OpenProjectInstanceHolder> parametersClass) throws Exception
    {
        OpenProjectInstanceHolder parameters = parametersClass.getDeclaredConstructor().newInstance();

        this.beanManager.populate(parameters, Map.of());

        assertNull(parameters.getInstance());
        assertFalse(this.beanManager.getBeanDescriptor(parametersClass).getProperty(INSTANCE).isMandatory());
    }

    @Test
    void createWorkPackageMacroRequiresTheInstance()
    {
        // The connection is chosen in the editor of the macro, which creates the work packages through it.
        assertThrows(PropertyMandatoryException.class,
            () -> this.beanManager.populate(new OpenProjectCreateWorkPackageMacroParameters(), Map.of()));
    }
}
