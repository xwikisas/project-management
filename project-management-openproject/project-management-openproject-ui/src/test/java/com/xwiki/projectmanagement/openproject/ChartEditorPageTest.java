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
package com.xwiki.projectmanagement.openproject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.script.ScriptContext;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.script.service.ScriptService;
import org.xwiki.template.TemplateManager;
import org.xwiki.template.script.TemplateScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.PageTest;

import com.xwiki.projectmanagement.livedata.script.ProjectManagementLivedataScriptService;
import com.xwiki.projectmanagement.presets.Preset;
import com.xwiki.projectmanagement.presets.script.PresetsScriptService;
import com.xwiki.projectmanagement.script.ProjectManagementScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Page test for the editors of the parameters of the OpenProject chart macro.
 *
 * @version $Id$
 */
// Includes the templates with #template.
@ComponentList(TemplateScriptService.class)
class ChartEditorPageTest extends PageTest
{
    private static final String CLIENT = "openproject";

    @MockComponent(classToMock = ProjectManagementScriptService.class)
    @Named(ProjectManagementScriptService.ROLE_HINT)
    private ScriptService projectManagementScriptService;

    @MockComponent(classToMock = ProjectManagementLivedataScriptService.class)
    @Named("projectmanagement.livedata")
    private ScriptService liveDataScriptService;

    @MockComponent(classToMock = PresetsScriptService.class)
    @Named("projectmanagement.presets")
    private ScriptService presetsScriptService;

    private TemplateManager templateManager;

    @BeforeEach
    void setUp() throws Exception
    {
        LiveDataPropertyDescriptor type = new LiveDataPropertyDescriptor();
        type.setId("type");
        type.setName("Type");
        type.setFilter(new LiveDataPropertyDescriptor.FilterDescriptor("list"));
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setMeta(new LiveDataMeta());
        configuration.getMeta().setPropertyDescriptors(List.of(type));
        ProjectManagementScriptService projectManagement =
            (ProjectManagementScriptService) this.projectManagementScriptService;
        // $services.projectmanagement.livedata and $services.projectmanagement.presets
        when(projectManagement.get("livedata")).thenReturn(this.liveDataScriptService);
        when(projectManagement.get("presets")).thenReturn(this.presetsScriptService);
        when(((ProjectManagementLivedataScriptService) this.liveDataScriptService).getLivedataConfiguration(CLIENT,
            Map.of())).thenReturn(configuration);

        setDisplayer(null);
        this.templateManager = this.componentManager.getInstance(TemplateManager.class);
    }

    @Test
    void filtersEditorTitlesTheSeries() throws Exception
    {
        Document result = render("html_displayer/openprojectfilters/edit.vm");

        // The JavaScript code titles the series from the template of their container.
        Element filters = result.selectFirst(".project-management-filters");
        assertEquals("projectmanagement.macro.chart.series [__N__]", filters.attr("data-series-title"), result.html());
        assertEquals("projectmanagement.macro.chart.series [1]",
            filters.selectFirst(".proj-manag-header-title").text());
        assertEquals("projectmanagement.macro.chart.addSeries",
            result.selectFirst("button.project-management-new-dataset.proj-manag-add-link").text());
    }

    @Test
    void filtersEditorProposesTheFilterableProperties() throws Exception
    {
        Document result = render("html_displayer/openprojectfilters/edit.vm");

        // A filter is added by choosing its property in a select, which goes back to its placeholder.
        Element addFilter = result.selectFirst(".proj-manag-add-filter-container label");
        assertEquals("projectmanagement.macro.filter.addFilterOn", addFilter.ownText(), result.html());
        assertEquals(List.of("", "type"), addFilter.select("select.proj-manag-add-filter option").eachAttr("value"));
        assertEquals(List.of("projectmanagement.macro.filter.chooseProperty", "Type"),
            addFilter.select("select.proj-manag-add-filter option").eachText());
        // One row per filter, without the old "Add constraint" action.
        assertNull(result.selectFirst(".proj-manag-add-constraint"));

        // The labels of the operators, by type of filter and operator id, and the words of the summary.
        String l10n = result.selectFirst(".proj-manag-constraint-builder").attr("data-l10n");
        assertTrue(l10n.contains("\"list.=\":\"projectmanagement.macro.filter.operator.list.isOneOf\""), l10n);
        assertTrue(l10n.contains("\"date.between\":\"projectmanagement.macro.filter.operator.date.isBetween\""),
            l10n);
        assertTrue(l10n.contains("\"summary\":\"projectmanagement.macro.filter.summary\""), l10n);
    }

