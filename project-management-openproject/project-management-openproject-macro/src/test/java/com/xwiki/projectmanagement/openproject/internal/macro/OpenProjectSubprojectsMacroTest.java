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
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectSubprojectsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectSubprojectsMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectSubprojectsMacroTest extends AbstractDirectMacroTest
{
    private static final String ANCESTOR_FILTER = "[{\"ancestor\":{\"operator\":\"=\",\"values\":[\"5\"]}}]";

    @InjectMockComponents
    private OpenProjectSubprojectsMacro macro;

    private final OpenProjectSubprojectsMacroParameters parameters = new OpenProjectSubprojectsMacroParameters();

    @BeforeEach
    void setUp() throws Exception
    {
        this.parameters.setInstance("main");
        this.parameters.setProject("5");
        this.parameters.setCount(10);
        when(this.apiClient.getProject(5)).thenReturn(project("Parent", "/projects/parent"));
    }

    private static Project project(String name, String path)
    {
        Project project = new Project();
        project.setName(name);
        project.setSelf(new Linkable(name, SERVER_URL + path));
        return project;
    }

    @Test
    void listsTheSubprojects() throws Exception
    {
        when(this.apiClient.getProjects(null, 10, ANCESTOR_FILTER)).thenReturn(new PaginatedResult<>(
            List.of(project("Child 1", "/projects/child1"), project("Child 2", "/projects/child2")), 1, 10, 2));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("Parent\nChild 1\nChild 2\n", text(result));
        assertEquals(List.of("Parent -> " + SERVER_URL + "/projects/parent",
            "Child 1 -> " + SERVER_URL + "/projects/child1", "Child 2 -> " + SERVER_URL + "/projects/child2"),
            links(result));
    }

    @Test
    void listsNoSubproject() throws Exception
    {
        when(this.apiClient.getProjects(null, 10, ANCESTOR_FILTER))
            .thenReturn(new PaginatedResult<>(List.of(), 1, 10, 0));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("Parent\nNo subprojects found.\n", text(result));
    }

    @Test
    void failsWhenTheProjectCannotBeRetrieved() throws Exception
    {
        ProjectManagementException cause = new ProjectManagementException("not found");
        when(this.apiClient.getProject(5)).thenThrow(cause);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        assertEquals("Failed to retrieve subprojects for project [5].", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
