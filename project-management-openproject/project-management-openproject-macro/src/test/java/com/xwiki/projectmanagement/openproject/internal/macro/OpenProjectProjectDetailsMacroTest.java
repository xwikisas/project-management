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

import java.util.GregorianCalendar;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectDetailsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.Project;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectProjectDetailsMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectProjectDetailsMacroTest extends AbstractDirectMacroTest
{
    @InjectMockComponents
    private OpenProjectProjectDetailsMacro macro;

    private final OpenProjectProjectDetailsMacroParameters parameters = new OpenProjectProjectDetailsMacroParameters();

    @BeforeEach
    void setUp()
    {
        this.parameters.setInstance("main");
        this.parameters.setProject("7");
    }

    @Test
    void displaysAllTheDetails() throws Exception
    {
        Project project = new Project();
        project.setId(7);
        project.setName("Alpha");
        project.setSelf(new Linkable("Alpha", SERVER_URL + "/projects/alpha"));
        project.setIdentifier("alpha");
        project.setDescription("The first project");
        project.setStatus(new Linkable("On track", SERVER_URL + "/statuses/on_track"));
        project.setCreatedAt(new GregorianCalendar(2026, 0, 2).getTime());
        when(this.apiClient.getProject(7)).thenReturn(project);

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        String[] lines = text(result).split("\n");
        assertEquals(6, lines.length);
        assertEquals("ID: 7", lines[0]);
        // The label of the name is not asserted because it's not capitalized like the other labels ("name").
        assertTrue(lines[1].endsWith(": Alpha"));
        assertEquals("Identifier: alpha", lines[2]);
        assertEquals("Description: The first project", lines[3]);
        assertEquals("Status: On track", lines[4]);
        // The creation date uses the date format configured in the wiki.
        assertEquals("Created: 2026-01-02", lines[5]);
        assertEquals(List.of("Alpha -> " + SERVER_URL + "/projects/alpha"), links(result));
    }

    @Test
    void skipsTheMissingDetails() throws Exception
    {
        Project project = new Project();
        project.setId(7);
        project.setSelf(new Linkable(" ", SERVER_URL + "/projects/alpha"));
        project.setDescription("");
        when(this.apiClient.getProject(7)).thenReturn(project);

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("ID: 7\n", text(result));
    }

    @Test
    void failsWhenTheProjectCannotBeRetrieved() throws Exception
    {
        ProjectManagementException cause = new ProjectManagementException("not found");
        when(this.apiClient.getProject(7)).thenThrow(cause);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        assertEquals("Failed to retrieve project [7] from OpenProject.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
