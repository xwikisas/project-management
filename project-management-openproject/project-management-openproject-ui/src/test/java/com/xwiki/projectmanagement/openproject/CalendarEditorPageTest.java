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

import java.util.Map;

import javax.script.ScriptContext;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.xwiki.script.ScriptContextManager;
import org.xwiki.template.TemplateManager;
import org.xwiki.test.page.PageTest;

import com.xpn.xwiki.plugin.skinx.SkinExtensionPluginApi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Page test for the editors of the parameters of the OpenProject calendar macro.
 *
 * @version $Id$
 */
class CalendarEditorPageTest extends PageTest
{
    @Test
    void visibleHoursEditorLoadsTheStylesOfItsTimes() throws Exception
    {
        SkinExtensionPluginApi ssrx = mock(SkinExtensionPluginApi.class);
        doReturn(ssrx).when(this.xwiki).getPluginApi("ssrx", this.context);
        ScriptContext scriptContext =
            this.componentManager.<ScriptContextManager>getInstance(ScriptContextManager.class).getScriptContext();
        scriptContext.setAttribute("displayer", Map.of("parameters", Map.of("name", "timeInterval")),
            ScriptContext.ENGINE_SCOPE);

        Document result = Jsoup.parse(this.componentManager.<TemplateManager>getInstance(TemplateManager.class)
            .render("html_displayer/timeinterval/edit.vm"));

        // The start and the end of the visible hours, which these styles put on one line with a dash between them.
        // The editor loads them because the edited page may display no calendar (a new one, or one failing).
        assertEquals(2, result.select(".time-interval-inputs input[type=time]").size(), result.html());
        verify(ssrx).use("projectmanagercalendar/css/parameters.css");
    }
}
