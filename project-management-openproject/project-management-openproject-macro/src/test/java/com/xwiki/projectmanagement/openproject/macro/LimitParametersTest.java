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
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.properties.BeanDescriptor;
import org.xwiki.properties.BeanManager;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

/**
 * Validate the {@code limit} parameter of the macros listing OpenProject entities, and its deprecated {@code count}
 * alias.
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
class LimitParametersTest
{
    private static final String LIMIT = "limit";

    private static final String COUNT = "count";

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

    static Stream<Arguments> macros()
    {
        return Stream.of(
            macro("news", OpenProjectNewsMacroParameters::new, OpenProjectNewsMacroParameters::getLimit, 5),
            macro("projects", OpenProjectProjectsMacroParameters::new, OpenProjectProjectsMacroParameters::getLimit,
                5),
            macro("subprojects", OpenProjectSubprojectsMacroParameters::new,
                OpenProjectSubprojectsMacroParameters::getLimit, 25),
            macro("project members", OpenProjectProjectMembersMacroParameters::new,
                OpenProjectProjectMembersMacroParameters::getLimit, 25),
            macro("spent time", OpenProjectSpentTimeMacroParameters::new,
                OpenProjectSpentTimeMacroParameters::getLimit, 25));
    }

    private static <T> Arguments macro(String name, Supplier<T> parameters, ToIntFunction<T> limit,
        int defaultLimit)
    {
        return Arguments.of(named(name, parameters), limit, defaultLimit);
    }

    @ParameterizedTest
    @MethodSource("macros")
    <T> void limit(Supplier<T> parameters, ToIntFunction<T> limit, int defaultLimit) throws Exception
    {
        assertEquals(defaultLimit, limit.applyAsInt(populate(parameters, Map.of())));
        assertEquals(3, limit.applyAsInt(populate(parameters, Map.of(LIMIT, "3"))));
        // The deprecated count parameter still works, and the limit parameter takes precedence over it.
        assertEquals(7, limit.applyAsInt(populate(parameters, Map.of(COUNT, "7"))));
        assertEquals(3, limit.applyAsInt(populate(parameters, Map.of(LIMIT, "3", COUNT, "7"))));

        // The macro editor displays the limit parameter, with its default value, but not the deprecated one.
        BeanDescriptor descriptor = this.beanManager.getBeanDescriptor(parameters.get().getClass());
        assertFalse(descriptor.getProperty(LIMIT).isDisplayHidden());
        assertEquals(defaultLimit, descriptor.getProperty(LIMIT).getDefaultValue());
        assertTrue(descriptor.getProperty(COUNT).isDisplayHidden());
        assertTrue(descriptor.getProperty(COUNT).isDeprecated());
    }

    @Test
    void chartOffsetIsNotDisplayed()
    {
        // Skipping work packages makes little sense for a chart: the parameter only stays for the existing macros.
        assertTrue(this.beanManager.getBeanDescriptor(OpenProjectChartMacroParameters.class).getProperty("offset")
            .isDisplayHidden());
    }

    private <T> T populate(Supplier<T> parameters, Map<String, String> values) throws Exception
    {
        T bean = parameters.get();
        this.beanManager.populate(bean, values);
        return bean;
    }
}
