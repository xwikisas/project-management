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
package com.xwiki.projectmanagement.calendar.internal.macro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Provider;

import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;
import com.xwiki.projectmanagement.ProjectManagementClientExecutionContext;
import com.xwiki.projectmanagement.calendar.CalendarViews;
import com.xwiki.projectmanagement.calendar.WeekDays;
import com.xwiki.projectmanagement.calendar.macro.CalendarMacroParameters;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the {@link AbstractProjectManagementCalendarMacro} through the {@link TestCalendarMacro} implementation.
 *
 * @version $Id$
 */
@ComponentTest
class AbstractProjectManagementCalendarMacroTest
{
    private static final String REST_PATH = "/xwiki/rest/wikis/mywiki/projectmanagement/test/calendar";

    private static final String JSON = "json";

    private static final String EXTRA_PARAMETER = "extra=value";

    @InjectMockComponents
    private TestCalendarMacro macro;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("ssrx")
    private SkinExtension ssrx;

    @MockComponent
    private PresetsManager presetsManager;

    @MockComponent
    private ProjectManagementClientExecutionContext clientContext;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWikiRequest request;

    @Mock
    private MacroTransformationContext macroTransformationContext;

    private CalendarMacroParameters parameters;

    @BeforeEach
    void setUp()
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getRequest()).thenReturn(this.request);
        when(this.xcontext.getWikiId()).thenReturn("mywiki");
        when(this.request.getContextPath()).thenReturn("/xwiki");
        when(this.clientContext.getContext()).thenReturn(Collections.emptyMap());

        this.parameters = new CalendarMacroParameters();
        this.parameters.setClient("test");
    }

    @Test
    void executeWithDefaultParameters() throws Exception
    {
        MacroBlock calendarBlock = execute();

        assertFalse(this.macro.supportsInlineMode());
        verify(this.ssrx).use("projectmanagercalendar/css/parameters.css");
        // The rendering is delegated to the calendar macro, fed by the calendar REST resource of the client.
        assertEquals("calendar", calendarBlock.getId());
        assertNull(calendarBlock.getContent());
        assertFalse(calendarBlock.isInline());
        Map<String, String> calendarParameters = calendarBlock.getParameters();
        assertEquals(Map.of(JSON, calendarParameters.get(JSON), "defaultView", "month", "editable", "false",
            "firstDay", "0"), calendarParameters);
        URIBuilder url = new URIBuilder(calendarParameters.get(JSON));
        assertEquals(REST_PATH, url.getPath());
        assertEquals(List.of("limit=25", EXTRA_PARAMETER), getQueryParameters(url));
        verify(this.presetsManager, never()).getPreset(any());
    }

    @Test
    void executeWithFiltersAndClientContext() throws Exception
    {
        String filters = "{\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":[{\"value\":\"a&b\"}]}]}}";
        this.parameters.setFilters(filters);
        this.parameters.setLimit(10);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("instance", "my instance");
        context.put("sprint", true);
        context.put("empty", null);
        when(this.clientContext.getContext()).thenReturn(context);

        URIBuilder url = new URIBuilder(execute().getParameter(JSON));

        assertEquals(REST_PATH, url.getPath());
        // The client context (e.g. the OpenProject instance) is forwarded to the REST resource, query values being
        // properly encoded.
        assertEquals(List.of("filters=" + filters, "instance=my instance", "sprint=true", "empty=", "limit=10",
            EXTRA_PARAMETER), getQueryParameters(url));
    }

    @Test
    void executeWithEmptyFilters() throws Exception
    {
        this.parameters.setFilters("");

        URIBuilder url = new URIBuilder(execute().getParameter(JSON));

        assertEquals(List.of("limit=25", EXTRA_PARAMETER), getQueryParameters(url));
    }

    @Test
    void executeExcludingWorkItems() throws Exception
    {
        this.macro.setExcludeWorkItems(true);

        URIBuilder url = new URIBuilder(execute().getParameter(JSON));

        assertEquals(List.of("limit=25", EXTRA_PARAMETER, "excludeWorkItems=true"), getQueryParameters(url));
    }

    @Test
    void executeWithDisplayParameters() throws Exception
    {
        this.parameters.setDefaultView(CalendarViews.agendaWeek);
        this.parameters.setFirstDay(WeekDays.MONDAY);

        MacroBlock calendarBlock = execute();

        assertEquals("agendaWeek", calendarBlock.getParameter("defaultView"));
        assertEquals("1", calendarBlock.getParameter("firstDay"));
    }

    @Test
    void executeWithoutDefaultView() throws Exception
    {
        this.parameters.setDefaultView(null);

        MacroBlock calendarBlock = execute();

        assertFalse(calendarBlock.getParameters().containsKey("defaultView"));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "06:00-20:00, 06:00, 20:00",
        "' 08:30 - 18:00 ', 08:30, 18:00",
        "07:00-, 07:00, NULL",
        "-19:00, NULL, 19:00",
        "' - ', NULL, NULL",
        "07:00, NULL, NULL",
        "'', NULL, NULL",
        "NULL, NULL, NULL"
    }, nullValues = "NULL")
    void executeWithTimeInterval(String timeInterval, String expectedMinTime, String expectedMaxTime)
        throws Exception
    {
        this.parameters.setTimeInterval(timeInterval);

        MacroBlock calendarBlock = execute();

        assertEquals(expectedMinTime, calendarBlock.getParameters().get("minTime"));
        assertEquals(expectedMaxTime, calendarBlock.getParameters().get("maxTime"));
    }

    @Test
    void executeWithPreset() throws Exception
    {
        String presetFilters = "{\"query\":{\"filters\":[{\"property\":\"status\"}]}}";
        Preset preset = mock(Preset.class);
        when(preset.getFilter()).thenReturn(presetFilters);
        when(this.presetsManager.getPreset(42)).thenReturn(preset);
        this.parameters.setPresetId("42");
        this.parameters.setFilters("{\"query\":{\"filters\":[]}}");

        URIBuilder url = new URIBuilder(execute().getParameter(JSON));

        // The preset filters replace the filters parameter in the REST URL.
        assertEquals(List.of("filters=" + presetFilters, "limit=25", EXTRA_PARAMETER), getQueryParameters(url));
    }

    @Test
    void executeWithoutRequest() throws Exception
    {
        when(this.xcontext.getRequest()).thenReturn(null);
        XWiki wiki = mock(XWiki.class);
        when(this.xcontext.getWiki()).thenReturn(wiki);
        when(wiki.getWebAppPath(this.xcontext)).thenReturn("xwiki/");

        assertEquals(REST_PATH, new URIBuilder(execute().getParameter(JSON)).getPath());

        // Web application deployed as ROOT.
        when(wiki.getWebAppPath(this.xcontext)).thenReturn("");

        assertEquals(REST_PATH.substring("/xwiki".length()), new URIBuilder(execute().getParameter(JSON)).getPath());
    }

    @Test
    void executeWithNonNumericPresetId()
    {
        this.parameters.setPresetId("notANumber");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, null, this.macroTransformationContext));

        assertEquals("Failed to execute the project management calendar macro.", exception.getMessage());
        assertEquals("The preset id should be a number.", exception.getCause().getMessage());
        verify(this.presetsManager, never()).getPreset(any());
    }

    @Test
    void executeWithMissingPreset()
    {
        this.parameters.setPresetId("42");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, null, this.macroTransformationContext));

        assertEquals("No preset was found with the given id.", exception.getCause().getMessage());
        verify(this.presetsManager).getPreset(42);
    }

    private MacroBlock execute() throws MacroExecutionException
    {
        List<Block> result = this.macro.execute(this.parameters, null, this.macroTransformationContext);
        assertEquals(1, result.size());
        return (MacroBlock) result.get(0);
    }

    private static List<String> getQueryParameters(URIBuilder url)
    {
        return url.getQueryParams().stream().map(pair -> pair.getName() + '=' + pair.getValue())
            .collect(Collectors.toList());
    }
}
