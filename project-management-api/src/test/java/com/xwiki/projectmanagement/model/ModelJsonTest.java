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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JSON (de)serialization of the model classes, as used by the REST resources and the script
 * service.
 *
 * @version $Id$
 */
class ModelJsonTest
{
    private static final String URL = "https://openproject.example.org/work_packages/42";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void paginatedResultOmitsNullFields() throws Exception
    {
        assertTrue(new PaginatedResult<String>().getItems().isEmpty());

        PaginatedResult<String> result = new PaginatedResult<>(List.of("a", "b"), 2, null, 12);

        assertEquals(Map.of("items", List.of("a", "b"), "page", 2, "totalItems", 12),
            this.objectMapper.readValue(this.objectMapper.writeValueAsString(result), Map.class));
    }

    @Test
    void linkableRoundTrip() throws Exception
    {
        String json = this.objectMapper.writeValueAsString(new Linkable("#42", URL));

        assertEquals(Map.of(Linkable.KEY_VALUE, "#42", Linkable.KEY_LOCATION, URL),
            this.objectMapper.readValue(json, Map.class));
        Linkable linkable = this.objectMapper.readValue(json, Linkable.class);
        assertEquals("#42", linkable.getValue());
        assertEquals(URL, linkable.getLocation());
    }

    @Test
    void workItemKeepsCustomProperties() throws Exception
    {
        WorkItem workItem = new WorkItem();
        workItem.setIdentifier(new Linkable("42", URL));
        workItem.setStatus("open");
        workItem.setLabels(List.of("bug"));
        workItem.putEntry("customField", "custom value");

        assertEquals("custom value", workItem.getMapEntries().get("customField"));
        assertEquals(Map.of(WorkItem.KEY_IDENTIFIER, Map.of(Linkable.KEY_VALUE, "42", Linkable.KEY_LOCATION, URL),
            WorkItem.KEY_STATUS, "open", WorkItem.KEY_LABELS, List.of("bug"), "customField", "custom value"),
            this.objectMapper.readValue(this.objectMapper.writeValueAsString(workItem), Map.class));
    }

    @Test
    void workItemDeserialization() throws Exception
    {
        WorkItem workItem = this.objectMapper.readValue(
            "{\"status\":\"open\",\"progress\":50,\"customField\":3}", WorkItem.class);

        assertEquals("open", workItem.getStatus());
        assertEquals(50, workItem.getProgress());
        assertEquals(3, workItem.get("customField"));
    }

    @Test
    void projectKeepsCustomProperties() throws Exception
    {
        Project project = new Project();
        project.setIdentifier(new Linkable("xwiki", URL));
        project.setName("XWiki");
        project.putEntry("active", true);

        assertEquals(true, project.getMapEntries().get("active"));
        assertEquals(Map.of(Project.KEY_IDENTIFIER, Map.of(Linkable.KEY_VALUE, "xwiki", Linkable.KEY_LOCATION, URL),
            Project.KEY_NAME, "XWiki", "active", true),
            this.objectMapper.readValue(this.objectMapper.writeValueAsString(project), Map.class));
    }
}
