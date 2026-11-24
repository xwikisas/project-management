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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.component.manager.ComponentManager;
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

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Validate the order of the parameters of the OpenProject macros.
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
class ParameterOrderTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @BeforeEach
    void setUp() throws Exception
    {
        // Used by the converter manager to look up the converters.
        this.componentManager.registerComponent(ComponentManager.class, "context", this.componentManager);
    }

    @ParameterizedTest
    @ValueSource(classes = {
        OpenProjectAssignedToMeMacroParameters.class,
        OpenProjectCalendarMacroParameters.class,
        OpenProjectChartMacroParameters.class,
        OpenProjectCreateWorkPackageMacroParameters.class,
        OpenProjectMacroParameters.class,
        OpenProjectNewsMacroParameters.class,
        OpenProjectOpenClosedMacroParameters.class,
        OpenProjectProjectDetailsMacroParameters.class,
        OpenProjectProjectMembersMacroParameters.class,
        OpenProjectProjectsMacroParameters.class,
        OpenProjectSpentTimeMacroParameters.class,
        OpenProjectSubprojectsMacroParameters.class
    })
    void parametersHaveDistinctOrders(Class<?> parametersClass) throws Exception
    {
        Map<Integer, String> parameterByOrder = new HashMap<>();
        for (PropertyDescriptor property : this.componentManager.<BeanManager>getInstance(BeanManager.class)
            .getBeanDescriptor(parametersClass).getProperties())
        {
            Integer order = getOrder(property);
            if (order != null) {
                // XWiki 17.5+ keeps only one of the parameters having the same order in the macro descriptor: the
                // other ones are neither displayed by the macro editor nor set when the macro is executed.
                String other = parameterByOrder.put(order, property.getId());
                assertNull(other,
                    String.format("[%s] and [%s] have the same order [%s]", other, property.getId(), order));
            }
        }
    }

    private static Integer getOrder(PropertyDescriptor property)
    {
        for (Method method : new Method[] { property.getWriteMethod(), property.getReadMethod() }) {
            if (method != null && method.isAnnotationPresent(PropertyOrder.class)) {
                return method.getAnnotation(PropertyOrder.class).value();
            }
        }
        return null;
    }
}
