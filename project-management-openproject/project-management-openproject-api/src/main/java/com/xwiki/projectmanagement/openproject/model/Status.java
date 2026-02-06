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

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.projectmanagement.model.Linkable;

/**
 * Describes the type object of a work package.
 *
 * @version $Id$
 * @since 1.0
 */
public class Status extends ColoredOpenProjectObject
{
    /**
     * Create a Status object from a JsonNode.
     *
     * @param statusNode the JsonNode containing the status information.
     * @param connectionUrl the connection URL of the OpenProject instance. If this parameter is set up, the
     *     reference of the status will point to the OpenProject instance, otherwise it will point to the API
     *     reference.
     */
    public Status(JsonNode statusNode, String connectionUrl)
    {
        int id = statusNode.path("id").asInt();
        String name = statusNode.path("name").asText();
        String color = statusNode.path("color").asText();

        this.setId(id);
        this.setName(name);
        this.setColor(color);

        if (connectionUrl == null || connectionUrl.isEmpty()) {
            String selfHrefStr = statusNode.path("_links").path("self").path("href").asText();
            this.setSelf(new Linkable("", selfHrefStr));
        } else {
            this.setSelf(new Linkable("", String.format("%s/statuses/%s/edit", connectionUrl, id)));
        }
    }

    /**
     * Default constructor.
     */
    public Status()
    {
    }
}
