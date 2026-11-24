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
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.annotation.ComponentList;
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
import com.xwiki.projectmanagement.internal.macro.PresetFiltersCombiner;
import com.xwiki.projectmanagement.internal.source.WorkItemSourceReader;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.PresetsManager;
import com.xwiki.projectmanagement.source.WorkItemSelection;
import com.xwiki.projectmanagement.source.WorkItemName;
import com.xwiki.projectmanagement.source.WorkItemSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
@ComponentList({ PresetFiltersCombiner.class, WorkItemSourceReader.class, CalendarLoadErrorMessages.class })
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
    @Named("jsrx")
    private SkinExtension jsrx;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private PresetsManager presetsManager;

    @MockComponent
    @Named("context")
    private ComponentManager contextComponentManager;

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
        assertEquals(List.of(EXTRA_PARAMETER), getQueryParameters(url));
        verify(this.presetsManager, never()).getPreset(any());
    }

    @Test
    void executeRendersTheMessagesOfTheLoadErrors() throws Exception
    {
        for (String key : List.of("invalidRequest", "notAuthenticated", "forbidden")) {
            Translation translation = mock(Translation.class);
            when(translation.render()).thenReturn(new WordBlock(key + " message"));
            when(this.l10n.getTranslation("calendar.loadError." + key)).thenReturn(translation);
        }

        GroupBlock calendar = executeCalendar(null);

        verify(this.jsrx).use("projectmanagercalendar/js/calendarLoadError.js");
        assertEquals("projectmanagement-calendar", calendar.getParameter("class"));
        // The script recognizes the requests of the events by their URL, the one given to the calendar macro.
        MacroBlock calendarBlock = (MacroBlock) calendar.getChildren().get(4);
        assertEquals(calendarBlock.getParameter(JSON), calendar.getParameter("data-events-url"));
        // One hidden message per error, the warnings being for the errors that the user may be able to fix.
        assertLoadError(calendar.getChildren().get(0), "400", "box errormessage", "invalidRequest message");
        assertLoadError(calendar.getChildren().get(1), "401", "box warningmessage", "notAuthenticated message");
        assertLoadError(calendar.getChildren().get(2), "403", "box warningmessage", "forbidden message");
        // A missing translation is displayed as its key.
        assertLoadError(calendar.getChildren().get(3), "other", "box errormessage", "calendar.loadError.failure");
        // The extending classes can display the hints in their own messages.
        assertEquals("forbidden message",
            ((WordBlock) this.macro.getLoadErrorHint(CalendarLoadError.FORBIDDEN)).getWord());
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

        assertEquals(List.of(EXTRA_PARAMETER), getQueryParameters(url));
    }

    @Test
    void executeExcludingWorkItems() throws Exception
    {
        this.macro.setExcludeWorkItems(true);

        URIBuilder url = new URIBuilder(execute().getParameter(JSON));

        assertEquals(List.of(EXTRA_PARAMETER, "excludeWorkItems=true"), getQueryParameters(url));
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
        // Until midnight.
        "20:00-24:00, 20:00, 24:00",
        "06:00-24:00:00, 06:00, 24:00:00",
        // An end of 00:00 is the end of the day, which the time fields of the macro editor can't hold as 24:00.
        "20:00-00:00, 20:00, 24:00",
        "00:00-00:00, 00:00, 24:00",
        "6:00-0:00:00, 6:00, 24:00",
        "-00:00, NULL, 24:00",
        "8:00-08:00:30, 8:00, 08:00:30",
        // The times that can't be read are passed to the calendar as they are.
        "20:00-6h, 20:00, 6h",
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

    @ParameterizedTest
    @CsvSource({
        "20:00-06:00",
        "08:00-08:00",
        "' 18:00 - 9:30 '",
        "20:00:30-20:00:15",
        "24:00-24:00",
        "24:00-00:00"
    })
    void executeWithTimeIntervalEndingBeforeItStarts(String timeInterval)
    {
        this.parameters.setTimeInterval(timeInterval);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, null, this.macroTransformationContext));

        // The calendar would display no time slot.
        assertEquals(String.format("The [timeInterval] parameter must end after it starts (e.g. 06:00-20:00), but is "
            + "[%s].", timeInterval), exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "06:00-24:30",
        "06:00-24:00:01",
        "' 06:00 - 25:00 '",
        "24:30-",
        "-99:00",
        // 24:15: the calendar adds the minutes to the hours.
        "06:00-23:75"
    })
    void executeWithTimeIntervalAfterTheEndOfTheDay(String timeInterval)
    {
        this.parameters.setTimeInterval(timeInterval);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, null, this.macroTransformationContext));

        // The calendar would display hours of the next day.
        assertEquals(String.format("The [timeInterval] parameter must hold times between 00:00 and 24:00 (e.g. "
            + "06:00-20:00), but is [%s].", timeInterval), exception.getMessage());
    }

    @Test
    void executeWithPreset() throws Exception
    {
        Preset preset = mock(Preset.class);
        when(preset.getFilter()).thenReturn("{\"query\":{\"filters\":[{\"property\":\"status\"}]}}");
        when(this.presetsManager.getPreset(42)).thenReturn(preset);
        this.parameters.setPresetId("42");
        this.parameters.setFilters("{\"query\":{\"filters\":[{\"property\":\"type\"}]}}");

        URIBuilder url = new URIBuilder(execute().getParameter(JSON));

        // The preset filters are combined with the filters parameter in the REST URL.
        assertEquals(List.of("filters={\"query\":{\"filters\":[{\"property\":\"status\"},{\"property\":\"type\"}]}}",
            EXTRA_PARAMETER), getQueryParameters(url));
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

        assertEquals("There is no preset with id [42].", exception.getCause().getMessage());
        verify(this.presetsManager).getPreset(42);
    }

    @Test
    void executeWithTheWorkItemsSelectedByTheContent() throws Exception
    {
        WorkItemSource source = mock(WorkItemSource.class);
        when(this.contextComponentManager.hasComponent(WorkItemSource.class, "test/url")).thenReturn(true);
        when(this.contextComponentManager.getInstance(WorkItemSource.class, "test/url")).thenReturn(source);
        when(source.read(eq("http://tool/list"), any())).thenReturn(new WorkItemSelection(
            List.of(new LiveDataQuery.Filter("status", "=", "1")), List.of(), Map.of("identifier", "http://tool/list")));
        this.parameters.setSource("url");

        URIBuilder url = new URIBuilder(execute("http://tool/list").getParameter(JSON));

        // The filters and the link read from the content are passed to the REST resource of the client.
        assertEquals(List.of("filters={\"query\":{\"filters\":[{\"property\":\"status\",\"constraints\":"
            + "[{\"operator\":\"=\",\"value\":\"1\"}]}]}}", "identifier=http://tool/list", EXTRA_PARAMETER),
            getQueryParameters(url));
    }

    @Test
    void executeWithTheNamesOfTheQueryWrittenAsText() throws Exception
    {
        WorkItemSource source = mock(WorkItemSource.class);
        when(this.contextComponentManager.hasComponent(WorkItemSource.class, "query")).thenReturn(true);
        when(this.contextComponentManager.getInstance(WorkItemSource.class, "query")).thenReturn(source);
        when(source.read(eq("project = xwiki"), any())).thenReturn(WorkItemSelection.ofFilters(
            List.of(new LiveDataQuery.Filter("project", "=", new WorkItemName("xwiki")))));

        URIBuilder url = new URIBuilder(execute("project = xwiki").getParameter(JSON));

        // The calendar macros of the clients that don't resolve the names pass them as text.
        assertEquals(List.of("filters={\"query\":{\"filters\":[{\"property\":\"project\",\"constraints\":"
            + "[{\"operator\":\"=\",\"value\":\"xwiki\"}]}]}}", EXTRA_PARAMETER), getQueryParameters(url));
    }

    @Test
    void failsWhenTheContentIsUsedWithTheFiltersParameter()
    {
        this.parameters.setFilters("{\"query\":{}}");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "status = 1", this.macroTransformationContext));

        assertEquals("The [filters] parameter can't be used together with the macro content: write the filters in "
            + "the content instead.", exception.getMessage());
    }

    private MacroBlock execute() throws MacroExecutionException
    {
        return execute(null);
    }

    private MacroBlock execute(String content) throws MacroExecutionException
    {
        List<Block> children = executeCalendar(content).getChildren();
        return (MacroBlock) children.get(children.size() - 1);
    }

    private GroupBlock executeCalendar(String content) throws MacroExecutionException
    {
        List<Block> result = this.macro.execute(this.parameters, content, this.macroTransformationContext);
        assertEquals(1, result.size());
        return (GroupBlock) result.get(0);
    }

    private static void assertLoadError(Block block, String status, String boxClass, String message)
    {
        assertEquals(Map.of("class", "projectmanagement-calendar-load-error", "data-status", status, "hidden",
            "hidden"), block.getParameters());
        Block box = block.getChildren().get(0);
        assertEquals(boxClass, box.getParameter("class"));
        assertEquals(message, ((WordBlock) box.getChildren().get(0)).getWord());
    }

    private static List<String> getQueryParameters(URIBuilder url)
    {
        return url.getQueryParams().stream().map(pair -> pair.getName() + '=' + pair.getValue())
            .collect(Collectors.toList());
    }
}
