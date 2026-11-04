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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.script.ScriptContext;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.template.TemplateManager;
import org.xwiki.template.script.TemplateScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.PageTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Page test for the editor of the properties parameter of the OpenProject Project Details macro.
 *
 * @version $Id$
 */
// Includes the templates with #template.
@ComponentList(TemplateScriptService.class)
class ProjectDetailsPropertiesPickerPageTest extends PageTest
{
    private static final String TEMPLATE = "html_displayer/openprojectprojectdetailsproperty/edit.vm";

    @Test
    void proposesTheProjectPropertiesInThePropertyPicker() throws Exception
    {
        Document result = render(List.of(OpenProjectProjectDetailsProperty.NAME,
            OpenProjectProjectDetailsProperty.STATUS));

        // The same picker as the one of the work package properties of the other macros.
        Element picker = result.selectFirst("select.proj-manag-property-picker[name=properties][multiple]");
        assertEquals(List.of("ID", "NAME", "IDENTIFIER", "DESCRIPTION", "STATUS", "CREATED"),
            picker.select("option").stream().map(Element::val).collect(Collectors.toList()), result.html());
        assertEquals(List.of("NAME", "STATUS"),
            picker.select("option[selected]").stream().map(Element::val).collect(Collectors.toList()));
        // The labels are the ones displayed by the macro.
        assertEquals("openproject.openproject-project-details.labels.identifier",
            picker.select("option").get(2).text());
    }

    private Document render(List<OpenProjectProjectDetailsProperty> value) throws Exception
    {
        ScriptContext scriptContext =
            this.componentManager.<ScriptContextManager>getInstance(ScriptContextManager.class).getScriptContext();
        scriptContext.setAttribute("displayer",
            Map.of("value", value, "parameters", Map.of("name", "properties")),
            ScriptContext.ENGINE_SCOPE);
        TemplateManager templateManager = this.componentManager.getInstance(TemplateManager.class);
        return Jsoup.parse(templateManager.render(TEMPLATE));
    }
}
