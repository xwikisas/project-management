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
import java.util.stream.Collectors;

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
import com.xwiki.projectmanagement.script.ProjectManagementScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Page test for the Sort By picker of the OpenProject macros ({@code sort_picker.vm}).
 *
 * @version $Id$
 */
// Includes the templates with #template.
@ComponentList(TemplateScriptService.class)
class SortPickerPageTest extends PageTest
{
    private static final String CLIENT = "openproject";

    @MockComponent(classToMock = ProjectManagementScriptService.class)
    @Named(ProjectManagementScriptService.ROLE_HINT)
    private ScriptService projectManagementScriptService;

    @MockComponent(classToMock = ProjectManagementLivedataScriptService.class)
    @Named("projectmanagement.livedata")
    private ScriptService liveDataScriptService;

    private TemplateManager templateManager;

    @BeforeEach
    void setUp() throws Exception
    {
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setMeta(new LiveDataMeta());
        LiveDataPropertyDescriptor priority = property("priority", null);
        priority.setType("String");
        configuration.getMeta().setPropertyDescriptors(List.of(property("identifier", true),
            property("description", false), property("status", true), priority, property("_actions", null)));
        // The properties whose sortable flag isn't set are sortable if their type is.
        LiveDataPropertyDescriptor stringType = property("String", true);
        configuration.getMeta().setPropertyTypes(List.of(stringType));
        // $services.projectmanagement.livedata
        when(((ProjectManagementScriptService) this.projectManagementScriptService).get("livedata"))
            .thenReturn(this.liveDataScriptService);
        when(((ProjectManagementLivedataScriptService) this.liveDataScriptService).getLivedataConfiguration(CLIENT))
            .thenReturn(configuration);

        ScriptContext scriptContext =
            this.componentManager.<ScriptContextManager>getInstance(ScriptContextManager.class).getScriptContext();
        scriptContext.setAttribute("displayer", Map.of("parameters", new HashMap<>()), ScriptContext.ENGINE_SCOPE);

        this.templateManager = this.componentManager.getInstance(TemplateManager.class);
    }

    @Test
    void sortPickerProposesOnlyTheSortableProperties() throws Exception
    {
        // A sort on a property that can't be sorted (e.g. the description) or that isn't a work item property (the
        // actions) has no effect.
        Document result = Jsoup.parse(this.templateManager.render("html_displayer/openprojectsort/edit.vm"));

        assertEquals(List.of("identifier:asc", "identifier:desc", "status:asc", "status:desc", "priority:asc",
            "priority:desc"),
            result.select("select option").stream().map(Element::val).collect(Collectors.toList()), result.html());
    }

    @Test
    void sortPickerIsNamedAfterTheLabelOfTheParameter() throws Exception
    {
        ScriptContext scriptContext =
            this.componentManager.<ScriptContextManager>getInstance(ScriptContextManager.class).getScriptContext();
        scriptContext.setAttribute("displayer", Map.of("parameters", new HashMap<>(Map.of("name", "sort"))),
            ScriptContext.ENGINE_SCOPE);

        Document result = Jsoup.parse(this.templateManager.render("html_displayer/openprojectsort/edit.vm"));

        // The picker moves the label of the parameter (for its select) to its text input, giving it this id.
        assertEquals("parameter-sort-ts-label", result.selectFirst("select").attr("aria-labelledby"), result.html());
    }

    private static LiveDataPropertyDescriptor property(String id, Boolean sortable)
    {
        LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();
        descriptor.setId(id);
        descriptor.setName(id);
        descriptor.setSortable(sortable);
        return descriptor;
    }
}
