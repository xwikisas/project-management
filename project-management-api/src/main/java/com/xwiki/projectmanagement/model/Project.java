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
package com.xwiki.projectmanagement.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes a project coming from a project management platform.
 *
 * @version $Id$
 * @since 1.2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Project extends HashMap<String, Object>
{
    /**
     * The key identifying the identifier property of the project.
     */
    public static final String KEY_IDENTIFIER = "identifier";

    /**
     * The key identifying the name property of the project.
     */
    public static final String KEY_NAME = "name";

    /**
     * The key identifying the description property of the project.
     */
    public static final String KEY_DESCRIPTION = "description";

    /**
     * @return the identifier for this project together with a link to its location. For example, Open project and
     *     GitHub identify work packages numerically: '1001', Jira using a key: 'XWIKI-1001'.
     */
    @JsonProperty(KEY_IDENTIFIER)
    public Linkable getIdentifier()
    {
        return (Linkable) get(KEY_IDENTIFIER);
    }

    /**
     * @param identifier see {@link #getIdentifier()}.
     */
    @JsonProperty(KEY_IDENTIFIER)
    public void setIdentifier(Linkable identifier)
    {
        put(KEY_IDENTIFIER, identifier);
    }

    /**
     * @return a user facing, pretty name of the project.
     */
    @JsonProperty(KEY_NAME)
    public String getName()
    {
        return (String) get(KEY_NAME);
    }

    /**
     * @param name see {@link #getName()}}.
     */
    @JsonProperty(KEY_NAME)
    public void setName(String name)
    {
        put(KEY_NAME, name);
    }

    /**
     * @return a text that describes this project.
     */
    @JsonProperty(KEY_DESCRIPTION)
    public String getDescription()
    {
        return (String) get(KEY_DESCRIPTION);
    }

    /**
     * @param description see {@link #getDescription()}.
     */
    @JsonProperty(KEY_DESCRIPTION)
    public void setDescription(String description)
    {
        put(KEY_DESCRIPTION, description);
    }

    /**
     * @return the map.
     */
    @JsonAnyGetter
    public Map<String, Object> getMapEntries()
    {
        return this;
    }

    /**
     * @param key the key.
     * @param value the value.
     */
    @JsonAnySetter
    public void putEntry(String key, Object value)
    {
        this.put(key, value);
    }
}
