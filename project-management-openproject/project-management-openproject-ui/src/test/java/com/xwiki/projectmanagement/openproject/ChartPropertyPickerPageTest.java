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
 * Page test for the pickers of the work package properties of the OpenProject macros ({@code property_picker.vm}).
 *
 * @version $Id$
 */
// Includes the templates with #template.
@ComponentList(TemplateScriptService.class)
class ChartPropertyPickerPageTest extends PageTest
{
    private static final String CLIENT = "openproject";

    private static final List<String> PROPERTIES =
        List.of("identifier", "type", "summary", "description", "progress", "status", "assignees", "_actions");

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
        configuration.getMeta().setPropertyDescriptors(PROPERTIES.stream().map(id -> {
            LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();
            descriptor.setId(id);
            descriptor.setName(id);
            return descriptor;
        }).collect(Collectors.toList()));
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
    void chartPropertyPickerDoesNotProposeThePropertiesUniqueToEachWorkItem() throws Exception
    {
        // A chart grouping by the identifier, the summary, the description or the progress would have one slice or
        // bar per work item, and the actions aren't a property of the work items.
        Document result = render("html_displayer/openprojectproperty/edit.vm");

        assertEquals(List.of("type", "status", "assignees"), getProposedProperties(result), result.html());
    }

    @Test
    void propertiesPickerProposesAllTheProperties() throws Exception
    {
        Document result = render("html_displayer/openprojectproperties/edit.vm");

        assertEquals(PROPERTIES, getProposedProperties(result), result.html());
    }

    private Document render(String template) throws Exception
    {
        return Jsoup.parse(this.templateManager.render(template));
    }

    private static List<String> getProposedProperties(Document result)
    {
        return result.select("select.proj-manag-property-picker option").stream().map(Element::val)
            .collect(Collectors.toList());
    }
}
