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
package com.xwiki.projectmanagement.macro;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.internal.WorkItemsDisplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the displayer parameters of {@link ProjectManagementMacroParameters}.
 *
 * @version $Id$
 */
class ProjectManagementMacroParametersTest
{
    private final ProjectManagementMacroParameters parameters = new ProjectManagementMacroParameters();

    @Test
    void displayerDefaultsToTable()
    {
        assertEquals(WorkItemsDisplayer.TABLE, this.parameters.getDisplayer());
        assertNull(this.parameters.getWorkItemsDisplayer());
    }

    @Test
    void legacyDisplayer()
    {
        this.parameters.setWorkItemsDisplayer("workItemsEnum");

        assertEquals(WorkItemsDisplayer.ENUM, this.parameters.getDisplayer());
        assertEquals("workItemsEnum", this.parameters.getWorkItemsDisplayer());
    }

    @Test
    void displayerTakesPrecedenceOverTheLegacyOne()
    {
        // The macro editor can leave the deprecated parameter of an existing macro next to the new one.
        this.parameters.setDisplayer(WorkItemsDisplayer.LIST);
        this.parameters.setWorkItemsDisplayer("workItemsSingle");

        assertEquals(WorkItemsDisplayer.LIST, this.parameters.getDisplayer());
    }

    @Test
    void unknownLegacyDisplayer()
    {
        IllegalArgumentException exception =
            assertThrows(IllegalArgumentException.class, () -> this.parameters.setWorkItemsDisplayer("html"));

        assertEquals("Unknown displayer [html]. Allowed values are liveData, liveDataCards, workItemsSingle, "
            + "workItemInline, workItemsList, workItemsEnum.", exception.getMessage());
    }

    @Test
    void displayerValues() throws Exception
    {
        // The displayer parameter takes the lower case names, also accepted with the legacy ones by the REST API.
        assertEquals("enum", WorkItemsDisplayer.ENUM.toString());
        assertEquals(WorkItemsDisplayer.CARDS, WorkItemsDisplayer.fromString("cards"));
        assertEquals(WorkItemsDisplayer.CARDS, WorkItemsDisplayer.fromString("LIVEDATACARDS"));
        assertNull(WorkItemsDisplayer.fromString(null));
        // The macro editor omits the displayer parameter when its value is the JSON serialization of the default one.
        assertEquals("\"table\"", new ObjectMapper().writeValueAsString(WorkItemsDisplayer.TABLE));
    }
}
