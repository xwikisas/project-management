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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xwiki.projectmanagement.openproject.script.OpenProjectScriptService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code OpenProject.WebHome}, the OpenProject dashboard.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class DashboardPageTest extends PageTest
{
    private static final String CONNECTION_NAME = "<img src=\"x\" class=\"injected\"/>{{/html}}";

    @MockComponent(classToMock = OpenProjectScriptService.class)
    @Named("openproject")
    private ScriptService openProjectScriptService;

    @Test
    void connectionNamesAreEscaped() throws Exception
    {
        OpenProjectScriptService openProject = (OpenProjectScriptService) this.openProjectScriptService;
        // The first option is the "use the dashboard connection" option, removed by the dashboard.
        when(openProject.getConnectionOptions()).thenReturn(new ArrayList<>(List.of(Map.of(),
            Map.of("name", CONNECTION_NAME, "value", CONNECTION_NAME),
            Map.of("name", "other", "value", "other"))));
        this.context.setUserReference(new DocumentReference("xwiki", "XWiki", "User"));
        registerVelocityTool("hasEdit", true);

        Document document = renderHTMLPage(new DocumentReference("xwiki", "OpenProject", "WebHome"));

        assertTrue(document.select(".injected").isEmpty(), document.html());
        List<String> options = new ArrayList<>();
        for (Element option : document.select("#openproject-dashboard-connection-select option")) {
            options.add(option.attr("value"));
        }
        assertEquals(List.of(CONNECTION_NAME, "other"), options);
    }
}
