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
package com.xwiki.projectmanagement.openproject.model;

import java.util.HashMap;
import java.util.Map;


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes the priority of work package.
 *
 * @version $Id$
 * @since 1.0
 */
public class Priority extends HashMap<String, Object>
{
    /**
     * The key identifying the id property of the priority.
     */
    public static final String KEY_ID = "id";

    /**
     * The key identifying the name property of the priority.
     */
    public static final String KEY_NAME = "name";

    /**
     * @return the id of the work item priority.
     */
    @JsonProperty
    public Integer getId()
    {
        return (Integer) get(KEY_ID);
    }

    /**
     * @param id see {@link #getId()}.
     */
    @JsonProperty(KEY_ID)
    public void setId(int id)
    {
        put(KEY_ID, id);
    }

    /**
     * @return the name of the work item priority.
     */
    @JsonProperty
    public String getName()
    {
        return (String) get(KEY_NAME);
    }

    /**
     * @param name see {@link #getName()}.
     */
    @JsonProperty
    public void setName(String name)
    {
        put(KEY_NAME, name);
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
