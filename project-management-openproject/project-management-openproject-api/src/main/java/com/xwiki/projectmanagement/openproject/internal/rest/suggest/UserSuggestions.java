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
package com.xwiki.projectmanagement.openproject.internal.rest.suggest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;

import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.openproject.FilterBuilder;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.model.User;

/**
 * Suggests the OpenProject users for the {@link Suggest} endpoint.
 *
 * @version $Id$
 * @since 1.5.0
 */
final class UserSuggestions
{
    private static final String NAME = "name";

    /**
     * The maximum number of users whose name contains the name of a selected user, among which the ones having
     * exactly this name are counted.
     */
    private static final int SAME_NAME_PAGE_SIZE = 100;

    private UserSuggestions()
    {
    }

    /**
     * @param searchString the text, in lower case, that the names of the suggested users contain
     * @param selectedIdFilter the filter on the id of the selected user, whose suggestion is returned alone, or
     *     {@code null} to search the users by name
     * @return the suggestions of the users
     */
    static List<Map<String, String>> get(OpenProjectApiClient openProjectApiClient, String searchString,
        int pageSize, String selectedIdFilter) throws ProjectManagementException
    {
        boolean selected = StringUtils.isNotEmpty(selectedIdFilter);
        String filter = selected ? selectedIdFilter : buildNameFilter(searchString);
        List<User> users = openProjectApiClient.getUsers(1, pageSize, filter).getItems();
        // A user is written by name only when no other user has the same name: the other users having it are among
        // the found ones (their name contains the searched text too), unless the search returned a full page.
        Set<String> uniqueNames;
        if (selected) {
            uniqueNames = users.size() == 1 && isOnlyUserNamed(openProjectApiClient, users.get(0).getName())
                ? NamedSuggestions.getUniqueNames(users) : Collections.emptySet();
        } else {
            uniqueNames = users.size() < pageSize ? NamedSuggestions.getUniqueNames(users) : Collections.emptySet();
        }
        // The suggestions of the users sharing their name with other suggested users are told apart by a hint. A
        // selected user is retrieved alone: it shares its name when other users have it.
        List<Map<String, String>> suggestions = NamedSuggestions.getNamedSuggestions(users, uniqueNames);
        return IntStream.range(0, users.size()).mapToObj(index -> {
            User user = users.get(index);
            boolean sharedName = selected ? user.getName() == null
                || !uniqueNames.contains(user.getName().toLowerCase())
                : NamedSuggestions.countSameName(users, user.getName()) > 1;
            return sharedName ? NamedSuggestions.withHint(suggestions.get(index), getHint(user))
                : suggestions.get(index);
        }).collect(Collectors.toList());
    }

    /**
     * @return what tells a user apart from the users sharing its name: its login, when OpenProject gives it to the
     *     current user (only to the users who can manage the users, who can see it in OpenProject too), else its id
     *     (e.g. {@code #5}); never its e-mail address
     */
    private static String getHint(User user)
    {
        return StringUtils.isNotEmpty(user.getLogin()) ? user.getLogin() : "#" + user.getId();
    }

    private static boolean isOnlyUserNamed(OpenProjectApiClient openProjectApiClient, String name)
        throws ProjectManagementException
    {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        List<User> users = openProjectApiClient.getUsers(1, SAME_NAME_PAGE_SIZE, buildNameFilter(name.toLowerCase()))
            .getItems();
        return users.size() < SAME_NAME_PAGE_SIZE && NamedSuggestions.countSameName(users, name) == 1;
    }

    private static String buildNameFilter(String name)
    {
        return new FilterBuilder().addFilter(NAME, FilterBuilder.Operator.CONTAINS, name).build();
    }
}
