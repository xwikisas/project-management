package com.xwiki.projectmanagement.source;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveDataQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WorkItemSelection}.
 *
 * @version $Id$
 */
class WorkItemSelectionTest
{
    @Test
    void keepsACopyOfItsParts()
    {
        List<LiveDataQuery.Filter> filters = new ArrayList<>(List.of(new LiveDataQuery.Filter("status", "=", "1")));
        List<String> ids = new ArrayList<>(List.of("2", "1"));
        Map<String, String> clientScope = new HashMap<>(Map.of("key", "value"));

        WorkItemSelection selection = new WorkItemSelection(filters, ids, clientScope);
        filters.clear();
        ids.clear();
        clientScope.clear();

        assertEquals("status", selection.getFilters().get(0).getProperty());
        assertEquals(List.of("2", "1"), selection.getIds());
        assertEquals(Map.of("key", "value"), selection.getClientScope());
        assertThrows(UnsupportedOperationException.class, () -> selection.getIds().add("3"));
    }

    @Test
    void selectsByASinglePart()
    {
        assertEquals(1, WorkItemSelection.ofFilters(List.of(new LiveDataQuery.Filter())).getFilters().size());
        assertTrue(WorkItemSelection.ofFilters(List.of()).getIds().isEmpty());
        assertEquals(List.of("1"), WorkItemSelection.ofIds(List.of("1")).getIds());
        assertTrue(WorkItemSelection.ofIds(List.of("1")).getClientScope().isEmpty());
        assertEquals(Map.of("a", "b"), WorkItemSelection.ofClientScope(Map.of("a", "b")).getClientScope());
        assertTrue(WorkItemSelection.ofClientScope(Map.of("a", "b")).getFilters().isEmpty());
    }
}
