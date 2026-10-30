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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.macro.OpenProjectProjectMembersMacroParameters;
import com.xwiki.projectmanagement.openproject.model.User;

import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.links;
import static com.xwiki.projectmanagement.openproject.internal.OpenProjectMacroTestUtils.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenProjectProjectMembersMacro}.
 *
 * @version $Id$
 */
@ComponentTest
class OpenProjectProjectMembersMacroTest extends AbstractDirectMacroTest
{
    private static final String MEMBERS_FILTER = "[{\"project\":{\"operator\":\"=\",\"values\":[\"12\"]}}]";

    private static final String VIEW_ALL = "View all members -> " + SERVER_URL + "/projects/12/members";

    @InjectMockComponents
    private OpenProjectProjectMembersMacro macro;

    private final OpenProjectProjectMembersMacroParameters parameters =
        new OpenProjectProjectMembersMacroParameters();

    @BeforeEach
    void setUp()
    {
        this.parameters.setInstance("main");
        this.parameters.setProject("12");
        this.parameters.setCount(20);
    }

    private static User user(String name, String... roles)
    {
        User user = new User();
        user.setName(name);
        user.setSelf(new Linkable(name, SERVER_URL + "/users/" + name));
        if (roles.length > 0) {
            user.setRoles(Arrays.stream(roles).map(role -> new Linkable(role, "/roles/" + role))
                .collect(Collectors.toList()));
        }
        return user;
    }

    @Test
    void groupsTheMembersByRole() throws Exception
    {
        when(this.apiClient.getMemberships(null, 20, MEMBERS_FILTER)).thenReturn(new PaginatedResult<>(
            List.of(user("alice", "Developer", "Admin"), user("bob", "Developer"), user("carol")), 1, 20, 3));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        // Roles are listed in the order they are found, members without role are listed under "Unknown".
        assertEquals("Developer\nalice, bob\nAdmin\nalice\nUnknown\ncarol\n View all members", text(result));
        assertEquals(List.of("alice -> " + SERVER_URL + "/users/alice", "bob -> " + SERVER_URL + "/users/bob",
            "alice -> " + SERVER_URL + "/users/alice", "carol -> " + SERVER_URL + "/users/carol", VIEW_ALL),
            links(result));
    }

    @Test
    void displaysNoMember() throws Exception
    {
        when(this.apiClient.getMemberships(null, 20, MEMBERS_FILTER))
            .thenReturn(new PaginatedResult<>(List.of(), 1, 20, 0));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("No members found.\n View all members", text(result));
        assertEquals(List.of(VIEW_ALL), links(result));
    }

    @Test
    void filtersOnAProjectWithSpecialCharacters() throws Exception
    {
        this.parameters.setProject("a\"b\\");
        when(this.apiClient.getMemberships(null, 20,
            "[{\"project\":{\"operator\":\"=\",\"values\":[\"a\\\"b\\\\\"]}}]"))
            .thenReturn(new PaginatedResult<>(List.of(), 1, 20, 0));

        List<Block> result = this.macro.execute(this.parameters, "", this.context);

        assertEquals("No members found.\n View all members", text(result));
        assertEquals(List.of("View all members -> " + SERVER_URL + "/projects/a%22b%5C/members"), links(result));
    }

    @Test
    void linksToTheMembersOfAProjectWithReservedURLCharacters() throws Exception
    {
        this.parameters.setProject("a/b c?d#e");
        when(this.apiClient.getMemberships(null, 20,
            "[{\"project\":{\"operator\":\"=\",\"values\":[\"a/b c?d#e\"]}}]"))
            .thenReturn(new PaginatedResult<>(List.of(), 1, 20, 0));

        assertEquals(List.of("View all members -> " + SERVER_URL + "/projects/a%2Fb%20c%3Fd%23e/members"),
            links(this.macro.execute(this.parameters, "", this.context)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", " " })
    void failsWhenTheProjectIsNotSet(String project)
    {
        this.parameters.setProject(project);

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(this.parameters, "", this.context));

        // The members of all the projects are not listed.
        assertEquals("The project whose members to display is missing.", exception.getMessage());
        verifyNoInteractions(this.apiClient);
    }
}
