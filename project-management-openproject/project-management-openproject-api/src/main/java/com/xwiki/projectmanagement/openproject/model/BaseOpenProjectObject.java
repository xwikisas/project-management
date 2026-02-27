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

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.model.Linkable;

/**
 * Describes the base object for Open Project objects.
 *
 * @version $Id$
 * @since 1.0
 */
public class BaseOpenProjectObject extends HashMap<String, Object>
{
    /**
     * The key identifying the id property of the open project object.
     */
    public static final String KEY_ID = "id";

    /**
     * The key identifying the name property of the open project object.
     */
    public static final String KEY_NAME = "name";

    /**
     * The key identifying the open project object itself.
     */
    private static final String SELF = "self";

    /**
     * Default constructor.
     */
    public BaseOpenProjectObject()
    {

    }

    /**
     * Create a BaseOpenProjectObject from a JsonNode.
     *
     * @param jsonNode the JsonNode containing the base open project object information.
     */
    public BaseOpenProjectObject(JsonNode jsonNode)
    {
        this.setId(jsonNode.path(KEY_ID).asInt());
        this.setName(jsonNode.path(KEY_NAME).asText());
        this.setSelf(new Linkable("", jsonNode.path("_links").path(SELF).path("href").asText()));
    }

    /**
     * @return the id of the work item project.
     */
    public Integer getId()
    {
        return (Integer) get(KEY_ID);
    }

    /**
     * @param id see {@link #getId()}.
     */
    public void setId(int id)
    {
        put(KEY_ID, id);
    }

    /**
     * @return the name of the work item project.
     */
    public String getName()
    {
        return (String) get(KEY_NAME);
    }

    /**
     * @param name see {@link #getName()}.
     */
    public void setName(String name)
    {
        put(KEY_NAME, name);
    }

    /**
     * Gets the reference to itself, including their value and reference link.
     *
     * @return the assignee as a {@link Linkable}
     */
    public Linkable getSelf()
    {
        return (Linkable) get(SELF);
    }

    /**
     * Sets the reference to itself, including their value and reference link.
     *
     * @param self the self to set as a {@link Linkable}
     */
    public void setSelf(Linkable self)
    {
        put(SELF, self);
    }

    /**
     * @return the map.
     */
    public Map<String, Object> getMapEntries()
    {
        return this;
    }

    /**
     * @param key the key.
     * @param value the value.
     */
    public void putEntry(String key, Object value)
    {
        this.put(key, value);
    }

    /**
     * Initializes the self link of the object from a connection URL and a resource path.
     *
     * @param connectionUrl the connection URL to the Open Project instance.
     * @param resourcePath the resource path of the resource.
     * @since 1.1
     */
    public void initializeSelfWithPath(String connectionUrl, String resourcePath)
    {
        this.setSelf(new Linkable("", String.format("%s%s", connectionUrl, resourcePath)));
    }
}
