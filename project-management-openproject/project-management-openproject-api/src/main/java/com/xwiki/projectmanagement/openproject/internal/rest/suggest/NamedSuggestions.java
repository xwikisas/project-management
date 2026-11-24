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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.xwiki.projectmanagement.openproject.model.BaseOpenProjectObject;

/**
 * Builds the suggestions returned by the {@link Suggest} endpoint, and tells which of the suggested resources are
 * identified by their name.
 *
 * @version $Id$
 * @since 1.5.0
 */
final class NamedSuggestions
{
    static final String HINT = "hint";

    private static final String VALUE = "value";

    private static final String LABEL = "label";

    private static final String URL = "url";

    /**
     * The text written for the suggested value in the query of a macro content, which OpenProject resolves back into
     * the value: the identifier of a project (which doesn't change when the project is renamed), the name of a status,
     * a type, a priority, and the name of a version or a user when no other one has it. The values without it are
     * written by id.
     */
    private static final String QUERY_VALUE = "queryValue";

    private NamedSuggestions()
    {
    }

    static Map<String, String> create(String value, String label, String url)
    {
        return Map.of(
            VALUE, value,
            LABEL, label,
            URL, url
        );
    }

    static Map<String, String> create(String value, String label, String url, String hint)
    {
        return Map.of(
            VALUE, value,
            LABEL, label,
            URL, url,
            HINT, hint
        );
    }

    static Map<String, String> withQueryValue(Map<String, String> suggestion, String queryValue)
    {
        if (StringUtils.isEmpty(queryValue)) {
            return suggestion;
        }
        return Map.of(VALUE, suggestion.get(VALUE), LABEL, suggestion.get(LABEL), URL, suggestion.get(URL),
            QUERY_VALUE, queryValue);
    }

    static Map<String, String> withHint(Map<String, String> suggestion, String hint)
    {
        if (StringUtils.isEmpty(hint)) {
            return suggestion;
        }
        Map<String, String> suggestionWithHint = new HashMap<>(suggestion);
        suggestionWithHint.put(HINT, hint);
        return suggestionWithHint;
    }

    static long countSameName(List<? extends BaseOpenProjectObject> objects, String name)
    {
        return objects.stream().filter(object -> name != null && name.equalsIgnoreCase(object.getName())).count();
    }

    /**
     * @return the names, in lower case, that only one of the given resources has
     */
    static Set<String> getUniqueNames(List<? extends BaseOpenProjectObject> objects)
    {
        return objects.stream().map(BaseOpenProjectObject::getName)
            .filter(name -> StringUtils.isNotBlank(name) && countSameName(objects, name) == 1)
            .map(String::toLowerCase).collect(Collectors.toSet());
    }

    /**
     * @return the suggestions of the given resources, the ones whose name no other of them has being written by name
     */
    static List<Map<String, String>> getNamedSuggestions(List<? extends BaseOpenProjectObject> openProjectObjects)
    {
        return getNamedSuggestions(openProjectObjects, getUniqueNames(openProjectObjects));
    }

    /**
     * @param uniqueNames the names, in lower case, that identify a resource: the resources having one of them are
     *     written by name in a query, the others by id
     * @return the suggestions of the given resources
     */
    static List<Map<String, String>> getNamedSuggestions(List<? extends BaseOpenProjectObject> openProjectObjects,
        Set<String> uniqueNames)
    {
        return openProjectObjects.stream().map(object -> getNamedSuggestion(object, uniqueNames))
            .collect(Collectors.toList());
    }

    static Map<String, String> getNamedSuggestion(BaseOpenProjectObject object, Set<String> uniqueNames)
    {
        Map<String, String> suggestion = create(String.valueOf(object.getId()), object.getName(),
            object.getSelf().getLocation());
        return object.getName() != null && uniqueNames.contains(object.getName().toLowerCase())
            ? withQueryValue(suggestion, object.getName()) : suggestion;
    }
}