    @Test
    void filterPropertiesOfTheInstance() throws Exception
    {
        LiveDataPropertyDescriptor labels = new LiveDataPropertyDescriptor();
        labels.setId("Labels");
        labels.setFilter(new LiveDataPropertyDescriptor.FilterDescriptor("list"));
        labels.getFilter().getParameters().put("multiValue", true);
        // Not filterable.
        LiveDataPropertyDescriptor category = new LiveDataPropertyDescriptor();
        category.setId("category");
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setMeta(new LiveDataMeta());
        configuration.getMeta().setPropertyDescriptors(List.of(labels, category));
        when(((ProjectManagementLivedataScriptService) this.liveDataScriptService).getLivedataConfiguration(CLIENT,
            Map.of("instance", "op.xwiki.org"))).thenReturn(configuration);
        this.request.put("client", CLIENT);
        this.request.put("sourceParameters", "{\"instance\":\"op.xwiki.org\"}");

        String result = this.templateManager.render("filter_properties.vm").trim();

        // The filterable properties of the instance, for the filter builder to propose them.
        assertTrue(result.startsWith("[{\"id\":\"Labels\""), result);
        assertTrue(result.contains("\"multiValue\":true"), result);
        assertFalse(result.contains("category"), result);
    }

    @Test
    void filterPropertiesOfAnUnknownClient() throws Exception
    {
        this.request.put("client", "unknown");

        assertEquals("[]", this.templateManager.render("filter_properties.vm").trim());
    }

    @Test
    void seriesNamesEditorIsTranslated() throws Exception
    {
        Document result = render("html_displayer/projectmanagementlabels/edit.vm");

        Element labels = result.selectFirst(".proj-manag-chart-labels");
        assertEquals("projectmanagement.macro.chart.seriesName [__N__]", labels.attr("data-label"), result.html());
        assertEquals("projectmanagement.macro.chart.series [__N__]", labels.attr("data-placeholder"));
        assertEquals("projectmanagement.macro.chart.addSeriesName",
            result.selectFirst("button.proj-manag-chart-label-new.proj-manag-add-link").text());
    }

    @Test
    void presetPickerWithoutPresets() throws Exception
    {
        Document result = render("html_displayer/openprojectchartpreset/edit.vm");

        // An empty picker would open no list.
        Element picker = result.selectFirst("select.op-preset-select");
        assertTrue(picker.hasAttr("disabled"), result.html());
        assertEquals("projectmanagement.macro.filter.preset.none.chart", picker.text());
    }

    @Test
    void presetPickerWithoutPresetsForTheMacro() throws Exception
    {
        // Only a chart preset exists, which the work packages macros can't use.
        Preset preset = mock(Preset.class);
        when(preset.getId()).thenReturn(3);
        when(preset.getName()).thenReturn("Bugs by status");
        when(((PresetsScriptService) this.presetsScriptService).getPresetsForClient(CLIENT, true, 0, 25))
            .thenReturn(List.of(preset));

        Document result = render("html_displayer/openprojectpreset/edit.vm");

        Element picker = result.selectFirst("select.op-preset-select");
        assertTrue(picker.hasAttr("disabled"), result.html());
        assertEquals("projectmanagement.macro.filter.preset.none", picker.text());
    }

    @Test
    void presetPickerWithPresets() throws Exception
    {
        Preset preset = mock(Preset.class);
        when(preset.getId()).thenReturn(3);
        when(preset.getName()).thenReturn("Open bugs");
        when(((PresetsScriptService) this.presetsScriptService).getPresetsForClient(CLIENT, true, 0, 25))
            .thenReturn(List.of(preset));

        Document result = render("html_displayer/openprojectchartpreset/edit.vm");

        Element picker = result.selectFirst("select.op-preset-select");
        assertFalse(picker.hasAttr("disabled"), result.html());
        assertEquals("3", picker.selectFirst("option[value=3]").val());
    }

    @Test
    void presetPickerKeepsTheValueOfADeletedPreset() throws Exception
    {
        setDisplayer("3");

        Document result = render("html_displayer/openprojectchartpreset/edit.vm");

        // The picker isn't replaced by the disabled one, so that submitting the macro doesn't lose its value.
        Element picker = result.selectFirst("select.op-preset-select");
        assertFalse(picker.hasAttr("disabled"), result.html());
    }

    private void setDisplayer(String value) throws Exception
    {
        Map<String, Object> displayer = new HashMap<>();
        displayer.put("parameters", new HashMap<>(Map.of("name", "presetId")));
        displayer.put("value", value);
        ScriptContext scriptContext =
            this.componentManager.<ScriptContextManager>getInstance(ScriptContextManager.class).getScriptContext();
        scriptContext.setAttribute("displayer", displayer, ScriptContext.ENGINE_SCOPE);
    }

    private Document render(String template) throws Exception
    {
        return Jsoup.parse(this.templateManager.render(template));
    }
}
