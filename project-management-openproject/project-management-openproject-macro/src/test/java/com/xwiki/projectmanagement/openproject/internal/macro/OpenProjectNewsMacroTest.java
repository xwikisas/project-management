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
import org.xwiki.rendering.block.HorizontalLineBlock;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectNewsMacroParameters;
import com.xwiki.projectmanagement.openproject.model.News;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.date;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectNewsMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectNewsMacroTest extends AbstractDirectMacroTest
{
    @InjectMockComponents
    private OpenProjectNewsMacro macro;

    private final OpenProjectNewsMacroParameters parameters = new OpenProjectNewsMacroParameters();

    @BeforeEach
    void setUp()
    {
        this.parameters.setInstance("main");
        this.parameters.setCount(2);
    }

    private static News news(int id, String title, Linkable project)
    {
        News news = new News();
        news.setId(id);
        news.setTitle(title);
        news.setSelf(new Linkable(title, SERVER_URL + "/news/" + id));
        news.setProjectLink(project);
        news.setAuthor(new Linkable("Alice", SERVER_URL + "/users/1"));
        news.setCreatedAt(date(2026, 2, 3));
        news.setSummary("Summary " + id);
        return news;
    }

    @Test
    void displaysTheNewsOfAProject() throws Exception
    {
        this.parameters.setProject("12");
        when(this.apiClient.getNews(null, 2, "[{\"project_id\":{\"operator\":\"=\",\"values\":[\"12\"]}}]"))
            .thenReturn(new PaginatedResult<>(List.of(
                news(1, "Release", new Linkable("Alpha", SERVER_URL + "/projects/alpha")),
                news(2, "Meeting", new Linkable("", SERVER_URL + "/projects/none"))), 1, 2, 2));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        // The project is displayed before the title only when the news has a project name.
        assertEquals("Alpha: Release\nAlice · 2026-02-03\nSummary 1\nMeeting\nAlice · 2026-02-03\nSummary 2\n",
            text(result));
        assertEquals(List.of("Alpha -> " + SERVER_URL + "/projects/alpha", "Release -> " + SERVER_URL + "/news/1",
            "Alice -> " + SERVER_URL + "/users/1", "Meeting -> " + SERVER_URL + "/news/2",
            "Alice -> " + SERVER_URL + "/users/1"), links(result));
        // The news are separated by a horizontal line.
        assertEquals(1,
            result.get(0).getBlocks(new ClassBlockMatcher(HorizontalLineBlock.class), Block.Axes.DESCENDANT).size());
    }

    @Test
    void filtersOnAProjectWithSpecialCharacters() throws Exception
    {
        this.parameters.setProject("a\"b\\");
        when(this.apiClient.getNews(null, 2,
            "[{\"project_id\":{\"operator\":\"=\",\"values\":[\"a\\\"b\\\\\"]}}]"))
            .thenReturn(new PaginatedResult<>(List.of(), 1, 2, 0));

        assertEquals("No news found.\n", text(this.macro.execute(this.parameters, "", this.context)));
    }

    @Test
    void displaysTheNewsOfAllProjects() throws Exception
    {
        when(this.apiClient.getNews(null, 2, ""))
            .thenReturn(new PaginatedResult<>(List.of(news(1, "Release", null)), 1, 2, 1));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("Release\nAlice · 2026-02-03\nSummary 1\n", text(result));
    }

    @Test
    void failsWhenTheDashboardProjectIsNotResolved()
    {
        this.parameters.setProject("use_selected_dashboard_project");

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        assertEquals("The project selected on the OpenProject dashboard couldn't be resolved. Select a project "
            + "explicitly when the macro is not on the dashboard.", exception.getMessage());
    }

    @Test
    void displaysNoNews() throws Exception
    {
        this.parameters.setProject(" ");
        when(this.apiClient.getNews(null, 2, "")).thenReturn(new PaginatedResult<>(List.of(), 1, 2, 0));

        assertEquals("No news found.\n", text(this.macro.execute(this.parameters, "", this.context)));
    }

    @Test
    void failsWhenTheNewsCannotBeRetrieved() throws Exception
    {
        ProjectManagementException cause = new ProjectManagementException("unreachable");
        when(this.apiClient.getNews(null, 2, "")).thenThrow(cause);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        assertEquals("Failed to retrieve news from OpenProject.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
