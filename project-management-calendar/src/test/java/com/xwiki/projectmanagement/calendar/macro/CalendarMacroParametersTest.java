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
package com.xwiki.projectmanagement.calendar.macro;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.properties.BeanDescriptor;
import org.xwiki.properties.BeanManager;
import org.xwiki.properties.PropertyDescriptor;
import org.xwiki.properties.PropertyException;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xwiki.projectmanagement.ProjectManagementFilter;
import com.xwiki.projectmanagement.calendar.CalendarViews;
import com.xwiki.projectmanagement.calendar.TimeInterval;
import com.xwiki.projectmanagement.calendar.WeekDays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validate how the macro parameters of {@link CalendarMacroParameters} are converted and described, which also covers
 * {@link CalendarViews}, {@link WeekDays} and {@link TimeInterval}.
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
class CalendarMacroParametersTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @BeforeEach
    void setUp() throws Exception
    {
        // Used by the converter manager to look up the converters.
        this.componentManager.registerComponent(ComponentManager.class, "context", this.componentManager);
    }

    @Test
    void populateWithDefaults() throws Exception
    {
        CalendarMacroParameters parameters = populate(Map.of());

        assertEquals(CalendarViews.month, parameters.getDefaultView());
        assertEquals(WeekDays.SUNDAY, parameters.getFirstDay());
        assertEquals(25, parameters.getLimit());
        assertNull(parameters.getTimeInterval());
        assertNull(parameters.getFilters());
        assertNull(parameters.getPresetId());
        assertNull(parameters.getClient());
    }

    @Test
    void populate() throws Exception
    {
        CalendarMacroParameters parameters = populate(Map.of("defaultView", "agendaWeek", "firstDay", "monday",
            "timeInterval", "06:00-20:00", "limit", "100", "filters", "{}", "presetId", "3"));

        assertEquals(CalendarViews.agendaWeek, parameters.getDefaultView());
        assertEquals(WeekDays.MONDAY, parameters.getFirstDay());
        assertEquals("06:00-20:00", parameters.getTimeInterval());
        assertEquals(100, parameters.getLimit());
        assertEquals("{}", parameters.getFilters());
        assertEquals("3", parameters.getPresetId());
    }

    @Test
    void populateWithUnknownView()
    {
        assertThrows(PropertyException.class, () -> populate(Map.of("defaultView", "year")));
    }

    @Test
    void populateWithUnknownDay()
    {
        assertThrows(PropertyException.class, () -> populate(Map.of("firstDay", "someday")));
    }

    @Test
    void populateWithInvalidLimit()
    {
        assertThrows(PropertyException.class, () -> populate(Map.of("limit", "ten")));
    }

    @Test
    void descriptor() throws Exception
    {
        BeanDescriptor descriptor = getBeanManager().getBeanDescriptor(CalendarMacroParameters.class);

        // The time interval is edited with the dedicated time interval displayer and the filters with the filter
        // builder.
        assertEquals(TimeInterval.class, descriptor.getProperty("timeInterval").getDisplayType());
        assertEquals(ProjectManagementFilter.class, descriptor.getProperty("filters").getDisplayType());
        PropertyDescriptor firstDay = descriptor.getProperty("firstDay");
        assertEquals(WeekDays.class, firstDay.getPropertyType());
        assertEquals(WeekDays.SUNDAY, firstDay.getDefaultValue());
        assertEquals(CalendarViews.month, descriptor.getProperty("defaultView").getDefaultValue());
        // The calendar views are the FullCalendar view names.
        assertEquals("month, agendaWeek, agendaDay, basicWeek, basicDay",
            Arrays.stream(CalendarViews.values()).map(Enum::name).collect(Collectors.joining(", ")));
    }

    @ParameterizedTest
    @EnumSource(WeekDays.class)
    void weekDayValues(WeekDays weekDay)
    {
        // FullCalendar numbers the days of the week from 0 (Sunday) to 6 (Saturday).
        assertEquals(DayOfWeek.valueOf(weekDay.name()).getValue() % 7, weekDay.getDayValue());
    }

    private CalendarMacroParameters populate(Map<String, Object> values) throws Exception
    {
        CalendarMacroParameters parameters = new CalendarMacroParameters();
        getBeanManager().populate(parameters, values);
        return parameters;
    }

    private BeanManager getBeanManager() throws Exception
    {
        return this.componentManager.getInstance(BeanManager.class);
    }
}
