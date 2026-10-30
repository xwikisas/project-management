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
package com.xwiki.projectmanagement.openproject.internal.rest.document;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes the client parameters stored in the relations to OpenProject.
 *
 * @version $Id$
 */
final class RelationClientParams
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RelationClientParams()
    {
    }

    /**
     * @param instance the name of the OpenProject connection
     * @return the client parameters, as JSON, holding the given connection
     */
    static String ofInstance(String instance)
    {
        try {
            return MAPPER.writeValueAsString(Map.of("instance", instance));
        } catch (JsonProcessingException e) {
            // Can't happen since the map only holds a string.
            throw new IllegalStateException("Failed to serialize the client parameters.", e);
        }
    }
}
