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
package com.xwiki.projectmanagement.openproject.internal.macro;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectProjectsMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectProjectsMacroTest extends AbstractDirectMacroTest
{
    private static final String INTRODUCTION =
        "Projects\nHere are the latest projects you have access to in OpenProject:\n";

    @InjectMockComponents
    private OpenProjectProjectsMacro macro;

    private final OpenProjectProjectsMacroParameters parameters = new OpenProjectProjectsMacroParameters();

    @BeforeEach
    void setUp()
    {
        this.parameters.setInstance("main");
        this.parameters.setCount(3);
    }

    private static Project project(String name, Linkable self)
    {
        Project project = new Project();
        project.setName(name);
        project.setSelf(self);
        return project;
    }

    @Test
    void listsTheProjects() throws Exception
    {
        when(this.apiClient.getProjects(null, 3, "")).thenReturn(new PaginatedResult<>(
            List.of(project("Alpha", new Linkable("Alpha", SERVER_URL + "/projects/alpha")),
                project("Beta", null)), 1, 3, 2));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals(INTRODUCTION + "Alpha\nBeta\n", text(result));
        // A project without self link is linked to "#".
        assertEquals(List.of("Alpha -> " + SERVER_URL + "/projects/alpha", "Beta -> #"), links(result));
    }

    @Test
    void listsNoProject() throws Exception
    {
        when(this.apiClient.getProjects(null, 3, "")).thenReturn(new PaginatedResult<>(List.of(), 1, 3, 0));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals(INTRODUCTION + "No projects found.\n", text(result));
        assertEquals(List.of(), links(result));
    }
}
